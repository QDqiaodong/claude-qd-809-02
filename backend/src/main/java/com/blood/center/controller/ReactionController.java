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
import com.blood.center.dto.ReactionReportRequest;
import com.blood.center.dto.ReactionReviewRequest;
import com.blood.center.dto.ReactionVO;
import com.blood.center.dto.TraceChainVO;
import com.blood.center.service.ReactionService;

/**
 * 模块六：输血不良反应追溯台。
 * 上报 → 同源隔离 → 质量复核（排除关联释放 / 确认风险报废）的完整闭环。
 */
@RestController
@RequestMapping("/api/reactions")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    /** 事件台账列表，可按状态过滤（OPEN 在办 / EXCLUDED 排除关联 / CONFIRMED 确认风险） */
    @GetMapping
    public ApiResponse<List<ReactionVO>> list(@RequestParam(required = false) String status) {
        return ApiResponse.success(reactionService.listEvents(status));
    }

    /** 事件详情：来源链、同源血袋清单、逐步处置台账 */
    @GetMapping("/{id}")
    public ApiResponse<ReactionVO> detail(@PathVariable Long id) {
        return ApiResponse.success(reactionService.detail(id));
    }

    /** 上报前反查来源链：发血记录 → 供血血袋 → 制备批次 → 采血记录 → 检验标本 */
    @GetMapping("/trace/{matchId}")
    public ApiResponse<TraceChainVO> trace(@PathVariable Long matchId) {
        return ApiResponse.success(reactionService.trace(matchId));
    }

    /**
     * 上报反应（幂等）：同发血记录 + 同反应时间 + 同症状只形成一宗事件；
     * 重复提交返回 duplicated=true 与既有事件；建立事件即隔离同源在库血袋。
     */
    @PostMapping("/report")
    public ApiResponse<ReactionVO> report(@RequestBody ReactionReportRequest request) {
        ReactionVO vo = reactionService.report(request);
        String message = vo.reportMessage() != null ? vo.reportMessage()
                : "反应事件已登记";
        return ApiResponse.success(message, vo);
    }

    /** 质量复核：EXCLUDED 排除关联（条件释放）/ CONFIRMED 确认风险（隔离转报废） */
    @PostMapping("/{id}/review")
    public ApiResponse<ReactionVO> review(@PathVariable Long id, @RequestBody ReactionReviewRequest request) {
        ReactionVO vo = reactionService.review(id, request);
        String message = "CONFIRMED".equals(vo.reviewResult())
                ? "已确认风险，同源隔离血袋全部报废，处置链已留痕"
                : "已排除关联，满足条件的血袋已释放回库";
        return ApiResponse.success(message, vo);
    }
}
