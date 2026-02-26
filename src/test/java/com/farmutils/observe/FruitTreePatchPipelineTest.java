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

public class FruitTreePatchPipelineTest
{
    @Test
    public void appleDecodesAndFeedsInference()
    {
        assertEquals(PatchId.FRUIT_TREE_BRIMHAVEN, Varbit4771FruitTreeVarbitObserver.patchForRegionId(11058));

        // Apple stage 6 (1-before check-health)
        DecodedPatchState stage6 = StandardFruitTreeSlotDecoder.decode(13);
        assertFalse(stage6.isEmpty());
        assertEquals(6, stage6.getStage());
        assertEquals(7, stage6.getMaxGrowthStageOrZero());
        assertEquals(PatchHealth.HEALTHY, stage6.getHealth());
        assertEquals(Integer.valueOf(ItemID.COOKING_APPLE), stage6.getCropItemIdOrNull());

        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.FRUIT_TREE_BRIMHAVEN;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage6.getStage(), stage6.getMaxGrowthStageOrZero(), Duration.ofMinutes(160), t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.cropObserved(patch, stage6.getCropItemIdOrNull(), stage6.getCropNameOrNull(), t0, ObservationSource.VARBIT));

        clock.setNow(t0.plus(Duration.ofMinutes(160)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(160)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(320)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(321)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
