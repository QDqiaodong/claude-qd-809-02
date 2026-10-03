package com.blood.center.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.CollectionRecord;

public interface CollectionRecordRepository extends JpaRepository<CollectionRecord, Long> {

    List<CollectionRecord> findByDonorIdOrderByIdDesc(Long donorId);

    List<CollectionRecord> findByCollectDateOrderByIdDesc(LocalDate collectDate);

    List<CollectionRecord> findByStationCodeOrderByIdDesc(String stationCode);

    Optional<CollectionRecord> findTopByStationCodeOrderByIdDesc(String stationCode);

    @Query("select count(c) from CollectionRecord c where c.collectDate = :date")
    long countByDate(@Param("date") LocalDate date);

    @Query("select coalesce(sum(c.volumeMl), 0) from CollectionRecord c where c.collectDate = :date")
    long sumVolumeByDate(@Param("date") LocalDate date);
}
