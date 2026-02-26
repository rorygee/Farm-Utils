package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for spirit tree patch transform table (Time Tracking: PatchImplementation.SPIRIT_TREE). */
public final class StandardSpiritTreeSlotDecoder
{
    private static final int MAX_STAGE = 13;

    private StandardSpiritTreeSlotDecoder() {}

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Growing (12 values => stages 1..12)
        if (raw >= 8 && raw <= 19)
        {
            int stage = (raw - 8) + 1;
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.SPIRIT_TREE_DUMMY, "Spirit tree", raw);
        }

        // Fully grown (travel/talk-to)
        if (raw == 20)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.SPIRIT_TREE_DUMMY, "Spirit tree", raw);
        }

        // Diseased.
        if (raw >= 21 && raw <= 31)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.SPIRIT_TREE_DUMMY, "Spirit tree", raw);
        }

        // Dead.
        if (raw >= 32 && raw <= 43)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.SPIRIT_TREE_DUMMY, "Spirit tree", raw);
        }

        // Check-health.
        if (raw == 44)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.SPIRIT_TREE_DUMMY, "Spirit tree", raw);
        }

        // Other values map back to weeds/placeholder.
        if (raw >= 45)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
