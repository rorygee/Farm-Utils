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
import java.util.OptionalInt;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class    InferenceEngine implements PatchInferenceEngine
{
    private static final Logger log = LoggerFactory.getLogger(InferenceEngine.class);

    private static final Duration MANUAL_LOCK_TTL = Duration.ofMinutes(2);

    private final PatchClock clock;
    private final PatchDurationModel durationModel;

    // v0: Only herbs use tick-snapped ready estimates for now.
    private final GrowthTickAligner herbTickAligner = new GrowthTickAligner(Duration.ofMinutes(20));
    private final Map<PatchId, PatchInference> inferredByPatch = new HashMap<>();
    private final Map<PatchId, PatchAnchorState> anchorsByPatch = new HashMap<>();

    /**
     * Monotonic counter for how many times any patch's inferred output changed.
     *
     * <p>Primarily for deterministic unit tests and lightweight instrumentation.
     * Not used for UI or persistence.</p>
     */
    private long changeCounter = 0;

    /**
     * Guice-friendly default constructor.
     *
     * v0 uses a system clock and a null duration model.
     */
    @Inject
    public InferenceEngine()
    {
        this(new SystemPatchClock(), new FarmDurationModelV0());
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

            // Manual lock expiry must be applied once; otherwise a patch would be considered
            // "just expired" forever and be recomputed every tick.
            if (s.manualLockUntil != null && !now.isBefore(s.manualLockUntil))
            {
                s.manualLockUntil = null;
                recomputeAndMaybeLog(patchId, now);
                continue;
            }

            // Time-based progression matters when we have any temporal anchor.
            if (s.plantedAt != null || s.growthStageObservedAt != null)
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
            Optional<ReadyWindow> w = computeReadyWindowFromPlanted(patchId, s);
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


        if (s.growthStageObservedAt != null && s.growthStage != null)
        {
            Optional<ReadyWindow> w = computeReadyWindowFromStage(patchId, s);
            if (w.isPresent())
            {
                Instant e;
                Instant l;

                Optional<ReadyWindowInstants> snapped = computeTickSnappedHerbReadyWindowFromStage(patchId, s, now);
                if (snapped.isPresent())
                {
                    e = snapped.get().earliest;
                    l = snapped.get().latest;
                }
                else
                {
                    e = s.growthStageObservedAt.plus(w.get().getMin());
                    l = s.growthStageObservedAt.plus(w.get().getMax());
                }
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

    private Integer resolveMaxGrowthStageOrNull(PatchId patchId, PatchAnchorState s)
    {
        OptionalInt maxOpt = durationModel.getMaxGrowthStage(patchId);
        if (maxOpt.isPresent())
        {
            return maxOpt.getAsInt();
        }
        if (s != null && s.maxGrowthStage != null && s.maxGrowthStage > 0)
        {
            return s.maxGrowthStage;
        }
        return null;
    }

    private Duration resolveGrowthStageDurationOrNull(PatchId patchId, PatchAnchorState s)
    {
        if (s != null && s.growthStageDuration != null && !s.growthStageDuration.isZero() && !s.growthStageDuration.isNegative())
        {
            return s.growthStageDuration;
        }
        return durationModel.getGrowthStageDuration(patchId).orElse(null);
    }

    private Optional<ReadyWindow> computeReadyWindowFromStage(PatchId patchId, PatchAnchorState s)
    {
        if (s == null || s.growthStage == null)
        {
            return Optional.empty();
        }

        Optional<ReadyWindow> byModel = durationModel.getReadyWindowFromStage(patchId, s.growthStage, s.growthStageTransition);
        if (byModel.isPresent())
        {
            return byModel;
        }

        // Generic fallback: if we know a stage duration and a max stage (from observation),
        // compute the same conservative window shape as herbs/flowers.
        Integer maxStage = resolveMaxGrowthStageOrNull(patchId, s);
        if (maxStage == null)
        {
            return Optional.empty();
        }
        Duration cycle = resolveGrowthStageDurationOrNull(patchId, s);
        if (cycle == null)
        {
            return Optional.empty();
        }

        int stage = s.growthStage;
        if (stage < 1 || stage > maxStage)
        {
            return Optional.empty();
        }
        if (stage >= maxStage)
        {
            return Optional.of(new ReadyWindow(Duration.ZERO, Duration.ZERO));
        }

        boolean stageTransition = s.growthStageTransition;
        int remainingCycles = stageTransition
            ? (maxStage - stage)
            : (maxStage - stage + 1);

        Duration max = cycle.multipliedBy(remainingCycles);
        Duration min = stageTransition ? max : max.minus(cycle);
        if (min.isNegative())
        {
            min = Duration.ZERO;
        }
        return Optional.of(new ReadyWindow(min, max));
    }

    private Optional<ReadyWindow> computeReadyWindowFromPlanted(PatchId patchId, PatchAnchorState s)
    {
        Optional<ReadyWindow> byModel = durationModel.getReadyWindow(patchId);
        if (byModel.isPresent())
        {
            return byModel;
        }

        // Generic fallback (allotments): needs a known max stage.
        Integer maxStage = resolveMaxGrowthStageOrNull(patchId, s);
        if (maxStage == null)
        {
            return Optional.empty();
        }
        Duration cycle = resolveGrowthStageDurationOrNull(patchId, s);
        if (cycle == null)
        {
            return Optional.empty();
        }
        Duration min = cycle.multipliedBy(Math.max(0, maxStage - 1));
        Duration max = cycle.multipliedBy(Math.max(0, maxStage));
        return Optional.of(new ReadyWindow(min, max));
    }

    /**
     * Returns how many inference output changes have been produced since engine construction.
     *
     * <p>This increments only when a patch's {@link PatchInference} transitions to a non-equal value.</p>
     */
    public synchronized long getChangeCounter()
    {
        return changeCounter;
    }

    

    

    /** Runtime-only crop icon id for a patch, when known (e.g. herb type). */
    public synchronized OptionalInt getCropItemId(PatchId patchId)
    {
        if (patchId == null)
        {
            return OptionalInt.empty();
        }
        PatchAnchorState s = anchorsByPatch.get(patchId);
        if (s == null || s.cropItemId == null)
        {
            return OptionalInt.empty();
        }
        return OptionalInt.of(s.cropItemId);
    }

    /** Runtime-only crop display name for a patch, when known. */
    public synchronized Optional<String> getCropName(PatchId patchId)
    {
        if (patchId == null)
        {
            return Optional.empty();
        }
        PatchAnchorState s = anchorsByPatch.get(patchId);
        if (s == null || s.cropName == null || s.cropName.isBlank())
        {
            return Optional.empty();
        }
        return Optional.of(s.cropName);
    }
/**
     * Estimates the current growth stage using stepwise time progression when possible.
     *
     * <p>This is intentionally conservative: we never advance below the last observed stage,
     * and we clamp to the patch type's configured max stage.</p>
     */
    private int estimateGrowthStageStepwise(PatchId patchId, PatchAnchorState s, Instant now, int maxStage)
    {
        if (s == null || s.growthStage == null || s.growthStageObservedAt == null || now == null)
        {
            return -1;
        }

        int observedStage = s.growthStage;
        Instant observedAt = s.growthStageObservedAt;

        int stage = observedStage;
        Duration step = resolveGrowthStageDurationOrNull(patchId, s);
        if (step != null)
        {
            if (!step.isZero() && !step.isNegative() && now.isAfter(observedAt))
            {
                // Once we have learned the herb tick offset, advance stage at the real tick boundaries.
                // This avoids "progress shading" drifting relative to the server-driven schedule.
                if (isHerbPatch(patchId) && herbTickAligner.hasOffset())
                {
                    Instant firstTick = herbTickAligner.nextTickAfter(observedAt);
                    Instant lastTick = herbTickAligner.floorToTick(now);
                    if (!lastTick.isBefore(firstTick))
                    {
                        long elapsedMillis = Duration.between(firstTick, lastTick).toMillis();
                        long stepMillis = step.toMillis();
                        if (stepMillis > 0)
                        {
                            long ticksPassed = (elapsedMillis / stepMillis) + 1L;
                            long est = (long) observedStage + ticksPassed;
                            if (est > Integer.MAX_VALUE)
                            {
                                est = Integer.MAX_VALUE;
                            }
                            stage = (int) est;
                        }
                    }
                }
                else
                {
                    // Offset unknown (or not a tick-aligned crop): conservative stepping by elapsed duration.
                    long stepMillis = step.toMillis();
                    if (stepMillis > 0)
                    {
                        long elapsedMillis = Duration.between(observedAt, now).toMillis();
                        long steps = elapsedMillis / stepMillis;
                        if (steps > 0)
                        {
                            long est = (long) observedStage + steps;
                            if (est > Integer.MAX_VALUE)
                            {
                                est = Integer.MAX_VALUE;
                            }
                            stage = (int) est;
                        }
                    }
                }
            }
        }

        if (stage < observedStage)
        {
            stage = observedStage;
        }
        if (stage > maxStage)
        {
            stage = maxStage;
        }

        return stage;
    }
/**
     * Returns a best-effort growth progress snapshot for a patch, when a concrete growth stage
     * has been observed and the duration model can supply a maximum stage for that patch type.
     *
     * <p>UI uses this for paint-only hints (e.g. progress remainder shading). If unavailable,
     * callers should gracefully fall back to existing "solid" semantics.</p>
     */
    public synchronized Optional<GrowthProgress> getGrowthProgress(PatchId patchId)
    {
        Objects.requireNonNull(patchId, "patchId");

        final Instant now = clock.now();

        PatchAnchorState s = anchorsByPatch.get(patchId);
        if (s == null)
        {
            return Optional.empty();
        }

        // Prefer harvest depletion progress when the patch is READY and a depletion stage is known.
        PatchInference inf = get(patchId);
        if (inf != null && inf.getStage() == InferredStage.READY && s.harvestStage != null && s.harvestStageObservedAt != null)
        {
            OptionalInt maxHarvestOpt = durationModel.getMaxHarvestStage(patchId);
            if (maxHarvestOpt.isPresent())
            {
                int max = maxHarvestOpt.getAsInt();
                int cur = s.harvestStage;
                if (cur < 0)
                {
                    cur = 0;
                }
                if (cur > max)
                {
                    cur = max;
                }
                if (max > 0)
                {
                    float progress = (float) cur / (float) max;
                    if (progress < 0f)
                    {
                        progress = 0f;
                    }
                    else if (progress > 1f)
                    {
                        progress = 1f;
                    }

                    return Optional.of(new GrowthProgress(patchId, cur, max, progress, s.harvestStageObservedAt));
                }
            }
        }

        // Otherwise, use growth stage progress (with conservative, stepwise time advancement when possible).
        if (s.growthStage == null || s.growthStageObservedAt == null)
        {
            return Optional.empty();
        }

        Integer maxBoxed = resolveMaxGrowthStageOrNull(patchId, s);
        if (maxBoxed == null)
        {
            return Optional.empty();
        }

        int max = maxBoxed;
        int observedStage = s.growthStage;
        Instant observedAt = s.growthStageObservedAt;
        int stage = estimateGrowthStageStepwise(patchId, s, now, max);
        if (stage < 0)
        {
            stage = observedStage;
        }
        if (stage <= 0 || max <= 1)
        {
            return Optional.empty();
        }

        float progress = (float) (stage - 1) / (float) (max - 1);
        if (progress < 0f)
        {
            progress = 0f;
        }
        else if (progress > 1f)
        {
            progress = 1f;
        }

        return Optional.of(new GrowthProgress(patchId, stage, max, progress, s.growthStageObservedAt));
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

        // Learn the player's herb growth tick offset from stage transitions.
        // Only trust explicit transitions (not baseline reads).
        if (o.getKind() == Observation.Kind.GROWTH_STAGE_OBSERVED
                && o.isGrowthStageTransition()
                && isHerbPatch(o.getPatchId()))
        {
            // IMPORTANT: Do not learn from stage 0 -> 1 transitions.
            // Planting is player-driven and can happen at any time, while natural growth stages
            // (1 -> 2 -> 3 -> 4 -> 5) only advance on the fixed 20-minute farming tick.
            final Integer stage = o.getGrowthStageOrNull();
            if (stage != null && stage >= 2)
            {
                int before = herbTickAligner.getOffsetMinutes().orElse(-1);
                herbTickAligner.observeTick(at);
                int after = herbTickAligner.getOffsetMinutes().orElse(-1);
                if (before != after)
                {
                    // This should stabilise quickly (per-account constant offset).
                    log.debug("[infer] learned herb tick offset={}min (interval=20) from stage transition at {}", after, at);
                }
            }
        }

        switch (o.getKind())
        {
            case PATCH_STATE_SET:
                PatchState ps = o.getPatchStateOrNull();
                if (ps == null)
                {
                    return;
                }

                // If the user/debug feed asserts a non-failure stage, treat it as a new-cycle boundary
                // so prior DEAD/DISEASED anchors can't "snap back" after the manual lock expires.
                if (ps == PatchState.EMPTY || ps == PatchState.GROWING || ps == PatchState.READY)
                {
                    clearCycleFailureState(s);
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
                // New cycle boundary: death/disease and harvest depletion must not persist.
                clearCycleFailureState(s);
                s.plantedAt = at;
                s.harvestStage = null;
                s.harvestStageObservedAt = null;
                s.cropItemId = null;
                s.cropName = null;
                s.cropObservedAt = null;
                s.maxGrowthStage = null;
                s.growthStageDuration = null;
                break;

            case HARVESTED:
                // Clearing to empty is a cycle boundary.
                clearCycleFailureState(s);
                s.harvestedAt = at;
                s.harvestStage = null;
                s.harvestStageObservedAt = null;
                s.cropItemId = null;
                s.cropName = null;
                s.cropObservedAt = null;
                s.maxGrowthStage = null;
                s.growthStageDuration = null;
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

            case GROWTH_STAGE_OBSERVED:
                Integer gs = o.getGrowthStageOrNull();
                if (gs != null)
                {
                    if (gs >= 1)
                    {
                        // Seeing a concrete growth stage implies the patch is not dead/diseased from a prior cycle.
                        clearCycleFailureState(s);
                    }
                    s.growthStage = gs;
                    Integer maxGs = o.getMaxGrowthStageOrNull();
                    if (maxGs != null && maxGs > 0)
                    {
                        s.maxGrowthStage = maxGs;
                    }

                    Duration dur = o.getGrowthStageDurationOrNull();
                    if (dur != null && !dur.isZero() && !dur.isNegative())
                    {
                        s.growthStageDuration = dur;
                    }
                    s.growthStageObservedAt = at;
                    s.growthStageTransition = o.isGrowthStageTransition();

                    Integer resolvedMax = resolveMaxGrowthStageOrNull(o.getPatchId(), s);
                    int maxStage = resolvedMax != null ? resolvedMax : 5;
                    if (gs < maxStage)
                    {
                        s.harvestStage = null;
                        s.harvestStageObservedAt = null;
                    }

                    // Seeing a concrete growth stage means the patch is not empty.
                    // Clear any prior HARVESTED/empty anchor (which may come from a baseline read).
                    if (gs >= 1)
                    {
                        s.harvestedAt = null;
                    }
                }
                break;


            case HARVEST_STAGE_OBSERVED:
                Integer hs = o.getHarvestStageOrNull();
                if (hs != null)
                {
                    s.harvestStage = hs;
                    s.harvestStageObservedAt = at;
                }
                break;

            case CROP_OBSERVED:
                Integer itemId = o.getCropItemIdOrNull();
                if (itemId != null)
                {
                    String name = o.getCropNameOrNull();
                    boolean changed = !itemId.equals(s.cropItemId)
                            || (name == null ? s.cropName != null : !name.equals(s.cropName));
                    s.cropItemId = itemId;
                    s.cropName = name;
                    s.cropObservedAt = at;
                    if (changed)
                    {
                        // Crop identity is part of the UI-visible output, so bump the counter
                        // even if stage inference did not change.
                        changeCounter++;
                    }
                }
                break;


            default:
                break;
        }
    }

    private static boolean isHerbPatch(PatchId patchId)
    {
        return patchId != null && patchId.name().startsWith("HERB_");
    }

    private boolean isReadyStage(PatchId patchId, PatchAnchorState s)
    {
        if (s == null || s.growthStage == null)
        {
            return false;
        }

        Integer max = resolveMaxGrowthStageOrNull(patchId, s);
        if (max == null)
        {
            return false;
        }

        return s.growthStage >= max;
    }

    /**
     * New-cycle boundary helper.
     *
     * <p>Death/disease and harvest depletion are cycle-scoped and must not survive a clear+replant
     * within the same client session.</p>
     */
    private static void clearCycleFailureState(PatchAnchorState s)
    {
        if (s == null)
        {
            return;
        }

        s.diseasedAt = null;
        s.deadAt = null;
        s.harvestStage = null;
        s.harvestStageObservedAt = null;
    }

    private Optional<ReadyWindowInstants> computeTickSnappedHerbReadyWindowFromStage(
            PatchId patchId,
            PatchAnchorState s,
            Instant now)
    {
        if (!isHerbPatch(patchId) || !herbTickAligner.hasOffset())
        {
            return Optional.empty();
        }

        if (s.growthStage == null || s.growthStageObservedAt == null)
        {
            return Optional.empty();
        }

        int stage = s.growthStage;

        Integer maxStageBoxed = resolveMaxGrowthStageOrNull(patchId, s);
        if (maxStageBoxed == null)
        {
            return Optional.empty();
        }

        int maxStage = maxStageBoxed;

        // Herb decoder stages: 1..(maxStage-1) growing, maxStage ready.
        if (stage < 1 || stage > maxStage)
        {
            return Optional.empty();
        }

        if (stage >= maxStage)
        {
            // Ready is a terminal observation. Do not use "now" here, otherwise the window
            // will drift forward every tick and spam inference updates. Anchor to the moment
            // we observed the patch in a ready stage.
            Instant readyAt = s.growthStageObservedAt;
            return Optional.of(new ReadyWindowInstants(readyAt, readyAt));
        }

        // Once the offset is known, stage progression is deterministic and tick-snappable.
        // There is no need for a (20m-wide) uncertainty window.
        Optional<Duration> stepOpt = durationModel.getGrowthStageDuration(patchId);
        Duration cycle = stepOpt.orElse(Duration.ofMinutes(20));

        int ticksToReady = maxStage - stage; // stage 1 => 4, stage (max-1) => 1
        if (ticksToReady <= 0)
        {
            return Optional.empty();
        }

        Instant nextTick = herbTickAligner.nextTickAfter(s.growthStageObservedAt);
        Instant readyAt = nextTick.plus(cycle.multipliedBy(Math.max(0, ticksToReady - 1L)));
        return Optional.of(new ReadyWindowInstants(readyAt, readyAt));
    }

    private static final class ReadyWindowInstants
    {
        private final Instant earliest;
        private final Instant latest;

        private ReadyWindowInstants(Instant earliest, Instant latest)
        {
            this.earliest = earliest;
            this.latest = latest;
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
            changeCounter++;
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

        // Harvest implies the patch is now empty.
        // We do not require a prior PLANTED anchor because we may observe only the tail end
        // of a cycle (e.g. logging in beside a grown patch and harvesting it).
        if (s.harvestedAt != null
                && isActiveAfterClears(s.harvestedAt, s.clearedAt, null)
                && (s.plantedAt == null || s.harvestedAt.isAfter(s.plantedAt)))
        {
            reasons.add(ReasonCode.DERIVED_FROM_EVENTS);
            return new PatchInference(patchId, InferredStage.EMPTY, lastObservedAt, null, null, 0.9f, reasons);
        }

        if (s.plantedAt != null)
        {
            Optional<ReadyWindow> windowOpt = computeReadyWindowFromPlanted(patchId, s);
            if (!windowOpt.isPresent())
            {
                reasons.add(ReasonCode.DERIVED_FROM_EVENTS);
                reasons.add(ReasonCode.NO_SCHEDULE);
                return new PatchInference(patchId, InferredStage.GROWING, lastObservedAt, null, null, 0.9f, reasons);
            }

            ReadyWindow w = windowOpt.get();
            Instant earliest = s.plantedAt.plus(w.getMin());
            Instant latest = s.plantedAt.plus(w.getMax());

            // If we have a concrete stage observation, intersect its implied window.
            if (s.growthStage != null && s.growthStageObservedAt != null)
            {
                Optional<ReadyWindow> stageWindowOpt = computeReadyWindowFromStage(patchId, s);
                if (stageWindowOpt.isPresent())
                {
                    Instant stageEarliest;
                    Instant stageLatest;

                    // Prefer tick-snapped projections for herbs once we have learned the offset.
                    Optional<ReadyWindowInstants> snapped = computeTickSnappedHerbReadyWindowFromStage(patchId, s, now);
                    if (snapped.isPresent())
                    {
                        stageEarliest = snapped.get().earliest;
                        stageLatest = snapped.get().latest;
                    }
                    else
                    {
                        ReadyWindow sw = stageWindowOpt.get();
                        stageEarliest = s.growthStageObservedAt.plus(sw.getMin());
                        stageLatest = s.growthStageObservedAt.plus(sw.getMax());
                    }

                    Instant intersectEarliest = maxInstant(earliest, stageEarliest);
                    Instant intersectLatest = minInstant(latest, stageLatest);

                    if (!intersectEarliest.isAfter(intersectLatest))
                    {
                        earliest = intersectEarliest;
                        latest = intersectLatest;
                        reasons.add(ReasonCode.DERIVED_FROM_STAGE);
                    }
                    else
                    {
                        // The stage observation is a direct read from the game state.
                        // If it conflicts with the planted-at-derived window, prefer the stage window
                        // (this avoids false "planted" anchors from region-load placeholders).
                        earliest = stageEarliest;
                        latest = stageLatest;
                        reasons.add(ReasonCode.CONFLICT_WITH_OBSERVATION);
                        reasons.add(ReasonCode.DERIVED_FROM_STAGE);
                    }
                }
            }

            reasons.add(ReasonCode.DERIVED_FROM_TIME);

            // If time-driven stage estimation reaches the max stage, treat the patch as READY.
            // This avoids invalid combinations like GROWING with a fully-complete (e.g. 5/5) progress hint.
            if (s.growthStage != null && s.growthStageObservedAt != null)
            {
                Integer maxStage = resolveMaxGrowthStageOrNull(patchId, s);
                if (maxStage != null)
                {
                    int estStage = estimateGrowthStageStepwise(patchId, s, now, maxStage);
                    if (estStage >= maxStage)
                    {
                        reasons.add(ReasonCode.DERIVED_FROM_TIME);
                        return new PatchInference(patchId, InferredStage.READY, lastObservedAt, earliest, latest, 0.6f, reasons);
                    }
                }
            }

            // A ready-stage observation is stronger than any time window.
            if (isReadyStage(patchId, s))
            {
                reasons.add(ReasonCode.DERIVED_FROM_STAGE);
                return new PatchInference(patchId, InferredStage.READY, lastObservedAt, earliest, latest, 0.8f, reasons);
            }

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

        // Stage-only inference: use when we can see a concrete stage but never observed planting.
        if (s.growthStage != null && s.growthStageObservedAt != null)
        {
            Optional<ReadyWindow> stageWindowOpt = computeReadyWindowFromStage(patchId, s);
            if (!stageWindowOpt.isPresent())
            {
                reasons.add(ReasonCode.DERIVED_FROM_EVENTS);
                reasons.add(ReasonCode.NO_SCHEDULE);
                return new PatchInference(patchId, InferredStage.GROWING, lastObservedAt, null, null, 0.9f, reasons);
            }

            Instant earliest;
            Instant latest;

            Optional<ReadyWindowInstants> snapped = computeTickSnappedHerbReadyWindowFromStage(patchId, s, now);
            if (snapped.isPresent())
            {
                earliest = snapped.get().earliest;
                latest = snapped.get().latest;
            }
            else
            {
                ReadyWindow sw = stageWindowOpt.get();
                earliest = s.growthStageObservedAt.plus(sw.getMin());
                latest = s.growthStageObservedAt.plus(sw.getMax());
            }

            reasons.add(ReasonCode.DERIVED_FROM_STAGE);

            // Promote to READY when time-driven stage estimation reaches max stage.
            Integer maxStage = resolveMaxGrowthStageOrNull(patchId, s);
            if (maxStage != null)
            {
                int estStage = estimateGrowthStageStepwise(patchId, s, now, maxStage);
                if (estStage >= maxStage)
                {
                    return new PatchInference(patchId, InferredStage.READY, lastObservedAt, earliest, latest, 0.6f, reasons);
                }
            }

            if (isReadyStage(patchId, s))
            {
                return new PatchInference(patchId, InferredStage.READY, lastObservedAt, earliest, latest, 0.8f, reasons);
            }

            if (now.isBefore(earliest))
            {
                return new PatchInference(patchId, InferredStage.GROWING, lastObservedAt, earliest, latest, 0.6f, reasons);
            }

            if (!now.isBefore(latest))
            {
                return new PatchInference(patchId, InferredStage.READY, lastObservedAt, earliest, latest, 0.6f, reasons);
            }

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

    private static Instant maxInstant(Instant a, Instant b)
    {
        if (a == null)
        {
            return b;
        }
        if (b == null)
        {
            return a;
        }
        return a.isAfter(b) ? a : b;
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
