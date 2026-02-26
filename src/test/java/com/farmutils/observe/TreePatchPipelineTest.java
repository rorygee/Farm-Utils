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
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

import static org.junit.Assert.*;

public class TreePatchPipelineTest
{
    @Test
    public void oakDecodesAndFeedsInference()
    {
        assertEquals(PatchId.TREE_LUMBRIDGE, Varbit4771TreeVarbitObserver.patchForRegionId(12594));

        // Oak stage 1
        DecodedPatchState stage1 = StandardTreeSlotDecoder.decode(8);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(5, stage1.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());
        assertEquals(Integer.valueOf(ItemID.OAK_LOGS), stage1.getCropItemIdOrNull());

        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.TREE_LUMBRIDGE;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), stage1.getMaxGrowthStageOrZero(), Duration.ofMinutes(40), t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.cropObserved(patch, stage1.getCropItemIdOrNull(), stage1.getCropNameOrNull(), t0, ObservationSource.VARBIT));

        clock.setNow(t0.plus(Duration.ofMinutes(160)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(160)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(200)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(201)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
