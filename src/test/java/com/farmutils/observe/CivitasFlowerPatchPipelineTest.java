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

public class CivitasFlowerPatchPipelineTest
{
    @Test
    public void civitasFlowerPatchDecodesAndFeedsInference()
    {
        // Region wiring (RuneLite Time Tracking: Civitas illa Fortis farming area)
        assertEquals(PatchId.FLOWER_CIVITAS_ILLA_FORTIS, Varbit4773FlowerVarbitObserver.patchForRegionId(6192));
        assertEquals(PatchId.FLOWER_CIVITAS_ILLA_FORTIS, Varbit4773FlowerVarbitObserver.patchForRegionId(6448));

        // Varbit decode (Marigold stage 1)
        DecodedPatchState stage1 = StandardFlowerSlotDecoder.decode(8);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());
        assertEquals(Integer.valueOf(6010), stage1.getCropItemIdOrNull());
        assertEquals("Marigold", stage1.getCropNameOrNull());

        // Harvestable marigold
        DecodedPatchState harvestable = StandardFlowerSlotDecoder.decode(12);
        assertFalse(harvestable.isEmpty());
        assertEquals(5, harvestable.getStage());
        assertEquals(PatchHealth.HEALTHY, harvestable.getHealth());
        assertEquals(Integer.valueOf(6010), harvestable.getCropItemIdOrNull());

        // Minimal inference pipeline: planted -> ready window.
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.FLOWER_CIVITAS_ILLA_FORTIS;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.cropObserved(
            patch,
            stage1.getCropItemIdOrNull(),
            stage1.getCropNameOrNull(),
            t0,
            ObservationSource.VARBIT
        ));

        assertTrue(engine.getCropItemId(patch).isPresent());
        assertEquals(6010, engine.getCropItemId(patch).getAsInt());

        clock.setNow(t0.plus(Duration.ofMinutes(20)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(20)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(25)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(26)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
