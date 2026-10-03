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
import com.blood.center.dto.CollectRequest;
import com.blood.center.dto.CollectionVO;
import com.blood.center.dto.DonorRequest;
import com.blood.center.dto.DonorVO;
import com.blood.center.dto.StationVO;
import com.blood.center.service.CollectionService;

/**
 * 模块一：献血者与采血（采血位状态盘、采血登记、完成采血）。
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /** 献血者列表，可按血型过滤 */
    @GetMapping("/donors")
    public ApiResponse<List<DonorVO>> donors(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(collectionService.listDonors(bloodType));
    }

    @PostMapping("/donors")
    public ApiResponse<DonorVO> createDonor(@RequestBody DonorRequest request) {
        return ApiResponse.success("献血者建档成功", collectionService.createDonor(request));
    }

    /** 采血位状态盘：12 个点位的占用 / 空闲 / 消毒中 */
    @GetMapping("/stations")
    public ApiResponse<List<StationVO>> stations(@RequestParam(required = false) String bloodType) {
        return ApiResponse.success(collectionService.stations(bloodType));
    }

    @GetMapping
    public ApiResponse<List<CollectionVO>> list(@RequestParam(required = false) String bloodType,
                                                @RequestParam(required = false) Long donorId) {
        return ApiResponse.success(collectionService.listCollections(bloodType, donorId));
    }

    /** 采血登记（点采血位弹出登记） */
    @PostMapping("/collect")
    public ApiResponse<CollectionVO> collect(@RequestBody CollectRequest request) {
        return ApiResponse.success("采血登记成功，可完成采血后自动生成检验标本",
                collectionService.collect(request));
    }

    /** 完成采血 → 领域事件生成检验标本 */
    @PostMapping("/{id}/finish")
    public ApiResponse<CollectionVO> finish(@PathVariable Long id) {
        return ApiResponse.success("采血完成，已自动生成检验标本进入待检轨道",
                collectionService.finish(id));
    }

    /** 采血异常 */
    @PostMapping("/{id}/abnormal")
    public ApiResponse<CollectionVO> abnormal(@PathVariable Long id) {
        return ApiResponse.success("已标记采血异常", collectionService.abnormal(id));
    }
}
