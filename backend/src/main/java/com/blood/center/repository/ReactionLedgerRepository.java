package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.ReactionLedger;

public interface ReactionLedgerRepository extends JpaRepository<ReactionLedger, Long> {

    /** 台账按时间顺序查看隔离、释放和报废的逐步记录 */
    List<ReactionLedger> findByEventIdOrderByIdAsc(Long eventId);

    /** 同事件最近一条台账（保证同秒内多条台账时间仍严格单调） */
    @Query("select l from ReactionLedger l where l.event.id = :eventId order by l.id desc limit 1")
    Optional<ReactionLedger> findLastByEventId(@Param("eventId") Long eventId);
}
