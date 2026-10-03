package com.blood.center.dto;

/**
 * 顶部 KPI 概览。
 */
public record OverviewVO(Long donorCount,
                         Long todayCollectionCount,
                         Long todayCollectionVolume,
                         Long pendingSampleCount,
                         Long testingSampleCount,
                         Long releasedSampleCount,
                         Long availableBagCount,
                         Long availableUnitCount,
                         Long lowStockCells,
                         Long expiredBagCount,
                         Long pendingRequestCount,
                         Long issuedMatchCount,
                         Long issuedUnits,
                         Long scrapCount,
                         Long redisCollectCount,
                         Long redisIssueCount) {
}
