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

public class CivitasAllotmentPatchPipelineTest
{
    @Test
    public void civitasAllotmentPatchDecodesAndFeedsInference()
    {
        // Region wiring (RuneLite Time Tracking: Civitas illa Fortis farming area)
        assertEquals(PatchId.ALLOTMENT_CIVITAS_ILLA_FORTIS_PLOT_1, Varbit4771AllotmentVarbitObserver.patchForRegionId(6192));
        assertEquals(PatchId.ALLOTMENT_CIVITAS_ILLA_FORTIS_PLOT_2, Varbit4772AllotmentVarbitObserver.patchForRegionId(6448));

        // Varbit decode (Potato stage 1)
        DecodedPatchState stage1 = StandardAllotmentSlotDecoder.decode(6);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(5, stage1.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());
        assertEquals(Integer.valueOf(1942), stage1.getCropItemIdOrNull());
        assertEquals("Potato", stage1.getCropNameOrNull());

        // Harvestable potato
        DecodedPatchState harvestable = StandardAllotmentSlotDecoder.decode(10);
        assertFalse(harvestable.isEmpty());
        assertEquals(5, harvestable.getStage());
        assertEquals(5, harvestable.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, harvestable.getHealth());

        // Minimal inference pipeline: planted -> ready window.
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.ALLOTMENT_CIVITAS_ILLA_FORTIS_PLOT_1;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), stage1.getMaxGrowthStageOrZero(), t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.cropObserved(
            patch,
            stage1.getCropItemIdOrNull(),
            stage1.getCropNameOrNull(),
            t0,
            ObservationSource.VARBIT
        ));

        assertTrue(engine.getCropItemId(patch).isPresent());
        assertEquals(1942, engine.getCropItemId(patch).getAsInt());

        clock.setNow(t0.plus(Duration.ofMinutes(40)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(40)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(50)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(51)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
