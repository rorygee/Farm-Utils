package com.farmutils.observe;

import net.runelite.api.gameval.ItemID;

/** Decoder for the Song of the Elves elder cadantine patch (varbit 9016). */
public final class QuestElderCadantineSlotDecoder
{
    private QuestElderCadantineSlotDecoder() {}

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int maxStage, int raw)
    {
        // Use cadantine icon as a close stand-in.
        return new DecodedPatchState(false, stage, maxStage, PatchHealth.HEALTHY, ItemID.CADANTINE, "Elder cadantine", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Growth states are 78, 80, 82, 84 (stage 1..4). Everything else is empty.
        if (raw == 78 || raw == 80 || raw == 82 || raw == 84)
        {
            int stage = ((raw - 78) / 2) + 1;
            return healthy(stage, 4, raw);
        }
        return empty(raw);
    }
}
