package com.blood.center.dto;

/**
 * 交叉配血登记请求：指定用血申请与供血袋号。
 */
public record CrossMatchRequest(Long requestId, String bagNo, String operator) {
}
