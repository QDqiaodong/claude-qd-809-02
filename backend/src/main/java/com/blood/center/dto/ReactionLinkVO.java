package com.blood.center.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 同源血袋关联视图：隔离袋与仅追溯袋（已发出 / 已报废）同清单展示。
 */
public record ReactionLinkVO(Long id,
                             Long bagId,
                             String bagNo,
                             String bloodType,
                             String component,
                             String componentName,
                             Integer volumeMl,
                             LocalDate expiryDate,
                             Integer daysToExpiry,
                             String bagStatus,
                             String bagStatusName,
                             String linkType,
                             String linkTypeName,
                             String linkStatus,
                             String linkStatusName,
                             String note,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt) {
}
