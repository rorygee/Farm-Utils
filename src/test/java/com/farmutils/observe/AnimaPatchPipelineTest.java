package com.farmutils.observe;

import com.farmutils.infer.FixedPatchClock;
import com.farmutils.infer.InferenceEngine;
import com.farmutils.infer.InferredStage;
import com.farmutils.infer.Observation;
import com.farmutils.infer.ObservationSource;
import com.farmutils.model.AnimaType;
import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.*;

public class AnimaPatchPipelineTest
{
    @Test
    public void animaPatchDecodesAndFeedsInference()
    {
        DecodedPatchState stage1 = StandardAnimaSlotDecoder.decode(8);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(9, stage1.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());
        assertEquals(AnimaType.ATTAS.getItemId(), (int) stage1.getCropItemIdOrNull());

        DecodedPatchState withering = StandardAnimaSlotDecoder.decode(15);
        assertFalse(withering.isEmpty());
        assertEquals(PatchHealth.DISEASED, withering.getHealth());

        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new com.farmutils.infer.FarmDurationModelV0());

        PatchId patch = PatchId.SPECIAL_ANIMA;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), 9, Duration.ofMinutes(640), t0, ObservationSource.VARBIT));
        assertNotNull(stage1.getCropItemIdOrNull());
        assertNotNull(stage1.getCropNameOrNull());
        engine.onObservation(Observation.cropObserved(patch, stage1.getCropItemIdOrNull(), stage1.getCropNameOrNull(), t0, ObservationSource.VARBIT));

        assertEquals(t0.plus(Duration.ofMinutes(640 * 8L)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(640 * 9L)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(640 * 8L)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());

        clock.setNow(t0.plus(Duration.ofMinutes(640 * 9L + 1L)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
