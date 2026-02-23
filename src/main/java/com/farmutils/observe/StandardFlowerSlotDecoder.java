package com.farmutils.observe;

import com.farmutils.model.FlowerType;

/**
 * Decoder for the standard flower patch transform table.
 *
 * <p>This is reference-driven from RuneLite Time Tracking's {@code PatchImplementation.FLOWER}
 * mapping, but kept intentionally small and structured.</p>
 */
public final class StandardFlowerSlotDecoder
{
    private StandardFlowerSlotDecoder() {}

    /**
     * Flower decoder stages:
     * <ul>
     *   <li>1..4 = growing</li>
     *   <li>5 = harvestable (or occupying the patch, e.g. scarecrow)</li>
     *   <li>0 = empty/weeds</li>
     * </ul>
     */
    public static DecodedPatchState decode(int raw)
    {
        // Weeds / empty.
        // (In the underlying table this includes multiple weed states and placeholders; for Farm Utils
        // we treat all of them as unplanted.)
        if (raw >= 0 && raw <= 7)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Healthy growing / harvestable
        DecodedPatchState healthy = decodeHealthy(raw);
        if (healthy != null)
        {
            return healthy;
        }

        // Diseased
        DecodedPatchState diseased = decodeDiseased(raw);
        if (diseased != null)
        {
            return diseased;
        }

        // Dead
        DecodedPatchState dead = decodeDead(raw);
        if (dead != null)
        {
            return dead;
        }

        // Large swathes of the table are weeds placeholders. If it's not a known crop state,
        // treat it as empty unless it looks like a real crop state.
        if (raw >= 8 && raw <= 255)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Unknown / out of expected range.
        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState decodeHealthy(int raw)
    {
        // Marigold
        DecodedPatchState m = decodeHealthyTwoBlocks(raw, 8, 11, 12, 72, 75, FlowerType.MARIGOLD);
        if (m != null) return m;

        // Rosemary
        DecodedPatchState r = decodeHealthyTwoBlocks(raw, 13, 16, 17, 77, 80, FlowerType.ROSEMARY);
        if (r != null) return r;

        // Nasturtium
        DecodedPatchState n = decodeHealthyTwoBlocks(raw, 18, 21, 22, 82, 85, FlowerType.NASTURTIUM);
        if (n != null) return n;

        // Woad
        DecodedPatchState w = decodeHealthyTwoBlocks(raw, 23, 26, 27, 87, 90, FlowerType.WOAD);
        if (w != null) return w;

        // Limpwurt
        DecodedPatchState l = decodeHealthyTwoBlocks(raw, 28, 31, 32, 92, 95, FlowerType.LIMPWURT);
        if (l != null) return l;

        // Scarecrow (occupies the flower patch; treat as always "ready")
        if (raw >= 33 && raw <= 36)
        {
            return new DecodedPatchState(false, 5, PatchHealth.HEALTHY, FlowerType.SCARECROW.getItemId(), FlowerType.SCARECROW.getDisplayName(), raw);
        }

        // White lily
        DecodedPatchState wl = decodeHealthyTwoBlocks(raw, 37, 40, 41, 101, 104, FlowerType.WHITE_LILY);
        if (wl != null) return wl;

        return null;
    }

    private static DecodedPatchState decodeDiseased(int raw)
    {
        // RuneLite mapping provides 3 diseased variants for each flower.
        if (raw >= 137 && raw <= 139)
        {
            int stage = 2 + (raw - 137);
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, FlowerType.MARIGOLD.getItemId(), FlowerType.MARIGOLD.getDisplayName(), raw);
        }
        if (raw >= 142 && raw <= 144)
        {
            int stage = 2 + (raw - 142);
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, FlowerType.ROSEMARY.getItemId(), FlowerType.ROSEMARY.getDisplayName(), raw);
        }
        if (raw >= 147 && raw <= 149)
        {
            int stage = 2 + (raw - 147);
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, FlowerType.NASTURTIUM.getItemId(), FlowerType.NASTURTIUM.getDisplayName(), raw);
        }
        if (raw >= 152 && raw <= 154)
        {
            int stage = 2 + (raw - 152);
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, FlowerType.WOAD.getItemId(), FlowerType.WOAD.getDisplayName(), raw);
        }
        if (raw >= 157 && raw <= 159)
        {
            int stage = 2 + (raw - 157);
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, FlowerType.LIMPWURT.getItemId(), FlowerType.LIMPWURT.getDisplayName(), raw);
        }
        if (raw >= 166 && raw <= 168)
        {
            int stage = 2 + (raw - 166);
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, FlowerType.WHITE_LILY.getItemId(), FlowerType.WHITE_LILY.getDisplayName(), raw);
        }
        return null;
    }

    private static DecodedPatchState decodeDead(int raw)
    {
        // RuneLite mapping provides 4 dead variants for each flower.
        if (raw >= 201 && raw <= 204)
        {
            int stage = raw - 200;
            return new DecodedPatchState(false, stage, PatchHealth.DEAD, FlowerType.MARIGOLD.getItemId(), FlowerType.MARIGOLD.getDisplayName(), raw);
        }
        if (raw >= 206 && raw <= 209)
        {
            int stage = raw - 205;
            return new DecodedPatchState(false, stage, PatchHealth.DEAD, FlowerType.ROSEMARY.getItemId(), FlowerType.ROSEMARY.getDisplayName(), raw);
        }
        if (raw >= 211 && raw <= 214)
        {
            int stage = raw - 210;
            return new DecodedPatchState(false, stage, PatchHealth.DEAD, FlowerType.NASTURTIUM.getItemId(), FlowerType.NASTURTIUM.getDisplayName(), raw);
        }
        if (raw >= 216 && raw <= 219)
        {
            int stage = raw - 215;
            return new DecodedPatchState(false, stage, PatchHealth.DEAD, FlowerType.WOAD.getItemId(), FlowerType.WOAD.getDisplayName(), raw);
        }
        if (raw >= 221 && raw <= 224)
        {
            int stage = raw - 220;
            return new DecodedPatchState(false, stage, PatchHealth.DEAD, FlowerType.LIMPWURT.getItemId(), FlowerType.LIMPWURT.getDisplayName(), raw);
        }
        if (raw >= 230 && raw <= 233)
        {
            int stage = raw - 229;
            return new DecodedPatchState(false, stage, PatchHealth.DEAD, FlowerType.WHITE_LILY.getItemId(), FlowerType.WHITE_LILY.getDisplayName(), raw);
        }
        return null;
    }

    private static DecodedPatchState decodeHealthyTwoBlocks(int raw, int aMin, int aMax, int aHarvest, int bMin, int bMax, FlowerType type)
    {
        // Growing block A
        if (raw >= aMin && raw <= aMax)
        {
            int stage = (raw - aMin) + 1;
            return new DecodedPatchState(false, stage, PatchHealth.HEALTHY, type.getItemId(), type.getDisplayName(), raw);
        }

        // Harvestable
        if (raw == aHarvest)
        {
            return new DecodedPatchState(false, 5, PatchHealth.HEALTHY, type.getItemId(), type.getDisplayName(), raw);
        }

        // Growing block B
        if (raw >= bMin && raw <= bMax)
        {
            int stage = (raw - bMin) + 1;
            return new DecodedPatchState(false, stage, PatchHealth.HEALTHY, type.getItemId(), type.getDisplayName(), raw);
        }

        return null;
    }
}
