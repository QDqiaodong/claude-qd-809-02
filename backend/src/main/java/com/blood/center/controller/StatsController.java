package com.blood.center.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blood.center.common.ApiResponse;
import com.blood.center.common.BloodDict;
import com.blood.center.dto.ComponentSummaryVO;
import com.blood.center.dto.HospitalIssueVO;
import com.blood.center.dto.OverviewVO;
import com.blood.center.service.StatsService;

/**
 * 概览 KPI 与汇总统计。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ApiResponse<OverviewVO> overview() {
        return ApiResponse.success(statsService.overview());
    }

    /** 按血型 × 成分汇总库存（JPQL） */
    @GetMapping("/inventory-summary")
    public ApiResponse<List<ComponentSummaryVO>> inventorySummary() {
        return ApiResponse.success(statsService.inventorySummary());
    }

    /** 按医院汇总发血量（JPQL） */
    @GetMapping("/hospital-summary")
    public ApiResponse<List<HospitalIssueVO>> hospitalSummary() {
        return ApiResponse.success(statsService.hospitalIssueSummary());
    }

    /** 前端下拉用的词典 */
    @GetMapping("/dict")
    public ApiResponse<Map<String, Object>> dict() {
        return ApiResponse.success(Map.of(
                "bloodTypes", BloodDict.BLOOD_TYPES,
                "components", BloodDict.COMPONENTS,
                "stations", BloodDict.STATIONS));
    }
}
