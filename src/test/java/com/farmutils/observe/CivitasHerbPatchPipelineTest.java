package com.farmutils.observe;

import com.farmutils.infer.FarmDurationModelV0;
import com.farmutils.infer.FixedPatchClock;
import com.farmutils.infer.InferenceEngine;
import com.farmutils.infer.InferredStage;
import com.farmutils.infer.Observation;
import com.farmutils.infer.ObservationSource;
import com.farmutils.model.HerbType;
import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.*;

public class CivitasHerbPatchPipelineTest
{
    @Test
    public void civitasHerbPatchDecodesAndFeedsInference()
    {
        // Region wiring (RuneLite Time Tracking: Civitas illa Fortis farming area)
        assertEquals(PatchId.HERB_CIVITAS_ILLA_FORTIS, Varbit4774HerbVarbitObserver.patchForRegionId(6192));
        assertEquals(PatchId.HERB_CIVITAS_ILLA_FORTIS, Varbit4774HerbVarbitObserver.patchForRegionId(6448));

        // Varbit decode (Huasca is Varlamore-specific and exercises the "new herb" mapping)
        DecodedPatchState stage1 = StandardHerbSlotDecoder.decode(60);
        assertFalse(stage1.isEmpty());
        assertEquals(1, stage1.getStage());
        assertEquals(HerbType.HUASCA, stage1.getHerbTypeOrNull());
        assertEquals(PatchHealth.HEALTHY, stage1.getHealth());

        // Decode a harvestable variant (stage 5)
        DecodedPatchState harvestable = StandardHerbSlotDecoder.decode(64);
        assertFalse(harvestable.isEmpty());
        assertEquals(5, harvestable.getStage());
        assertEquals(HerbType.HUASCA, harvestable.getHerbTypeOrNull());
        assertEquals(3, StandardHerbSlotDecoder.getHarvestStageOrZero(64));

        // Minimal inference pipeline: planted -> ready window for Civitas herb patch.
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        PatchId patch = PatchId.HERB_CIVITAS_ILLA_FORTIS;
        engine.onObservation(Observation.planted(patch, t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.growthStageObserved(patch, stage1.getStage(), t0, ObservationSource.VARBIT));
        engine.onObservation(Observation.cropObserved(
            patch,
            HerbType.HUASCA.getCleanItemId(),
            HerbType.HUASCA.getDisplayName(),
            t0,
            ObservationSource.VARBIT
        ));

        assertTrue(engine.getCropItemId(patch).isPresent());
        assertEquals(HerbType.HUASCA.getCleanItemId(), engine.getCropItemId(patch).getAsInt());

        clock.setNow(t0.plus(Duration.ofMinutes(80)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(patch).getStage());
        assertEquals(t0.plus(Duration.ofMinutes(80)), engine.get(patch).getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(100)), engine.get(patch).getLatestReadyAt());

        clock.setNow(t0.plus(Duration.ofMinutes(101)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(patch).getStage());
    }
}
