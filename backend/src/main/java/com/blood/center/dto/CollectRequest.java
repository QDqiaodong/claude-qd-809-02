package com.blood.center.dto;

/**
 * 采血登记请求。
 */
public record CollectRequest(Long donorId, String stationCode, Integer volumeMl, String nurse) {
}
