package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for Unferth's quest patch (varbit 1033). */
public final class QuestUnferthSlotDecoder
{
    private QuestUnferthSlotDecoder() {}

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int maxStage, int raw)
    {
        // Potatoes are the only supported crop for this patch.
        return new DecodedPatchState(false, stage, maxStage, PatchHealth.HEALTHY, ItemID.POTATO, "Potatoes", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // 0..2 are weeds/raking substages, 3 is fully raked.
        if (raw >= 0 && raw <= 3)
        {
            return empty(raw);
        }

        // 4..8 are planted -> grown.
        if (raw >= 4 && raw <= 8)
        {
            // Stage is 1..5.
            return healthy(raw - 3, 5, raw);
        }

        // Treat unknown values as empty to avoid false "growing".
        return empty(raw);
    }
}
