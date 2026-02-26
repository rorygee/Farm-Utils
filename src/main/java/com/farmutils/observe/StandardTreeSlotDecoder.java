package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for standard tree patch transform table (Time Tracking: PatchImplementation.TREE). */
public final class StandardTreeSlotDecoder
{
    private StandardTreeSlotDecoder() {}

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

        // Oak (maxStage=5)
        if (raw >= 8 && raw <= 11)
        {
            return healthy((raw - 8) + 1, 5, ItemID.OAK_LOGS, "Oak", raw);
        }
        if (raw == 12 || raw == 13 || raw == 14)
        {
            return healthy(5, 5, ItemID.OAK_LOGS, "Oak", raw);
        }
        if ((raw >= 73 && raw <= 75) || raw == 77)
        {
            return diseased(5, ItemID.OAK_LOGS, "Oak", raw);
        }
        if ((raw >= 137 && raw <= 139) || raw == 141)
        {
            return dead(5, ItemID.OAK_LOGS, "Oak", raw);
        }

        // Willow (maxStage=7)
        if (raw >= 15 && raw <= 20)
        {
            return healthy((raw - 15) + 1, 7, ItemID.WILLOW_LOGS, "Willow", raw);
        }
        if (raw == 21 || raw == 22 || raw == 23 || (raw >= 192 && raw <= 197))
        {
            return healthy(7, 7, ItemID.WILLOW_LOGS, "Willow", raw);
        }
        if ((raw >= 80 && raw <= 84) || raw == 86)
        {
            return diseased(7, ItemID.WILLOW_LOGS, "Willow", raw);
        }
        if ((raw >= 144 && raw <= 148) || raw == 150)
        {
            return dead(7, ItemID.WILLOW_LOGS, "Willow", raw);
        }

        // Maple (maxStage=9)
        if (raw >= 24 && raw <= 31)
        {
            return healthy((raw - 24) + 1, 9, ItemID.MAPLE_LOGS, "Maple", raw);
        }
        if (raw == 32 || raw == 33 || raw == 34)
        {
            return healthy(9, 9, ItemID.MAPLE_LOGS, "Maple", raw);
        }
        if ((raw >= 89 && raw <= 95) || raw == 97)
        {
            return diseased(9, ItemID.MAPLE_LOGS, "Maple", raw);
        }
        if ((raw >= 153 && raw <= 159) || raw == 161)
        {
            return dead(9, ItemID.MAPLE_LOGS, "Maple", raw);
        }

        // Yew (maxStage=11)
        if (raw >= 35 && raw <= 44)
        {
            return healthy((raw - 35) + 1, 11, ItemID.YEW_LOGS, "Yew", raw);
        }
        if (raw == 45 || raw == 46 || raw == 47)
        {
            return healthy(11, 11, ItemID.YEW_LOGS, "Yew", raw);
        }
        if ((raw >= 100 && raw <= 108) || raw == 110)
        {
            return diseased(11, ItemID.YEW_LOGS, "Yew", raw);
        }
        if ((raw >= 164 && raw <= 172) || raw == 174)
        {
            return dead(11, ItemID.YEW_LOGS, "Yew", raw);
        }

        // Magic (maxStage=13)
        if (raw >= 48 && raw <= 59)
        {
            return healthy((raw - 48) + 1, 13, ItemID.MAGIC_LOGS, "Magic", raw);
        }
        if (raw == 60 || raw == 61 || raw == 62)
        {
            return healthy(13, 13, ItemID.MAGIC_LOGS, "Magic", raw);
        }
        if ((raw >= 113 && raw <= 123) || raw == 125)
        {
            return diseased(13, ItemID.MAGIC_LOGS, "Magic", raw);
        }
        if ((raw >= 177 && raw <= 187) || raw == 189)
        {
            return dead(13, ItemID.MAGIC_LOGS, "Magic", raw);
        }

        // Many other raw values map back to weeds/placeholder states.
        if (raw >= 63)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
