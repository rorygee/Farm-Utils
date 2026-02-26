package com.farmutils.observe;

/** Decoder for the Forgettable Tale Kelda hops patch (varbit 823). */
public final class QuestKeldaHopsSlotDecoder
{
    private QuestKeldaHopsSlotDecoder() {}

    private static DecodedPatchState empty(int raw)
    {
        // 0..2 are weed/raking substages, 3 is fully raked.
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int maxStage, int raw)
    {
        // Kelda hops are quest-specific; keep icon generic but name the crop.
        return new DecodedPatchState(false, stage, maxStage, PatchHealth.HEALTHY, null, "Kelda hops", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        if (raw >= 0 && raw <= 3)
        {
            return empty(raw);
        }

        // 4..8 map to stages 1..5.
        if (raw >= 4 && raw <= 8)
        {
            return healthy(raw - 3, 5, raw);
        }

        return empty(raw);
    }
}
