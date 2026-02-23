package com.farmutils.observe;

import com.farmutils.model.HerbType;

/**
 * Decoder for the standard herb patch transform table used by classic herb patches.
 *
 * <p>This is the table you extracted from the herb patch object transforms: each herb occupies
 * a 7-value block in the slot:</p>
 *
 * <ul>
 *   <li>Stage 1..4: 4 consecutive values</li>
 *   <li>Stage 5: 3 consecutive values (fully-grown + harvesting transients)</li>
 * </ul>
 *
 * <p>Additionally:</p>
 * <ul>
 *   <li>Weeds/empty: 0..3</li>
 *   <li>Diseased: 128..169, grouped per herb with 3 variants (stages 2..4)</li>
 *   <li>Dead: 170..172 (stages 2..4), not herb-specific visually</li>
 * </ul>
 */
public final class StandardHerbSlotDecoder
{
    private static final int EMPTY_MIN = 0;
    private static final int EMPTY_MAX = 3;

    // Healthy herb blocks, in the canonical herb order.
    private static final HerbType[] HERB_ORDER = new HerbType[]
    {
        HerbType.GUAM,
        HerbType.MARRENTILL,
        HerbType.TARROMIN,
        HerbType.HARRALANDER,
        HerbType.RANARR,
        HerbType.TOADFLAX,
        HerbType.IRIT,
        HerbType.AVANTOE,
        HerbType.KWUARM,
        HerbType.SNAPDRAGON,
        HerbType.CADANTINE,
        HerbType.LANTADYME,
        HerbType.DWARF_WEED,
        HerbType.TORSTOL
    };

    // Varlamore: Huasca uses the same 7-value healthy block shape as classic herbs, but it is
    // inserted between Avantoe and Kwuarm without changing the diseased ordering.
    private static final int HUASCA_HEALTHY_BLOCK_START = 60;
    private static final int HUASCA_DISEASED_MIN = 173;
    private static final int HUASCA_DISEASED_MAX = 175;

    // Start raw values for each 7-value healthy block (stage1). Derived from cache transform table.
    private static final int[] HEALTHY_BLOCK_START = new int[]
    {
        4,   // guam
        11,  // marrentill
        18,  // tarromin
        25,  // harralander
        32,  // ranarr
        39,  // toadflax
        46,  // irit
        53,  // avantoe
        68,  // kwuarm
        75,  // snapdragon
        82,  // cadantine
        89,  // lantadyme
        96,  // dwarf weed
        103  // torstol
    };

    private static final int DISEASED_MIN = 128;
    private static final int DISEASED_MAX = 169;

    private static final int DEAD_MIN = 170;
    private static final int DEAD_MAX = 172;

    private StandardHerbSlotDecoder() {}

    /** Returns true when {@code raw} is one of the stage-5 harvesting transient values for any herb. */
    public static boolean isHealthyHarvestTransient(int raw)
    {
        // Huasca (Varlamore)
        if (raw == HUASCA_HEALTHY_BLOCK_START + 5 || raw == HUASCA_HEALTHY_BLOCK_START + 6)
        {
            return true;
        }

        for (int start : HEALTHY_BLOCK_START)
        {
            if (raw == start + 5 || raw == start + 6)
            {
                return true;
            }
        }
        return false;
    }


    /**
     * For stage-5 healthy herbs, returns a small "harvest depletion stage" in [1..3].
     *
     * <p>3 = full (first harvestable variant), 2 = fewer picks remaining, 1 = fewest picks remaining.
     * Returns 0 when {@code raw} is not one of the stage-5 healthy herb values.</p>
     */
    public static int getHarvestStageOrZero(int raw)
    {
        // Huasca (Varlamore)
        if (raw == HUASCA_HEALTHY_BLOCK_START + 4)
        {
            return 3;
        }
        if (raw == HUASCA_HEALTHY_BLOCK_START + 5)
        {
            return 2;
        }
        if (raw == HUASCA_HEALTHY_BLOCK_START + 6)
        {
            return 1;
        }

        for (int start : HEALTHY_BLOCK_START)
        {
            if (raw == start + 4)
            {
                return 3;
            }
            if (raw == start + 5)
            {
                return 2;
            }
            if (raw == start + 6)
            {
                return 1;
            }
        }
        return 0;
    }

    public static int getMaxHarvestStage()
    {
        return 3;
    }
    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= EMPTY_MIN && raw <= EMPTY_MAX)
        {
            return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, null, raw);
        }

        // Healthy Huasca (Varlamore).
        DecodedPatchState huasca = decodeHealthyHuasca(raw);
        if (huasca != null)
        {
            return huasca;
        }

        // Healthy herb stages.
        DecodedPatchState healthy = decodeHealthy(raw);
        if (healthy != null)
        {
            return healthy;
        }

        // Diseased herbs: herb-specific (14 herbs * 3 variants = 42 values).
        if (raw >= DISEASED_MIN && raw <= DISEASED_MAX)
        {
            int idx = (raw - DISEASED_MIN) / 3;
            int variant = (raw - DISEASED_MIN) % 3;

            HerbType herb = idx >= 0 && idx < HERB_ORDER.length ? HERB_ORDER[idx] : null;
            int stage = 2 + variant; // stages 2..4
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, herb, raw);
        }

        // Diseased Huasca (Varlamore): 3 variants.
        if (raw >= HUASCA_DISEASED_MIN && raw <= HUASCA_DISEASED_MAX)
        {
            int stage = 2 + (raw - HUASCA_DISEASED_MIN); // stages 2..4
            return new DecodedPatchState(false, stage, PatchHealth.DISEASED, HerbType.HUASCA, raw);
        }

        // Dead herbs: 3 variants (stages 2..4). Visual is not herb-specific.
        if (raw >= DEAD_MIN && raw <= DEAD_MAX)
        {
            int stage = 2 + (raw - DEAD_MIN);
            return new DecodedPatchState(false, stage, PatchHealth.DEAD, null, raw);
        }

        // Unknown / new states.
        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, null, raw);
    }

    private static DecodedPatchState decodeHealthyHuasca(int raw)
    {
        int start = HUASCA_HEALTHY_BLOCK_START;
        int end = start + 6;
        if (raw < start || raw > end)
        {
            return null;
        }

        // Stage 1..4 are direct.
        if (raw <= start + 3)
        {
            int stage = (raw - start) + 1;
            return new DecodedPatchState(false, stage, PatchHealth.HEALTHY, HerbType.HUASCA, raw);
        }

        // Fully grown + harvest transients.
        return new DecodedPatchState(false, 5, PatchHealth.HEALTHY, HerbType.HUASCA, raw);
    }

    private static DecodedPatchState decodeHealthy(int raw)
    {
        // Each block is 7 values wide: [s1..s4][g5,g5_h1,g5_h2]
        for (int i = 0; i < HEALTHY_BLOCK_START.length; i++)
        {
            int start = HEALTHY_BLOCK_START[i];
            int end = start + 6;
            if (raw < start || raw > end)
            {
                continue;
            }

            HerbType herb = HERB_ORDER[i];

            // Stage 1..4 are direct.
            if (raw <= start + 3)
            {
                int stage = (raw - start) + 1;
                return new DecodedPatchState(false, stage, PatchHealth.HEALTHY, herb, raw);
            }

            // Fully grown + harvest transients.
            // All three values represent "ready" herbs, with the latter two reflecting
            // the same grown herb but fewer picks remaining.
            return new DecodedPatchState(false, 5, PatchHealth.HEALTHY, herb, raw);
        }

        return null;
    }
}
