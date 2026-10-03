package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.TestSample;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface TestSampleRepository extends JpaRepository<TestSample, Long> {

    List<TestSample> findByStatusOrderByIdAsc(String status);

    List<TestSample> findByBloodTypeOrderByIdAsc(String bloodType);

    Optional<TestSample> findByBarcode(String barcode);

    Optional<TestSample> findByCollectionId(Long collectionId);

    long countByStatus(String status);

    @Query("select count(s) from TestSample s where s.bloodType = :bloodType and s.status = :status")
    long countByBloodTypeAndStatus(@Param("bloodType") String bloodType, @Param("status") String status);

    /** 复测 / 放行关键路径上的行锁读取，串行化对同一标本的并发写。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select s from TestSample s where s.id = :id")
    Optional<TestSample> findByIdForUpdate(@Param("id") Long id);
}
