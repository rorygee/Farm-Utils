package com.farmutils.observe;

import com.farmutils.model.AnimaType;

/** Decoder for anima patch transform table (Time Tracking: PatchImplementation.ANIMA). */
public final class StandardAnimaSlotDecoder
{
    private StandardAnimaSlotDecoder() {}

    private static final int MAX_STAGE = 9;

    private static DecodedPatchState empty(int raw)
    {
        return new DecodedPatchState(true, 0, PatchHealth.HEALTHY, raw);
    }

    private static DecodedPatchState healthy(AnimaType type, int stage, int raw)
    {
        return new DecodedPatchState(false, stage, MAX_STAGE, PatchHealth.HEALTHY, type.getItemId(), type.getDisplayName(), raw);
    }

    private static DecodedPatchState diseased(AnimaType type, int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DISEASED, type.getItemId(), type.getDisplayName(), raw);
    }

    private static DecodedPatchState dead(AnimaType type, int raw)
    {
        return new DecodedPatchState(false, -1, MAX_STAGE, PatchHealth.DEAD, type.getItemId(), type.getDisplayName(), raw);
    }

    private static DecodedPatchState decodeFor(AnimaType type, int base, int raw)
    {
        int idx = raw - base; // 0..8
        if (idx == 7)
        {
            return diseased(type, raw); // "Withering" variant
        }
        if (idx == 8)
        {
            return dead(type, raw);
        }
        return healthy(type, idx + 1, raw);
    }

    public static DecodedPatchState decode(int raw)
    {
        // Weeds/empty.
        if (raw >= 0 && raw <= 7)
        {
            return empty(raw);
        }

        if (raw >= 8 && raw <= 16)
        {
            return decodeFor(AnimaType.ATTAS, 8, raw);
        }

        if (raw >= 17 && raw <= 25)
        {
            return decodeFor(AnimaType.IASOR, 17, raw);
        }

        if (raw >= 26 && raw <= 34)
        {
            return decodeFor(AnimaType.KRONOS, 26, raw);
        }

        if (raw >= 0 && raw <= 255)
        {
            return empty(raw);
        }

        return new DecodedPatchState(false, -1, PatchHealth.HEALTHY, raw);
    }
}
