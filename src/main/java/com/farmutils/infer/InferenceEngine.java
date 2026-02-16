package com.farmutils.infer;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchState;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InferenceEngine implements PatchInferenceEngine
{
    private static final Logger log = LoggerFactory.getLogger(InferenceEngine.class);

    private static final Duration MANUAL_LOCK_TTL = Duration.ofMinutes(2);

    private final PatchClock clock;
    private final PatchDurationModel durationModel;
    private final Map<PatchId, PatchInference> inferredByPatch = new HashMap<>();
    private final Map<PatchId, PatchAnchorState> anchorsByPatch = new HashMap<>();

    /**
     * Guice-friendly default constructor.
     *
     * v0 uses a system clock and a null duration model.
     */
    @Inject
    public InferenceEngine()
    {
        this(new SystemPatchClock(), new NullDurationModel());
    }

    public InferenceEngine(PatchClock clock, PatchDurationModel durationModel)
    {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.durationModel = Objects.requireNonNull(durationModel, "durationModel");
    }

    @Override
    public synchronized PatchInference get(PatchId patchId)
    {
        Objects.requireNonNull(patchId, "patchId");
        return inferredByPatch.getOrDefault(patchId, defaultInference(patchId));
    }

    @Override
    public synchronized Map<PatchId, PatchInference> getAll()
    {
        return Collections.unmodifiableMap(new HashMap<>(inferredByPatch));
    }

    @Override
    public synchronized void onObservation(Observation observation)
    {
        Objects.requireNonNull(observation, "observation");
        PatchId patchId = observation.getPatchId();
        PatchAnchorState s = anchorsByPatch.computeIfAbsent(patchId, k -> new PatchAnchorState());
        applyToAnchorState(s, observation);
        recomputeAndMaybeLog(patchId, clock.now());
    }

    /**
     * v1 temporal progression hook.
     *
     * <p>This is intentionally NOT scheduled or auto-invoked yet. Future wiring may call this
     * from a RuneLite game tick or another deterministic scheduler.</p>
     */
    public synchronized void tick()
    {
        Instant now = clock.now();

        for (PatchId patchId : new HashMap<>(anchorsByPatch).keySet())
        {
            PatchAnchorState s = anchorsByPatch.get(patchId);
            if (s == null)
            {
                continue;
            }

            boolean timeSensitive = s.plantedAt != null;
            boolean lockActive = s.manualLockUntil != null && now.isBefore(s.manualLockUntil);
            boolean lockJustExpired = s.manualLockUntil != null && !now.isBefore(s.manualLockUntil);

            if (timeSensitive || lockActive || lockJustExpired)
            {
                recomputeAndMaybeLog(patchId, now);
            }
        }
    }

    public synchronized Optional<Instant> nextChangeAt(PatchId patchId)
    {
        Objects.requireNonNull(patchId, "patchId");
        Instant now = clock.now();
        PatchAnchorState s = anchorsByPatch.get(patchId);
        if (s == null)
        {
            return Optional.empty();
        }

        Instant best = null;

        if (s.manualLockUntil != null && s.manualLockUntil.isAfter(now))
        {
            best = s.manualLockUntil;
        }

        if (s.plantedAt != null)
        {
            Optional<ReadyWindow> w = durationModel.getReadyWindow(patchId);
            if (w.isPresent())
            {
                Instant e = s.plantedAt.plus(w.get().getMin());
                Instant l = s.plantedAt.plus(w.get().getMax());
                if (e.isAfter(now))
                {
                    best = minInstant(best, e);
                }
                if (l.isAfter(now))
                {
                    best = minInstant(best, l);
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static InferredStage mapStage(PatchState state)
    {
        switch (state)
        {
            case GROWING:
                return InferredStage.GROWING;
            case READY:
                return InferredStage.READY;
            case DISEASED:
                return InferredStage.DISEASED;
            case DEAD:
                return InferredStage.DEAD;
            case EMPTY:
                return InferredStage.EMPTY;
            default:
                return InferredStage.UNKNOWN;
        }
    }

    private PatchInference defaultInference(PatchId patchId)
    {
        // For v0, the absence of observations is explicit.
        return new PatchInference(patchId, InferredStage.UNKNOWN, null, null, null, 0.0f, EnumSet.of(ReasonCode.NO_OBSERVATIONS));
    }

    private void applyToAnchorState(PatchAnchorState s, Observation o)
    {
        Instant at = o.getObservedAt();
        s.noteObservationAt(at);

        switch (o.getKind())
        {
            case PATCH_STATE_SET:
                PatchState ps = o.getPatchStateOrNull();
                if (ps == null)
                {
                    return;
                }
                s.lastManualSetAt = at;
                s.manualStage = mapStage(ps);
                s.manualLockUntil = at.plus(MANUAL_LOCK_TTL);
                // Keep anchors; manual override is only a short stabilization.
                break;

            case PATCH_STATE_CLEARED:
                s.reset();
                s.clearedAt = at;
                s.lastObservationAt = at;
                break;

            case PLANTED:
                s.plantedAt = at;
                break;

            case HARVESTED:
                s.harvestedAt = at;
                break;

            case DISEASED_SET:
                s.diseasedAt = at;
                break;

            case DISEASED_CLEARED:
                s.clearedAt = at;
                break;

            case DEAD_SET:
                s.deadAt = at;
                break;

            default:
                break;
        }
    }

    private void recomputeAndMaybeLog(PatchId patchId, Instant now)
    {
        PatchInference before = inferredByPatch.getOrDefault(patchId, defaultInference(patchId));
        PatchAnchorState s = anchorsByPatch.get(patchId);

        PatchInference after;
        if (s == null || s.lastObservationAt == null)
        {
            after = defaultInference(patchId);
        }
        else if (s.clearedAt != null && s.lastObservationAt != null && s.clearedAt.equals(s.lastObservationAt)
                && s.plantedAt == null && s.harvestedAt == null && s.diseasedAt == null && s.deadAt == null && s.manualStage == null)
        {
            // Explicit clear with no remaining anchors.
            after = new PatchInference(patchId, InferredStage.UNKNOWN, null, null, null, 0.0f, EnumSet.of(ReasonCode.CLEARED));
        }
        else
        {
            after = inferFromAnchors(patchId, s, now);
        }

        if (!after.equals(before))
        {
            inferredByPatch.put(patchId, after);
            log.info("[infer] {} -> stage={}, lastObservedAt={}, earliestReadyAt={}, latestReadyAt={}, reasons={}",
                    patchId, after.getStage(), after.getLastObservedAt(), after.getEarliestReadyAt(), after.getLatestReadyAt(), after.getReasons());
        }
    }

    private PatchInference inferFromAnchors(PatchId patchId, PatchAnchorState s, Instant now)
    {
        EnumSet<ReasonCode> reasons = EnumSet.noneOf(ReasonCode.class);
        Instant lastObservedAt = s.lastObservationAt;

        boolean manualLockActive = s.manualLockUntil != null && now.isBefore(s.manualLockUntil);
        if (manualLockActive && s.manualStage != null)
        {
            reasons.add(ReasonCode.MANUAL_LOCK_ACTIVE);
            return new PatchInference(patchId, s.manualStage, lastObservedAt, null, null, 1.0f, reasons);
        }

        // Stage precedence: dead > diseased > harvested > planted > unknown
        if (isActiveAfterClears(s.deadAt, s.clearedAt, s.harvestedAt))
        {
            reasons.add(ReasonCode.DERIVED_FROM_EVENTS);
            return new PatchInference(patchId, InferredStage.DEAD, lastObservedAt, null, null, 0.9f, reasons);
        }

        if (isActiveAfterClears(s.diseasedAt, s.clearedAt, null))
        {
            reasons.add(ReasonCode.DERIVED_FROM_EVENTS);
            return new PatchInference(patchId, InferredStage.DISEASED, lastObservedAt, null, null, 0.9f, reasons);
        }

        if (s.harvestedAt != null && s.plantedAt != null && s.harvestedAt.isAfter(s.plantedAt))
        {
            reasons.add(ReasonCode.DERIVED_FROM_EVENTS);
            return new PatchInference(patchId, InferredStage.EMPTY, lastObservedAt, null, null, 0.9f, reasons);
        }

        if (s.plantedAt != null)
        {
            Optional<ReadyWindow> windowOpt = durationModel.getReadyWindow(patchId);
            if (!windowOpt.isPresent())
            {
                reasons.add(ReasonCode.DERIVED_FROM_EVENTS);
                reasons.add(ReasonCode.NO_SCHEDULE);
                return new PatchInference(patchId, InferredStage.GROWING, lastObservedAt, null, null, 0.9f, reasons);
            }

            ReadyWindow w = windowOpt.get();
            Instant earliest = s.plantedAt.plus(w.getMin());
            Instant latest = s.plantedAt.plus(w.getMax());

            reasons.add(ReasonCode.DERIVED_FROM_TIME);

            if (now.isBefore(earliest))
            {
                return new PatchInference(patchId, InferredStage.GROWING, lastObservedAt, earliest, latest, 0.6f, reasons);
            }

            if (!now.isBefore(latest))
            {
                // Certain readiness only after the latest bound.
                return new PatchInference(patchId, InferredStage.READY, lastObservedAt, earliest, latest, 0.6f, reasons);
            }

            // Between earliest and latest: still conservatively GROWING, but expose the window.
            reasons.add(ReasonCode.DURATION_WINDOW);
            return new PatchInference(patchId, InferredStage.GROWING, lastObservedAt, earliest, latest, 0.5f, reasons);
        }

        // Observations exist, but none are strong enough to anchor a stage.
        return new PatchInference(patchId, InferredStage.UNKNOWN, lastObservedAt, null, null, 0.0f, reasons);
    }

    private static boolean isActiveAfterClears(Instant setAt, Instant clearedAt, Instant otherClearLike)
    {
        if (setAt == null)
        {
            return false;
        }

        Instant latestClear = clearedAt;
        if (otherClearLike != null && (latestClear == null || otherClearLike.isAfter(latestClear)))
        {
            latestClear = otherClearLike;
        }

        return latestClear == null || setAt.isAfter(latestClear);
    }

    private static Instant minInstant(Instant a, Instant b)
    {
        if (a == null)
        {
            return b;
        }
        if (b == null)
        {
            return a;
        }
        return a.isBefore(b) ? a : b;
    }
}
