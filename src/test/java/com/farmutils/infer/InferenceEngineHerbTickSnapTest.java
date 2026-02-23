package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InferenceEngineHerbTickSnapTest
{
    @Test
    public void herbStageTransitionSnapsToTickMinute()
    {
        // Observed at 12:28:51, with a 20-minute cadence.
        // This implies an offset of 8 minutes (28 % 20), so tick boundaries are :08, :28, :48.
        Instant observedAt = Instant.parse("2026-02-20T12:28:51Z");
        FixedPatchClock clock = new FixedPatchClock(observedAt);
        InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

        // Stage 4 => one cycle remaining to READY.
        engine.onObservation(Observation.growthStageTransition(PatchId.HERB_CATHERBY, 4, observedAt, ObservationSource.VARBIT));

        PatchInference inf = engine.get(PatchId.HERB_CATHERBY);
        assertEquals(InferredStage.GROWING, inf.getStage());

        // Stage started at the 12:28 tick boundary (seconds snapped to 00), ready at 12:48.
        Instant expectedReady = Instant.parse("2026-02-20T12:48:00Z");
        assertEquals(expectedReady, inf.getEarliestReadyAt());
        assertEquals(expectedReady, inf.getLatestReadyAt());

        // Advance to just after 12:48 and ensure we flip to READY.
        clock.setNow(expectedReady.plus(Duration.ofSeconds(1)));
        engine.tick();
        assertEquals(InferredStage.READY, engine.get(PatchId.HERB_CATHERBY).getStage());
    }
}
