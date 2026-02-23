package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for calquat patch transform table (Time Tracking: PatchImplementation.CALQUAT). */
public final class StandardCalquatSlotDecoder
{
    private static final int MAX_STAGE = 9;

    private StandardCalquatSlotDecoder() {}

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 3)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Calquat growing (8 values).
        if (raw >= 4 && raw <= 11)
        {
            int stage = (raw - 4) + 1; // 1..8
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CALQUAT_FRUIT, "Calquat", raw);
        }

        // Harvestable.
        if (raw >= 12 && raw <= 18)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CALQUAT_FRUIT, "Calquat", raw);
        }

        // Diseased.
        if (raw >= 19 && raw <= 25)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.CALQUAT_FRUIT, "Calquat", raw);
        }

        // Dead.
        if (raw >= 26 && raw <= 33)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.CALQUAT_FRUIT, "Calquat", raw);
        }

        // Check-health.
        if (raw == 34)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CALQUAT_FRUIT, "Calquat", raw);
        }

        // Fallback.
        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
