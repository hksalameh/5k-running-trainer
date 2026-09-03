package com.rakdatak.core.training

import com.rakdatak.core.training.model.PainLevel
import com.rakdatak.core.training.model.PerceivedDifficulty
import com.rakdatak.core.training.model.TrainingAction
import com.rakdatak.core.training.model.TrainingDecision
import com.rakdatak.core.training.model.WorkoutResult

/**
 * Conservative first-pass decision engine.
 *
 * Heart-rate safety thresholds are intentionally evaluated outside this class and supplied as
 * excessiveHeartRateFraction. This keeps medical/sensor policy separate from progression logic
 * and lets us tune it later using validated guidance and device capabilities.
 */
class AdaptiveTrainingEngine {

    fun decide(
        result: WorkoutResult,
        missedWorkoutsThisWeek: Int,
        recentModerateOrStrongPainCount: Int,
        recentStrongPerformanceCount: Int,
    ): TrainingDecision {
        if (result.pain == PainLevel.STRONG || recentModerateOrStrongPainCount >= 2) {
            return TrainingDecision(
                action = TrainingAction.RECOVERY,
                reasonAr = "الأولوية الآن للتعافي قبل زيادة الحمل.",
            )
        }

        if (result.pain == PainLevel.MODERATE) {
            return TrainingDecision(
                action = TrainingAction.REPEAT,
                reasonAr = "لن نرفع شدة التدريب مع وجود ألم متوسط.",
            )
        }

        if (missedWorkoutsThisWeek > 1) {
            return TrainingDecision(
                action = TrainingAction.STEP_BACK_ONE,
                reasonAr = "فات أكثر من تمرين هذا الأسبوع، لذلك نرجع خطوة واحدة بأمان.",
            )
        }

        val heartRateWasHigh = (result.excessiveHeartRateFraction ?: 0.0) >= 0.15
        val struggled = result.completionRatio < 0.9 ||
            result.difficulty == PerceivedDifficulty.VERY_HARD ||
            result.unplannedWalkBreaks >= 2 ||
            heartRateWasHigh

        if (struggled) {
            return TrainingDecision(
                action = TrainingAction.REPEAT,
                reasonAr = "الأداء يشير أن التدرج الحالي يحتاج تثبيت قبل الانتقال.",
            )
        }

        val excellent = result.completionRatio >= 1.0 &&
            result.difficulty == PerceivedDifficulty.EASY &&
            result.unplannedWalkBreaks == 0 &&
            !heartRateWasHigh &&
            result.pain == PainLevel.NONE

        if (excellent && recentStrongPerformanceCount >= 2) {
            return TrainingDecision(
                action = TrainingAction.ADVANCE_FASTER,
                reasonAr = "الأداء مستقر ومريح في عدة جلسات، ويمكن تسريع التدرج بشكل محافظ.",
            )
        }

        return TrainingDecision(
            action = TrainingAction.ADVANCE,
            reasonAr = "التمرين مكتمل والمؤشرات مناسبة للانتقال للخطوة التالية.",
        )
    }
}
