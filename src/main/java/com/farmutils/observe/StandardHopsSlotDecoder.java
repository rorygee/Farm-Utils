package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for hops patch transform table (Time Tracking: PatchImplementation.HOPS). */
public final class StandardHopsSlotDecoder
{
    private StandardHopsSlotDecoder() {}

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
        // Weeds / empty (raking states).
        if (raw >= 0 && raw <= 3)
        {
            return empty(raw);
        }

        DecodedPatchState healthy = decodeHealthy(raw);
        if (healthy != null)
        {
            return healthy;
        }

        DecodedPatchState diseased = decodeDiseased(raw);
        if (diseased != null)
        {
            return diseased;
        }

        DecodedPatchState dead = decodeDead(raw);
        if (dead != null)
        {
            return dead;
        }

        // Large swathes of the table are weeds placeholders.
        if (raw >= 0 && raw <= 255)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState decodeHealthy(int raw)
    {
        // Hammerstone hops (stages=5)
        if ((raw >= 4 && raw <= 7) || (raw >= 132 && raw <= 135))
        {
            int base = raw <= 7 ? 4 : 132;
            return healthy((raw - base) + 1, 5, ItemID.HAMMERSTONE_HOPS, "Hammerstone hops", raw);
        }
        if (raw >= 8 && raw <= 10)
        {
            return healthy(5, 5, ItemID.HAMMERSTONE_HOPS, "Hammerstone hops", raw);
        }

        // Asgarnian hops (stages=6)
        if ((raw >= 14 && raw <= 18) || (raw >= 142 && raw <= 146))
        {
            int base = raw <= 18 ? 14 : 142;
            return healthy((raw - base) + 1, 6, ItemID.ASGARNIAN_HOPS, "Asgarnian hops", raw);
        }
        if (raw >= 19 && raw <= 21)
        {
            return healthy(6, 6, ItemID.ASGARNIAN_HOPS, "Asgarnian hops", raw);
        }

        // Yanillian hops (stages=7)
        if ((raw >= 26 && raw <= 31) || (raw >= 154 && raw <= 159))
        {
            int base = raw <= 31 ? 26 : 154;
            return healthy((raw - base) + 1, 7, ItemID.YANILLIAN_HOPS, "Yanillian hops", raw);
        }
        if (raw >= 32 && raw <= 34)
        {
            return healthy(7, 7, ItemID.YANILLIAN_HOPS, "Yanillian hops", raw);
        }

        // Krandorian hops (stages=8)
        if ((raw >= 40 && raw <= 46) || (raw >= 168 && raw <= 174))
        {
            int base = raw <= 46 ? 40 : 168;
            return healthy((raw - base) + 1, 8, ItemID.KRANDORIAN_HOPS, "Krandorian hops", raw);
        }
        if (raw >= 47 && raw <= 49)
        {
            return healthy(8, 8, ItemID.KRANDORIAN_HOPS, "Krandorian hops", raw);
        }

        // Wildblood hops (stages=9)
        if ((raw >= 56 && raw <= 63) || (raw >= 184 && raw <= 191))
        {
            int base = raw <= 63 ? 56 : 184;
            return healthy((raw - base) + 1, 9, ItemID.WILDBLOOD_HOPS, "Wildblood hops", raw);
        }
        if (raw >= 64 && raw <= 66)
        {
            return healthy(9, 9, ItemID.WILDBLOOD_HOPS, "Wildblood hops", raw);
        }

        // Barley (stages=5)
        if ((raw >= 74 && raw <= 77) || (raw >= 202 && raw <= 205))
        {
            int base = raw <= 77 ? 74 : 202;
            return healthy((raw - base) + 1, 5, ItemID.BARLEY, "Barley", raw);
        }
        if (raw >= 78 && raw <= 80)
        {
            return healthy(5, 5, ItemID.BARLEY, "Barley", raw);
        }

        // Jute (stages=6)
        if ((raw >= 84 && raw <= 88) || (raw >= 212 && raw <= 216))
        {
            int base = raw <= 88 ? 84 : 212;
            return healthy((raw - base) + 1, 6, ItemID.JUTE_FIBRE, "Jute", raw);
        }
        if (raw >= 89 && raw <= 91)
        {
            return healthy(6, 6, ItemID.JUTE_FIBRE, "Jute", raw);
        }

        // Flax (stages=4)
        if ((raw >= 96 && raw <= 98) || (raw >= 224 && raw <= 226))
        {
            int base = raw <= 98 ? 96 : 224;
            return healthy((raw - base) + 1, 4, ItemID.FLAX, "Flax", raw);
        }
        if (raw >= 99 && raw <= 101)
        {
            return healthy(4, 4, ItemID.FLAX, "Flax", raw);
        }

        // Hemp (stages=5)
        if ((raw >= 104 && raw <= 107) || (raw >= 232 && raw <= 235))
        {
            int base = raw <= 107 ? 104 : 232;
            return healthy((raw - base) + 1, 5, ItemID.HEMP, "Hemp", raw);
        }
        if (raw >= 108 && raw <= 110)
        {
            return healthy(5, 5, ItemID.HEMP, "Hemp", raw);
        }

        // Cotton (stages=6)
        if ((raw >= 114 && raw <= 118) || (raw >= 242 && raw <= 246))
        {
            int base = raw <= 118 ? 114 : 242;
            return healthy((raw - base) + 1, 6, ItemID.COTTON_BOLL, "Cotton", raw);
        }
        if (raw >= 119 && raw <= 121)
        {
            return healthy(6, 6, ItemID.COTTON_BOLL, "Cotton", raw);
        }

        return null;
    }

    private static DecodedPatchState decodeDiseased(int raw)
    {
        if (raw >= 11 && raw <= 13)
        {
            return diseased(5, ItemID.HAMMERSTONE_HOPS, "Hammerstone hops", raw);
        }
        if (raw >= 22 && raw <= 25)
        {
            return diseased(6, ItemID.ASGARNIAN_HOPS, "Asgarnian hops", raw);
        }
        if (raw >= 35 && raw <= 39)
        {
            return diseased(7, ItemID.YANILLIAN_HOPS, "Yanillian hops", raw);
        }
        if (raw >= 50 && raw <= 55)
        {
            return diseased(8, ItemID.KRANDORIAN_HOPS, "Krandorian hops", raw);
        }
        if (raw >= 67 && raw <= 73)
        {
            return diseased(9, ItemID.WILDBLOOD_HOPS, "Wildblood hops", raw);
        }
        if (raw >= 81 && raw <= 83)
        {
            return diseased(5, ItemID.BARLEY, "Barley", raw);
        }
        if (raw >= 92 && raw <= 95)
        {
            return diseased(6, ItemID.JUTE_FIBRE, "Jute", raw);
        }
        if (raw >= 102 && raw <= 103)
        {
            return diseased(4, ItemID.FLAX, "Flax", raw);
        }
        if (raw >= 111 && raw <= 113)
        {
            return diseased(5, ItemID.HEMP, "Hemp", raw);
        }
        if (raw >= 122 && raw <= 125)
        {
            return diseased(6, ItemID.COTTON_BOLL, "Cotton", raw);
        }
        return null;
    }

    private static DecodedPatchState decodeDead(int raw)
    {
        if (raw >= 139 && raw <= 141)
        {
            return dead(5, ItemID.HAMMERSTONE_HOPS, "Hammerstone hops", raw);
        }
        if (raw >= 150 && raw <= 153)
        {
            return dead(6, ItemID.ASGARNIAN_HOPS, "Asgarnian hops", raw);
        }
        if (raw >= 163 && raw <= 167)
        {
            return dead(7, ItemID.YANILLIAN_HOPS, "Yanillian hops", raw);
        }
        if (raw >= 178 && raw <= 183)
        {
            return dead(8, ItemID.KRANDORIAN_HOPS, "Krandorian hops", raw);
        }
        if (raw >= 195 && raw <= 201)
        {
            return dead(9, ItemID.WILDBLOOD_HOPS, "Wildblood hops", raw);
        }
        if (raw >= 209 && raw <= 211)
        {
            return dead(5, ItemID.BARLEY, "Barley", raw);
        }
        if (raw >= 220 && raw <= 223)
        {
            return dead(6, ItemID.JUTE_FIBRE, "Jute", raw);
        }
        if (raw >= 230 && raw <= 231)
        {
            return dead(4, ItemID.FLAX, "Flax", raw);
        }
        if (raw >= 239 && raw <= 241)
        {
            return dead(5, ItemID.HEMP, "Hemp", raw);
        }
        if (raw >= 250 && raw <= 253)
        {
            return dead(6, ItemID.COTTON_BOLL, "Cotton", raw);
        }
        return null;
    }
}
