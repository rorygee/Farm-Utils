package com.farmutils.infer;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchState;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** A single observation about a patch at a specific moment. */
public final class Observation
{
    public enum Kind
    {
        PATCH_STATE_SET,
        PATCH_STATE_CLEARED,

        // v3 anchor events (debug-fed / test-fed)
        PLANTED,
        HARVESTED,
        DISEASED_SET,
        DISEASED_CLEARED,
        DEAD_SET,

        // v4: concrete growth stage observed (e.g. stage 1..5 for herbs).
        GROWTH_STAGE_OBSERVED,

        // v5: harvest depletion stage observed for harvestable crops (e.g. herbs have 3 ready variants).
        HARVEST_STAGE_OBSERVED,

        // v6: crop identity observed (for row icon + tooltip context). Runtime-only.
        CROP_OBSERVED
    }

    private final Kind kind;
    private final PatchId patchId;
    private final PatchState patchStateOrNull;
    private final Integer growthStageOrNull;

    /** Optional max growth stage for this crop cycle (e.g. allotments vary by crop). */
    private final Integer maxGrowthStageOrNull;

    /** Optional stage duration for this crop cycle (e.g. cactus vs potato cactus). */
    private final Duration growthStageDurationOrNull;

    private final boolean growthStageTransition;
    private final Integer harvestStageOrNull;
    private final Integer cropItemIdOrNull;
    private final String cropNameOrNull;
    private final Instant observedAt;
    private final ObservationSource source;

    private Observation(
            Kind kind,
            PatchId patchId,
            PatchState patchStateOrNull,
            Integer growthStageOrNull,
            Integer maxGrowthStageOrNull,
            Duration growthStageDurationOrNull,
            boolean growthStageTransition,
            Integer harvestStageOrNull,
            Integer cropItemIdOrNull,
            String cropNameOrNull,
            Instant observedAt,
            ObservationSource source)
    {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.patchId = Objects.requireNonNull(patchId, "patchId");
        this.patchStateOrNull = patchStateOrNull;
        this.growthStageOrNull = growthStageOrNull;
        this.maxGrowthStageOrNull = maxGrowthStageOrNull;
        this.growthStageDurationOrNull = growthStageDurationOrNull;
        this.growthStageTransition = growthStageTransition;
        this.harvestStageOrNull = harvestStageOrNull;
        this.cropItemIdOrNull = cropItemIdOrNull;
        this.cropNameOrNull = cropNameOrNull;
        this.observedAt = Objects.requireNonNull(observedAt, "observedAt");
        this.source = Objects.requireNonNull(source, "source");
    }

    public static Observation patchStateSet(PatchId patchId, PatchState patchState, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.PATCH_STATE_SET, patchId, Objects.requireNonNull(patchState, "patchState"), null, null, null, false, null, null, null, observedAt, source);
    }

    public static Observation patchStateCleared(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.PATCH_STATE_CLEARED, patchId, null, null, null, null, false, null, null, null, observedAt, source);
    }

    public static Observation planted(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.PLANTED, patchId, null, null, null, null, false, null, null, null, observedAt, source);
    }

    public static Observation harvested(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.HARVESTED, patchId, null, null, null, null, false, null, null, null, observedAt, source);
    }

    public static Observation diseasedSet(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.DISEASED_SET, patchId, null, null, null, null, false, null, null, null, observedAt, source);
    }

    public static Observation diseasedCleared(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.DISEASED_CLEARED, patchId, null, null, null, null, false, null, null, null, observedAt, source);
    }

    public static Observation deadSet(PatchId patchId, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.DEAD_SET, patchId, null, null, null, null, false, null, null, null, observedAt, source);
    }

    public static Observation growthStageObserved(PatchId patchId, int growthStage, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.GROWTH_STAGE_OBSERVED, patchId, null, growthStage, null, null, false, null, null, null, observedAt, source);
    }

    /** Growth stage observed with an explicit max growth stage for the current crop cycle. */
    public static Observation growthStageObserved(PatchId patchId, int growthStage, Integer maxGrowthStageOrNull, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.GROWTH_STAGE_OBSERVED, patchId, null, growthStage, maxGrowthStageOrNull, null, false, null, null, null, observedAt, source);
    }

    /** Growth stage observed with explicit max stage + per-cycle stage duration. */
    public static Observation growthStageObserved(PatchId patchId, int growthStage, Integer maxGrowthStageOrNull, Duration growthStageDurationOrNull, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.GROWTH_STAGE_OBSERVED, patchId, null, growthStage, maxGrowthStageOrNull, growthStageDurationOrNull, false, null, null, null, observedAt, source);
    }

    public static Observation growthStageTransition(PatchId patchId, int growthStage, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.GROWTH_STAGE_OBSERVED, patchId, null, growthStage, null, null, true, null, null, null, observedAt, source);
    }

    /** Growth stage transition with an explicit max growth stage for the current crop cycle. */
    public static Observation growthStageTransition(PatchId patchId, int growthStage, Integer maxGrowthStageOrNull, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.GROWTH_STAGE_OBSERVED, patchId, null, growthStage, maxGrowthStageOrNull, null, true, null, null, null, observedAt, source);
    }

    /** Growth stage transition with explicit max stage + per-cycle stage duration. */
    public static Observation growthStageTransition(PatchId patchId, int growthStage, Integer maxGrowthStageOrNull, Duration growthStageDurationOrNull, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.GROWTH_STAGE_OBSERVED, patchId, null, growthStage, maxGrowthStageOrNull, growthStageDurationOrNull, true, null, null, null, observedAt, source);
    }

    public static Observation harvestStageObserved(PatchId patchId, int harvestStage, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.HARVEST_STAGE_OBSERVED, patchId, null, null, null, null, false, harvestStage, null, null, observedAt, source);
    }

    /** Crop identity, for icons/tooltips. This should not affect stage inference directly. */
    public static Observation cropObserved(PatchId patchId, int cropItemId, String cropNameOrNull, Instant observedAt, ObservationSource source)
    {
        return new Observation(Kind.CROP_OBSERVED, patchId, null, null, null, null, false, null, cropItemId, cropNameOrNull, observedAt, source);
    }

    public Kind getKind()
    {
        return kind;
    }

    public PatchId getPatchId()
    {
        return patchId;
    }

    public PatchState getPatchStateOrNull()
    {
        return patchStateOrNull;
    }

    public Integer getGrowthStageOrNull()
    {
        return growthStageOrNull;
    }

    public Integer getMaxGrowthStageOrNull()
    {
        return maxGrowthStageOrNull;
    }

    public Duration getGrowthStageDurationOrNull()
    {
        return growthStageDurationOrNull;
    }

    public boolean isGrowthStageTransition()
    {
        return growthStageTransition;
    }

    public Integer getHarvestStageOrNull()
    {
        return harvestStageOrNull;
    }

    public Integer getCropItemIdOrNull()
    {
        return cropItemIdOrNull;
    }

    public String getCropNameOrNull()
    {
        return cropNameOrNull;
    }

    public Instant getObservedAt()
    {
        return observedAt;
    }

    public ObservationSource getSource()
    {
        return source;
    }
}
