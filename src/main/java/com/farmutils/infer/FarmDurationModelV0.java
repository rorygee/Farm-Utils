package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Very small, conservative duration model for v0.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Never pretend to know more than we do.</li>
 *   <li>Provide useful min/max windows when we have an anchor.</li>
 *   <li>Stay easy to replace when better per-crop / per-patch data arrives.</li>
 * </ul>
 */
public class FarmDurationModelV0 implements PatchDurationModel
{
    private static final Duration HERB_CYCLE = Duration.ofMinutes(20);
    private static final Duration FLOWER_CYCLE = Duration.ofMinutes(5);
    private static final Duration ALLOTMENT_CYCLE = Duration.ofMinutes(10);

    // Herbs: conservatively 5 cycles (100 minutes) from planting, where the first natural growth
    // tick occurs on the NEXT cadence boundary (up to +20m after planting).
    // That yields a durable window:
    //   min = 4 cycles (80m)
    //   max = 5 cycles (100m)
    // Tick-snapping (when available) is handled higher up.
    private static final ReadyWindow HERB_WINDOW = new ReadyWindow(Duration.ofMinutes(80), Duration.ofMinutes(100));

    // Flowers: same conservative shape as herbs.
    // Cycle cadence is 5 minutes.
    private static final ReadyWindow FLOWER_WINDOW = new ReadyWindow(Duration.ofMinutes(20), Duration.ofMinutes(25));

    private static boolean isHerbPatch(PatchId patchId)
    {
        return patchId != null && patchId.name().startsWith("HERB_");
    }

    private static boolean isFlowerPatch(PatchId patchId)
    {
        return patchId != null && patchId.name().startsWith("FLOWER_");
    }

    private static boolean isAllotmentPatch(PatchId patchId)
    {
        return patchId != null && patchId.name().startsWith("ALLOTMENT_");
    }

    @Override
    public Optional<Duration> durationToComplete(PatchId patchId)
    {
        return Optional.empty();
    }

    @Override
    public OptionalInt getMaxGrowthStage(PatchId patchId)
    {
        if (isHerbPatch(patchId))
        {
            // Herb decoder stages: 1..4 growing, 5 ready.
            return OptionalInt.of(5);
        }
        if (isFlowerPatch(patchId))
        {
            // Flower decoder stages: 1..4 growing, 5 ready.
            return OptionalInt.of(5);
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<Duration> getGrowthStageDuration(PatchId patchId)
    {
        if (isHerbPatch(patchId))
        {
            return Optional.of(HERB_CYCLE);
        }
        if (isFlowerPatch(patchId))
        {
            return Optional.of(FLOWER_CYCLE);
        }
        if (isAllotmentPatch(patchId))
        {
            // Allotments advance on the standard 10-minute farming tick.
            return Optional.of(ALLOTMENT_CYCLE);
        }
        return Optional.empty();
    }

    @Override
    public OptionalInt getMaxHarvestStage(PatchId patchId)
    {
        if (isHerbPatch(patchId))
        {
            // Standard herb patch transform table has 3 harvestable variants (full -> fewer picks).
            return OptionalInt.of(3);
        }
        return OptionalInt.empty();
    }

    @Override
    public Optional<StageSchedule> getStageSchedule(PatchId patchId)
    {
        return Optional.empty();
    }

    @Override
    public Optional<ReadyWindow> getReadyWindow(PatchId patchId)
    {
        if (isHerbPatch(patchId))
        {
            return Optional.of(HERB_WINDOW);
        }
        if (isFlowerPatch(patchId))
        {
            return Optional.of(FLOWER_WINDOW);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ReadyWindow> getReadyWindowFromStage(PatchId patchId, int observedGrowthStage)
    {
        return getReadyWindowFromStage(patchId, observedGrowthStage, false);
    }

    @Override
    public Optional<ReadyWindow> getReadyWindowFromStage(PatchId patchId, int observedGrowthStage, boolean stageTransition)
    {

        if (isHerbPatch(patchId))
        {
            return readyWindowFromStage(observedGrowthStage, stageTransition, HERB_CYCLE);
        }

        if (isFlowerPatch(patchId))
        {
            return readyWindowFromStage(observedGrowthStage, stageTransition, FLOWER_CYCLE);
        }

        return Optional.empty();
    }

    private static Optional<ReadyWindow> readyWindowFromStage(int observedGrowthStage, boolean stageTransition, Duration cycle)
    {
        // Decoder stages: 1..4 growing, 5 ready.
        if (observedGrowthStage < 1 || observedGrowthStage > 5)
        {
            return Optional.empty();
        }

        if (observedGrowthStage >= 5)
        {
            return Optional.of(new ReadyWindow(Duration.ZERO, Duration.ZERO));
        }

        // If this is a baseline read (not a transition), conservatively include the current cycle,
        // since the next growth tick is not guaranteed to be "soon" after we first see a stage.
        int remainingCycles = stageTransition
            ? (5 - observedGrowthStage)         // stage 2 at transition => 3
            : (5 - observedGrowthStage + 1);    // stage 1 baseline => 5

        Duration max = cycle.multipliedBy(remainingCycles);
        Duration min;

        if (stageTransition)
        {
            min = max;
        }
        else
        {
            min = max.minus(cycle);
        }

        if (min.isNegative())
        {
            min = Duration.ZERO;
        }

        return Optional.of(new ReadyWindow(min, max));
    }
}