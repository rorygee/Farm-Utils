package com.farmutils.infer;

import java.time.Duration;
import java.time.Instant;

/** Internal, minimal per-patch anchor state (runtime-only). */
final class PatchAnchorState
{
    Instant lastObservationAt;

    Instant lastManualSetAt;
    InferredStage manualStage;
    Instant manualLockUntil;

    Integer growthStage;
    Integer maxGrowthStage;

    /** Optional per-cycle growth stage duration override (crop-dependent). */
    Duration growthStageDuration;

    Instant growthStageObservedAt;
    boolean growthStageTransition;

    Integer harvestStage;
    Instant harvestStageObservedAt;

    // Runtime-only crop identity for UI chrome (icons/tooltips).
    Integer cropItemId;
    String cropName;
    Instant cropObservedAt;

    Instant plantedAt;
    Instant harvestedAt;
    Instant diseasedAt;
    Instant deadAt;
    Instant clearedAt;

    void noteObservationAt(Instant at)
    {
        if (at == null)
        {
            return;
        }
        if (lastObservationAt == null || at.isAfter(lastObservationAt))
        {
            lastObservationAt = at;
        }
    }

    void reset()
    {
        lastObservationAt = null;
        lastManualSetAt = null;
        manualStage = null;
        manualLockUntil = null;

        growthStage = null;
        maxGrowthStage = null;
        growthStageDuration = null;
        growthStageObservedAt = null;
        growthStageTransition = false;

        harvestStage = null;
        harvestStageObservedAt = null;

        cropItemId = null;
        cropName = null;
        cropObservedAt = null;

        plantedAt = null;
        harvestedAt = null;
        diseasedAt = null;
        deadAt = null;
        clearedAt = null;
    }
}
