package com.blood.center.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.ReactionBagLink;

public interface ReactionBagLinkRepository extends JpaRepository<ReactionBagLink, Long> {

    List<ReactionBagLink> findByEventIdOrderByIdAsc(Long eventId);

    /** 某血袋被哪些「在办事件」隔离（排除关联）——任一事件未结束前不得释放 */
    @Query("select l from ReactionBagLink l where l.bag.id = :bagId and l.linkStatus = 'QUARANTINED' "
            + "and l.event.status = 'OPEN' and l.event.id <> :excludeEventId")
    List<ReactionBagLink> findOpenQuarantineByBagExcludingEvent(@Param("bagId") Long bagId,
                                                                @Param("excludeEventId") Long excludeEventId);

    /** 确认风险时：同一血袋在其他在办事件中的隔离关联同步标注为已报废，避免重复报废 */
    @Modifying(flushAutomatically = true)
    @Query("update ReactionBagLink l set l.linkStatus = 'SCRAPPED', "
            + "l.note = :note, l.updatedAt = :now "
            + "where l.bag.id = :bagId and l.linkStatus = 'QUARANTINED' and l.event.status = 'OPEN' "
            + "and l.event.id <> :eventId")
    int markOtherEventsScrapped(@Param("bagId") Long bagId, @Param("eventId") Long eventId,
                                @Param("note") String note,
                                @Param("now") java.time.LocalDateTime now);

    /**
     * 复核后的孤儿扫描：找出所有事件都已结束、自身却仍挂「隔离中 / 保持隔离」的关联。
     * 并发两宗「排除关联」同时结束时，最后关闭的一方用它补释放，避免血袋被永远锁在隔离态。
     */
    @Query("select distinct l from ReactionBagLink l where l.linkStatus in ('QUARANTINED','KEPT') "
            + "and l.event.status <> 'OPEN' "
            + "and not exists (select 1 from ReactionBagLink l2 where l2.bag.id = l.bag.id "
            + "and l2.linkStatus in ('QUARANTINED','KEPT') and l2.event.status = 'OPEN') "
            + "order by l.id asc")
    List<ReactionBagLink> findOrphanQuarantinedLinks();

    /** 某血袋被哪些事件关联（复核后补释放 / 同步已释放用） */
    List<ReactionBagLink> findByBagIdOrderByIdAsc(Long bagId);
}
