package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for crystal tree patch transform table (Time Tracking: PatchImplementation.CRYSTAL_TREE). */
public final class StandardCrystalTreeSlotDecoder
{
    private static final int MAX_STAGE = 7;

    private StandardCrystalTreeSlotDecoder() {}

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Growing (6 values => stages 1..6)
        if (raw >= 8 && raw <= 13)
        {
            int stage = (raw - 8) + 1;
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.GAUNTLET_CRYSTAL_SHARD, "Crystal tree", raw);
        }

        // Check-health.
        if (raw == 14)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.GAUNTLET_CRYSTAL_SHARD, "Crystal tree", raw);
        }

        // Chop-down.
        if (raw == 15)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.GAUNTLET_CRYSTAL_SHARD, "Crystal tree", raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
