package com.blood.center.dto;

/**
 * 配血结果三态：COMPATIBLE 相合 / SUSPECT 可疑 / INCOMPATIBLE 不相合。
 */
public record MatchResultRequest(String result, String operator) {
}
