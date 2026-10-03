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
import com.blood.center.dto.PreparationSummaryVO;
import com.blood.center.dto.PreparationVO;
import com.blood.center.dto.ReleaseRequest;
import com.blood.center.dto.RetestRequest;
import com.blood.center.dto.SampleVO;
import com.blood.center.dto.TestResultRequest;
import com.blood.center.service.LabService;

/**
 * 模块二、三：标本检验（三条流转轨道）与成分制备批次。
 */
@RestController
@RequestMapping("/api/lab")
public class LabController {

    private final LabService labService;

    public LabController(LabService labService) {
        this.labService = labService;
    }

    /** 标本列表：待检 / 检测中 / 已放行 / 报废 */
    @GetMapping("/samples")
    public ApiResponse<List<SampleVO>> samples(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(labService.listSamples(bloodType));
    }

    /** 录入六项检验结果（初检 / 第 0 轮；已有复测轮后冻结） */
    @PostMapping("/samples/{id}/result")
    public ApiResponse<SampleVO> inputResult(@PathVariable Long id, @RequestBody TestResultRequest request) {
        return ApiResponse.success("检验结果已录入", labService.inputResult(id, request));
    }

    /** 发起复测：单项目一轮，填写新结果与复测原因；原结果不覆盖 */
    @PostMapping("/samples/{id}/retest")
    public ApiResponse<SampleVO> retest(@PathVariable Long id, @RequestBody RetestRequest request) {
        return ApiResponse.success("复测已记录为新一轮结果", labService.retest(id, request));
    }

    /** 标本放行（指定复核人）→ 领域事件自动生成成分制备单 */
    @PostMapping("/samples/{id}/release")
    public ApiResponse<SampleVO> release(@PathVariable Long id, @RequestBody ReleaseRequest request) {
        return ApiResponse.success("标本已放行，已自动生成成分制备批次并入库",
                labService.release(id, request));
    }

    /** 轨道拖拽流转 */
    @PostMapping("/samples/{id}/move")
    public ApiResponse<SampleVO> move(@PathVariable Long id,
                                      @RequestParam String status,
                                      @RequestParam(required = false) String reviewer) {
        return ApiResponse.success("标本状态已更新", labService.move(id, status, reviewer));
    }

    /** 成分制备批次列表 */
    @GetMapping("/preparations")
    public ApiResponse<List<PreparationVO>> preparations(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(labService.listPreparations(bloodType));
    }

    /** 按血型汇总制备成果（JPQL） */
    @GetMapping("/preparations/summary")
    public ApiResponse<List<PreparationSummaryVO>> preparationSummary() {
        return ApiResponse.success(labService.preparationSummary());
    }
}
