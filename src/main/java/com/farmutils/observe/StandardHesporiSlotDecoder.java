package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for Hespori patch transform table (Time Tracking: PatchImplementation.HESPORI). */
public final class StandardHesporiSlotDecoder
{
    private StandardHesporiSlotDecoder() {}

    private static final int MAX_STAGE = 4;

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int raw)
    {
        return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.HESPORI, "Hespori", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 3)
        {
            return empty(raw);
        }

        // Growing Hespori.
        if (raw >= 4 && raw <= 6)
        {
            return healthy((raw - 4) + 1, raw); // stages 1..3
        }

        // Harvestable Hespori.
        if (raw >= 7 && raw <= 8)
        {
            return healthy(MAX_STAGE, raw);
        }

        // Placeholder weeds state.
        if (raw == 9)
        {
            return empty(raw);
        }

        // Unknown/unused values treated as empty to avoid crashing attribution.
        if (raw >= 0 && raw <= 255)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }

    /**
     * Hespori has 2 harvestable variants (raw 7..8). Returns 1..2 when harvestable; otherwise 0.
     */
    public static int getHarvestStageOrZero(int raw)
    {
        if (raw >= 7 && raw <= 8)
        {
            return (raw - 7) + 1;
        }
        return 0;
    }
}
