package com.blood.center.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.blood.center.domain.CrossMatch;
import com.blood.center.dto.HospitalIssueVO;

public interface CrossMatchRepository extends JpaRepository<CrossMatch, Long> {

    List<CrossMatch> findByRequestIdOrderByIdDesc(Long requestId);

    List<CrossMatch> findByBloodTypeOrderByIdDesc(String bloodType);

    List<CrossMatch> findByIssuedTrueOrderByIdDesc();

    long countByIssuedTrue();

    /** 反应上报事务内读取：一次 JOIN FETCH 出供血血袋与采血来源，杜绝懒加载无会话 */
    @Query("select c from CrossMatch c left join fetch c.bag b left join fetch b.collection "
            + "left join fetch c.request where c.id = :id")
    Optional<CrossMatch> findByIdWithBag(@Param("id") Long id);

    /**
     * 手写 JPQL：按医院汇总发血量（发血单数与已发 U 数）
     */
    @Query("select new com.blood.center.dto.HospitalIssueVO(c.hospital, count(c), sum(c.unitsU)) "
            + "from CrossMatch c where c.issued = true "
            + "group by c.hospital order by sum(c.unitsU) desc")
    List<HospitalIssueVO> summaryByHospital();

    @Query("select coalesce(sum(c.unitsU), 0) from CrossMatch c where c.issued = true")
    long sumIssuedUnits();
}
