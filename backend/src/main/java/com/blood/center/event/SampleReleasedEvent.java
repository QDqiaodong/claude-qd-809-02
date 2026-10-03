package com.blood.center.event;

/**
 * 领域事件：标本检验放行 → 自动生成成分制备单，并把分离出的血袋计入库存。
 */
public record SampleReleasedEvent(Long sampleId, String barcode, Long collectionId,
                                  String bloodType, Integer wholeBloodVolumeMl) {
}
