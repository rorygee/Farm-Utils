package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * v1 test-friendly duration model.
 *
 * <p>Deterministic, map-backed, and intentionally minimal.</p>
 */
public class SimpleStaticDurationModel implements PatchDurationModel
{
    private final Map<PatchId, Duration> durationsByPatch;

    public SimpleStaticDurationModel(Map<PatchId, Duration> durationsByPatch)
    {
        this.durationsByPatch = Collections.unmodifiableMap(new java.util.HashMap<>(
                Objects.requireNonNull(durationsByPatch, "durationsByPatch")));
    }

    @Override
    public Optional<Duration> durationToComplete(PatchId patchId)
    {
        // v0 compatibility: treat as total duration.
        return getTotalGrowthDuration(patchId);
    }

    @Override
    public Optional<Duration> getTotalGrowthDuration(PatchId patchId)
    {
        Objects.requireNonNull(patchId, "patchId");
        return Optional.ofNullable(durationsByPatch.get(patchId));
    }
}
