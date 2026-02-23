package com.farmutils.observe;

/**
 * Lightweight decoded state snapshot from a varbit.
 *
 * <p>Java 11 compatible (no records).</p>
 */
public final class DecodedPatchState
{
    private final boolean empty;
    private final int stage;
    private final PatchHealth health;

    /** Optional crop item id for row icons; null when unknown or not applicable. */
    private final Integer cropItemIdOrNull;

    /** Optional crop display name; null when unknown or not applicable. */
    private final String cropNameOrNull;

    /** Optional herb type for standard herb patches; null when unknown or not applicable. */
    private final com.farmutils.model.HerbType herbTypeOrNull;

    /** Optional max growth stage for this crop/patch, when known (0 when unknown/not applicable). */
    private final int maxGrowthStageOrZero;

    private final int raw;

    private DecodedPatchState(
        boolean empty,
        int stage,
        PatchHealth health,
        Integer cropItemIdOrNull,
        String cropNameOrNull,
        com.farmutils.model.HerbType herbTypeOrNull,
        int maxGrowthStageOrZero,
        int raw)
    {
        this.empty = empty;
        this.stage = stage;
        this.health = health;
        this.cropItemIdOrNull = cropItemIdOrNull;
        this.cropNameOrNull = cropNameOrNull;
        this.herbTypeOrNull = herbTypeOrNull;
        this.maxGrowthStageOrZero = maxGrowthStageOrZero;
        this.raw = raw;
    }

    /** Herb-friendly ctor. */
    public DecodedPatchState(boolean empty, int stage, PatchHealth health, com.farmutils.model.HerbType herbTypeOrNull, int raw)
    {
        this(empty, stage, health,
            null,
            null,
            herbTypeOrNull,
            0,
            raw);
    }

    /** Generic crop ctor (non-herb). */
    public DecodedPatchState(boolean empty, int stage, PatchHealth health, Integer cropItemIdOrNull, String cropNameOrNull, int raw)
    {
        this(empty, stage, health,
            cropItemIdOrNull,
            cropNameOrNull,
            null,
            0,
            raw);
    }

    /** Generic crop ctor with an explicit max growth stage (e.g. allotments). */
    public DecodedPatchState(boolean empty, int stage, int maxGrowthStageOrZero, PatchHealth health, Integer cropItemIdOrNull, String cropNameOrNull, int raw)
    {
        this(empty, stage, health,
            cropItemIdOrNull,
            cropNameOrNull,
            null,
            maxGrowthStageOrZero,
            raw);
    }

    /** Backwards-friendly convenience ctor (no crop identity). */
    public DecodedPatchState(boolean empty, int stage, PatchHealth health, int raw)
    {
        this(empty, stage, health,
            null,
            null,
            null,
            0,
            raw);
    }

    public boolean isEmpty()
    {
        return empty;
    }

    public int getStage()
    {
        return stage;
    }

    public int getMaxGrowthStageOrZero()
    {
        return maxGrowthStageOrZero;
    }

    public PatchHealth getHealth()
    {
        return health;
    }

    public com.farmutils.model.HerbType getHerbTypeOrNull()
    {
        return herbTypeOrNull;
    }

    public Integer getCropItemIdOrNull()
    {
        if (cropItemIdOrNull != null)
        {
            return cropItemIdOrNull;
        }
        if (herbTypeOrNull != null)
        {
            return herbTypeOrNull.getCleanItemId();
        }
        return null;
    }

    public String getCropNameOrNull()
    {
        if (cropNameOrNull != null)
        {
            return cropNameOrNull;
        }
        if (herbTypeOrNull != null)
        {
            return herbTypeOrNull.getDisplayName();
        }
        return null;
    }

    public int getRaw()
    {
        return raw;
    }
}
