package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Instant;

/**
 * Snapshot of growth-stage progress for a patch.
 *
 * <p>Headless DTO: no UI types. Intended for paint-only UI hints (e.g. remainder shading).
 * This is runtime-only and does not imply persistence.</p>
 */
public final class GrowthProgress
{
    private final PatchId patchId;
    private final int stageCurrent;
    private final int stageMax;
    private final float progress01;
    private final Instant observedAt;

    public GrowthProgress(PatchId patchId, int stageCurrent, int stageMax, float progress01, Instant observedAt)
    {
        this.patchId = patchId;
        this.stageCurrent = stageCurrent;
        this.stageMax = stageMax;
        this.progress01 = progress01;
        this.observedAt = observedAt;
    }

    public PatchId getPatchId()
    {
        return patchId;
    }

    public int getStageCurrent()
    {
        return stageCurrent;
    }

    public int getStageMax()
    {
        return stageMax;
    }

    public float getProgress01()
    {
        return progress01;
    }

    public Instant getObservedAt()
    {
        return observedAt;
    }
}
