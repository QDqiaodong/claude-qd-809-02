package com.blood.center.dto;

import java.util.List;

/**
 * 标本放行请求：必须指定复核人。
 * 若标本存在复测轮次，必须逐轮给出采用 / 拒绝及理由（decisions）。
 */
public record ReleaseRequest(String reviewer, List<RetestDecision> decisions) {

    public ReleaseRequest(String reviewer) {
        this(reviewer, null);
    }
}
