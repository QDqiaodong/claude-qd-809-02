package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blood.center.domain.Donor;

public interface DonorRepository extends JpaRepository<Donor, Long> {

    List<Donor> findByBloodTypeOrderByIdAsc(String bloodType);

    Optional<Donor> findByIdCardNo(String idCardNo);
}
