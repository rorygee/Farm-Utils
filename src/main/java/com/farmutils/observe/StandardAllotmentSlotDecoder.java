package com.farmutils.observe;

import com.farmutils.model.AllotmentType;

/**
 * Decoder for the standard allotment patch transform table.
 *
 * <p>This is derived from RuneLite Time Tracking's {@code PatchImplementation.ALLOTMENT} mapping.
 * We keep the logic centralized and generic so all allotment locations can reuse it.</p>
 *
 * <p>Farm Utils semantics:
 * <ul>
 *   <li>All weeds / raking states are treated as {@code empty=true}.</li>
 *   <li>Healthy crops report growth stages {@code 1..max}, where {@code stage==max} is harvestable/ready.</li>
 *   <li>Diseased/dead crops report {@link PatchHealth} but do not drive growth-stage inference directly.</li>
 * </ul>
 * </p>
 */
public final class StandardAllotmentSlotDecoder
{
    private StandardAllotmentSlotDecoder() {}

    // Weed/raking + "empty" presentation states. These ranges are intentionally inclusive.
    // (See RuneLite Time Tracking PatchImplementation.ALLOTMENT.)
    private static final int[][] WEED_RANGES = new int[][]
    {
        {0, 5},
        {127, 127},
        {141, 141},
        {145, 148},
        {152, 155},
        {159, 162},
        {168, 171},
        {177, 180},
        {188, 192},
        {205, 205},
        {212, 212},
        {216, 219},
        {223, 226},
        {232, 235},
        {241, 244},
        {252, 255}
    };

    public static DecodedPatchState decode(int raw)
    {
        if (isWeeds(raw))
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
        }

        // Healthy crops
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

        // Unknown / new states.
        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }

    private static boolean isWeeds(int raw)
    {
        for (int[] r : WEED_RANGES)
        {
            if (raw >= r[0] && raw <= r[1])
            {
                return true;
            }
        }
        return false;
    }

    private static DecodedPatchState decodeHealthy(int raw)
    {
        // Potato
        DecodedPatchState potato = decodeHealthyCrop(raw, AllotmentType.POTATO,
            new int[][]{{6, 9}, {70, 73}},
            new int[][]{{10, 12}, {74, 76}});
        if (potato != null)
        {
            return potato;
        }

        // Onion
        DecodedPatchState onion = decodeHealthyCrop(raw, AllotmentType.ONION,
            new int[][]{{13, 16}, {77, 80}},
            new int[][]{{17, 19}, {81, 83}});
        if (onion != null)
        {
            return onion;
        }

        // Cabbage
        DecodedPatchState cabbage = decodeHealthyCrop(raw, AllotmentType.CABBAGE,
            new int[][]{{20, 23}, {84, 87}},
            new int[][]{{24, 26}, {88, 90}});
        if (cabbage != null)
        {
            return cabbage;
        }

        // Tomato
        DecodedPatchState tomato = decodeHealthyCrop(raw, AllotmentType.TOMATO,
            new int[][]{{27, 30}, {91, 94}},
            new int[][]{{31, 33}, {95, 97}});
        if (tomato != null)
        {
            return tomato;
        }

        // Sweetcorn
        DecodedPatchState sweetcorn = decodeHealthyCrop(raw, AllotmentType.SWEETCORN,
            new int[][]{{34, 39}, {98, 103}},
            new int[][]{{40, 42}, {104, 106}});
        if (sweetcorn != null)
        {
            return sweetcorn;
        }

        // Strawberry
        DecodedPatchState strawberry = decodeHealthyCrop(raw, AllotmentType.STRAWBERRY,
            new int[][]{{43, 48}, {107, 112}},
            new int[][]{{49, 51}, {113, 115}});
        if (strawberry != null)
        {
            return strawberry;
        }

        // Watermelon
        DecodedPatchState watermelon = decodeHealthyCrop(raw, AllotmentType.WATERMELON,
            new int[][]{{52, 59}, {116, 123}},
            new int[][]{{60, 62}, {124, 126}});
        if (watermelon != null)
        {
            return watermelon;
        }

        // Snape grass
        DecodedPatchState snape = decodeHealthyCrop(raw, AllotmentType.SNAPE_GRASS,
            new int[][]{{63, 69}, {128, 134}},
            new int[][]{{138, 140}});
        if (snape != null)
        {
            return snape;
        }

        return null;
    }

    private static DecodedPatchState decodeHealthyCrop(
        int raw,
        AllotmentType type,
        int[][] growingRanges,
        int[][] harvestableRanges)
    {
        // Growing ranges are contiguous stage blocks.
        for (int[] r : growingRanges)
        {
            int start = r[0];
            int end = r[1];
            if (raw >= start && raw <= end)
            {
                int stage = (raw - start) + 1;
                // Clamp (some crops have more than 4 growing stages).
                if (stage >= type.getMaxGrowthStage())
                {
                    stage = type.getMaxGrowthStage() - 1;
                }
                if (stage < 1)
                {
                    stage = 1;
                }
                return new DecodedPatchState(false, stage, type.getMaxGrowthStage(), PatchHealth.HEALTHY,
                    type.getItemId(), type.getDisplayName(), raw);
            }
        }

        // Harvestable ranges are visual variants of the fully-grown crop.
        for (int[] r : harvestableRanges)
        {
            int start = r[0];
            int end = r[1];
            if (raw >= start && raw <= end)
            {
                return new DecodedPatchState(false, type.getMaxGrowthStage(), type.getMaxGrowthStage(), PatchHealth.HEALTHY,
                    type.getItemId(), type.getDisplayName(), raw);
            }
        }

        return null;
    }

    private static DecodedPatchState decodeDiseased(int raw)
    {
        // The exact stage within disease/death is not used for growth inference.
        // Preserve a best-effort stage value for debug logs.

        if (raw >= 135 && raw <= 137)
        {
            return diseased(AllotmentType.POTATO, raw, 135);
        }
        if (raw >= 142 && raw <= 144)
        {
            return diseased(AllotmentType.ONION, raw, 142);
        }
        if (raw >= 149 && raw <= 151)
        {
            return diseased(AllotmentType.CABBAGE, raw, 149);
        }
        if (raw >= 156 && raw <= 158)
        {
            return diseased(AllotmentType.TOMATO, raw, 156);
        }
        if (raw >= 163 && raw <= 167)
        {
            return diseased(AllotmentType.SWEETCORN, raw, 163);
        }
        if (raw >= 172 && raw <= 176)
        {
            return diseased(AllotmentType.STRAWBERRY, raw, 172);
        }
        if (raw >= 181 && raw <= 187)
        {
            return diseased(AllotmentType.WATERMELON, raw, 181);
        }
        if (raw >= 196 && raw <= 198)
        {
            return diseased(AllotmentType.SNAPE_GRASS, raw, 196);
        }
        if (raw >= 202 && raw <= 204)
        {
            return diseased(AllotmentType.SNAPE_GRASS, raw, 202);
        }

        return null;
    }

    private static DecodedPatchState diseased(AllotmentType type, int raw, int start)
    {
        int stage = (raw - start) + 2; // typically corresponds to mid-growth stages
        if (stage < 1)
        {
            stage = 1;
        }
        if (stage > type.getMaxGrowthStage())
        {
            stage = type.getMaxGrowthStage();
        }
        return new DecodedPatchState(false, stage, type.getMaxGrowthStage(), PatchHealth.DISEASED,
            type.getItemId(), type.getDisplayName(), raw);
    }

    private static DecodedPatchState decodeDead(int raw)
    {
        if (raw >= 199 && raw <= 201)
        {
            return dead(AllotmentType.POTATO, raw, 199);
        }
        if (raw >= 206 && raw <= 208)
        {
            return dead(AllotmentType.ONION, raw, 206);
        }
        if (raw >= 213 && raw <= 215)
        {
            return dead(AllotmentType.CABBAGE, raw, 213);
        }
        if (raw >= 220 && raw <= 222)
        {
            return dead(AllotmentType.TOMATO, raw, 220);
        }
        if (raw >= 227 && raw <= 231)
        {
            return dead(AllotmentType.SWEETCORN, raw, 227);
        }
        if (raw >= 236 && raw <= 240)
        {
            return dead(AllotmentType.STRAWBERRY, raw, 236);
        }
        if (raw >= 245 && raw <= 251)
        {
            return dead(AllotmentType.WATERMELON, raw, 245);
        }
        if (raw >= 193 && raw <= 195)
        {
            return dead(AllotmentType.SNAPE_GRASS, raw, 193);
        }
        if (raw >= 209 && raw <= 211)
        {
            return dead(AllotmentType.SNAPE_GRASS, raw, 209);
        }

        return null;
    }

    private static DecodedPatchState dead(AllotmentType type, int raw, int start)
    {
        int stage = (raw - start) + 2;
        if (stage < 1)
        {
            stage = 1;
        }
        if (stage > type.getMaxGrowthStage())
        {
            stage = type.getMaxGrowthStage();
        }
        return new DecodedPatchState(false, stage, type.getMaxGrowthStage(), PatchHealth.DEAD,
            type.getItemId(), type.getDisplayName(), raw);
    }
}
