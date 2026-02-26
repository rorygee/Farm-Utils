package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for the White Knights' Castle enriched snapdragon patch (varbit 10781). */
public final class QuestEnrichedSnapdragonSlotDecoder
{
    private QuestEnrichedSnapdragonSlotDecoder() {}

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int maxStage, int raw)
    {
        // Use normal snapdragon icon as a close stand-in.
        return new DecodedPatchState(false, stage, maxStage, PatchHealth.HEALTHY, ItemID.SNAPDRAGON, "Enriched snapdragon", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Patch has three morph states: empty, planted, grown.
        // Most morph tables use consecutive values starting at 0.
        if (raw == 1)
        {
            return healthy(1, 2, raw);
        }
        if (raw == 2)
        {
            return healthy(2, 2, raw);
        }
        return empty(raw);
    }
}
