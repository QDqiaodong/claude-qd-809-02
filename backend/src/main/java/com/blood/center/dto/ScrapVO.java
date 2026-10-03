package com.blood.center.dto;

import java.time.LocalDateTime;

/**
 * 报废记录视图（过期 / 破损 / 检验不合格）。
 */
public record ScrapVO(Long id,
                      String scrapNo,
                      String bagNo,
                      String bloodType,
                      String component,
                      String componentName,
                      Integer bags,
                      Integer volumeMl,
                      String reason,
                      String reasonName,
                      String operator,
                      LocalDateTime scrapTime,
                      String remark) {
}
