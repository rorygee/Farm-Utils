package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for standard fruit tree patch transform table (Time Tracking: PatchImplementation.FRUIT_TREE). */
public final class StandardFruitTreeSlotDecoder
{
    private StandardFruitTreeSlotDecoder() {}

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, stage, 7, PatchHealth.HEALTHY, itemId, name, raw);
    }

    private static DecodedPatchState diseased(int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, -1, 7, PatchHealth.DISEASED, itemId, name, raw);
    }

    private static DecodedPatchState dead(int itemId, String name, int raw)
    {
        return new DecodedPatchState(false, -1, 7, PatchHealth.DEAD, itemId, name, raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return empty(raw);
        }

        // Apple
        if (raw >= 8 && raw <= 13)
        {
            return healthy((raw - 8) + 1, ItemID.COOKING_APPLE, "Apple", raw);
        }
        if ((raw >= 14 && raw <= 20) || raw == 33 || raw == 34)
        {
            return healthy(7, ItemID.COOKING_APPLE, "Apple", raw);
        }
        if (raw >= 21 && raw <= 26)
        {
            return diseased(ItemID.COOKING_APPLE, "Apple", raw);
        }
        if (raw >= 27 && raw <= 32)
        {
            return dead(ItemID.COOKING_APPLE, "Apple", raw);
        }

        // Banana
        if (raw >= 35 && raw <= 40)
        {
            return healthy((raw - 35) + 1, ItemID.BANANA, "Banana", raw);
        }
        if ((raw >= 41 && raw <= 47) || raw == 60 || raw == 61)
        {
            return healthy(7, ItemID.BANANA, "Banana", raw);
        }
        if (raw >= 48 && raw <= 53)
        {
            return diseased(ItemID.BANANA, "Banana", raw);
        }
        if (raw >= 54 && raw <= 59)
        {
            return dead(ItemID.BANANA, "Banana", raw);
        }

        // Placeholder weeds.
        if (raw >= 62 && raw <= 71)
        {
            return empty(raw);
        }

        // Orange
        if (raw >= 72 && raw <= 77)
        {
            return healthy((raw - 72) + 1, ItemID.ORANGE, "Orange", raw);
        }
        if ((raw >= 78 && raw <= 84) || raw == 97 || raw == 98)
        {
            return healthy(7, ItemID.ORANGE, "Orange", raw);
        }
        if ((raw >= 85 && raw <= 89) || raw == 90)
        {
            return diseased(ItemID.ORANGE, "Orange", raw);
        }
        if (raw >= 91 && raw <= 96)
        {
            return dead(ItemID.ORANGE, "Orange", raw);
        }

        // Curry
        if (raw >= 99 && raw <= 104)
        {
            return healthy((raw - 99) + 1, ItemID.CURRY_LEAF, "Curry", raw);
        }
        if ((raw >= 105 && raw <= 111) || raw == 124 || raw == 125)
        {
            return healthy(7, ItemID.CURRY_LEAF, "Curry", raw);
        }
        if (raw >= 112 && raw <= 117)
        {
            return diseased(ItemID.CURRY_LEAF, "Curry", raw);
        }
        if (raw >= 118 && raw <= 123)
        {
            return dead(ItemID.CURRY_LEAF, "Curry", raw);
        }

        // Placeholder weeds.
        if (raw >= 126 && raw <= 135)
        {
            return empty(raw);
        }

        // Pineapple
        if (raw >= 136 && raw <= 141)
        {
            return healthy((raw - 136) + 1, ItemID.PINEAPPLE, "Pineapple", raw);
        }
        if ((raw >= 142 && raw <= 148) || raw == 161 || raw == 162)
        {
            return healthy(7, ItemID.PINEAPPLE, "Pineapple", raw);
        }
        if (raw >= 149 && raw <= 154)
        {
            return diseased(ItemID.PINEAPPLE, "Pineapple", raw);
        }
        if (raw >= 155 && raw <= 160)
        {
            return dead(ItemID.PINEAPPLE, "Pineapple", raw);
        }

        // Papaya
        if (raw >= 163 && raw <= 168)
        {
            return healthy((raw - 163) + 1, ItemID.PAPAYA, "Papaya", raw);
        }
        if ((raw >= 169 && raw <= 175) || raw == 188 || raw == 189)
        {
            return healthy(7, ItemID.PAPAYA, "Papaya", raw);
        }
        if (raw >= 176 && raw <= 181)
        {
            return diseased(ItemID.PAPAYA, "Papaya", raw);
        }
        if (raw >= 182 && raw <= 187)
        {
            return dead(ItemID.PAPAYA, "Papaya", raw);
        }

        // Placeholder weeds.
        if (raw >= 190 && raw <= 199)
        {
            return empty(raw);
        }

        // Palm
        if (raw >= 200 && raw <= 205)
        {
            return healthy((raw - 200) + 1, ItemID.COCONUT, "Palm", raw);
        }
        if ((raw >= 206 && raw <= 212) || raw == 225 || raw == 226)
        {
            return healthy(7, ItemID.COCONUT, "Palm", raw);
        }
        if (raw >= 213 && raw <= 218)
        {
            return diseased(ItemID.COCONUT, "Palm", raw);
        }
        if (raw >= 219 && raw <= 224)
        {
            return dead(ItemID.COCONUT, "Palm", raw);
        }

        // Dragonfruit
        if (raw >= 227 && raw <= 232)
        {
            return healthy((raw - 227) + 1, ItemID.DRAGONFRUIT, "Dragonfruit", raw);
        }
        if ((raw >= 233 && raw <= 239) || raw == 252 || raw == 253)
        {
            return healthy(7, ItemID.DRAGONFRUIT, "Dragonfruit", raw);
        }
        if (raw >= 240 && raw <= 245)
        {
            return diseased(ItemID.DRAGONFRUIT, "Dragonfruit", raw);
        }
        if (raw >= 246 && raw <= 251)
        {
            return dead(ItemID.DRAGONFRUIT, "Dragonfruit", raw);
        }

        // Tail placeholders.
        if (raw >= 254)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
