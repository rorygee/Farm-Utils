package com.farmutils.observe;

/** Decoder for the Grim Tales magic beanstalk patch (varbit 3714). */
public final class QuestMagicBeansSlotDecoder
{
    private QuestMagicBeansSlotDecoder() {}

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(int stage, int maxStage, int raw)
    {
        return new DecodedPatchState(false, stage, maxStage, PatchHealth.HEALTHY, null, "Magic beanstalk", raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // 0 = empty mound, 1 = planted, 2 = grown, 3 = grown (shrunk), 4 = stump.
        switch (raw)
        {
            case 0:
                return empty(raw);
            case 1:
                return healthy(1, 2, raw);
            case 2:
            case 3:
                return healthy(2, 2, raw);
            case 4:
                // Post-quest state is a permanent stump.
                return empty(raw);
            default:
                return empty(raw);
        }
    }
}
