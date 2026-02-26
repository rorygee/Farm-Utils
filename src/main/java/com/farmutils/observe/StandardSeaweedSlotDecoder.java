package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for seaweed patch transform table (Time Tracking: PatchImplementation.SEAWEED). */
public final class StandardSeaweedSlotDecoder
{
    private StandardSeaweedSlotDecoder() {}

    private static final int MAX_STAGE = 5;

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int raw)
    {
        return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.GIANT_SEAWEED, "Giant seaweed", raw);
    }

    private static DecodedPatchState diseased(int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.GIANT_SEAWEED, "Giant seaweed", raw);
    }

    private static DecodedPatchState dead(int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.GIANT_SEAWEED, "Giant seaweed", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 3)
        {
            return empty(raw);
        }

        // Growing seaweed: 4 values => stages 1..4.
        if (raw >= 4 && raw <= 7)
        {
            return healthy((raw - 4) + 1, raw);
        }

        // Harvestable seaweed variants: map to max stage.
        if (raw >= 8 && raw <= 10)
        {
            return healthy(MAX_STAGE, raw);
        }

        // Diseased.
        if (raw >= 11 && raw <= 13)
        {
            return diseased(raw);
        }

        // Dead.
        if (raw >= 14 && raw <= 16)
        {
            return dead(raw);
        }

        // Placeholder weeds/empty across the rest of the table.
        if (raw >= 0 && raw <= 255)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
