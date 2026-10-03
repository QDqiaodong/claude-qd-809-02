package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.blood.center.domain.PreparationBatch;
import com.blood.center.dto.PreparationSummaryVO;

public interface PreparationBatchRepository extends JpaRepository<PreparationBatch, Long> {

    List<PreparationBatch> findByBloodTypeOrderByIdDesc(String bloodType);

    Optional<PreparationBatch> findTopByOrderByIdDesc();

    List<PreparationBatch> findByCollectionId(Long collectionId);

    /**
     * 手写 JPQL：按血型汇总成分制备成果（批次数 / 总袋数 / 总容量 / 最近制备时间）
     */
    @Query("select new com.blood.center.dto.PreparationSummaryVO(p.bloodType, count(p), "
            + "sum(p.rbcBags + p.plasmaBags + p.plateletBags + p.cryoBags), "
            + "sum(p.rbcVolumeMl + p.plasmaVolumeMl + p.plateletVolumeMl + p.cryoVolumeMl), "
            + "max(p.preparedTime)) "
            + "from PreparationBatch p group by p.bloodType order by p.bloodType")
    List<PreparationSummaryVO> summaryByBloodType();
}
