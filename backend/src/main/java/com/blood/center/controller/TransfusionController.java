package com.blood.center.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.blood.center.common.ApiResponse;
import com.blood.center.dto.CrossMatchRequest;
import com.blood.center.dto.CrossMatchVO;
import com.blood.center.dto.HospitalIssueVO;
import com.blood.center.dto.IssueRequest;
import com.blood.center.dto.MatchResultRequest;
import com.blood.center.dto.RequestCreateRequest;
import com.blood.center.dto.RequestVO;
import com.blood.center.service.TransfusionService;

/**
 * 模块五：医院用血申请、交叉配血与发血出库。
 */
@RestController
@RequestMapping("/api/transfusion")
public class TransfusionController {

    private final TransfusionService transfusionService;

    public TransfusionController(TransfusionService transfusionService) {
        this.transfusionService = transfusionService;
    }

    /** 用血申请列表 */
    @GetMapping("/requests")
    public ApiResponse<List<RequestVO>> requests(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(transfusionService.listRequests(bloodType));
    }

    @PostMapping("/requests")
    public ApiResponse<RequestVO> createRequest(@RequestBody RequestCreateRequest request) {
        return ApiResponse.success("用血申请已登记", transfusionService.createRequest(request));
    }

    /** 交叉配血列表（左右对照卡） */
    @GetMapping("/matches")
    public ApiResponse<List<CrossMatchVO>> matches(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(transfusionService.listMatches(bloodType));
    }

    /** 登记配血 */
    @PostMapping("/matches")
    public ApiResponse<CrossMatchVO> createMatch(@RequestBody CrossMatchRequest request) {
        return ApiResponse.success("配血登记成功，请判定配血结果", transfusionService.createMatch(request));
    }

    /** 配血结果三态 */
    @PostMapping("/matches/{id}/result")
    public ApiResponse<CrossMatchVO> setResult(@PathVariable Long id, @RequestBody MatchResultRequest request) {
        return ApiResponse.success("配血结果已记录", transfusionService.setResult(id, request));
    }

    /** 发血出库 → 领域事件扣减库存并记录发血流水 */
    @PostMapping("/matches/{id}/issue")
    public ApiResponse<CrossMatchVO> issue(@PathVariable Long id, @RequestBody(required = false) IssueRequest request) {
        return ApiResponse.success("发血出库成功，已扣减库存并记录发血流水",
                transfusionService.issue(id, request == null ? new IssueRequest(null) : request));
    }

    /** 血袋回收核对 */
    @PostMapping("/matches/{id}/recovery")
    public ApiResponse<CrossMatchVO> recovery(@PathVariable Long id) {
        return ApiResponse.success("回收核对完成", transfusionService.recovery(id));
    }

    /** 发血流水 */
    @GetMapping("/issues")
    public ApiResponse<List<CrossMatchVO>> issues() {
        return ApiResponse.success(transfusionService.issuedRecords());
    }

    /** 按医院汇总发血量（JPQL） */
    @GetMapping("/issues/by-hospital")
    public ApiResponse<List<HospitalIssueVO>> byHospital() {
        return ApiResponse.success(transfusionService.hospitalSummary());
    }
}
