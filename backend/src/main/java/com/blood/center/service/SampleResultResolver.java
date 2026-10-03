package com.blood.center.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.blood.center.common.BloodDict;
import com.blood.center.domain.TestRetestRound;
import com.blood.center.domain.TestSample;

/**
 * 标本「当前有效结果」的唯一口径：初检（test_sample 第 0 轮）永不覆盖，
 * 各项目取最后一次有效复测轮的值；放行前未复核的最新轮同样作为当前判定依据
 * （检测阶段需要看到复测后的真实结论，复核人在放行时再决定采用或拒绝）。
 *
 * 轮次有效性：ADOPTED 永远有效；PENDING 在放行前的检测阶段视为有效；REJECTED 无效。
 * 放行后不存在 PENDING（放行时必须逐轮处置），故放行后有效轮=初检或最近一次 ADOPTED。
 */
@Component
public class SampleResultResolver {

    /** 六个项目当前有效结果的快照 */
    public record Snapshot(BigDecimal altValue, BigDecimal hbValue,
                           String hbvResult, String hcvResult, String hivResult, String syphilisResult) {

        public boolean allTested() {
            return altValue != null && hbValue != null && hbvResult != null
                    && hcvResult != null && hivResult != null && syphilisResult != null;
        }
    }

    /** 当前有效结果（含放行前未复核的最新轮） */
    public Snapshot effective(TestSample sample, List<TestRetestRound> rounds) {
        BigDecimal alt = sample.getAltValue();
        BigDecimal hb = sample.getHbValue();
        String hbv = sample.getHbvResult();
        String hcv = sample.getHcvResult();
        String hiv = sample.getHivResult();
        String syphilis = sample.getSyphilisResult();
        for (TestRetestRound round : rounds) {
            if (!isEffectiveBeforeRelease(round)) {
                continue;
            }
            switch (round.getItemCode()) {
                case "ALT" -> alt = round.getNewValue();
                case "HB" -> hb = round.getNewValue();
                case "HBV" -> hbv = round.getNewResult();
                case "HCV" -> hcv = round.getNewResult();
                case "HIV" -> hiv = round.getNewResult();
                case "SYPHILIS" -> syphilis = round.getNewResult();
                default -> {
                }
            }
        }
        return new Snapshot(alt, hb, hbv, hcv, hiv, syphilis);
    }

    /**
     * 放行时刻的有效结果：只认真正被采用的轮次（PENDING 若漏处置会被放行流程拦回，
     * 一旦放行时全部处置完毕，本口径与 {@link #effective} 一致）。
     */
    public Snapshot effectiveAtRelease(TestSample sample, List<TestRetestRound> rounds) {
        BigDecimal alt = sample.getAltValue();
        BigDecimal hb = sample.getHbValue();
        String hbv = sample.getHbvResult();
        String hcv = sample.getHcvResult();
        String hiv = sample.getHivResult();
        String syphilis = sample.getSyphilisResult();
        for (TestRetestRound round : rounds) {
            if ("ADOPTED".equals(round.getDecision())) {
                switch (round.getItemCode()) {
                    case "ALT" -> alt = round.getNewValue();
                    case "HB" -> hb = round.getNewValue();
                    case "HBV" -> hbv = round.getNewResult();
                    case "HCV" -> hcv = round.getNewResult();
                    case "HIV" -> hiv = round.getNewResult();
                    case "SYPHILIS" -> syphilis = round.getNewResult();
                    default -> {
                    }
                }
            }
        }
        return new Snapshot(alt, hb, hbv, hcv, hiv, syphilis);
    }

    /** 单项目单轮结论：合格 / 偏高 / 偏低 / 阳性 / 阴性 / 未检 */
    public String conclusion(String itemCode, BigDecimal numericValue, String discreteResult, String donorGender) {
        if (BloodDict.isNumericTestItem(itemCode)) {
            if (numericValue == null) {
                return "未检";
            }
            if ("ALT".equals(itemCode)) {
                return numericValue.compareTo(BloodDict.ALT_LIMIT) > 0 ? "偏高" : "合格";
            }
            BigDecimal limit = "女".equals(donorGender) ? BloodDict.HB_LIMIT_FEMALE : BloodDict.HB_LIMIT_MALE;
            return numericValue.compareTo(limit) < 0 ? "偏低" : "合格";
        }
        return discreteResult == null ? "未检" : BloodDict.testResultName(discreteResult);
    }

    /** 六项是否全部合格（任一未检或不合格返回原因，全部合格返回 null） */
    public String unqualifiedReason(Snapshot snapshot, String donorGender) {
        if (snapshot.altValue() != null && snapshot.altValue().compareTo(BloodDict.ALT_LIMIT) > 0) {
            return "ALT " + snapshot.altValue() + " U/L 超过 " + BloodDict.ALT_LIMIT + " U/L";
        }
        if (snapshot.hbValue() != null) {
            BigDecimal limit = "女".equals(donorGender) ? BloodDict.HB_LIMIT_FEMALE : BloodDict.HB_LIMIT_MALE;
            if (snapshot.hbValue().compareTo(limit) < 0) {
                return "血红蛋白 " + snapshot.hbValue() + " g/L 低于 " + limit + " g/L";
            }
        }
        if ("POSITIVE".equals(snapshot.hbvResult())) {
            return "HBV 阳性";
        }
        if ("POSITIVE".equals(snapshot.hcvResult())) {
            return "HCV 阳性";
        }
        if ("POSITIVE".equals(snapshot.hivResult())) {
            return "HIV 阳性";
        }
        if ("POSITIVE".equals(snapshot.syphilisResult())) {
            return "梅毒阳性";
        }
        return null;
    }

    /** 放行前该轮是否计入当前判定：采用轮 + 待复核轮都计入，拒绝轮剔除 */
    private boolean isEffectiveBeforeRelease(TestRetestRound round) {
        return "ADOPTED".equals(round.getDecision()) || "PENDING".equals(round.getDecision());
    }
}
