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

public class HardwoodPatchPipelineTest
{
    @Test
    public void teakDecodesAndFeedsInference()
    {
        assertEquals(PatchId.SPECIAL_TREE_HARDWOOD_FOSSIL_ISLAND_PLOT_1, Varbit4771HardwoodVarbitObserver.patchForRegionId(14651));
        assertEquals(PatchId.SPECIAL_TREE_HARDWOOD_LOCUS_OASIS, Varbit4771HardwoodVarbitObserver.patchForRegionId(6702));

        DecodedPatchState stage1 = StandardHardwoodSlotDecoder.decode(8);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(8, stage1.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());

        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.SPECIAL_TREE_HARDWOOD_FOSSIL_ISLAND_PLOT_1;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), stage1.getMaxGrowthStageOrZero(), Duration.ofMinutes(640), t0, ObservationSource.VARBIT));

        // Stage 1 baseline => earliest = 7 cycles, latest = 8 cycles.
        clock.setNow(t0.plus(Duration.ofMinutes(4480)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(4480)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(5120)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(5121)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
