package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InferenceEngineChangeCounterTest
{
    @Test
    public void changeCounterIncrementsOnlyOnOutputChanges()
    {
        Instant t0 = Instant.parse("2026-02-18T00:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        assertEquals(0L, engine.getChangeCounter());

        engine.onObservation(Observation.planted(PatchId.HERB_CATHERBY, t0, ObservationSource.VARBIT));
        assertEquals(1L, engine.getChangeCounter());

        // Still growing; no output change.
        clock.setNow(t0.plus(Duration.ofMinutes(50)));
        engine.tick();
        assertEquals(1L, engine.getChangeCounter());

        // Past latest bound; should flip to READY exactly once.
        clock.setNow(t0.plus(Duration.ofMinutes(101)));
        engine.tick();
        assertEquals(2L, engine.getChangeCounter());

        // Further ticks without new observations should not churn.
        clock.setNow(t0.plus(Duration.ofMinutes(150)));
        engine.tick();
        assertEquals(2L, engine.getChangeCounter());
    }
}
