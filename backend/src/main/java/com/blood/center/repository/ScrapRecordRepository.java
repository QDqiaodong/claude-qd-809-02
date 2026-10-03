package com.blood.center.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blood.center.domain.ScrapRecord;

public interface ScrapRecordRepository extends JpaRepository<ScrapRecord, Long> {

    List<ScrapRecord> findTop50ByOrderByIdDesc();

    List<ScrapRecord> findByBloodTypeOrderByIdDesc(String bloodType);
}
