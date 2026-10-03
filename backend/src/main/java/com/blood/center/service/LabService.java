package com.blood.center.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blood.center.common.BizException;
import com.blood.center.common.BloodDict;
import com.blood.center.domain.CollectionRecord;
import com.blood.center.domain.PreparationBatch;
import com.blood.center.domain.TestRetestRound;
import com.blood.center.domain.TestSample;
import com.blood.center.dto.PreparationSummaryVO;
import com.blood.center.dto.PreparationVO;
import com.blood.center.dto.ReleaseRequest;
import com.blood.center.dto.RetestDecision;
import com.blood.center.dto.RetestRequest;
import com.blood.center.dto.RoundVO;
import com.blood.center.dto.SampleVO;
import com.blood.center.dto.TestResultRequest;
import com.blood.center.repository.PreparationBatchRepository;
import com.blood.center.repository.TestRetestRoundRepository;
import com.blood.center.repository.TestSampleRepository;

/**
 * 标本检验与成分制备：待检 → 检测中 → 已放行（→ 领域事件自动生成制备单）/ 报废。
 *
 * 复测闭环（初检值视为第 0 轮，永不覆盖）：
 *  - 检测人员可对「检测中」或「因单项不合格报废」的标本按项目发起复测，填写新结果与原因；
 *  - 复测按 (标本, 项目) 独立计轮，同一项目存在在办（待复核）轮时并发/重复发起只允许同一轮；
 *  - 已放行标本一律不得复测；
 *  - 复核人放行时必须逐轮「采用 / 拒绝」并填写理由，系统只依据各项目最后一次有效结果判定。
 */
@Service
public class LabService {

    private static final DateTimeFormatter SEQ_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TestSampleRepository sampleRepository;
    private final TestRetestRoundRepository retestRepository;
    private final PreparationBatchRepository preparationRepository;
    private final SampleResultResolver resolver;

    public LabService(TestSampleRepository sampleRepository,
                      TestRetestRoundRepository retestRepository,
                      PreparationBatchRepository preparationRepository,
                      SampleResultResolver resolver) {
        this.sampleRepository = sampleRepository;
        this.retestRepository = retestRepository;
        this.preparationRepository = preparationRepository;
        this.resolver = resolver;
    }

    // ---------------- 标本检验 ----------------

    @Transactional(readOnly = true)
    public List<SampleVO> listSamples(String bloodType) {
        List<TestSample> samples = bloodType == null || bloodType.isBlank()
                ? sampleRepository.findAll()
                : sampleRepository.findByBloodTypeOrderByIdAsc(bloodType);
        List<SampleVO> vos = new ArrayList<>();
        for (TestSample sample : samples) {
            vos.add(toVO(sample, retestRepository.findBySampleIdOrderByItemCodeAscRoundNoAsc(sample.getId())));
        }
        return vos;
    }

    /**
     * 录入检验结果：ALT / 血红蛋白 / HBV / HCV / HIV / 梅毒；任一不合格直接判为报废。
     * 该接口只写初检（第 0 轮）；一旦标本存在复测轮次，初检即冻结，改结果必须走复测。
     */
    @Transactional
    public SampleVO inputResult(Long id, TestResultRequest request) {
        TestSample sample = getSample(id);
        if ("RELEASED".equals(sample.getStatus())) {
            throw new BizException("标本 " + sample.getBarcode() + " 已放行，不能再修改检验结果");
        }
        if (!retestRepository.findBySampleIdOrderByItemCodeAscRoundNoAsc(id).isEmpty()) {
            throw new BizException("标本 " + sample.getBarcode()
                    + " 已发起复测，初检结果作为第 0 轮不得覆盖，请使用「发起复测」追加轮次");
        }
        if (request.altValue() == null || request.hbValue() == null
                || request.hbvResult() == null || request.hcvResult() == null
                || request.hivResult() == null || request.syphilisResult() == null) {
            throw new BizException("ALT、血红蛋白、HBV、HCV、HIV、梅毒 六项结果必须全部录入");
        }
        sample.setAltValue(request.altValue());
        sample.setHbValue(request.hbValue());
        sample.setHbvResult(request.hbvResult());
        sample.setHcvResult(request.hcvResult());
        sample.setHivResult(request.hivResult());
        sample.setSyphilisResult(request.syphilisResult());
        sample.setTestedAt(LocalDateTime.now());
        if (request.operator() != null && !request.operator().isBlank()) {
            sample.setReviewer(request.operator());
        }
        SampleResultResolver.Snapshot snapshot = new SampleResultResolver.Snapshot(
                sample.getAltValue(), sample.getHbValue(), sample.getHbvResult(),
                sample.getHcvResult(), sample.getHivResult(), sample.getSyphilisResult());
        if (resolver.unqualifiedReason(snapshot, sample.donorGender()) != null) {
            sample.setStatus("SCRAPPED");
        } else {
            sample.setStatus("TESTING");
        }
        return toVO(sampleRepository.save(sample), List.of());
    }

    /**
     * 发起复测：仅检测中 / 报废标本可复测，已放行禁止；一轮只复测一个项目。
     * 同一项目已有待复核轮时不允许再开新一轮（并发请求经标本行锁串行后由本规则挡回，
     * 配合 uk_retest_round 唯一索引兜底，同一项目并发复测只会新增一轮）。
     */
    @Transactional
    public SampleVO retest(Long id, RetestRequest request) {
        if (request == null || !BloodDict.isValidTestItem(request.itemCode())) {
            throw new BizException("复测项目不合法，须为 ALT / HB / HBV / HCV / HIV / SYPHILIS 之一");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BizException("发起复测必须填写复测原因");
        }
        if (request.operator() == null || request.operator().isBlank()) {
            throw new BizException("发起复测必须填写检测人");
        }
        if (BloodDict.isNumericTestItem(request.itemCode())) {
            if (request.newValue() == null) {
                throw new BizException(BloodDict.testItemName(request.itemCode()) + " 复测必须填写新数值结果");
            }
        } else if (request.newResult() == null || request.newResult().isBlank()) {
            throw new BizException(BloodDict.testItemName(request.itemCode()) + " 复测必须填写新的阴阳性结果");
        }

        // 行锁串行化同一标本的并发复测
        TestSample sample = sampleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("标本不存在：id=" + id));
        if ("RELEASED".equals(sample.getStatus())) {
            throw new BizException("标本 " + sample.getBarcode() + " 已放行，不得复测");
        }
        if (!"TESTING".equals(sample.getStatus()) && !"SCRAPPED".equals(sample.getStatus())) {
            throw new BizException("标本 " + sample.getBarcode() + " 当前状态为「"
                    + BloodDict.sampleStatusName(sample.getStatus()) + "」，只有检测中或报废的标本可以复测");
        }
        if (!initialComplete(sample)) {
            throw new BizException("标本 " + sample.getBarcode() + " 初检六项结果尚未录全，请先完成初检再发起复测");
        }

        List<TestRetestRound> rounds = retestRepository.findBySampleIdForUpdate(id);
        String itemCode = request.itemCode();
        boolean pendingExists = rounds.stream().anyMatch(r -> itemCode.equals(r.getItemCode())
                && "PENDING".equals(r.getDecision()));
        if (pendingExists) {
            throw new BizException(BloodDict.testItemName(itemCode)
                    + " 已有待复核的复测轮，请等待复核人放行处置后再发起下一轮");
        }
        int nextRoundNo = rounds.stream()
                .filter(r -> itemCode.equals(r.getItemCode()))
                .map(TestRetestRound::getRoundNo)
                .max(Integer::compareTo).orElse(0) + 1;

        TestRetestRound round = new TestRetestRound();
        round.setSample(sample);
        round.setItemCode(itemCode);
        round.setRoundNo(nextRoundNo);
        if (BloodDict.isNumericTestItem(itemCode)) {
            round.setNewValue(request.newValue());
        } else {
            round.setNewResult(request.newResult().trim());
        }
        round.setReason(request.reason().trim());
        round.setOperator(request.operator().trim());
        round.setTestedAt(LocalDateTime.now());
        round.setDecision("PENDING");
        round.setCreatedAt(LocalDateTime.now());
        retestRepository.save(round);

        // 用「各项目最后一次有效结果」重算标本当前判定；报废标本复测合格可回到检测中
        rounds.add(round);
        SampleResultResolver.Snapshot snapshot = resolver.effective(sample, rounds);
        String unqualified = resolver.unqualifiedReason(snapshot, sample.donorGender());
        if (unqualified != null) {
            sample.setStatus("SCRAPPED");
        } else {
            sample.setStatus("TESTING");
        }
        sampleRepository.save(sample);
        return toVO(sample, rounds);
    }

    /**
     * 标本放行：必须有复核人、六项有效结果齐全且合格。
     * 存在复测轮次时，复核人必须逐轮给出「采用 / 拒绝」及理由；放行判定只依据
     * 各项目最后一次有效（被采用）结果。放行后触发成分制备（领域事件）。
     */
    @Transactional
    public SampleVO release(Long id, ReleaseRequest request) {
        TestSample sample = sampleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("标本不存在：id=" + id));
        if ("RELEASED".equals(sample.getStatus())) {
            throw new BizException("标本 " + sample.getBarcode() + " 已放行，请勿重复操作");
        }
        if (!"TESTING".equals(sample.getStatus())) {
            throw new BizException("标本 " + sample.getBarcode() + " 当前状态为「"
                    + BloodDict.sampleStatusName(sample.getStatus()) + "」，只有检测中的标本可以放行");
        }
        if (request.reviewer() == null || request.reviewer().isBlank()) {
            throw new BizException("放行前必须选择复核人");
        }
        String reviewer = request.reviewer().trim();

        List<TestRetestRound> rounds = retestRepository.findBySampleIdForUpdate(id);
        if (!rounds.isEmpty()) {
            applyReviewDecisions(rounds, request.decisions() == null ? List.of() : request.decisions(), reviewer);
        }

        SampleResultResolver.Snapshot snapshot = resolver.effectiveAtRelease(sample, rounds);
        if (!snapshot.allTested()) {
            throw new BizException("标本 " + sample.getBarcode() + " 检验项目结果不全，不能放行");
        }
        String unqualified = resolver.unqualifiedReason(snapshot, sample.donorGender());
        if (unqualified != null) {
            throw new BizException("标本 " + sample.getBarcode() + " 检验不合格（" + unqualified + "），不能放行");
        }
        sample.release(reviewer);
        sampleRepository.save(sample);
        return toVO(sample, rounds);
    }

    /**
     * 轨道拖拽：待检 → 检测中 → 已放行 / 报废。
     */
    @Transactional
    public SampleVO move(Long id, String status, String reviewer) {
        TestSample sample = getSample(id);
        if (!BloodDict.isValidSampleStatus(status)) {
            throw new BizException("未知的标本状态：" + status);
        }
        switch (status) {
            case "PENDING" -> {
                if ("RELEASED".equals(sample.getStatus())) {
                    throw new BizException("已放行的标本不能退回待检");
                }
                sample.setStatus("PENDING");
            }
            case "TESTING" -> {
                if ("RELEASED".equals(sample.getStatus())) {
                    throw new BizException("已放行的标本不能退回检测中");
                }
                sample.setStatus("TESTING");
                if (sample.getTestedAt() == null) {
                    sample.setTestedAt(LocalDateTime.now());
                }
            }
            case "RELEASED" -> {
                return release(id, new ReleaseRequest(reviewer));
            }
            case "SCRAPPED" -> {
                if ("RELEASED".equals(sample.getStatus())) {
                    throw new BizException("已放行的标本不能作废，请走血液报废流程");
                }
                sample.scrap();
            }
            default -> throw new BizException("未知的标本状态：" + status);
        }
        return toVO(sampleRepository.save(sample),
                retestRepository.findBySampleIdOrderByItemCodeAscRoundNoAsc(id));
    }

    // ---------------- 成分制备 ----------------

    @Transactional(readOnly = true)
    public List<PreparationVO> listPreparations(String bloodType) {
        List<PreparationBatch> batches = bloodType == null || bloodType.isBlank()
                ? preparationRepository.findAll()
                : preparationRepository.findByBloodTypeOrderByIdDesc(bloodType);
        List<PreparationVO> vos = new ArrayList<>();
        for (PreparationBatch batch : batches) {
            vos.add(toVO(batch));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public List<PreparationSummaryVO> preparationSummary() {
        return preparationRepository.summaryByBloodType();
    }

    // ---------------- 内部方法 ----------------

    /**
     * 按全血量计算可分离成分：红细胞悬液 / 血浆 / 血小板 / 冷沉淀。
     */
    public static int[] splitComponents(Integer wholeVolumeMl) {
        int volume = wholeVolumeMl == null ? 0 : wholeVolumeMl;
        int rbc = (int) Math.round(volume * 0.45);
        int plasma = (int) Math.round(volume * 0.40);
        int platelet = volume >= 350 ? 250 : 0;
        int cryo = volume >= 350 ? 30 : 0;
        return new int[]{rbc, plasma, platelet, cryo};
    }

    /** 初检六项是否录全（无初检结果不得复测） */
    private boolean initialComplete(TestSample sample) {
        return sample.getAltValue() != null && sample.getHbValue() != null
                && sample.getHbvResult() != null && sample.getHcvResult() != null
                && sample.getHivResult() != null && sample.getSyphilisResult() != null;
    }

    /**
     * 放行时逐轮落实复核决定：每一轮都必须处置、理由必填；处置后重算，
     * 若最终有效结果仍不合格则拒绝放行（决定已随失败事务回滚，不留半成品）。
     */
    private void applyReviewDecisions(List<TestRetestRound> rounds,
                                      List<RetestDecision> decisions, String reviewer) {
        Map<Long, RetestDecision> byId = new HashMap<>();
        for (RetestDecision decision : decisions) {
            if (decision != null && decision.roundId() != null) {
                byId.put(decision.roundId(), decision);
            }
        }
        for (TestRetestRound round : rounds) {
            // 已经处置过（采用/拒绝）的轮次不再要求重复给理由
            if (!"PENDING".equals(round.getDecision())) {
                continue;
            }
            RetestDecision decision = byId.get(round.getId());
            String itemName = BloodDict.testItemName(round.getItemCode());
            if (decision == null) {
                throw new BizException("第 " + round.getRoundNo() + " 轮复测（" + itemName
                        + "，检测人 " + round.getOperator() + "）尚未给出采用或拒绝的处置，不能放行");
            }
            if (!"ADOPTED".equals(decision.decision()) && !"REJECTED".equals(decision.decision())) {
                throw new BizException("第 " + round.getRoundNo() + " 轮复测（" + itemName
                        + "）的复核处置必须是「采用」或「拒绝」");
            }
            if (decision.reason() == null || decision.reason().isBlank()) {
                throw new BizException("第 " + round.getRoundNo() + " 轮复测（" + itemName
                        + "）" + ("ADOPTED".equals(decision.decision()) ? "采用" : "拒绝")
                        + "复测结果必须填写理由");
            }
            // 前端只应为在办轮提交决定，重复提交到已处置轮直接忽略其内容
            round.review(decision.decision(), decision.reason().trim(), reviewer);
            retestRepository.save(round);
        }
    }

    private TestSample getSample(Long id) {
        return sampleRepository.findById(id).orElseThrow(() -> new BizException("标本不存在：id=" + id));
    }

    /**
     * 组装标本视图：扁平字段与 items 一律取「各项目最后一次有效结果」，
     * rounds 按项目与轮次展开完整复测历史，并标出每一轮是否为当前判定依据。
     */
    private SampleVO toVO(TestSample sample, List<TestRetestRound> rounds) {
        CollectionRecord collection = sample.getCollection();
        boolean released = "RELEASED".equals(sample.getStatus());
        SampleResultResolver.Snapshot snapshot = released
                ? resolver.effectiveAtRelease(sample, rounds)
                : resolver.effective(sample, rounds);

        // 每个项目当前生效的轮次号（0=初检）
        Map<String, Integer> currentRoundNo = new HashMap<>();
        for (String code : BloodDict.TEST_ITEMS) {
            currentRoundNo.put(code, 0);
        }
        for (TestRetestRound round : rounds) {
            boolean effective = released ? "ADOPTED".equals(round.getDecision())
                    : ("ADOPTED".equals(round.getDecision()) || "PENDING".equals(round.getDecision()));
            if (effective) {
                currentRoundNo.put(round.getItemCode(), round.getRoundNo());
            }
        }
        // 项目是否「在办」：仅当当前生效轮本身是待复核轮（旧在办轮已被后续轮接续时不再高亮）
        Map<String, Boolean> pendingMap = new HashMap<>();
        for (Map.Entry<String, Integer> entry : currentRoundNo.entrySet()) {
            int roundNo = entry.getValue();
            if (roundNo > 0) {
                int finalRoundNo = roundNo;
                pendingMap.put(entry.getKey(), rounds.stream().anyMatch(r ->
                        entry.getKey().equals(r.getItemCode()) && r.getRoundNo() == finalRoundNo
                                && "PENDING".equals(r.getDecision())));
            }
        }
        Map<String, Boolean> retestedMap = new HashMap<>();
        for (TestRetestRound round : rounds) {
            retestedMap.put(round.getItemCode(), true);
        }

        List<SampleVO.ItemVO> items = buildItems(sample, snapshot, currentRoundNo, pendingMap, retestedMap);

        List<RoundVO> roundVOs = rounds.stream()
                .sorted(Comparator.comparing(TestRetestRound::getItemCode)
                        .thenComparing(TestRetestRound::getRoundNo))
                .map(round -> toRoundVO(round, currentRoundNo.getOrDefault(round.getItemCode(), 0), sample))
                .toList();

        long pendingCount = rounds.stream().filter(r -> "PENDING".equals(r.getDecision())).count();

        return new SampleVO(sample.getId(), sample.getBarcode(),
                collection == null ? null : collection.getId(),
                collection == null ? null : collection.getCollectNo(),
                sample.getDonorName(), sample.getBloodType(),
                snapshot.altValue(), snapshot.hbValue(),
                snapshot.hbvResult(), snapshot.hcvResult(), snapshot.hivResult(), snapshot.syphilisResult(),
                sample.getReviewer(), sample.getStatus(), BloodDict.sampleStatusName(sample.getStatus()),
                sample.getTestedAt(), sample.getReleasedAt(), items, roundVOs,
                rounds.size(), pendingCount);
    }

    private List<SampleVO.ItemVO> buildItems(TestSample sample, SampleResultResolver.Snapshot snapshot,
                                             Map<String, Integer> currentRoundNo,
                                             Map<String, Boolean> pendingMap,
                                             Map<String, Boolean> retestedMap) {
        String gender = sample.donorGender();
        List<SampleVO.ItemVO> items = new ArrayList<>();
        items.add(item("ALT", "ALT", str(snapshot.altValue()), "U/L",
                resolver.conclusion("ALT", snapshot.altValue(), null, gender),
                currentRoundNo, pendingMap, retestedMap));
        items.add(item("HB", "血红蛋白", str(snapshot.hbValue()), "g/L",
                resolver.conclusion("HB", snapshot.hbValue(), null, gender),
                currentRoundNo, pendingMap, retestedMap));
        items.add(item("HBV", "HBV", BloodDict.testResultName(snapshot.hbvResult()), "",
                resolver.conclusion("HBV", null, snapshot.hbvResult(), gender),
                currentRoundNo, pendingMap, retestedMap));
        items.add(item("HCV", "HCV", BloodDict.testResultName(snapshot.hcvResult()), "",
                resolver.conclusion("HCV", null, snapshot.hcvResult(), gender),
                currentRoundNo, pendingMap, retestedMap));
        items.add(item("HIV", "HIV", BloodDict.testResultName(snapshot.hivResult()), "",
                resolver.conclusion("HIV", null, snapshot.hivResult(), gender),
                currentRoundNo, pendingMap, retestedMap));
        items.add(item("SYPHILIS", "梅毒", BloodDict.testResultName(snapshot.syphilisResult()), "",
                resolver.conclusion("SYPHILIS", null, snapshot.syphilisResult(), gender),
                currentRoundNo, pendingMap, retestedMap));
        return items;
    }

    private SampleVO.ItemVO item(String code, String name, String value, String unit, String conclusion,
                                 Map<String, Integer> currentRoundNo,
                                 Map<String, Boolean> pendingMap,
                                 Map<String, Boolean> retestedMap) {
        return new SampleVO.ItemVO(code, name, value, unit, conclusion,
                currentRoundNo.getOrDefault(code, 0),
                retestedMap.getOrDefault(code, false),
                pendingMap.getOrDefault(code, false));
    }

    private RoundVO toRoundVO(TestRetestRound round, int currentRoundNo, TestSample sample) {
        boolean numeric = BloodDict.isNumericTestItem(round.getItemCode());
        BigDecimal numericValue = numeric ? round.getNewValue() : null;
        String discreteResult = numeric ? null : round.getNewResult();
        String unit = "ALT".equals(round.getItemCode()) ? "U/L" : "HB".equals(round.getItemCode()) ? "g/L" : "";
        boolean current = round.getRoundNo() == currentRoundNo
                && ("ADOPTED".equals(round.getDecision()) || "PENDING".equals(round.getDecision()));
        return new RoundVO(round.getId(), round.getItemCode(),
                BloodDict.testItemName(round.getItemCode()), round.getRoundNo(),
                numeric ? str(numericValue) : BloodDict.testResultName(discreteResult),
                unit,
                resolver.conclusion(round.getItemCode(), numericValue, discreteResult, sample.donorGender()),
                round.getReason(), round.getOperator(), round.getTestedAt(),
                round.getDecision(), decisionName(round.getDecision()),
                round.getDecisionReason(), round.getReviewer(), round.getReviewedAt(), current);
    }

    private String decisionName(String decision) {
        return switch (decision) {
            case "ADOPTED" -> "采用";
            case "REJECTED" -> "拒绝";
            default -> "待复核";
        };
    }

    private String str(BigDecimal value) {
        return value == null ? "--" : value.toPlainString();
    }

    private PreparationVO toVO(PreparationBatch batch) {
        int rbcBags = nz(batch.getRbcBags());
        int plasmaBags = nz(batch.getPlasmaBags());
        int plateletBags = nz(batch.getPlateletBags());
        int cryoBags = nz(batch.getCryoBags());
        int rbcVol = nz(batch.getRbcVolumeMl());
        int plasmaVol = nz(batch.getPlasmaVolumeMl());
        int plateletVol = nz(batch.getPlateletVolumeMl());
        int cryoVol = nz(batch.getCryoVolumeMl());
        return new PreparationVO(batch.getId(), batch.getBatchNo(),
                batch.getCollection() == null ? null : batch.getCollection().getId(),
                batch.getCollectNo(), batch.getBloodType(),
                rbcBags, rbcVol, plasmaBags, plasmaVol, plateletBags, plateletVol, cryoBags, cryoVol,
                batch.getOperator(), batch.getPreparedTime(), batch.getStatus(),
                rbcBags + plasmaBags + plateletBags + cryoBags,
                rbcVol + plasmaVol + plateletVol + cryoVol);
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
