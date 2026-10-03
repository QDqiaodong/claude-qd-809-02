package com.blood.center.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blood.center.common.BizException;
import com.blood.center.common.BloodDict;
import com.blood.center.domain.CollectionRecord;
import com.blood.center.domain.Donor;
import com.blood.center.dto.CollectionVO;
import com.blood.center.dto.CollectRequest;
import com.blood.center.dto.DonorRequest;
import com.blood.center.dto.DonorVO;
import com.blood.center.dto.StationVO;
import com.blood.center.repository.CollectionRecordRepository;
import com.blood.center.repository.DonorRepository;

/**
 * 献血者与采血：点位状态盘、采血登记、业务红线校验（间隔 6 个月 / 体重上限 / 暂缓献血）。
 */
@Service
public class CollectionService {

    /** Redis：累计采血人次 */
    public static final String COLLECT_COUNTER = "blood:center:collect:count";

    private static final DateTimeFormatter SEQ_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DonorRepository donorRepository;
    private final CollectionRecordRepository collectionRepository;
    private final StringRedisTemplate redis;

    public CollectionService(DonorRepository donorRepository, CollectionRecordRepository collectionRepository,
                             StringRedisTemplate redis) {
        this.donorRepository = donorRepository;
        this.collectionRepository = collectionRepository;
        this.redis = redis;
    }

    // ---------------- 献血者 ----------------

    @Transactional(readOnly = true)
    public List<DonorVO> listDonors(String bloodType) {
        List<Donor> donors = bloodType == null || bloodType.isBlank()
                ? donorRepository.findAll()
                : donorRepository.findByBloodTypeOrderByIdAsc(bloodType);
        List<DonorVO> vos = new ArrayList<>();
        for (Donor donor : donors) {
            vos.add(toVO(donor));
        }
        return vos;
    }

    @Transactional
    public DonorVO createDonor(DonorRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new BizException("献血者姓名不能为空");
        }
        if (!BloodDict.isValidBloodType(request.bloodType())) {
            throw new BizException("未知的血型：" + request.bloodType());
        }
        if (request.weightKg() == null || request.weightKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("体重必须大于 0，用于核定单次采血上限");
        }
        if (request.idCardNo() == null || request.idCardNo().isBlank()) {
            throw new BizException("证件号不能为空");
        }
        if (donorRepository.findByIdCardNo(request.idCardNo().trim()).isPresent()) {
            throw new BizException("证件号 " + request.idCardNo() + " 已建档，不能重复登记");
        }
        Donor donor = new Donor();
        donor.setDonorNo("D" + LocalDate.now().format(SEQ_DAY) + "-"
                + String.format("%03d", donorRepository.count() + 1));
        donor.setName(request.name().trim());
        donor.setGender(request.gender() == null || request.gender().isBlank() ? "男" : request.gender());
        donor.setAge(request.age() == null ? 30 : request.age());
        donor.setBloodType(request.bloodType());
        donor.setIdCardNo(request.idCardNo().trim());
        donor.setPhone(request.phone());
        donor.setWeightKg(request.weightKg());
        donor.setDonationCount(0);
        donor.setTotalVolumeMl(0);
        donor.setCreatedAt(LocalDateTime.now());
        return toVO(donorRepository.save(donor));
    }

    // ---------------- 采血位状态盘 ----------------

    /**
     * 12 个采血位围成一圈：占用 / 空闲 / 消毒中。
     */
    @Transactional(readOnly = true)
    public List<StationVO> stations(String bloodType) {
        List<StationVO> list = new ArrayList<>();
        for (String code : BloodDict.STATIONS) {
            CollectionRecord latest = collectionRepository.findTopByStationCodeOrderByIdDesc(code).orElse(null);
            String status = "IDLE";
            if (latest != null && latest.getCollectDate() != null
                    && !latest.getCollectDate().isBefore(LocalDate.now())) {
                status = "COLLECTING".equals(latest.getStatus()) ? "OCCUPIED" : "CLEANING";
            }
            Donor donor = latest == null ? null : latest.getDonor();
            String donorBlood = donor == null ? null : donor.getBloodType();
            if (bloodType != null && !bloodType.isBlank() && donorBlood != null
                    && !bloodType.equals(donorBlood) && !"IDLE".equals(status)) {
                // 顶部血型 chips 过滤：被其它血型占用的点位在圆环上置灰，本血型点位高亮
                status = "OTHER";
            }
            list.add(new StationVO(code, status, BloodDict.stationStatusName(status),
                    latest == null ? null : latest.getId(),
                    donor == null ? null : donor.getName(),
                    donor == null ? null : donor.getName().substring(0, 1),
                    donorBlood,
                    latest == null ? null : latest.getVolumeMl(),
                    latest == null ? null : latest.getNurse(),
                    latest == null ? null : latest.getCollectNo(),
                    latest == null ? null : latest.getCollectDate()));
        }
        return list;
    }

    // ---------------- 采血登记 ----------------

    /**
     * 采血登记：间隔不足 6 个月 / 超过体重上限 / 处于暂缓期 一律拦截。
     */
    @Transactional
    public CollectionVO collect(CollectRequest request) {
        Donor donor = donorRepository.findById(request.donorId())
                .orElseThrow(() -> new BizException("献血者不存在：id=" + request.donorId()));
        LocalDate today = LocalDate.now();

        if (donor.getDeferUntil() != null && !donor.getDeferUntil().isBefore(today)) {
            throw new BizException(donor.getName() + " 处于暂缓献血期，截止日期 " + donor.getDeferUntil()
                    + "，暂缓期结束前不得采血");
        }
        LocalDate nextAvailable = donor.getLastDonationDate() == null
                ? today
                : donor.getLastDonationDate().plusMonths(BloodDict.MIN_INTERVAL_MONTHS);
        if (donor.getLastDonationDate() != null && nextAvailable.isAfter(today)) {
            throw new BizException(donor.getName() + " 上次献血 " + donor.getLastDonationDate()
                    + "，距下次可献日期 " + nextAvailable + " 还有 "
                    + java.time.temporal.ChronoUnit.DAYS.between(today, nextAvailable)
                    + " 天，两次全血献血间隔不得小于 " + BloodDict.MIN_INTERVAL_MONTHS + " 个月");
        }
        int maxByWeight = BloodDict.maxVolumeByWeight(donor.getWeightKg());
        int volume = request.volumeMl() == null ? maxByWeight : request.volumeMl();
        if (volume < BloodDict.MIN_SINGLE_VOLUME_ML) {
            throw new BizException("采血量不得低于 " + BloodDict.MIN_SINGLE_VOLUME_ML + " ml");
        }
        if (volume > maxByWeight) {
            throw new BizException("采血量 " + volume + " ml 超过 " + donor.getName() + " 的允许上限 "
                    + maxByWeight + " ml（体重 " + donor.getWeightKg() + " kg × "
                    + BloodDict.VOLUME_PER_KG + " ml/kg）");
        }
        if (volume > BloodDict.MAX_SINGLE_VOLUME_ML) {
            throw new BizException("单次采血量不得超过 " + BloodDict.MAX_SINGLE_VOLUME_ML + " ml");
        }
        String station = request.stationCode() == null || request.stationCode().isBlank()
                ? freeStation() : request.stationCode();
        CollectionRecord occupying = collectionRepository.findTopByStationCodeOrderByIdDesc(station).orElse(null);
        if (occupying != null && "COLLECTING".equals(occupying.getStatus())
                && today.equals(occupying.getCollectDate())) {
            throw new BizException("采血位 " + station + " 正在采血中，请先完成或换一个采血位");
        }

        CollectionRecord record = new CollectionRecord();
        record.setCollectNo("C" + today.format(SEQ_DAY) + "-"
                + String.format("%03d", collectionRepository.count() + 1));
        record.setDonor(donor);
        record.setStationCode(station);
        record.setVolumeMl(volume);
        record.setCollectDate(today);
        record.setNurse(request.nurse() == null || request.nurse().isBlank() ? "沈青" : request.nurse());
        record.setStatus("COLLECTING");
        record.setCreatedAt(LocalDateTime.now());
        CollectionRecord saved = collectionRepository.save(record);

        donor.setDonationCount(nz(donor.getDonationCount()) + 1);
        donor.setTotalVolumeMl(nz(donor.getTotalVolumeMl()) + volume);
        donor.setLastDonationDate(today);
        donorRepository.save(donor);

        redis.opsForValue().increment(COLLECT_COUNTER);
        return toVO(saved);
    }

    /**
     * 完成采血：置为已完成，领域事件会生成检验标本。
     */
    @Transactional
    public CollectionVO finish(Long id) {
        CollectionRecord record = getRecord(id);
        if (!"COLLECTING".equals(record.getStatus())) {
            throw new BizException("采血单 " + record.getCollectNo() + " 当前状态为「"
                    + BloodDict.collectionStatusName(record.getStatus()) + "」，只有采血中的记录可以完成");
        }
        record.complete();
        return toVO(collectionRepository.save(record));
    }

    /**
     * 采血过程异常（晕针、血流不畅等）。
     */
    @Transactional
    public CollectionVO abnormal(Long id) {
        CollectionRecord record = getRecord(id);
        if (!"COLLECTING".equals(record.getStatus())) {
            throw new BizException("只有采血中的记录可以标记异常");
        }
        record.abnormal();
        return toVO(collectionRepository.save(record));
    }

    @Transactional(readOnly = true)
    public List<CollectionVO> listCollections(String bloodType, Long donorId) {
        List<CollectionRecord> records;
        if (donorId != null) {
            records = collectionRepository.findByDonorIdOrderByIdDesc(donorId);
        } else if (bloodType == null || bloodType.isBlank()) {
            records = collectionRepository.findAll();
        } else {
            records = collectionRepository.findAll().stream()
                    .filter(r -> r.getDonor() != null && bloodType.equals(r.getDonor().getBloodType()))
                    .toList();
        }
        List<CollectionVO> vos = new ArrayList<>();
        for (CollectionRecord record : records) {
            vos.add(toVO(record));
        }
        return vos;
    }

    @Transactional(readOnly = true)
    public long todayVolume() {
        return collectionRepository.sumVolumeByDate(LocalDate.now());
    }

    // ---------------- 视图转换 ----------------

    private String freeStation() {
        for (String code : BloodDict.STATIONS) {
            CollectionRecord latest = collectionRepository.findTopByStationCodeOrderByIdDesc(code).orElse(null);
            if (latest == null || !"COLLECTING".equals(latest.getStatus())
                    || !LocalDate.now().equals(latest.getCollectDate())) {
                return code;
            }
        }
        throw new BizException("12 个采血位全部占用，请稍后再登记");
    }

    private CollectionRecord getRecord(Long id) {
        return collectionRepository.findById(id)
                .orElseThrow(() -> new BizException("采血记录不存在：id=" + id));
    }

    private DonorVO toVO(Donor donor) {
        LocalDate today = LocalDate.now();
        LocalDate nextAvailable = donor.getLastDonationDate() == null
                ? today
                : donor.getLastDonationDate().plusMonths(BloodDict.MIN_INTERVAL_MONTHS);
        String block = null;
        if (donor.getDeferUntil() != null && !donor.getDeferUntil().isBefore(today)) {
            block = "暂缓献血至 " + donor.getDeferUntil();
        } else if (donor.getLastDonationDate() != null && nextAvailable.isAfter(today)) {
            block = "间隔不足 " + BloodDict.MIN_INTERVAL_MONTHS + " 个月，下次可献 " + nextAvailable;
        }
        return new DonorVO(donor.getId(), donor.getDonorNo(), donor.getName(), donor.getGender(), donor.getAge(),
                donor.getBloodType(), donor.getIdCardNo(), donor.getPhone(), donor.getWeightKg(),
                donor.getDonationCount(), donor.getTotalVolumeMl(), donor.getDeferUntil(),
                donor.getLastDonationDate(), nextAvailable,
                BloodDict.maxVolumeByWeight(donor.getWeightKg()), block == null, block);
    }

    private CollectionVO toVO(CollectionRecord record) {
        Donor donor = record.getDonor();
        return new CollectionVO(record.getId(), record.getCollectNo(), donor == null ? null : donor.getId(),
                donor == null ? null : donor.getName(), donor == null ? null : donor.getBloodType(),
                record.getStationCode(), record.getVolumeMl(), record.getCollectDate(), record.getNurse(),
                record.getStatus(), BloodDict.collectionStatusName(record.getStatus()));
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
