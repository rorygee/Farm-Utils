package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for hardwood tree patch transform table (Time Tracking: PatchImplementation.HARDWOOD_TREE). */
public final class StandardHardwoodSlotDecoder
{
    private StandardHardwoodSlotDecoder() {}

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int maxStage, int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, stage, maxStage, PatchHealth.HEALTHY, itemId, name, raw);
    }

    private static DecodedPatchState diseased(int maxStage, int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, -1, maxStage, PatchHealth.DISEASED, itemId, name, raw);
    }

    private static DecodedPatchState dead(int maxStage, int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, -1, maxStage, PatchHealth.DEAD, itemId, name, raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return empty(raw);
        }

        // Teak (maxStage=8)
        if (raw >= 8 && raw <= 14)
        {
            return healthy((raw - 8) + 1, 8, ItemID.TEAK_LOGS, "Teak", raw);
        }
        if (raw == 15)
        {
            return healthy(8, 8, ItemID.TEAK_LOGS, "Teak", raw);
        }
        if (raw == 16 || raw == 17)
        {
            return healthy(8, 8, ItemID.TEAK_LOGS, "Teak", raw);
        }
        if (raw >= 18 && raw <= 23)
        {
            return diseased(8, ItemID.TEAK_LOGS, "Teak", raw);
        }
        if (raw >= 24 && raw <= 29)
        {
            return dead(8, ItemID.TEAK_LOGS, "Teak", raw);
        }

        // Mahogany (maxStage=9)
        if (raw >= 30 && raw <= 37)
        {
            return healthy((raw - 30) + 1, 9, ItemID.MAHOGANY_LOGS, "Mahogany", raw);
        }
        if (raw == 38)
        {
            return healthy(9, 9, ItemID.MAHOGANY_LOGS, "Mahogany", raw);
        }
        if (raw == 39 || raw == 40)
        {
            return healthy(9, 9, ItemID.MAHOGANY_LOGS, "Mahogany", raw);
        }
        if (raw >= 41 && raw <= 47)
        {
            return diseased(9, ItemID.MAHOGANY_LOGS, "Mahogany", raw);
        }
        if (raw >= 48 && raw <= 54)
        {
            return dead(9, ItemID.MAHOGANY_LOGS, "Mahogany", raw);
        }

        // Camphor (maxStage=9)
        if (raw >= 55 && raw <= 62)
        {
            return healthy((raw - 55) + 1, 9, ItemID.CAMPHOR_LOGS, "Camphor", raw);
        }
        if (raw == 63)
        {
            return healthy(9, 9, ItemID.CAMPHOR_LOGS, "Camphor", raw);
        }
        if (raw == 64 || raw == 65)
        {
            return healthy(9, 9, ItemID.CAMPHOR_LOGS, "Camphor", raw);
        }
        if (raw >= 66 && raw <= 72)
        {
            return diseased(9, ItemID.CAMPHOR_LOGS, "Camphor", raw);
        }
        if (raw >= 73 && raw <= 79)
        {
            return dead(9, ItemID.CAMPHOR_LOGS, "Camphor", raw);
        }

        // Ironwood (maxStage=9)
        if (raw >= 80 && raw <= 87)
        {
            return healthy((raw - 80) + 1, 9, ItemID.IRONWOOD_LOGS, "Ironwood", raw);
        }
        if (raw == 88)
        {
            return healthy(9, 9, ItemID.IRONWOOD_LOGS, "Ironwood", raw);
        }
        if (raw == 89 || raw == 90)
        {
            return healthy(9, 9, ItemID.IRONWOOD_LOGS, "Ironwood", raw);
        }
        if (raw >= 91 && raw <= 97)
        {
            return diseased(9, ItemID.IRONWOOD_LOGS, "Ironwood", raw);
        }
        if (raw >= 98 && raw <= 104)
        {
            return dead(9, ItemID.IRONWOOD_LOGS, "Ironwood", raw);
        }

        // Rosewood (maxStage=10)
        if (raw >= 105 && raw <= 113)
        {
            return healthy((raw - 105) + 1, 10, ItemID.ROSEWOOD_LOGS, "Rosewood", raw);
        }
        if (raw == 114)
        {
            return healthy(10, 10, ItemID.ROSEWOOD_LOGS, "Rosewood", raw);
        }
        if (raw == 115 || raw == 116)
        {
            return healthy(10, 10, ItemID.ROSEWOOD_LOGS, "Rosewood", raw);
        }
        if (raw >= 117 && raw <= 124)
        {
            return diseased(10, ItemID.ROSEWOOD_LOGS, "Rosewood", raw);
        }
        if (raw >= 125 && raw <= 132)
        {
            return dead(10, ItemID.ROSEWOOD_LOGS, "Rosewood", raw);
        }

        // Many other raw values map back to weeds/placeholder.
        if (raw >= 133)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
