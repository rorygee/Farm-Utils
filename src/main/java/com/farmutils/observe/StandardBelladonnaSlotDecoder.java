package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for the belladonna patch transform table (Time Tracking: PatchImplementation.BELLADONNA). */
public final class StandardBelladonnaSlotDecoder
{
    private static final int MAX_STAGE = 5;

    private StandardBelladonnaSlotDecoder() {}

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 3)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Healthy belladonna growth (4 values).
        if (raw >= 4 && raw <= 7)
        {
            int stage = (raw - 4) + 1; // 1..4
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.NIGHTSHADE, "Belladonna", raw);
        }

        // Harvestable.
        if (raw == 8)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.NIGHTSHADE, "Belladonna", raw);
        }

        // Diseased.
        if (raw >= 9 && raw <= 11)
        {
            int stage = 2 + (raw - 9); // best-effort 2..4
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.DISEASED, ItemID.NIGHTSHADE, "Belladonna", raw);
        }

        // Dead.
        if (raw >= 12 && raw <= 14)
        {
            int stage = 2 + (raw - 12); // best-effort 2..4
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.DEAD, ItemID.NIGHTSHADE, "Belladonna", raw);
        }

        // Fallback: treat as empty patch.
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }
}
