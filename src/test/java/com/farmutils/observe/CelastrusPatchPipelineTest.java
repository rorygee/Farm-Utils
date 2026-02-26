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

public class CelastrusPatchPipelineTest
{
    @Test
    public void celastrusDecodesAndFeedsInference()
    {
        // Growing stage 1
        DecodedPatchState stage1 = StandardCelastrusSlotDecoder.decode(8);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(6, stage1.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());
        assertNotNull(stage1.getCropItemIdOrNull());

        // Harvest depletion mapping
        assertEquals(4, StandardCelastrusSlotDecoder.getHarvestStageOrZero(14));
        assertEquals(3, StandardCelastrusSlotDecoder.getHarvestStageOrZero(15));
        assertEquals(2, StandardCelastrusSlotDecoder.getHarvestStageOrZero(16));
        assertEquals(1, StandardCelastrusSlotDecoder.getHarvestStageOrZero(17));

        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.SPECIAL_TREE_CELASTRUS_FARMING_GUILD;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), stage1.getMaxGrowthStageOrZero(), Duration.ofMinutes(160), t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.cropObserved(patch, stage1.getCropItemIdOrNull(), stage1.getCropNameOrNull(), t0, ObservationSource.VARBIT));

        // Stage 1 baseline => earliest = 5 cycles, latest = 6 cycles.
        clock.setNow(t0.plus(Duration.ofMinutes(800)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(800)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(960)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(961)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());

        // When READY, a harvest depletion stage can be shown (max=4 from duration model).
        engine.onObservation(Observation.harvestStageObserved(patch, 4, clock.now(), ObservationSource.VARBIT));
        engine.tick();
        assertTrue(engine.getGrowthProgress(patch).isPresent());
        assertEquals(4, engine.getGrowthProgress(patch).get().getStageCurrent());
        assertEquals(4, engine.getGrowthProgress(patch).get().getStageMax());
    }
}
