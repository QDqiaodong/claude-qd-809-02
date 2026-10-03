package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.TransfusionReaction;

import jakarta.persistence.LockModeType;

public interface ReactionRepository extends JpaRepository<TransfusionReaction, Long> {

    /** 幂等键（发血记录 + 反应时间 + 症状）：重复点击 / 双人同时上报只落一宗 */
    Optional<TransfusionReaction> findByDedupKey(String dedupKey);

    List<TransfusionReaction> findAllByOrderByIdDesc();

    List<TransfusionReaction> findByStatusOrderByIdDesc(String status);

    long countByStatus(String status);

    /** 复核落结论时加行锁，并发复核串行化，防止重复释放 / 重复报废 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TransfusionReaction r where r.id = :id")
    Optional<TransfusionReaction> findByIdForUpdate(@Param("id") Long id);
}
