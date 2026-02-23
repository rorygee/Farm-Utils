package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for cactus patch transform table (Time Tracking: PatchImplementation.CACTUS). */
public final class StandardCactusSlotDecoder
{
    private static final int MAX_STAGE = 8;

    private StandardCactusSlotDecoder() {}

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Cactus growing.
        if (raw >= 8 && raw <= 14)
        {
            int stage = (raw - 8) + 1; // 1..7
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CACTUS_SPINE, "Cactus", raw);
        }

        // Cactus harvestable (multiple pick/depletion variants).
        if (raw >= 15 && raw <= 18)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CACTUS_SPINE, "Cactus", raw);
        }

        // Cactus check-health.
        if (raw == 31)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CACTUS_SPINE, "Cactus", raw);
        }

        // Diseased cactus.
        if (raw >= 19 && raw <= 24)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.CACTUS_SPINE, "Cactus", raw);
        }

        // Dead cactus.
        if (raw >= 25 && raw <= 30)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.CACTUS_SPINE, "Cactus", raw);
        }

        // Potato cactus growing.
        if (raw >= 32 && raw <= 38)
        {
            int stage = (raw - 32) + 1; // 1..7
            return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CACTUS_POTATO, "Potato cactus", raw);
        }

        // Potato cactus harvestable.
        if (raw >= 39 && raw <= 45)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CACTUS_POTATO, "Potato cactus", raw);
        }

        // Potato cactus check-health.
        if (raw == 58)
        {
            return new DecodedPatchState(false, MAX_STAGE, MAX_STAGE, PatchHealth.HEALTHY, ItemID.CACTUS_POTATO, "Potato cactus", raw);
        }

        // Diseased potato cactus.
        if (raw >= 46 && raw <= 51)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, ItemID.CACTUS_POTATO, "Potato cactus", raw);
        }

        // Dead potato cactus.
        if (raw >= 52 && raw <= 57)
        {
            return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, ItemID.CACTUS_POTATO, "Potato cactus", raw);
        }

        // Unknown / new states.
        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
