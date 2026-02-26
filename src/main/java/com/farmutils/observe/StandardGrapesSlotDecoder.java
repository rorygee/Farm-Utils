package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for Hosidius Vinery grape patch varbits (Time Tracking: PatchImplementation.GRAPES). */
public final class StandardGrapesSlotDecoder
{
    private StandardGrapesSlotDecoder() {}

    private static final int MAX_STAGE = 8;

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int raw)
    {
        return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.GRAPES, "Grapes", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Empty / empty+fertilizer.
        if (raw >= 0 && raw <= 1)
        {
            return empty(raw);
        }

        // Growing: 2..9 represent stages 0..7 in Time Tracking => stages 1..8 here.
        if (raw >= 2 && raw <= 9)
        {
            return healthy((raw - 2) + 1, raw);
        }

        // Growing: value 10 is an alternate object for the final growing stage.
        if (raw == 10)
        {
            return healthy(MAX_STAGE, raw);
        }

        // Harvestable variants: treat as fully grown/ready.
        if (raw >= 11 && raw <= 15)
        {
            return healthy(MAX_STAGE, raw);
        }

        // Unknown table space: treat as empty to avoid false positives.
        if (raw >= 0 && raw <= 255)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
