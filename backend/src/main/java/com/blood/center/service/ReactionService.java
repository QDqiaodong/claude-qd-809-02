package com.blood.center.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blood.center.common.BizException;
import com.blood.center.common.BloodDict;
import com.blood.center.domain.BloodBag;
import com.blood.center.domain.CollectionRecord;
import com.blood.center.domain.CrossMatch;
import com.blood.center.domain.Donor;
import com.blood.center.domain.PreparationBatch;
import com.blood.center.domain.ReactionBagLink;
import com.blood.center.domain.ReactionLedger;
import com.blood.center.domain.ScrapRecord;
import com.blood.center.domain.TestSample;
import com.blood.center.domain.TestRetestRound;
import com.blood.center.domain.TransfusionReaction;
import com.blood.center.dto.ReactionLedgerVO;
import com.blood.center.dto.ReactionLinkVO;
import com.blood.center.dto.ReactionReportRequest;
import com.blood.center.dto.ReactionReviewRequest;
import com.blood.center.dto.ReactionVO;
import com.blood.center.dto.TraceBagVO;
import com.blood.center.dto.TraceChainVO;
import com.blood.center.event.ReactionReviewedEvent;
import com.blood.center.repository.BloodBagRepository;
import com.blood.center.repository.CrossMatchRepository;
import com.blood.center.repository.PreparationBatchRepository;
import com.blood.center.repository.ReactionBagLinkRepository;
import com.blood.center.repository.ReactionLedgerRepository;
import com.blood.center.repository.ReactionRepository;
import com.blood.center.repository.ScrapRecordRepository;
import com.blood.center.repository.TestRetestRoundRepository;
import com.blood.center.repository.TestSampleRepository;

/**
 * 输血不良反应追溯台：
 * 上报（幂等）→ 同源血袋隔离（在库全部成分血袋）→ 质量复核（排除关联释放 / 确认风险报废）。
 *
 * 并发红线：
 * 1. 同一发血记录 + 反应时间（分钟）+ 症状相同，只允许一宗事件：dedup_key 唯一约束 + 事务内重查，
 *    重复点击或两名人员同时上报都命中同一宗，不重复隔离。
 * 2. 血袋状态流转全部走条件式 UPDATE（仅 AVAILABLE→QUARANTINED→AVAILABLE/SCRAPPED），
 *    配血 / 发血与隔离之间用行级条件互斥，不留下可被再次发出的风险血袋。
 * 3. 同一血袋被多个事件隔离时，任一事件在办都不得释放；并发复核用事件行锁串行化，
 *    不重复报废、不重复写报废单 / 扣减库存、不状态倒退。
 * 4. 已发出、已报废血袋只入追溯清单（linkType=TRACE），任何处置都不改写其状态。
 */
@Service
public class ReactionService {

    private static final DateTimeFormatter SEQ_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DEDUP_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReactionRepository reactionRepository;
    private final ReactionBagLinkRepository linkRepository;
    private final ReactionLedgerRepository ledgerRepository;
    private final CrossMatchRepository matchRepository;
    private final BloodBagRepository bagRepository;
    private final PreparationBatchRepository preparationRepository;
    private final TestSampleRepository sampleRepository;
    private final TestRetestRoundRepository retestRepository;
    private final SampleResultResolver resultResolver;
    private final ScrapRecordRepository scrapRepository;
    private final InventoryService inventoryService;
    private final ReactionService self;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public ReactionService(ReactionRepository reactionRepository, ReactionBagLinkRepository linkRepository,
                           ReactionLedgerRepository ledgerRepository, CrossMatchRepository matchRepository,
                           BloodBagRepository bagRepository,
                           PreparationBatchRepository preparationRepository, TestSampleRepository sampleRepository,
                           TestRetestRoundRepository retestRepository, SampleResultResolver resultResolver,
                           ScrapRecordRepository scrapRepository, InventoryService inventoryService,
                           @Lazy ReactionService self, ApplicationEventPublisher eventPublisher) {
        this.reactionRepository = reactionRepository;
        this.linkRepository = linkRepository;
        this.ledgerRepository = ledgerRepository;
        this.matchRepository = matchRepository;
        this.bagRepository = bagRepository;
        this.preparationRepository = preparationRepository;
        this.sampleRepository = sampleRepository;
        this.retestRepository = retestRepository;
        this.resultResolver = resultResolver;
        this.scrapRepository = scrapRepository;
        this.inventoryService = inventoryService;
        this.self = self;
        this.eventPublisher = eventPublisher;
    }

    // ---------------- 来源链追溯 ----------------

    /**
     * 从一条发血记录反查完整来源链：供血血袋 → 制备批次 → 采血记录 → 检验标本。
     */
    @Transactional(readOnly = true)
    public TraceChainVO trace(Long matchId) {
        CrossMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new BizException("发血记录不存在：id=" + matchId));
        if (!Boolean.TRUE.equals(match.getIssued())) {
            throw new BizException("配血单 " + match.getMatchNo() + " 尚未发血，不能针对其上报不良反应");
        }
        return buildChain(match);
    }

    // ---------------- 事件列表 / 详情 ----------------

    @Transactional(readOnly = true)
    public List<ReactionVO> listEvents(String status) {
        List<TransfusionReaction> events = (status == null || status.isBlank())
                ? reactionRepository.findAllByOrderByIdDesc()
                : reactionRepository.findByStatusOrderByIdDesc(status);
        List<ReactionVO> list = new ArrayList<>();
        for (TransfusionReaction event : events) {
            list.add(toSummaryVO(event));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public ReactionVO detail(Long id) {
        TransfusionReaction event = reactionRepository.findById(id)
                .orElseThrow(() -> new BizException("反应事件不存在：id=" + id));
        return toDetailVO(event.getId());
    }

    // ---------------- 上报（幂等） ----------------

    /** 上报前规范化后的参数载体 */
    private record ReportInput(CrossMatch match, String symptoms, LocalDateTime reactionTime,
                               String hospital, String reporter, String dedupKey) {
    }

    /** 上报事务结果：事件 id 与给页面的提示语（含「已有在办事件」提示） */
    public record ReportOutcome(Long eventId, String message) {
    }

    /**
     * 医院上报不良反应（幂等）。
     * 同发血记录 + 同反应时间 + 同症状视为同一宗：返回既有事件并标记 duplicated=true；
     * 内容有差异则另立事件，并在返回信息中提示该发血记录已有在办事件。
     *
     * 本方法不带事务：并发的第二个事务会在唯一约束上失败，且约束违例一旦抛出 Hibernate 会话即作废，
     * 因此「冲突后转读既有事件」必须在事务边界之外完成，不能在同一事务内补救。
     */
    public ReactionVO report(ReactionReportRequest request) {
        ReportInput input = normalizeReport(request);
        TransfusionReaction existing = reactionRepository.findByDedupKey(input.dedupKey()).orElse(null);
        if (existing != null) {
            return withDuplicate(self.detail(existing.getId()),
                    "该反应（相同反应时间与症状）已由 " + existing.getReporter()
                            + " 上报，事件单号 " + existing.getEventNo() + "，不重复建立处置事件");
        }
        ReportOutcome outcome;
        try {
            outcome = self.insertAndQuarantine(input);
        } catch (DataIntegrityViolationException e) {
            // 两名人员同时上报完全相同内容：赢家已提交，输家事务整体回滚，新事务里转读既有事件
            TransfusionReaction raced = reactionRepository.findByDedupKey(input.dedupKey())
                    .orElseThrow(() -> e);
            return withDuplicate(self.detail(raced.getId()),
                    "该反应（相同反应时间与症状）已由 " + raced.getReporter()
                            + " 同时上报，事件单号 " + raced.getEventNo() + "，不重复建立处置事件");
        }
        ReactionVO vo = self.detail(outcome.eventId());
        return new ReactionVO(vo.id(), vo.eventNo(), vo.matchId(), vo.matchNo(), vo.bagNo(), vo.recipient(),
                vo.hospital(), vo.reporter(), vo.symptoms(), vo.reactionTime(), vo.reportTime(), vo.status(),
                vo.statusName(), vo.reviewResult(), vo.reviewResultName(), vo.reviewer(), vo.reviewComment(),
                vo.reviewedAt(), vo.sourceCollectionId(), vo.isolatedCount(), vo.releasedCount(),
                vo.scrappedCount(), vo.traceCount(), false, outcome.message(), vo.links(), vo.ledger(),
                vo.trace());
    }

    /** 参数校验与规范化（只读） */
    private ReportInput normalizeReport(ReactionReportRequest request) {
        if (request.matchId() == null) {
            throw new BizException("请选择一条已发血记录");
        }
        CrossMatch match = matchRepository.findById(request.matchId())
                .orElseThrow(() -> new BizException("发血记录不存在：id=" + request.matchId()));
        if (!Boolean.TRUE.equals(match.getIssued())) {
            throw new BizException("配血单 " + match.getMatchNo() + " 尚未发血，不能针对其上报不良反应");
        }
        String symptoms = request.symptoms() == null ? "" : request.symptoms().trim();
        if (symptoms.isEmpty()) {
            throw new BizException("请登记反应症状");
        }
        if (request.reactionTime() == null) {
            throw new BizException("请选择反应发生时间");
        }
        LocalDateTime reactionTime = request.reactionTime();
        if (reactionTime.isAfter(LocalDateTime.now())) {
            throw new BizException("反应发生时间不能晚于当前时间");
        }
        if (match.getIssueTime() != null && reactionTime.isBefore(match.getIssueTime())) {
            throw new BizException("反应发生时间不能早于该血液的发血时间（"
                    + match.getIssueTime().format(DEDUP_TIME) + "）");
        }
        String hospital = request.hospital() == null || request.hospital().isBlank()
                ? (match.getHospital() == null ? "" : match.getHospital())
                : request.hospital().trim();
        String reporter = request.reporter() == null || request.reporter().isBlank()
                ? "医院上报人" : request.reporter().trim();
        return new ReportInput(match, symptoms, reactionTime, hospital, reporter,
                dedupKey(match.getId(), reactionTime, symptoms));
    }

    /**
     * 独立事务：落事件（唯一约束兜底并发）→ 写台账 → 同源隔离 → 重算库存。
     * 约束冲突时整个事务回滚，不留下半成品隔离；隔离数量与「在办事件」提示随结果返回。
     */
    @Transactional
    public ReportOutcome insertAndQuarantine(ReportInput input) {
        // match 是在无事务的 normalizeReport 中读的（已脱管），这里在事务内重新加载，
        // 并 JOIN FETCH 其供血血袋与采血来源，避免懒加载代理在隔离时无会话可用。
        CrossMatch match = matchRepository.findByIdWithBag(input.match().getId())
                .orElseGet(input::match);
        LocalDateTime now = LocalDateTime.now();

        TransfusionReaction event = new TransfusionReaction();
        event.setEventNo("AR" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", reactionRepository.count() + 1));
        event.setMatch(match);
        event.setMatchNo(match.getMatchNo());
        event.setBagNo(match.getBagNo());
        event.setRecipient(match.getRecipient());
        event.setHospital(input.hospital());
        event.setReporter(input.reporter());
        event.setSymptoms(input.symptoms());
        event.setReactionTime(input.reactionTime());
        event.setReportTime(now);
        event.setStatus("OPEN");
        event.setDedupKey(input.dedupKey());
        event.setCreatedAt(now);
        BloodBag issuedBag = match.getBag();
        event.setSourceCollectionId(issuedBag == null || issuedBag.getCollection() == null
                ? null : issuedBag.getCollection().getId());
        // flush 让唯一约束在本事务内立即判定；冲突直接穿透到外层（事务回滚）
        event = reactionRepository.saveAndFlush(event);

        List<String> quarantinedCells = new ArrayList<>();
        writeLedger(event, null, null, "REPORT",
                "医院上报输血不良反应：症状「" + input.symptoms() + "」；发生时间 "
                        + input.reactionTime().format(DEDUP_TIME) + "；上报医院 " + input.hospital()
                        + "；处置人 " + input.reporter(),
                input.reporter(), now);

        int isolated = quarantineSameSourceBags(event, issuedBag, now, quarantinedCells);
        // 隔离已即时改变在库可用量，同步本次涉及的每个「血型 × 成分」库存格，防止矩阵仍显示可发
        syncCells(quarantinedCells);

        // 内容有差异、且该发血记录已有其他在办事件：独立建档，但必须提示
        String reportMessage = "反应事件 " + event.getEventNo() + " 已建立，同源在库血袋已隔离 "
                + isolated + " 袋";
        final Long newEventId = event.getId();
        List<TransfusionReaction> openOnSameRecord = reactionRepository.findByStatusOrderByIdDesc("OPEN")
                .stream()
                .filter(r -> !r.getId().equals(newEventId)
                        && match.getId().equals(r.getMatch() == null ? null : r.getMatch().getId()))
                .toList();
        if (!openOnSameRecord.isEmpty()) {
            reportMessage += "；提示：该发血记录已有在办事件 " + openOnSameRecord.get(0).getEventNo()
                    + "（反应时间或症状不同，已作为独立事件保留）";
        }
        reactionRepository.save(event);
        return new ReportOutcome(event.getId(), reportMessage);
    }

    // ---------------- 质量复核 ----------------

    /**
     * 质量复核落结论。事件行锁串行化并发复核，重复提交直接拒绝；
     * 排除关联：只释放仍满足原检验放行、未过期、且未被其他在办事件隔离的血袋；
     * 确认风险：隔离袋全部转报废，写报废单、扣减库存，保留完整处置链。
     */
    @Transactional
    public ReactionVO review(Long eventId, ReactionReviewRequest request) {
        TransfusionReaction event = reactionRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new BizException("反应事件不存在：id=" + eventId));
        if (!event.isOpen()) {
            throw new BizException("事件 " + event.getEventNo() + " 已完成复核（"
                    + BloodDict.reactionStatusName(event.getStatus()) + "），不能重复提交复核结论");
        }
        if (request == null || !BloodDict.isValidReactionReview(request.result())) {
            throw new BizException("复核结论只能为 EXCLUDED 排除关联 或 CONFIRMED 确认风险");
        }
        String reviewer = request.reviewer() == null || request.reviewer().isBlank()
                ? "质量复核人" : request.reviewer().trim();
        String comment = request.comment() == null ? null : request.comment().trim();
        LocalDateTime now = LocalDateTime.now();

        List<ReactionBagLink> links = linkRepository.findByEventIdOrderByIdAsc(eventId);
        List<String> affectedCells = new ArrayList<>();
        if ("CONFIRMED".equals(request.result())) {
            int scrapped = 0;
            for (ReactionBagLink link : links) {
                if (!"ISOLATED".equals(link.getLinkType()) || !"QUARANTINED".equals(link.getLinkStatus())) {
                    continue;
                }
                BloodBag bag = link.getBag();
                if (bag == null) {
                    continue;
                }
                // 条件式 UPDATE：仅 QUARANTINED→SCRAPPED，返回 0 说明已被并发处理，绝不重复报废 / 重复扣减
                int changed = bagRepository.scrapIfQuarantined(bag.getId());
                if (changed > 0) {
                    bag.setStatus("SCRAPPED");
                    writeScrapRecord(bag, event, reviewer, now);
                    scrapped++;
                    link.setLinkStatus("SCRAPPED");
                    link.setNote("复核确认风险，同源血袋转报废");
                    link.setUpdatedAt(now);
                    linkRepository.save(link);
                    writeLedger(event, bag.getId(), bag.getBagNo(), "SCRAP",
                            "确认风险：同源血袋转报废并扣减库存", reviewer, now);
                    String cell = bag.getBloodType() + "|" + BloodDict.inventoryComponent(bag.getComponent());
                    if (!affectedCells.contains(cell)) {
                        affectedCells.add(cell);
                    }
                    // 同一血袋若还挂在其他在办事件上，同步把那些隔离关联标注为已报废（其他事件不得再释放它）
                    linkRepository.markOtherEventsScrapped(bag.getId(), eventId,
                            "血袋经事件 " + event.getEventNo() + " 复核确认风险已报废，本事件不再释放", now);
                } else {
                    // 并发窗口已被另一事件报废：以数据库现状为准，只同步本事件关联，不再写报废单 / 扣减
                    entityManager.refresh(bag);
                    if ("SCRAPPED".equals(bag.getStatus())) {
                        link.setLinkStatus("SCRAPPED");
                        link.setNote("血袋已由其他事件复核确认风险报废，本事件不重复报废");
                        link.setUpdatedAt(now);
                        linkRepository.save(link);
                    }
                }
            }
            event.setStatus("CONFIRMED");
            event.setReviewResult("CONFIRMED");
            event.setReviewer(reviewer);
            event.setReviewComment(comment);
            event.setReviewedAt(now);
            reactionRepository.save(event);
            writeLedger(event, null, null, "REVIEW",
                    "质量复核确认风险，隔离血袋报废 " + scrapped + " 袋"
                            + (comment == null || comment.isEmpty() ? "" : "；复核意见：" + comment),
                    reviewer, now);
            syncCells(affectedCells);
        } else {
            int released = 0;
            int kept = 0;
            for (ReactionBagLink link : links) {
                if (!"ISOLATED".equals(link.getLinkType()) || !"QUARANTINED".equals(link.getLinkStatus())) {
                    continue;
                }
                Long bagId = link.getBag() == null ? null : link.getBag().getId();
                if (bagId == null) {
                    continue;
                }
                BloodBag bag = bagRepository.findById(bagId).orElse(null);
                if (bag == null) {
                    continue;
                }
                String holdReason = releaseHoldReason(bag, event);
                if (holdReason != null) {
                    // 仍不满足释放条件（含血袋在并发窗口已被确认风险报废）：保持隔离，台账逐条说明
                    if ("SCRAPPED".equals(bag.getStatus())) {
                        link.setLinkStatus("SCRAPPED");
                        link.setNote("血袋已被其他事件复核确认风险报废");
                    } else {
                        link.setLinkStatus("KEPT");
                        link.setNote(holdReason);
                    }
                    link.setUpdatedAt(now);
                    linkRepository.save(link);
                    kept++;
                    writeLedger(event, bagId, bag.getBagNo(),
                            "SCRAPPED".equals(bag.getStatus()) ? "SCRAP" : "KEEP",
                            "SCRAPPED".equals(bag.getStatus())
                                    ? "血袋已被其他事件复核确认风险报废，本事件不释放" : holdReason,
                            reviewer, now);
                    continue;
                }
                // 条件式 UPDATE：仅 QUARANTINED→AVAILABLE，杜绝状态倒退
                int changed = bagRepository.releaseIfQuarantined(bagId);
                if (changed > 0) {
                    bag.setStatus("AVAILABLE");
                    link.setLinkStatus("RELEASED");
                    link.setNote("复核排除关联，血袋恢复可用");
                    link.setUpdatedAt(now);
                    linkRepository.save(link);
                    released++;
                    writeLedger(event, bagId, bag.getBagNo(), "RELEASE",
                            "排除关联：血袋恢复可用并回补库存", reviewer, now);
                    String cell = bag.getBloodType() + "|" + BloodDict.inventoryComponent(bag.getComponent());
                    if (!affectedCells.contains(cell)) {
                        affectedCells.add(cell);
                    }
                }
            }
            event.setStatus("EXCLUDED");
            event.setReviewResult("EXCLUDED");
            event.setReviewer(reviewer);
            event.setReviewComment(comment);
            event.setReviewedAt(now);
            reactionRepository.save(event);
            writeLedger(event, null, null, "REVIEW",
                    "质量复核排除关联，释放血袋 " + released + " 袋，保持隔离 " + kept + " 袋"
                            + (comment == null || comment.isEmpty() ? "" : "；复核意见：" + comment),
                    reviewer, now);
            syncCells(affectedCells);
        }
        // 复核事务提交后补扫孤儿隔离（并发两宗排除关联同时结束时，最后一方负责释放）
        eventPublisher.publishEvent(new ReactionReviewedEvent(eventId, request.result()));
        return toDetailVO(event.getId());
    }

    /**
     * 复核提交后的孤儿隔离补扫（由监听器在新事务中调用）。
     * 只处理「该血袋的所有关联事件都已结束、但血袋仍是 QUARANTINED、且有关联仍挂隔离态」的血袋：
     * 仍满足原检验放行 + 未过期 → 释放回库，并把各已结束事件的残留关联同步为 RELEASED；
     * 不满足 → 保持 QUARANTINED（这种情况下已无在办事件，需要人工在台账中看到它继续隔离）。
     */
    @Transactional
    public void sweepOrphanQuarantines() {
        List<ReactionBagLink> orphans = linkRepository.findOrphanQuarantinedLinks();
        if (orphans.isEmpty()) {
            return;
        }
        List<String> affectedCells = new ArrayList<>();
        for (ReactionBagLink link : orphans) {
            BloodBag bag = link.getBag();
            if (bag == null || !"QUARANTINED".equals(bag.getStatus())) {
                // 血袋已在并发窗口被报废 / 发出：各残留关联据实同步
                syncResidualLinks(bag);
                continue;
            }
            boolean releasable = releaseHoldReason(bag, link.getEvent()) == null;
            if (!releasable) {
                continue;
            }
            int changed = bagRepository.releaseIfQuarantined(bag.getId());
            if (changed == 0) {
                continue;
            }
            bag.setStatus("AVAILABLE");
            LocalDateTime now = LocalDateTime.now();
            // 把该血袋在所有「已结束」事件上的残留隔离关联同步为已释放，并在各自台账留痕
            for (ReactionBagLink same : linkRepository.findByBagIdOrderByIdAsc(bag.getId())) {
                if (same.getLinkStatus() == null
                        || !("QUARANTINED".equals(same.getLinkStatus()) || "KEPT".equals(same.getLinkStatus()))) {
                    continue;
                }
                if (!"EXCLUDED".equals(same.getEvent().getStatus())) {
                    continue;
                }
                same.setLinkStatus("RELEASED");
                same.setNote("最后一宗在办事件已排除关联，复核后补释放");
                same.setUpdatedAt(now);
                linkRepository.save(same);
                writeLedger(same.getEvent(), bag.getId(), bag.getBagNo(), "RELEASE",
                        "并发复核结束后补扫：该血袋所有关联事件均已排除关联，满足释放条件，恢复可用",
                        same.getEvent().getReviewer(), now);
            }
            String cell = bag.getBloodType() + "|" + BloodDict.inventoryComponent(bag.getComponent());
            if (!affectedCells.contains(cell)) {
                affectedCells.add(cell);
            }
        }
        syncCells(affectedCells);
    }

    /** 血袋已报废 / 发出时，把仍挂「隔离中 / 保持隔离」的已结束事件关联据实同步（仅状态标注，不改血袋） */
    private void syncResidualLinks(BloodBag bag) {
        if (bag == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (ReactionBagLink same : linkRepository.findByBagIdOrderByIdAsc(bag.getId())) {
            String s = same.getLinkStatus();
            if (!"QUARANTINED".equals(s) && !"KEPT".equals(s)) {
                continue;
            }
            if ("SCRAPPED".equals(bag.getStatus())) {
                same.setLinkStatus("SCRAPPED");
                same.setNote("复核后补扫：血袋已报废");
            } else if ("ISSUED".equals(bag.getStatus())) {
                same.setLinkStatus("TRACED");
                same.setNote("复核后补扫：血袋已发出，仅追溯");
                same.setLinkType("TRACE");
            } else {
                continue;
            }
            same.setUpdatedAt(now);
            linkRepository.save(same);
        }
    }

    // ---------------- 内部：隔离 ----------------

    /**
     * 事件建立后立即对同一采血来源仍未发出的全部成分血袋建立同源隔离。
     * 仅 AVAILABLE 血袋被条件式置为 QUARANTINED；已发出 / 已报废血袋只入追溯清单。
     * 返回成功隔离的袋数。
     */
    private int quarantineSameSourceBags(TransfusionReaction event, BloodBag issuedBag, LocalDateTime now,
                                         List<String> affectedCells) {
        if (issuedBag == null || issuedBag.getCollection() == null) {
            return 0;
        }
        Long collectionId = issuedBag.getCollection().getId();
        List<BloodBag> sourceBags = bagRepository.findByCollectionIdOrderByIdAsc(collectionId);
        int isolated = 0;
        for (BloodBag bag : sourceBags) {
            ReactionBagLink link = new ReactionBagLink();
            link.setEvent(event);
            link.setBag(bag);
            link.setBagNo(bag.getBagNo());
            link.setBloodType(bag.getBloodType());
            link.setComponent(bag.getComponent());
            link.setCreatedAt(now);
            link.setUpdatedAt(now);

            if ("AVAILABLE".equals(bag.getStatus())) {
                // 条件式 UPDATE：与配血 / 发血事务互斥。0 行说明并发下该袋刚被发出 / 报废 / 被别的事件隔离
                int changed = bagRepository.quarantineIfAvailable(bag.getId());
                if (changed > 0) {
                    bag.setStatus("QUARANTINED");
                    link.setLinkType("ISOLATED");
                    link.setLinkStatus("QUARANTINED");
                    link.setNote("同源血袋，事件建立即隔离");
                    isolated++;
                    String cell = bag.getBloodType() + "|" + BloodDict.inventoryComponent(bag.getComponent());
                    if (!affectedCells.contains(cell)) {
                        affectedCells.add(cell);
                    }
                    writeLedger(event, bag.getId(), bag.getBagNo(), "ISOLATE",
                            "与发血记录 " + event.getMatchNo() + " 同一采血来源（采血记录 id="
                                    + collectionId + "），在库血袋立即隔离，配血与发血一律拒绝",
                            event.getReporter(), now);
                } else {
                    // 并发窗口里状态已变：以数据库现状为准（refresh 强制重读），绝不改写已发出 / 已报废状态
                    entityManager.refresh(bag);
                    if ("QUARANTINED".equals(bag.getStatus())) {
                        link.setLinkType("ISOLATED");
                        link.setLinkStatus("QUARANTINED");
                        link.setNote("血袋已先被其他反应事件隔离，本事件共同持有隔离");
                        isolated++;
                        String cell = bag.getBloodType() + "|" + BloodDict.inventoryComponent(bag.getComponent());
                        if (!affectedCells.contains(cell)) {
                            affectedCells.add(cell);
                        }
                    } else {
                        link.setLinkType("TRACE");
                        link.setLinkStatus("TRACED");
                        link.setNote("建立事件时血袋已为「" + BloodDict.bagStatusName(bag.getStatus())
                                + "」，只入追溯清单");
                        writeLedger(event, bag.getId(), bag.getBagNo(), "REPORT",
                                "同源血袋 " + bag.getBagNo() + " 建立事件时已为「"
                                        + BloodDict.bagStatusName(bag.getStatus()) + "」，仅入追溯清单，不改写状态",
                                event.getReporter(), now);
                    }
                }
            } else if ("QUARANTINED".equals(bag.getStatus())) {
                // 并发窗口：该在库血袋刚被另一事件隔离。本事件同样持有隔离关联，
                // 两事件任一在办都不得释放；血袋状态不被改写。
                link.setLinkType("ISOLATED");
                link.setLinkStatus("QUARANTINED");
                link.setNote("血袋已先被其他反应事件隔离，本事件共同持有隔离");
                isolated++;
                String cell = bag.getBloodType() + "|" + BloodDict.inventoryComponent(bag.getComponent());
                if (!affectedCells.contains(cell)) {
                    affectedCells.add(cell);
                }
            } else {
                // 已发出 / 已报废：只追溯
                link.setLinkType("TRACE");
                link.setLinkStatus("TRACED");
                link.setNote("血袋状态「" + BloodDict.bagStatusName(bag.getStatus()) + "」，只入追溯清单");
            }
            linkRepository.save(link);
        }
        return isolated;
    }

    // ---------------- 内部：释放条件 ----------------

    /**
     * 排除关联时的释放三条件，任一不满足返回原因（保持隔离）：
     * ① 仍满足原检验放行（标本 RELEASED）；② 未过期；③ 未被其他在办事件隔离。
     */
    private String releaseHoldReason(BloodBag bag, TransfusionReaction currentEvent) {
        if ("SCRAPPED".equals(bag.getStatus())) {
            return "血袋已报废（可能由其他事件复核确认风险），不能释放";
        }
        if ("ISSUED".equals(bag.getStatus())) {
            return "血袋已发出，不能释放";
        }
        boolean otherOpen = !linkRepository
                .findOpenQuarantineByBagExcludingEvent(bag.getId(), currentEvent.getId()).isEmpty();
        if (otherOpen) {
            return "血袋同时被其他在办反应事件隔离，任一事件未结束前不得释放";
        }
        if (bag.getExpiryDate() != null && !bag.getExpiryDate().isAfter(LocalDate.now())) {
            return "血袋已于 " + bag.getExpiryDate() + " 过期，不满足释放条件，继续隔离";
        }
        if (bag.getCollection() == null) {
            return "缺少采血来源，无法确认原检验放行结论，继续隔离";
        }
        TestSample sample = sampleRepository.findByCollectionId(bag.getCollection().getId()).orElse(null);
        if (sample == null || !"RELEASED".equals(sample.getStatus())) {
            String statusName = sample == null ? "无检验标本" : BloodDict.sampleStatusName(sample.getStatus());
            return "检验标本状态为「" + statusName + "」，不满足原检验放行条件，继续隔离";
        }
        return null;
    }

    // ---------------- 内部：报废单与库存 ----------------

    private void writeScrapRecord(BloodBag bag, TransfusionReaction event, String reviewer, LocalDateTime now) {
        ScrapRecord record = new ScrapRecord();
        record.setScrapNo("SC" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", scrapRepository.count() + 1));
        record.setBagNo(bag.getBagNo());
        record.setBloodType(bag.getBloodType());
        record.setComponent(bag.getComponent());
        record.setBags(1);
        record.setVolumeMl(bag.getVolumeMl() == null ? 0 : bag.getVolumeMl());
        record.setReason("REACTION");
        record.setOperator("质量复核 " + reviewer);
        record.setScrapTime(now);
        record.setRemark("输血不良反应事件 " + event.getEventNo() + " 复核确认风险，同源血袋报废");
        record.setCreatedAt(now);
        scrapRepository.save(record);
    }

    private void syncCells(List<String> cells) {
        for (String cell : cells) {
            String[] parts = cell.split("\\|", 2);
            inventoryService.syncInventory(parts[0], parts[1]);
        }
    }

    // ---------------- 内部：台账 / 幂等键 / VO ----------------

    private void writeLedger(TransfusionReaction event, Long bagId, String bagNo, String action,
                             String detail, String operator, LocalDateTime time) {
        // DATETIME 精度到秒，同一事务内多条台账可能落在同一秒：
        // 保证同事件台账时间严格单调，避免时间并列让「逐步记录」顺序产生歧义。
        LocalDateTime t = time;
        if (event.getId() != null) {
            ReactionLedger last = ledgerRepository.findLastByEventId(event.getId()).orElse(null);
            if (last != null && last.getActionTime() != null && !t.isAfter(last.getActionTime())) {
                t = last.getActionTime().plusSeconds(1);
            }
        }
        ReactionLedger ledger = new ReactionLedger();
        ledger.setEvent(event);
        ledger.setBagId(bagId);
        ledger.setBagNo(bagNo);
        ledger.setAction(action);
        ledger.setDetail(detail);
        ledger.setOperator(operator);
        ledger.setActionTime(t);
        ledger.setCreatedAt(t);
        ledgerRepository.save(ledger);
    }

    /** 幂等键：发血记录 + 反应时间截断到分钟 + 规范化症状 */
    private String dedupKey(Long matchId, LocalDateTime reactionTime, String symptoms) {
        String raw = matchId + "|" + reactionTime.truncatedTo(ChronoUnit.MINUTES).format(DEDUP_TIME)
                + "|" + symptoms.replaceAll("\\s+", "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成幂等键", e);
        }
    }

    private ReactionVO withDuplicate(ReactionVO vo, String message) {
        return new ReactionVO(vo.id(), vo.eventNo(), vo.matchId(), vo.matchNo(), vo.bagNo(), vo.recipient(),
                vo.hospital(), vo.reporter(), vo.symptoms(), vo.reactionTime(), vo.reportTime(), vo.status(),
                vo.statusName(), vo.reviewResult(), vo.reviewResultName(), vo.reviewer(), vo.reviewComment(),
                vo.reviewedAt(), vo.sourceCollectionId(), vo.isolatedCount(), vo.releasedCount(),
                vo.scrappedCount(), vo.traceCount(), true, message, vo.links(), vo.ledger(), vo.trace());
    }

    private ReactionVO toSummaryVO(TransfusionReaction event) {
        List<ReactionBagLink> links = linkRepository.findByEventIdOrderByIdAsc(event.getId());
        int isolated = 0, released = 0, scrapped = 0, trace = 0;
        for (ReactionBagLink link : links) {
            switch (link.getLinkStatus()) {
                case "QUARANTINED", "KEPT" -> isolated++;
                case "RELEASED" -> released++;
                case "SCRAPPED" -> scrapped++;
                default -> trace++;
            }
        }
        return ReactionVO.summary(event.getId(), event.getEventNo(),
                event.getMatch() == null ? null : event.getMatch().getId(), event.getMatchNo(),
                event.getBagNo(), event.getRecipient(), event.getHospital(), event.getReporter(),
                event.getSymptoms(), event.getReactionTime(), event.getReportTime(), event.getStatus(),
                BloodDict.reactionStatusName(event.getStatus()), event.getReviewResult(),
                BloodDict.reactionReviewName(event.getReviewResult()), event.getReviewer(),
                event.getReviewComment(), event.getReviewedAt(), event.getSourceCollectionId(),
                isolated, released, scrapped, trace);
    }

    private ReactionVO toDetailVO(Long eventId) {
        // 始终重新读取：条件式批量 UPDATE（clearAutomatically）会清空一级缓存，
        // 旧实体上的 LAZY 代理已脱管，必须在当前会话内重新取托管态再组装来源链。
        TransfusionReaction event = reactionRepository.findById(eventId)
                .orElseThrow(() -> new BizException("反应事件不存在：id=" + eventId));
        List<ReactionBagLink> links = linkRepository.findByEventIdOrderByIdAsc(event.getId());
        List<ReactionLinkVO> linkVOs = new ArrayList<>();
        int isolated = 0, released = 0, scrapped = 0, trace = 0;
        for (ReactionBagLink link : links) {
            linkVOs.add(toLinkVO(link));
            switch (link.getLinkStatus()) {
                case "QUARANTINED", "KEPT" -> isolated++;
                case "RELEASED" -> released++;
                case "SCRAPPED" -> scrapped++;
                default -> trace++;
            }
        }
        List<ReactionLedgerVO> ledgerVOs = ledgerRepository.findByEventIdOrderByIdAsc(event.getId())
                .stream().map(this::toLedgerVO).toList();
        TraceChainVO chain = event.getMatch() == null ? null : buildChain(event.getMatch());
        return new ReactionVO(event.getId(), event.getEventNo(),
                event.getMatch() == null ? null : event.getMatch().getId(), event.getMatchNo(),
                event.getBagNo(), event.getRecipient(), event.getHospital(), event.getReporter(),
                event.getSymptoms(), event.getReactionTime(), event.getReportTime(), event.getStatus(),
                BloodDict.reactionStatusName(event.getStatus()), event.getReviewResult(),
                BloodDict.reactionReviewName(event.getReviewResult()), event.getReviewer(),
                event.getReviewComment(), event.getReviewedAt(), event.getSourceCollectionId(),
                isolated, released, scrapped, trace, false, null, linkVOs, ledgerVOs, chain);
    }

    private ReactionLinkVO toLinkVO(ReactionBagLink link) {
        BloodBag bag = link.getBag();
        LocalDate expiry = bag == null ? null : bag.getExpiryDate();
        Integer days = expiry == null ? null : (int) ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        return new ReactionLinkVO(link.getId(), bag == null ? null : bag.getId(), link.getBagNo(),
                link.getBloodType(), link.getComponent(), BloodDict.componentName(link.getComponent()),
                bag == null ? null : bag.getVolumeMl(), expiry, days,
                bag == null ? null : bag.getStatus(),
                bag == null ? null : BloodDict.bagStatusName(bag.getStatus()),
                link.getLinkType(), BloodDict.reactionLinkTypeName(link.getLinkType()),
                link.getLinkStatus(), BloodDict.reactionLinkStatusName(link.getLinkStatus()),
                link.getNote(), link.getCreatedAt(), link.getUpdatedAt());
    }

    private ReactionLedgerVO toLedgerVO(ReactionLedger ledger) {
        return new ReactionLedgerVO(ledger.getId(), ledger.getBagId(), ledger.getBagNo(), ledger.getAction(),
                BloodDict.reactionActionName(ledger.getAction()), ledger.getDetail(), ledger.getOperator(),
                ledger.getActionTime());
    }

    /** 组装完整来源链：发血记录 → 供血血袋 → 制备批次 → 采血记录 → 检验标本 */
    private TraceChainVO buildChain(CrossMatch match) {
        BloodBag bag = match.getBag();
        TraceBagVO bagVO = null;
        TraceBagVO.Batch batchVO = null;
        TraceBagVO.Collection collectionVO = null;
        TraceBagVO.Sample sampleVO = null;
        String blockHint = null;

        if (bag != null) {
            PreparationBatch batch = bag.getBatch() == null ? null : bag.getBatch();
            if (batch == null && bag.getCollection() != null) {
                List<PreparationBatch> batches = preparationRepository
                        .findByCollectionId(bag.getCollection().getId());
                batch = batches.isEmpty() ? null : batches.get(0);
            }
            CollectionRecord collection = bag.getCollection();
            if (collection == null && batch != null && batch.getCollection() != null) {
                collection = batch.getCollection();
            }

            if (batch != null) {
                batchVO = new TraceBagVO.Batch(batch.getId(), batch.getBatchNo(), batch.getCollectNo(),
                        batch.getBloodType(), nz(batch.getRbcBags()), nz(batch.getPlasmaBags()),
                        nz(batch.getPlateletBags()), nz(batch.getCryoBags()), batch.getOperator(),
                        batch.getPreparedTime(), batch.getStatus());
            }
            if (collection != null) {
                Donor donor = collection.getDonor();
                collectionVO = new TraceBagVO.Collection(collection.getId(), collection.getCollectNo(),
                        donor == null ? null : donor.getDonorNo(),
                        donor == null ? bag.getDonorName() : donor.getName(),
                        donor == null ? null : donor.getGender(), donor == null ? null : donor.getAge(),
                        donor == null ? null : donor.getBloodType(), collection.getStationCode(),
                        collection.getVolumeMl(), collection.getCollectDate(), collection.getNurse(),
                        collection.getStatus(), BloodDict.collectionStatusName(collection.getStatus()));
                TestSample sample = sampleRepository.findByCollectionId(collection.getId()).orElse(null);
                if (sample != null) {
                    // 溯源展示与放行口径一致：取各项目最后一次有效（被采用）复测结果
                    List<TestRetestRound> sampleRounds =
                            retestRepository.findBySampleIdOrderByItemCodeAscRoundNoAsc(sample.getId());
                    SampleResultResolver.Snapshot sampleSnapshot =
                            resultResolver.effectiveAtRelease(sample, sampleRounds);
                    sampleVO = new TraceBagVO.Sample(sample.getId(), sample.getBarcode(), sample.getBloodType(),
                            decimal(sampleSnapshot.altValue()), decimal(sampleSnapshot.hbValue()),
                            resultName(sampleSnapshot.hbvResult()), resultName(sampleSnapshot.hcvResult()),
                            resultName(sampleSnapshot.hivResult()), resultName(sampleSnapshot.syphilisResult()),
                            sample.getReviewer(), sample.getStatus(),
                            BloodDict.sampleStatusName(sample.getStatus()), sample.getTestedAt(),
                            sample.getReleasedAt());
                }
            }
            bagVO = new TraceBagVO(bag.getId(), bag.getBagNo(), bag.getBloodType(), bag.getComponent(),
                    BloodDict.componentName(bag.getComponent()), bag.getVolumeMl(), bag.getCollectDate(),
                    bag.getExpiryDate(), bag.getStatus(), BloodDict.bagStatusName(bag.getStatus()),
                    bag.getDonorName(), batch == null ? null : batch.getBatchNo());
            if ("QUARANTINED".equals(bag.getStatus())) {
                blockHint = "该供血血袋当前处于同源隔离状态，配血与发血一律拒绝";
            }
        }

        TraceBagVO.Issue issueVO = new TraceBagVO.Issue(match.getId(), match.getMatchNo(),
                match.getRequest() == null ? null : match.getRequest().getRequestNo(), match.getBagNo(),
                match.getDonorName(), match.getBloodType(), match.getComponent(),
                BloodDict.componentName(match.getComponent()), match.getRecipient(), match.getHospital(),
                match.getUnitsU(), match.getUrgency(), BloodDict.urgencyName(match.getUrgency()),
                match.getResult(), BloodDict.matchResultName(match.getResult()), match.getIssueOperator(),
                match.getIssueTime(), match.getRecoveryChecked());
        return new TraceChainVO(issueVO, bagVO, batchVO, collectionVO, sampleVO, blockHint);
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String resultName(String result) {
        return result == null ? null : BloodDict.testResultName(result) + "（" + result + "）";
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
