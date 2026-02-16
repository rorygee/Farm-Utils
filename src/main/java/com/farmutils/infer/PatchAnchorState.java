package com.farmutils.infer;

import java.time.Instant;

/** Internal, minimal per-patch anchor state (runtime-only). */
final class PatchAnchorState
{
    Instant lastObservationAt;

    Instant lastManualSetAt;
    InferredStage manualStage;
    Instant manualLockUntil;

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

        plantedAt = null;
        harvestedAt = null;
        diseasedAt = null;
        deadAt = null;
        clearedAt = null;
    }
}
