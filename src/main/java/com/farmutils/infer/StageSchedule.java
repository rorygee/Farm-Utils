package com.farmutils.infer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal, deterministic, multi-stage schedule anchored at {@code lastObservedAt}.
 *
 * <p>The schedule is an ordered list of stages with durations between each stage transition.
 * The first entry must have a zero duration (meaning it applies at elapsed=0).</p>
 */
public final class StageSchedule
{
    public static final class Step
    {
        private final InferredStage stage;
        private final Duration after;

        /**
         * @param stage stage for this step
         * @param after duration after the previous step at which this stage becomes active
         */
        public Step(InferredStage stage, Duration after)
        {
            this.stage = Objects.requireNonNull(stage, "stage");
            this.after = Objects.requireNonNull(after, "after");
        }

        public InferredStage getStage()
        {
            return stage;
        }

        public Duration getAfter()
        {
            return after;
        }
    }

    private final List<Step> steps;
    private final List<Duration> cumulative;
    private final Duration totalDuration;

    public StageSchedule(List<Step> steps)
    {
        Objects.requireNonNull(steps, "steps");
        if (steps.isEmpty())
        {
            throw new IllegalArgumentException("steps must not be empty");
        }

        List<Step> copy = new ArrayList<>(steps.size());
        for (Step s : steps)
        {
            copy.add(Objects.requireNonNull(s, "step"));
        }

        if (!Duration.ZERO.equals(copy.get(0).getAfter()))
        {
            throw new IllegalArgumentException("first step must have after=Duration.ZERO");
        }

        List<Duration> cum = new ArrayList<>(copy.size());
        Duration running = Duration.ZERO;
        for (int i = 0; i < copy.size(); i++)
        {
            Duration d = copy.get(i).getAfter();
            if (d.isNegative())
            {
                throw new IllegalArgumentException("step durations must be non-negative");
            }
            if (i == 0)
            {
                running = Duration.ZERO;
            }
            else
            {
                running = running.plus(d);
            }
            cum.add(running);
        }

        this.steps = Collections.unmodifiableList(copy);
        this.cumulative = Collections.unmodifiableList(cum);
        this.totalDuration = cumulative.get(cumulative.size() - 1);
    }

    public List<Step> getSteps()
    {
        return steps;
    }

    public Duration getTotalDuration()
    {
        return totalDuration;
    }

    /**
     * Returns the stage implied by the given elapsed duration.
     */
    public InferredStage stageAt(Duration elapsed)
    {
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative())
        {
            elapsed = Duration.ZERO;
        }

        int idx = 0;
        for (int i = 0; i < cumulative.size(); i++)
        {
            if (!elapsed.minus(cumulative.get(i)).isNegative())
            {
                idx = i;
            }
            else
            {
                break;
            }
        }

        return steps.get(idx).getStage();
    }

    /**
     * Returns the completion time for the schedule (end of the final step), anchored at {@code anchor}.
     */
    public Optional<Instant> completionAt(Instant anchor)
    {
        if (anchor == null)
        {
            return Optional.empty();
        }
        return Optional.of(anchor.plus(totalDuration));
    }
}
