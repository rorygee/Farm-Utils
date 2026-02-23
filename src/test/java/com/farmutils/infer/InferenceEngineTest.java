package com.farmutils.infer;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchState;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;

import static org.junit.Assert.*;

public class InferenceEngineTest
{
    private static final PatchId ID = PatchId.ALLOTMENT_FALADOR_PLOT_1;
	private static final PatchId HERB = PatchId.HERB_CATHERBY;

    private static final class FixedReadyWindowModel implements PatchDurationModel
    {
        private final ReadyWindow window;

        private FixedReadyWindowModel(Duration min, Duration max)
        {
            this.window = new ReadyWindow(min, max);
        }

        @Override
        public Optional<Duration> durationToComplete(PatchId patchId)
        {
            return Optional.empty();
        }

        @Override
        public Optional<ReadyWindow> getReadyWindow(PatchId patchId)
        {
            return Optional.of(window);
        }
    }

    @Test
    public void plantedComputesReadyWindowAndStartsGrowing()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FixedReadyWindowModel(Duration.ofMinutes(10), Duration.ofMinutes(20)));

        engine.onObservation(Observation.planted(ID, t0, ObservationSource.MANUAL_DEBUG));
        PatchInference inf = engine.get(ID);

        assertEquals(InferredStage.GROWING, inf.getStage());
        assertEquals(t0, inf.getLastObservedAt());
        assertEquals(t0.plus(Duration.ofMinutes(10)), inf.getEarliestReadyAt());
        assertEquals(t0.plus(Duration.ofMinutes(20)), inf.getLatestReadyAt());
        assertTrue(inf.getReasons().contains(ReasonCode.DERIVED_FROM_TIME));
        assertEquals(0.6f, inf.getConfidence(), 0.0001f);
    }

    @Test
    public void betweenEarliestAndLatestRemainsGrowingWithWindowReason()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FixedReadyWindowModel(Duration.ofMinutes(10), Duration.ofMinutes(20)));

        engine.onObservation(Observation.planted(ID, t0, ObservationSource.MANUAL_DEBUG));

        clock.setNow(t0.plus(Duration.ofMinutes(15)));
        engine.tick();

        PatchInference inf = engine.get(ID);
        assertEquals(InferredStage.GROWING, inf.getStage());
        assertTrue(inf.getReasons().contains(ReasonCode.DERIVED_FROM_TIME));
        assertTrue(inf.getReasons().contains(ReasonCode.DURATION_WINDOW));
        assertEquals(0.5f, inf.getConfidence(), 0.0001f);
    }

    @Test
    public void afterLatestIsReady()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FixedReadyWindowModel(Duration.ofMinutes(10), Duration.ofMinutes(20)));

        engine.onObservation(Observation.planted(ID, t0, ObservationSource.MANUAL_DEBUG));

        clock.setNow(t0.plus(Duration.ofMinutes(20)));
        engine.tick();

        PatchInference inf = engine.get(ID);
        assertEquals(InferredStage.READY, inf.getStage());
        assertTrue(inf.getReasons().contains(ReasonCode.DERIVED_FROM_TIME));
        assertFalse(inf.getReasons().contains(ReasonCode.DURATION_WINDOW));
        assertEquals(0.6f, inf.getConfidence(), 0.0001f);
    }

    @Test
    public void diseasedOverridesTimeUntilCleared()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FixedReadyWindowModel(Duration.ofMinutes(1), Duration.ofMinutes(2)));

        engine.onObservation(Observation.planted(ID, t0, ObservationSource.MANUAL_DEBUG));

        Instant diseasedAt = t0.plus(Duration.ofSeconds(30));
        engine.onObservation(Observation.diseasedSet(ID, diseasedAt, ObservationSource.MANUAL_DEBUG));
        PatchInference diseased = engine.get(ID);
        assertEquals(InferredStage.DISEASED, diseased.getStage());
        assertTrue(diseased.getReasons().contains(ReasonCode.DERIVED_FROM_EVENTS));
        assertEquals(0.9f, diseased.getConfidence(), 0.0001f);

        // Even after it would otherwise be ready, diseased remains.
        clock.setNow(t0.plus(Duration.ofMinutes(5)));
        engine.tick();
        assertEquals(InferredStage.DISEASED, engine.get(ID).getStage());

        Instant clearedAt = t0.plus(Duration.ofMinutes(5));
        engine.onObservation(Observation.diseasedCleared(ID, clearedAt, ObservationSource.MANUAL_DEBUG));

        PatchInference afterClear = engine.get(ID);
        assertEquals(InferredStage.READY, afterClear.getStage());
        assertTrue(afterClear.getReasons().contains(ReasonCode.DERIVED_FROM_TIME));
    }

    @Test
    public void deadOverridesAll()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FixedReadyWindowModel(Duration.ofMinutes(1), Duration.ofMinutes(2)));

        engine.onObservation(Observation.planted(ID, t0, ObservationSource.MANUAL_DEBUG));
        engine.onObservation(Observation.deadSet(ID, t0.plus(Duration.ofSeconds(10)), ObservationSource.MANUAL_DEBUG));

        PatchInference inf = engine.get(ID);
        assertEquals(InferredStage.DEAD, inf.getStage());
        assertTrue(inf.getReasons().contains(ReasonCode.DERIVED_FROM_EVENTS));
        assertEquals(0.9f, inf.getConfidence(), 0.0001f);
    }

    @Test
    public void harvestedAfterPlantedYieldsEmpty()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FixedReadyWindowModel(Duration.ofMinutes(1), Duration.ofMinutes(2)));

        engine.onObservation(Observation.planted(ID, t0, ObservationSource.MANUAL_DEBUG));
        engine.onObservation(Observation.harvested(ID, t0.plus(Duration.ofMinutes(1)), ObservationSource.MANUAL_DEBUG));

        PatchInference inf = engine.get(ID);
        assertEquals(InferredStage.EMPTY, inf.getStage());
        assertTrue(inf.getReasons().contains(ReasonCode.DERIVED_FROM_EVENTS));
    }

    @Test
    public void manualLockPreventsTickOverrideUntilExpiry()
    {
        Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
        FixedPatchClock clock = new FixedPatchClock(t0);
        InferenceEngine engine = new InferenceEngine(clock, new FixedReadyWindowModel(Duration.ZERO, Duration.ofMinutes(1)));

        engine.onObservation(Observation.planted(ID, t0, ObservationSource.MANUAL_DEBUG));

        Instant manualAt = t0.plus(Duration.ofSeconds(30));
        engine.onObservation(Observation.patchStateSet(ID, PatchState.GROWING, manualAt, ObservationSource.MANUAL_DEBUG));

        // Would otherwise be ready at t0+1m, but lock holds for 2 minutes.
        clock.setNow(t0.plus(Duration.ofMinutes(1)).plusSeconds(1));
        engine.tick();

        PatchInference locked = engine.get(ID);
        assertEquals(InferredStage.GROWING, locked.getStage());
        assertEquals(1.0f, locked.getConfidence(), 0.0001f);
        assertTrue(locked.getReasons().contains(ReasonCode.MANUAL_LOCK_ACTIVE));

        // After lock expires, time-derived stage takes over.
        clock.setNow(manualAt.plus(Duration.ofMinutes(2)).plusSeconds(1));
        engine.tick();

        PatchInference after = engine.get(ID);
        assertEquals(InferredStage.READY, after.getStage());
        assertTrue(after.getReasons().contains(ReasonCode.DERIVED_FROM_TIME));
    }

	@Test
	public void diseasedDoesNotStickAcrossCycleBoundaries()
	{
		Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
		FixedPatchClock clock = new FixedPatchClock(t0);
		InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

		engine.onObservation(Observation.diseasedSet(HERB, t0, ObservationSource.VARBIT));
		assertEquals(InferredStage.DISEASED, engine.get(HERB).getStage());

		// Clear to empty (cycle boundary).
		Instant emptyAt = t0.plusSeconds(10);
		engine.onObservation(Observation.harvested(HERB, emptyAt, ObservationSource.VARBIT));
		assertEquals(InferredStage.EMPTY, engine.get(HERB).getStage());

		// Planting begins a new cycle; diseased must not persist.
		Instant plantedAt = t0.plusSeconds(20);
		engine.onObservation(Observation.planted(HERB, plantedAt, ObservationSource.VARBIT));
		assertEquals(InferredStage.GROWING, engine.get(HERB).getStage());

		// Stage observations should also keep the cycle clean.
		engine.onObservation(Observation.growthStageObserved(HERB, 1, plantedAt.plusSeconds(5), ObservationSource.VARBIT));
		assertTrue(engine.get(HERB).getStage() != InferredStage.DISEASED);
	}

	@Test
	public void growthProgressAdvancesConservativelyOverTime()
	{
		Instant t0 = Instant.parse("2026-01-01T12:00:00Z");
		FixedPatchClock clock = new FixedPatchClock(t0);
		InferenceEngine engine = new InferenceEngine(clock, new FarmDurationModelV0());

		engine.onObservation(Observation.growthStageObserved(HERB, 1, t0, ObservationSource.VARBIT));
		assertTrue(engine.getGrowthProgress(HERB).isPresent());
		GrowthProgress p0 = engine.getGrowthProgress(HERB).get();
		assertEquals(1, p0.getStageCurrent());
		assertEquals(5, p0.getStageMax());
		assertEquals(0.0f, p0.getProgress01(), 0.0001f);

		// After 40 minutes (2 stage durations), estimate stage 3.
		clock.setNow(t0.plus(Duration.ofMinutes(40)));
		GrowthProgress p1 = engine.getGrowthProgress(HERB).get();
		assertEquals(3, p1.getStageCurrent());
		assertEquals(0.5f, p1.getProgress01(), 0.0001f);
	}
}
