package com.farmutils.observe;

import com.farmutils.infer.FixedPatchClock;
import com.farmutils.infer.InferenceEngine;
import com.farmutils.infer.InferredStage;
import com.farmutils.infer.Observation;
import com.farmutils.infer.ObservationSource;
import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.*;

public class HesporiPatchPipelineTest
{
    @Test
    public void hesporiPatchDecodesAndFeedsInference()
    {
        DecodedPatchState stage1 = StandardHesporiSlotDecoder.decode(4);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(4, stage1.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());

        DecodedPatchState ready = StandardHesporiSlotDecoder.decode(7);
        assertFalse(ready.isEmpty());
        assertEquals(4, ready.getStage());

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new com.farmutils.infer.FarmDurationModelV0());

        PatchId patch = PatchId.SPECIAL_HESPORI;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), 4, Duration.ofMinutes(640), t0, ObservationSource.VARBIT));
        assertNotNull(stage1.getCropItemIdOrNull());
        assertNotNull(stage1.getCropNameOrNull());
        engine.onObservation(Observation.cropObserved(patch, stage1.getCropItemIdOrNull(), stage1.getCropNameOrNull(), t0, ObservationSource.VARBIT));

        assertEquals(t0.plus(Duration.ofMinutes(640 * 3L)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(640 * 4L)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(640 * 3L)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());

        clock.setNow(t0.plus(Duration.ofMinutes(640 * 4L + 1L)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
