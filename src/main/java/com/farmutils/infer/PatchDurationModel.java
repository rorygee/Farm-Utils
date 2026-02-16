package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.util.Optional;

/**
 * Returns a duration estimate until completion for a patch.
 *
 * v0: this is intentionally nullable/optional.
 */
public interface PatchDurationModel
{
    Optional<Duration> durationToComplete(PatchId patchId);

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
}
