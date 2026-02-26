package com.farmutils.observe;

import com.farmutils.infer.FarmDurationModelV0;
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

public class CrystalTreePatchPipelineTest
{
    @Test
    public void crystalTreeDecodesAndFeedsInference()
    {
        DecodedPatchState stage1 = StandardCrystalTreeSlotDecoder.decode(8);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(7, stage1.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());
        assertNotNull(stage1.getCropItemIdOrNull());

        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.SPECIAL_TREE_CRYSTAL_PRIFDDINAS;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), stage1.getMaxGrowthStageOrZero(), Duration.ofMinutes(80), t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.cropObserved(patch, stage1.getCropItemIdOrNull(), stage1.getCropNameOrNull(), t0, ObservationSource.VARBIT));

        // Stage 1 baseline => earliest = 6 cycles, latest = 7 cycles.
        clock.setNow(t0.plus(Duration.ofMinutes(480)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(480)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(560)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(561)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
