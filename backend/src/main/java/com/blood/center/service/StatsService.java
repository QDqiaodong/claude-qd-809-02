package com.blood.center.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blood.center.common.BloodDict;
import com.blood.center.dto.ComponentSummaryVO;
import com.blood.center.dto.HospitalIssueVO;
import com.blood.center.dto.OverviewVO;
import com.blood.center.listener.BloodEventListener;
import com.blood.center.repository.BloodBagRepository;
import com.blood.center.repository.BloodInventoryRepository;
import com.blood.center.repository.CollectionRecordRepository;
import com.blood.center.repository.CrossMatchRepository;
import com.blood.center.repository.DonorRepository;
import com.blood.center.repository.HospitalRequestRepository;
import com.blood.center.repository.ScrapRecordRepository;
import com.blood.center.repository.TestSampleRepository;

/**
 * 顶部 KPI 概览与两处 JPQL 汇总（按血型×成分、按医院）。
 */
@Service
public class StatsService {

    private final DonorRepository donorRepository;
    private final CollectionRecordRepository collectionRepository;
    private final TestSampleRepository sampleRepository;
    private final BloodInventoryRepository inventoryRepository;
    private final BloodBagRepository bagRepository;
    private final ScrapRecordRepository scrapRepository;
    private final CrossMatchRepository matchRepository;
    private final HospitalRequestRepository requestRepository;
    private final StringRedisTemplate redis;

    public StatsService(DonorRepository donorRepository, CollectionRecordRepository collectionRepository,
                        TestSampleRepository sampleRepository, BloodInventoryRepository inventoryRepository,
                        BloodBagRepository bagRepository, ScrapRecordRepository scrapRepository,
                        CrossMatchRepository matchRepository, HospitalRequestRepository requestRepository,
                        StringRedisTemplate redis) {
        this.donorRepository = donorRepository;
        this.collectionRepository = collectionRepository;
        this.sampleRepository = sampleRepository;
        this.inventoryRepository = inventoryRepository;
        this.bagRepository = bagRepository;
        this.scrapRepository = scrapRepository;
        this.matchRepository = matchRepository;
        this.requestRepository = requestRepository;
        this.redis = redis;
    }

    @Transactional(readOnly = true)
    public OverviewVO overview() {
        LocalDate today = LocalDate.now();
        return new OverviewVO(
                donorRepository.count(),
                collectionRepository.countByDate(today),
                collectionRepository.sumVolumeByDate(today),
                sampleRepository.countByStatus("PENDING"),
                sampleRepository.countByStatus("TESTING"),
                sampleRepository.countByStatus("RELEASED"),
                bagRepository.countByStatus("AVAILABLE"),
                bagRepository.sumVolumeByStatus("AVAILABLE") / BloodDict.ML_PER_UNIT,
                inventoryRepository.countLowStock(),
                bagRepository.countExpiredAvailable(today),
                requestRepository.countByStatus("PENDING"),
                matchRepository.countByIssuedTrue(),
                matchRepository.sumIssuedUnits(),
                scrapRepository.count(),
                redisCount("blood:center:collect:count"),
                redisCount(BloodEventListener.ISSUE_COUNTER));
    }

    @Transactional(readOnly = true)
    public List<ComponentSummaryVO> inventorySummary() {
        return inventoryRepository.summaryByBloodTypeAndComponent().stream()
                .map(v -> new ComponentSummaryVO(v.bloodType(), v.component(),
                        BloodDict.componentName(v.component()), v.bagCount(), v.unitCount(), v.nearestExpiry()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HospitalIssueVO> hospitalIssueSummary() {
        return matchRepository.summaryByHospital();
    }

    private long redisCount(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
