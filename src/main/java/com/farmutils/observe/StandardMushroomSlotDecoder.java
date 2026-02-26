package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for mushroom patch transform table (Time Tracking: PatchImplementation.MUSHROOM). */
public final class StandardMushroomSlotDecoder
{
    private StandardMushroomSlotDecoder() {}

    private static final int MAX_STAGE = 7;

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int raw)
    {
        return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.BITTERCAP_MUSHROOM, "Bittercap mushroom", raw);
    }

    private static DecodedPatchState diseased(int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.BITTERCAP_MUSHROOM, "Bittercap mushroom", raw);
    }

    private static DecodedPatchState dead(int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.BITTERCAP_MUSHROOM, "Bittercap mushroom", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 3)
        {
            return empty(raw);
        }

        // Growing bittercap: 6 values.
        if (raw >= 4 && raw <= 9)
        {
            return healthy((raw - 4) + 1, raw); // stages 1..6
        }

        // Harvestable bittercap: treat as max stage.
        if (raw >= 10 && raw <= 15)
        {
            return healthy(MAX_STAGE, raw);
        }

        // Diseased.
        if (raw >= 16 && raw <= 20)
        {
            return diseased(raw);
        }

        // Dead.
        if (raw >= 21 && raw <= 25)
        {
            return dead(raw);
        }

        // Placeholder weeds/empty states across the rest of the table.
        if (raw >= 0 && raw <= 255)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
