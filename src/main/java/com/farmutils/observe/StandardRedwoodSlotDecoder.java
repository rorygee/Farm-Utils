package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for redwood tree patch transform table (Time Tracking: PatchImplementation.REDWOOD). */
public final class StandardRedwoodSlotDecoder
{
    private static final int MAX_STAGE = 11;

    private StandardRedwoodSlotDecoder() {}

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Growing (10 values => stages 1..10)
        if (raw >= 8 && raw <= 17)
        {
            int stage = (raw - 8) + 1;
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.REDWOOD_LOGS, "Redwood", raw);
        }

        // Harvestable.
        if (raw == 18 || (raw >= 41 && raw <= 55))
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.REDWOOD_LOGS, "Redwood", raw);
        }

        // Diseased.
        if (raw >= 19 && raw <= 27)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.REDWOOD_LOGS, "Redwood", raw);
        }

        // Dead.
        if (raw >= 28 && raw <= 36)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.REDWOOD_LOGS, "Redwood", raw);
        }

        // Check-health.
        if (raw == 37)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.REDWOOD_LOGS, "Redwood", raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
