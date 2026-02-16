package com.farmutils.infer;

import java.time.Duration;
import java.util.Objects;

/** Bounded readiness window represented as min/max total growth duration since planting. */
public final class ReadyWindow
{
    private final Duration min;
    private final Duration max;

    public ReadyWindow(Duration min, Duration max)
    {
        this.min = Objects.requireNonNull(min, "min");
        this.max = Objects.requireNonNull(max, "max");
        if (min.isNegative() || max.isNegative())
        {
            throw new IllegalArgumentException("ReadyWindow durations must be non-negative");
        }
        if (max.minus(min).isNegative())
        {
            throw new IllegalArgumentException("ReadyWindow max must be >= min");
        }
    }

    public Duration getMin()
    {
        return min;
    }

    public Duration getMax()
    {
        return max;
    }
}
