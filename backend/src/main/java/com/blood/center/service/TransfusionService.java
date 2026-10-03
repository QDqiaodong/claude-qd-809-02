package com.blood.center.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blood.center.common.BizException;
import com.blood.center.common.BloodDict;
import com.blood.center.domain.BloodBag;
import com.blood.center.domain.BloodInventory;
import com.blood.center.domain.CrossMatch;
import com.blood.center.domain.HospitalRequest;
import com.blood.center.domain.TestSample;
import com.blood.center.dto.CrossMatchRequest;
import com.blood.center.dto.CrossMatchVO;
import com.blood.center.dto.HospitalIssueVO;
import com.blood.center.dto.IssueRequest;
import com.blood.center.dto.MatchResultRequest;
import com.blood.center.dto.RequestCreateRequest;
import com.blood.center.dto.RequestVO;
import com.blood.center.repository.BloodBagRepository;
import com.blood.center.repository.BloodInventoryRepository;
import com.blood.center.repository.CrossMatchRepository;
import com.blood.center.repository.HospitalRequestRepository;
import com.blood.center.repository.TestSampleRepository;

/**
 * 医院用血申请、交叉配血与发血：出库前的业务红线（不相合 / 过期 / 未放行 / 库存不足）。
 */
@Service
public class TransfusionService {

    private static final DateTimeFormatter SEQ_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final HospitalRequestRepository requestRepository;
    private final CrossMatchRepository matchRepository;
    private final BloodBagRepository bagRepository;
    private final BloodInventoryRepository inventoryRepository;
    private final TestSampleRepository sampleRepository;

    public TransfusionService(HospitalRequestRepository requestRepository, CrossMatchRepository matchRepository,
                              BloodBagRepository bagRepository, BloodInventoryRepository inventoryRepository,
                              TestSampleRepository sampleRepository) {
        this.requestRepository = requestRepository;
        this.matchRepository = matchRepository;
        this.bagRepository = bagRepository;
        this.inventoryRepository = inventoryRepository;
        this.sampleRepository = sampleRepository;
    }

    // ---------------- 用血申请 ----------------

    @Transactional(readOnly = true)
    public List<RequestVO> listRequests(String bloodType) {
        List<HospitalRequest> requests = bloodType == null || bloodType.isBlank()
                ? requestRepository.findAll()
                : requestRepository.findByPatientBloodTypeOrderByIdDesc(bloodType);
        List<RequestVO> vos = new ArrayList<>();
        for (HospitalRequest request : requests) {
            vos.add(toVO(request));
        }
        return vos;
    }

    @Transactional
    public RequestVO createRequest(RequestCreateRequest request) {
        if (request.hospital() == null || request.hospital().isBlank()) {
            throw new BizException("申请医院不能为空");
        }
        if (request.patientName() == null || request.patientName().isBlank()) {
            throw new BizException("患者姓名不能为空");
        }
        if (!BloodDict.isValidBloodType(request.patientBloodType())) {
            throw new BizException("未知的患者血型：" + request.patientBloodType());
        }
        if (!BloodDict.isValidComponent(request.component())) {
            throw new BizException("未知的血液成分：" + request.component());
        }
        if (!BloodDict.isValidUrgency(request.urgency())) {
            throw new BizException("未知的紧急程度：" + request.urgency());
        }
        int units = request.unitsU() == null ? 1 : request.unitsU();
        if (units <= 0) {
            throw new BizException("申请 U 数必须大于 0");
        }
        HospitalRequest entity = new HospitalRequest();
        entity.setRequestNo("RQ" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", requestRepository.count() + 1));
        entity.setHospital(request.hospital().trim());
        entity.setPatientName(request.patientName().trim());
        entity.setPatientBloodType(request.patientBloodType());
        entity.setComponent(request.component());
        entity.setUnitsU(units);
        entity.setUrgency(request.urgency());
        entity.setStatus("PENDING");
        entity.setIssuedU(0);
        entity.setRequestTime(LocalDateTime.now());
        entity.setRemark(request.remark());
        entity.setCreatedAt(LocalDateTime.now());
        return toVO(requestRepository.save(entity));
    }

    // ---------------- 交叉配血 ----------------

    @Transactional(readOnly = true)
    public List<CrossMatchVO> listMatches(String bloodType) {
        List<CrossMatch> matches = bloodType == null || bloodType.isBlank()
                ? matchRepository.findAll()
                : matchRepository.findByBloodTypeOrderByIdDesc(bloodType);
        List<CrossMatchVO> vos = new ArrayList<>();
        for (CrossMatch match : matches) {
            vos.add(toVO(match));
        }
        return vos;
    }

    /**
     * 登记配血：供血袋必须可用、未过期，且血型与受血者同型（O- 仅在紧急/特急时作万能供者）。
     */
    @Transactional
    public CrossMatchVO createMatch(CrossMatchRequest request) {
        HospitalRequest req = requestRepository.findById(request.requestId())
                .orElseThrow(() -> new BizException("用血申请不存在：id=" + request.requestId()));
        if ("DONE".equals(req.getStatus()) || "CANCELED".equals(req.getStatus())) {
            throw new BizException("用血申请 " + req.getRequestNo() + " 状态为「"
                    + BloodDict.requestStatusName(req.getStatus()) + "」，不能再配血");
        }
        BloodBag bag = bagRepository.findByBagNo(request.bagNo())
                .orElseThrow(() -> new BizException("血袋不存在：" + request.bagNo()));
        if ("QUARANTINED".equals(bag.getStatus())) {
            throw new BizException("血袋 " + bag.getBagNo() + " 已因输血不良反应同源隔离，配血一律拒绝");
        }
        if (!"AVAILABLE".equals(bag.getStatus())) {
            throw new BizException("血袋 " + bag.getBagNo() + " 状态为「"
                    + BloodDict.bagStatusName(bag.getStatus()) + "」，不可用于配血");
        }
        if (bag.getExpiryDate() != null && !bag.getExpiryDate().isAfter(LocalDate.now())) {
            throw new BizException("血袋 " + bag.getBagNo() + " 已于 " + bag.getExpiryDate() + " 过期，禁止用于配血");
        }
        boolean sameType = bag.getBloodType().equals(req.getPatientBloodType());
        boolean universal = "O-".equals(bag.getBloodType()) && !"ROUTINE".equals(req.getUrgency());
        if (!sameType && !universal) {
            throw new BizException("血型不合：供血袋为 " + bag.getBloodType() + "，受血者 "
                    + req.getPatientName() + " 为 " + req.getPatientBloodType()
                    + "；仅紧急 / 特急时可选用 O- 万能供者");
        }
        if (!bag.getComponent().equals(req.getComponent())) {
            throw new BizException("成分不符：申请 " + BloodDict.componentName(req.getComponent())
                    + "，血袋为 " + BloodDict.componentName(bag.getComponent()));
        }
        CrossMatch match = new CrossMatch();
        match.setMatchNo("XM" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", matchRepository.count() + 1));
        match.setRequest(req);
        match.setBag(bag);
        match.setBagNo(bag.getBagNo());
        match.setDonorName(bag.getDonorName());
        match.setBloodType(bag.getBloodType());
        match.setComponent(bag.getComponent());
        match.setExpiryDate(bag.getExpiryDate());
        match.setRecipient(req.getPatientName());
        match.setHospital(req.getHospital());
        match.setUnitsU(Math.min(nz(req.getUnitsU()) - nz(req.getIssuedU()),
                BloodDict.unitsOf(bag.getVolumeMl() == null ? 200 : bag.getVolumeMl())));
        match.setUrgency(req.getUrgency());
        match.setResult("PENDING");
        match.setOperator(request.operator() == null ? "姜妍" : request.operator());
        match.setMatchTime(LocalDateTime.now());
        match.setIssued(Boolean.FALSE);
        match.setRecoveryChecked(Boolean.FALSE);
        match.setCreatedAt(LocalDateTime.now());
        return toVO(matchRepository.save(match));
    }

    /**
     * 记录配血结果三态：相合 / 可疑 / 不相合。
     */
    @Transactional
    public CrossMatchVO setResult(Long id, MatchResultRequest request) {
        CrossMatch match = getMatch(id);
        if (Boolean.TRUE.equals(match.getIssued())) {
            throw new BizException("配血单 " + match.getMatchNo() + " 已出库，不能修改配血结果");
        }
        if (!BloodDict.isValidMatchResult(request.result())) {
            throw new BizException("未知的配血结果：" + request.result()
                    + "（可选 COMPATIBLE 相合 / SUSPECT 可疑 / INCOMPATIBLE 不相合）");
        }
        match.setResult(request.result());
        if (request.operator() != null && !request.operator().isBlank()) {
            match.setOperator(request.operator());
        }
        return toVO(matchRepository.save(match));
    }

    /**
     * 发血出库：配血不相合绝对不允许出库；过期、检验未放行、库存不足同样拦截。
     */
    @Transactional
    public CrossMatchVO issue(Long id, IssueRequest request) {
        CrossMatch match = getMatch(id);
        if (Boolean.TRUE.equals(match.getIssued())) {
            throw new BizException("配血单 " + match.getMatchNo() + " 已出库，请勿重复发血");
        }
        if ("INCOMPATIBLE".equals(match.getResult())) {
            throw new BizException("配血结果不相合，绝对不允许出库！请重新选择供血袋");
        }
        if ("SUSPECT".equals(match.getResult())) {
            throw new BizException("配血结果可疑，需重新复核确认相合后方可出库");
        }
        if ("PENDING".equals(match.getResult()) || match.getResult() == null) {
            throw new BizException("尚未判定配血结果，不能出库");
        }
        BloodBag bag = match.getBag();
        if (bag == null) {
            throw new BizException("配血单 " + match.getMatchNo() + " 未关联供血袋，不能出库");
        }
        if ("QUARANTINED".equals(bag.getStatus())) {
            throw new BizException("血袋 " + bag.getBagNo() + " 已因输血不良反应同源隔离，禁止发血出库");
        }
        if (!"AVAILABLE".equals(bag.getStatus())) {
            throw new BizException("血袋 " + bag.getBagNo() + " 当前状态为「"
                    + BloodDict.bagStatusName(bag.getStatus()) + "」，不能发血出库");
        }
        if (bag.getExpiryDate() != null && !bag.getExpiryDate().isAfter(LocalDate.now())) {
            throw new BizException("血袋 " + bag.getBagNo() + " 已于 " + bag.getExpiryDate()
                    + " 过期，过期血液不允许出库");
        }
        String sampleStatus = sampleStatusOf(bag);
        if (!"RELEASED".equals(sampleStatus)) {
            throw new BizException("血袋 " + bag.getBagNo() + " 的检验标本状态为「"
                    + BloodDict.sampleStatusName(sampleStatus) + "」，检验未放行的血液不允许发血");
        }
        int units = match.getUnitsU() == null ? 0 : match.getUnitsU();
        BloodInventory inv = inventoryRepository
                .findByBloodTypeAndComponent(match.getBloodType(), BloodDict.inventoryComponent(match.getComponent()))
                .orElse(null);
        int stock = inv == null || inv.getUnitCount() == null ? 0 : inv.getUnitCount();
        if (stock < units) {
            throw new BizException(match.getBloodType() + " " + BloodDict.componentName(match.getComponent())
                    + " 库存仅 " + stock + " U，本次需发 " + units + " U，缺口 " + (units - stock) + " U");
        }
        // 先登记发血领域事件（随事务提交触发 AFTER_COMMIT：扣减库存、回写申请、记流水），
        // 再在同一事务内用条件式 UPDATE 原子占用血袋（AVAILABLE→ISSUED）：
        // 与不良反应同源隔离的 AVAILABLE→QUARANTINED 互斥，谁先提交谁生效，风险血袋绝不流出。
        match.issue(request.operator() == null || request.operator().isBlank() ? "姜妍" : request.operator());
        matchRepository.save(match);
        int claimed = bagRepository.markIssuedIfAvailable(bag.getId());
        if (claimed == 0) {
            BloodBag latest = bagRepository.findById(bag.getId()).orElse(bag);
            if ("QUARANTINED".equals(latest.getStatus())) {
                throw new BizException("血袋 " + bag.getBagNo() + " 已因输血不良反应同源隔离，禁止发血出库");
            }
            throw new BizException("血袋 " + bag.getBagNo() + " 当前状态为「"
                    + BloodDict.bagStatusName(latest.getStatus()) + "」，不能发血出库");
        }
        return toVO(match);
    }

    /**
     * 血袋回收核对。
     */
    @Transactional
    public CrossMatchVO recovery(Long id) {
        CrossMatch match = getMatch(id);
        if (!Boolean.TRUE.equals(match.getIssued())) {
            throw new BizException("配血单 " + match.getMatchNo() + " 尚未出库，无需回收核对");
        }
        if (Boolean.TRUE.equals(match.getRecoveryChecked())) {
            throw new BizException("配血单 " + match.getMatchNo() + " 已完成回收核对");
        }
        match.setRecoveryChecked(Boolean.TRUE);
        match.setRecoveryTime(LocalDateTime.now());
        return toVO(matchRepository.save(match));
    }

    @Transactional(readOnly = true)
    public List<CrossMatchVO> issuedRecords() {
        List<CrossMatchVO> vos = new ArrayList<>();
        for (CrossMatch match : matchRepository.findByIssuedTrueOrderByIdDesc()) {
            vos.add(toVO(match));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public List<HospitalIssueVO> hospitalSummary() {
        return matchRepository.summaryByHospital();
    }

    // ---------------- 内部方法 ----------------

    private String sampleStatusOf(BloodBag bag) {
        if (bag.getCollection() == null) {
            return "PENDING";
        }
        TestSample sample = sampleRepository.findByCollectionId(bag.getCollection().getId()).orElse(null);
        return sample == null ? "PENDING" : sample.getStatus();
    }

    private CrossMatch getMatch(Long id) {
        return matchRepository.findById(id).orElseThrow(() -> new BizException("配血记录不存在：id=" + id));
    }

    private RequestVO toVO(HospitalRequest request) {
        int units = nz(request.getUnitsU());
        int issued = nz(request.getIssuedU());
        return new RequestVO(request.getId(), request.getRequestNo(), request.getHospital(),
                request.getPatientName(), request.getPatientBloodType(), request.getComponent(),
                BloodDict.componentName(request.getComponent()), units, request.getUrgency(),
                BloodDict.urgencyName(request.getUrgency()), request.getStatus(),
                BloodDict.requestStatusName(request.getStatus()), issued, units - issued,
                request.getRequestTime(), request.getRemark());
    }

    private CrossMatchVO toVO(CrossMatch match) {
        LocalDate expiry = match.getExpiryDate();
        Integer days = expiry == null ? null : (int) ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        String sampleStatus = match.getBag() == null ? "PENDING" : sampleStatusOf(match.getBag());
        String block = null;
        if (Boolean.TRUE.equals(match.getIssued())) {
            block = "已出库";
        } else if (match.getBag() != null && "QUARANTINED".equals(match.getBag().getStatus())) {
            block = "血袋已因输血不良反应同源隔离，禁止出库";
        } else if ("INCOMPATIBLE".equals(match.getResult())) {
            block = "配血不相合，绝对不允许出库";
        } else if ("SUSPECT".equals(match.getResult())) {
            block = "配血可疑，需复核后出库";
        } else if (expiry != null && !expiry.isAfter(LocalDate.now())) {
            block = "血袋已于 " + expiry + " 过期，禁止出库";
        } else if (!"RELEASED".equals(sampleStatus)) {
            block = "检验标本未放行（" + BloodDict.sampleStatusName(sampleStatus) + "），禁止出库";
        }
        return new CrossMatchVO(match.getId(), match.getMatchNo(),
                match.getRequest() == null ? null : match.getRequest().getId(),
                match.getRequest() == null ? null : match.getRequest().getRequestNo(),
                match.getBag() == null ? null : match.getBag().getId(), match.getBagNo(), match.getDonorName(),
                match.getBloodType(), match.getComponent(), BloodDict.componentName(match.getComponent()),
                expiry, days, match.getRecipient(), match.getHospital(), match.getUnitsU(), match.getUrgency(),
                BloodDict.urgencyName(match.getUrgency()), match.getResult(),
                BloodDict.matchResultName(match.getResult()), match.getOperator(), match.getMatchTime(),
                match.getIssued(), match.getIssueTime(), match.getIssueOperator(), match.getRecoveryChecked(),
                match.getRecoveryTime(), sampleStatus, BloodDict.sampleStatusName(sampleStatus), block);
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
