package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for bush patch transform table (Time Tracking: PatchImplementation.BUSH). */
public final class StandardBushSlotDecoder
{
    private StandardBushSlotDecoder() {}

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
        if (raw >= 0 && raw <= 4)
        {
            return empty(raw);
        }

        // Redberries (stages=6)
        if (raw >= 5 && raw <= 9)
        {
            return healthy((raw - 5) + 1, 6, ItemID.REDBERRIES, "Redberries", raw);
        }
        if (raw >= 10 && raw <= 14)
        {
            return healthy(6, 6, ItemID.REDBERRIES, "Redberries", raw);
        }
        if (raw == 250)
        {
            return healthy(6, 6, ItemID.REDBERRIES, "Redberries", raw);
        }
        if (raw >= 70 && raw <= 74)
        {
            return diseased(6, ItemID.REDBERRIES, "Redberries", raw);
        }
        if (raw >= 134 && raw <= 138)
        {
            return dead(6, ItemID.REDBERRIES, "Redberries", raw);
        }

        // Cadavaberries (stages=7)
        if (raw >= 15 && raw <= 20)
        {
            return healthy((raw - 15) + 1, 7, ItemID.CADAVABERRIES, "Cadava berries", raw);
        }
        if (raw >= 21 && raw <= 25)
        {
            return healthy(7, 7, ItemID.CADAVABERRIES, "Cadava berries", raw);
        }
        if (raw == 251)
        {
            return healthy(7, 7, ItemID.CADAVABERRIES, "Cadava berries", raw);
        }
        if (raw >= 80 && raw <= 85)
        {
            return diseased(7, ItemID.CADAVABERRIES, "Cadava berries", raw);
        }
        if (raw >= 144 && raw <= 149)
        {
            return dead(7, ItemID.CADAVABERRIES, "Cadava berries", raw);
        }

        // Dwellberries (stages=8)
        if (raw >= 26 && raw <= 32)
        {
            return healthy((raw - 26) + 1, 8, ItemID.DWELLBERRIES, "Dwellberries", raw);
        }
        if (raw >= 33 && raw <= 37)
        {
            return healthy(8, 8, ItemID.DWELLBERRIES, "Dwellberries", raw);
        }
        if (raw == 252)
        {
            return healthy(8, 8, ItemID.DWELLBERRIES, "Dwellberries", raw);
        }
        if (raw >= 91 && raw <= 97)
        {
            return diseased(8, ItemID.DWELLBERRIES, "Dwellberries", raw);
        }
        if (raw >= 155 && raw <= 161)
        {
            return dead(8, ItemID.DWELLBERRIES, "Dwellberries", raw);
        }

        // Jangerberries (stages=9)
        if (raw >= 38 && raw <= 45)
        {
            return healthy((raw - 38) + 1, 9, ItemID.JANGERBERRIES, "Jangerberries", raw);
        }
        if (raw >= 46 && raw <= 50)
        {
            return healthy(9, 9, ItemID.JANGERBERRIES, "Jangerberries", raw);
        }
        if (raw == 253)
        {
            return healthy(9, 9, ItemID.JANGERBERRIES, "Jangerberries", raw);
        }
        if (raw >= 103 && raw <= 110)
        {
            return diseased(9, ItemID.JANGERBERRIES, "Jangerberries", raw);
        }
        if (raw >= 167 && raw <= 174)
        {
            return dead(9, ItemID.JANGERBERRIES, "Jangerberries", raw);
        }

        // White berries (stages=9)
        if (raw >= 51 && raw <= 58)
        {
            return healthy((raw - 51) + 1, 9, ItemID.WHITE_BERRIES, "White berries", raw);
        }
        if (raw >= 59 && raw <= 63)
        {
            return healthy(9, 9, ItemID.WHITE_BERRIES, "White berries", raw);
        }
        if (raw == 254)
        {
            return healthy(9, 9, ItemID.WHITE_BERRIES, "White berries", raw);
        }
        if (raw >= 116 && raw <= 123)
        {
            return diseased(9, ItemID.WHITE_BERRIES, "White berries", raw);
        }
        if (raw >= 180 && raw <= 187)
        {
            return dead(9, ItemID.WHITE_BERRIES, "White berries", raw);
        }

        // Poison ivy (stages=9)
        if (raw >= 197 && raw <= 204)
        {
            return healthy((raw - 197) + 1, 9, ItemID.POISONIVY_BERRIES, "Poison ivy berries", raw);
        }
        if (raw >= 205 && raw <= 209)
        {
            return healthy(9, 9, ItemID.POISONIVY_BERRIES, "Poison ivy berries", raw);
        }
        if (raw == 255)
        {
            return healthy(9, 9, ItemID.POISONIVY_BERRIES, "Poison ivy berries", raw);
        }
        if ((raw >= 210 && raw <= 216) || raw == 225)
        {
            return diseased(9, ItemID.POISONIVY_BERRIES, "Poison ivy berries", raw);
        }
        if (raw >= 217 && raw <= 224)
        {
            return dead(9, ItemID.POISONIVY_BERRIES, "Poison ivy berries", raw);
        }

        // Many other raw values map back to "bush patch"/weeds placeholders.
        if (raw >= 64 && raw <= 196)
        {
            return empty(raw);
        }
        if (raw >= 226)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
