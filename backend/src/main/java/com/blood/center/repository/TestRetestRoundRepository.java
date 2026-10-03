package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.TestRetestRound;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface TestRetestRoundRepository extends JpaRepository<TestRetestRound, Long> {

    List<TestRetestRound> findBySampleIdOrderByItemCodeAscRoundNoAsc(Long sampleId);

    Optional<TestRetestRound> findBySampleIdAndItemCodeAndRoundNo(Long sampleId, String itemCode, Integer roundNo);

    /**
     * 某项目当前是否已有「待复核」复测轮：同一项目同时只能有一轮在办，
     * 并发发起也只会新增一轮（第二个请求被业务规则挡回）。
     */
    long countBySampleIdAndItemCodeAndDecision(Long sampleId, String itemCode, String decision);

    long countBySampleIdAndDecision(Long sampleId, String decision);

    /**
     * 行锁加载标本已有的全部复测轮，配合 test_sample 行锁串行化同一标本的并发复测，
     * 保证 round_no 取号不重号、「在办轮唯一」判断不打滑。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select r from TestRetestRound r where r.sample.id = :sampleId order by r.itemCode asc, r.roundNo asc")
    List<TestRetestRound> findBySampleIdForUpdate(@Param("sampleId") Long sampleId);
}
