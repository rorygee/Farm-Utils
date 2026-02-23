package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

    /**
 * Returns a duration estimate until completion for a patch.
 *
 * v0: this is intentionally nullable/optional.
 */
public interface PatchDurationModel
{
    Optional<Duration> durationToComplete(PatchId patchId);

    /**
     * Optional maximum "growth stage" for a patch type.
     *
     * <p>This is intentionally separate from timing models. It allows callers (UI/inference)
     * to compute a generic stage fraction (0..1) when a concrete stage observation exists,
     * without hardcoding per-patch-type constants into rendering code.</p>
     */
    default OptionalInt getMaxGrowthStage(PatchId patchId)
    {
        return OptionalInt.empty();
    }

    /**
     * Optional duration of a single growth stage, when a patch type has discrete stages.
     *
     * <p>Used for conservative, stepwise stage progression (e.g. remote patches) without requiring
     * constant re-observation.</p>
     */
    default Optional<Duration> getGrowthStageDuration(PatchId patchId)
    {
        return Optional.empty();
    }

    /**
     * Optional maximum "harvest depletion stage" for a patch when it is harvestable or ready.
     *
     * <p>For herbs this corresponds to the 3 harvestable object variants (full -> fewer picks).
     * Other patch types may return empty until their models are implemented.</p>
     */
    default OptionalInt getMaxHarvestStage(PatchId patchId)
    {
        return OptionalInt.empty();
    }

    /**
     * v2: Optional multi-stage schedule anchored at {@code lastObservedAt}.
     *
     * <p>Implementations may return empty to indicate no schedule is available.
     * Engine logic must remain deterministic and fall back to v1 behavior where applicable.</p>
     */
    default Optional<StageSchedule> getStageSchedule(PatchId patchId)
    {
        return Optional.empty();
    }

    /**
     * v1: total growth duration from the moment a patch is observed as {@link InferredStage#GROWING}
     * until it should be considered {@link InferredStage#READY}.
     *
     * <p>Kept separate from any future multi-stage modeling; this is a single-duration spine.</p>
     */
    default Optional<Duration> getTotalGrowthDuration(PatchId patchId)
    {
        // Backwards-compatible default: v0 callers used durationToComplete as a total duration.
        return durationToComplete(patchId);
    }

    /**
     * v3: bounded readiness window (min/max total growth duration) anchored at {@code plantedAt}.
     *
     * <p>Implementations may return empty to indicate no bounded window information is available.
     * The engine must remain deterministic and conservative.</p>
     */
    default Optional<ReadyWindow> getReadyWindow(PatchId patchId)
    {
        // Backwards-compatible default: treat single-duration models as min=max.
        Optional<Duration> total = getTotalGrowthDuration(patchId);
        if (total.isPresent())
        {
            return Optional.of(new ReadyWindow(total.get(), total.get()));
        }

        // If a v2 stage schedule exists, treat its total duration as min=max.
        Optional<StageSchedule> schedule = getStageSchedule(patchId);
        if (schedule.isPresent())
        {
            Duration d = schedule.get().getTotalDuration();
            return Optional.of(new ReadyWindow(d, d));
        }

        return Optional.empty();
    }

    /**
     * v4: bounded readiness window anchored at the moment a growth stage is observed.
     *
     * <p>The returned durations represent time remaining until {@link InferredStage#READY} from the
     * observation moment, expressed as a conservative min/max window. Implementations may return empty
     * when stage-based timing is unknown for the patch.</p>
     */
    default Optional<ReadyWindow> getReadyWindowFromStage(PatchId patchId, int observedGrowthStage)
    {
        return Optional.empty();
    }


    /**
     * v4b: stage readiness window with information about whether this observation was captured
     * at the moment the stage changed (transition) vs. a baseline read.
     */
    default Optional<ReadyWindow> getReadyWindowFromStage(PatchId patchId, int observedGrowthStage, boolean stageTransition)
    {
        return getReadyWindowFromStage(patchId, observedGrowthStage);
    }

}
