package com.blood.center.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blood.center.domain.HospitalRequest;

public interface HospitalRequestRepository extends JpaRepository<HospitalRequest, Long> {

    List<HospitalRequest> findByPatientBloodTypeOrderByIdDesc(String bloodType);

    List<HospitalRequest> findByStatusOrderByIdDesc(String status);

    long countByStatus(String status);
}
