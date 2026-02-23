package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for coral nursery transform table (Time Tracking: PatchImplementation.CORAL). */
public final class StandardCoralSlotDecoder
{
    private StandardCoralSlotDecoder() {}

    private static final int MAX_STAGE = 5;

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, itemId, name, raw);
    }

    private static DecodedPatchState diseased(int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, itemId, name, raw);
    }

    private static DecodedPatchState dead(int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, itemId, name, raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Empty nursery.
        if (raw >= 0 && raw <= 3)
        {
            return empty(raw);
        }

        // Elkhorn coral
        if (raw >= 4 && raw <= 7)
        {
            return healthy((raw - 4) + 1, ItemID.CORAL_ELKHORN, "Elkhorn coral", raw);
        }
        if (raw == 8)
        {
            return healthy(MAX_STAGE, ItemID.CORAL_ELKHORN, "Elkhorn coral", raw);
        }
        if (raw >= 9 && raw <= 11)
        {
            return diseased(ItemID.CORAL_ELKHORN, "Elkhorn coral", raw);
        }
        if (raw >= 12 && raw <= 14)
        {
            return dead(ItemID.CORAL_ELKHORN, "Elkhorn coral", raw);
        }

        // Pillar coral
        if (raw >= 15 && raw <= 18)
        {
            return healthy((raw - 15) + 1, ItemID.CORAL_PILLAR, "Pillar coral", raw);
        }
        if (raw == 19)
        {
            return healthy(MAX_STAGE, ItemID.CORAL_PILLAR, "Pillar coral", raw);
        }
        if (raw >= 20 && raw <= 22)
        {
            return diseased(ItemID.CORAL_PILLAR, "Pillar coral", raw);
        }
        if (raw >= 23 && raw <= 25)
        {
            return dead(ItemID.CORAL_PILLAR, "Pillar coral", raw);
        }

        // Umbral coral
        if (raw >= 26 && raw <= 29)
        {
            return healthy((raw - 26) + 1, ItemID.CORAL_UMBRAL, "Umbral coral", raw);
        }
        if (raw == 30)
        {
            return healthy(MAX_STAGE, ItemID.CORAL_UMBRAL, "Umbral coral", raw);
        }
        if (raw >= 31 && raw <= 33)
        {
            return diseased(ItemID.CORAL_UMBRAL, "Umbral coral", raw);
        }
        if (raw >= 34 && raw <= 36)
        {
            return dead(ItemID.CORAL_UMBRAL, "Umbral coral", raw);
        }

        // Placeholder nursery states across the rest of the table.
        if (raw >= 0 && raw <= 255)
        {
            return empty(raw);
        }

        // Unknown / out of expected range.
        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
