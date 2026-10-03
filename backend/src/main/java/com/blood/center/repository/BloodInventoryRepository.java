package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.blood.center.domain.BloodInventory;
import com.blood.center.dto.ComponentSummaryVO;

public interface BloodInventoryRepository extends JpaRepository<BloodInventory, Long> {

    List<BloodInventory> findByBloodTypeOrderByIdAsc(String bloodType);

    Optional<BloodInventory> findByBloodTypeAndComponent(String bloodType, String component);

    /**
     * 手写 JPQL：按「血型 × 成分」二维汇总库存（袋数 / U 数 / 最近效期）
     */
    @Query("select new com.blood.center.dto.ComponentSummaryVO(i.bloodType, i.component, '', "
            + "sum(i.bagCount), sum(i.unitCount), min(i.nearestExpiry)) "
            + "from BloodInventory i group by i.bloodType, i.component order by i.bloodType, i.component")
    List<ComponentSummaryVO> summaryByBloodTypeAndComponent();

    /**
     * 手写 JPQL：只看某一血型的四格库存
     */
    @Query("select new com.blood.center.dto.ComponentSummaryVO(i.bloodType, i.component, '', "
            + "sum(i.bagCount), sum(i.unitCount), min(i.nearestExpiry)) "
            + "from BloodInventory i where i.bloodType = :bloodType "
            + "group by i.bloodType, i.component order by i.component")
    List<ComponentSummaryVO> summaryOfBloodType(String bloodType);

    /** 手写 JPQL：低于预警线的格子数 */
    @Query("select count(i) from BloodInventory i where i.bagCount < i.warningLine")
    long countLowStock();
}
