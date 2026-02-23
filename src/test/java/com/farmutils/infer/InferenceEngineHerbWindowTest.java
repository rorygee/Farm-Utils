package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.*;

public class InferenceEngineHerbWindowTest
{
    @Test
    public void herbWindowIsConservativeAndExposesBounds()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        engine.onObservation(Observation.planted(PatchId.HERB_CATHERBY, t0, ObservationSource.VARBIT));

        // Before earliest
        clock.setNow(t0.plus(Duration.ofMinutes(79)));
        engine.tick();
        assertEquals(InferredStage.GROWING, engine.get(PatchId.HERB_CATHERBY).getStage());

        // At earliest: still conservatively GROWING, but with a window.
        clock.setNow(t0.plus(Duration.ofMinutes(80)));
        engine.tick();
        PatchInference atEarliest = engine.get(PatchId.HERB_CATHERBY);
        assertEquals(InferredStage.GROWING, atEarliest.getStage());
        assertEquals(t0.plus(Duration.ofMinutes(80)), atEarliest.getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(100)), atEarliest.getLatestReadyAt());

        // After latest: READY.
        clock.setNow(t0.plus(Duration.ofMinutes(101)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(PatchId.HERB_CATHERBY).getStage());
    }
}
