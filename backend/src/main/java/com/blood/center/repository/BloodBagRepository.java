package com.blood.center.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.BloodBag;

public interface BloodBagRepository extends JpaRepository<BloodBag, Long> {

    Optional<BloodBag> findByBagNo(String bagNo);

    List<BloodBag> findByBloodTypeAndComponentOrderByExpiryDateAsc(String bloodType, String component);

    List<BloodBag> findByBloodTypeOrderByExpiryDateAsc(String bloodType);

    /** 同一采血来源的全部成分血袋（不良反应同源追溯） */
    List<BloodBag> findByCollectionIdOrderByIdAsc(Long collectionId);

    long countByStatus(String status);

    /** 手写 JPQL：待报废的过期可用血袋 */
    @Query("select b from BloodBag b where b.status = 'AVAILABLE' and b.expiryDate <= :today order by b.expiryDate asc")
    List<BloodBag> findExpiredAvailable(@Param("today") LocalDate today);

    @Query("select count(b) from BloodBag b where b.status = 'AVAILABLE' and b.expiryDate <= :today")
    long countExpiredAvailable(@Param("today") LocalDate today);

    @Query("select coalesce(sum(b.volumeMl), 0) from BloodBag b where b.status = :status")
    long sumVolumeByStatus(@Param("status") String status);

    // 条件式状态流转先 flushAutomatically 落掉待写数据；不 clear 一级缓存，
    // 调用方在 UPDATE 后显式重新读取血袋，避免用过期状态算库存。

    /**
     * 条件式状态流转：仅当血袋当前为 AVAILABLE 时置为 QUARANTINED。
     * 返回受影响行数：0 表示已被其他事务发出 / 报废 / 隔离，调用方不得继续处理该袋。
     */
    @Modifying(flushAutomatically = true)
    @Query("update BloodBag b set b.status = 'QUARANTINED' where b.id = :bagId and b.status = 'AVAILABLE'")
    int quarantineIfAvailable(@Param("bagId") Long bagId);

    /** 仅当血袋当前为 QUARANTINED 时释放为 AVAILABLE，防止状态倒退与重复释放 */
    @Modifying(flushAutomatically = true)
    @Query("update BloodBag b set b.status = 'AVAILABLE' where b.id = :bagId and b.status = 'QUARANTINED'")
    int releaseIfQuarantined(@Param("bagId") Long bagId);

    /** 仅当血袋当前为 QUARANTINED 时转为 SCRAPPED，防止重复报废、重复扣减 */
    @Modifying(flushAutomatically = true)
    @Query("update BloodBag b set b.status = 'SCRAPPED' where b.id = :bagId and b.status = 'QUARANTINED'")
    int scrapIfQuarantined(@Param("bagId") Long bagId);

    /**
     * 发血事务内原子占用血袋：仅 AVAILABLE→ISSUED。
     * 返回 0 说明血袋在并发窗口被同源隔离 / 报废 / 发出，本次发血必须失败，风险血袋不得出库。
     */
    @Modifying(flushAutomatically = true)
    @Query("update BloodBag b set b.status = 'ISSUED' where b.id = :bagId and b.status = 'AVAILABLE'")
    int markIssuedIfAvailable(@Param("bagId") Long bagId);
}
