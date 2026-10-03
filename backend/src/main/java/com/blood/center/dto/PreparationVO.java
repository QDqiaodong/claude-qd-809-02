package com.blood.center.dto;

import java.time.LocalDateTime;

/**
 * 成分制备批次视图。
 */
public record PreparationVO(Long id,
                            String batchNo,
                            Long collectionId,
                            String collectNo,
                            String bloodType,
                            Integer rbcBags,
                            Integer rbcVolumeMl,
                            Integer plasmaBags,
                            Integer plasmaVolumeMl,
                            Integer plateletBags,
                            Integer plateletVolumeMl,
                            Integer cryoBags,
                            Integer cryoVolumeMl,
                            String operator,
                            LocalDateTime preparedTime,
                            String status,
                            Integer totalBags,
                            Integer totalVolumeMl) {
}
