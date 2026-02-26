package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for celastrus patch transform table (Time Tracking: PatchImplementation.CELASTRUS). */
public final class StandardCelastrusSlotDecoder
{
    private static final int MAX_STAGE = 6;

    private StandardCelastrusSlotDecoder() {}

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Growing (5 values => stages 1..5)
        if (raw >= 8 && raw <= 12)
        {
            int stage = (raw - 8) + 1;
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.BATTLESTAFF, "Celastrus", raw);
        }

        // Check-health.
        if (raw == 13)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.BATTLESTAFF, "Celastrus", raw);
        }

        // Harvestable variants.
        if (raw >= 14 && raw <= 17)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.BATTLESTAFF, "Celastrus", raw);
        }

        // Diseased.
        if (raw >= 18 && raw <= 22)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.BATTLESTAFF, "Celastrus", raw);
        }

        // Dead.
        if (raw >= 23 && raw <= 27)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.BATTLESTAFF, "Celastrus", raw);
        }

        // Stump.
        if (raw == 28)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.BATTLESTAFF, "Celastrus", raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }

    /**
     * For harvestable celastrus states, return a depletion stage in [1..4].
     *
     * <p>4 = full (first harvestable variant), 1 = most depleted/harvested state.
     * Returns 0 when {@code raw} is not one of the healthy harvestable celastrus values.</p>
     */
    public static int getHarvestStageOrZero(int raw)
    {
        if (raw == 14)
        {
            return 4;
        }
        if (raw == 15)
        {
            return 3;
        }
        if (raw == 16)
        {
            return 2;
        }
        if (raw == 17 || raw == 28)
        {
            return 1;
        }
        return 0;
    }

    public static int getMaxHarvestStage()
    {
        return 4;
    }
}
