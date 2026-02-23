package com.farmutils.infer;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.OptionalInt;

/**
 * Aligns instants to a repeating, server-driven growth tick cadence.
 *
 * <p>OSRS farming growth only advances on discrete "growth ticks". For many crop families
 * (including herbs), those ticks are on a fixed interval with a per-account minute offset.
 *
 * <p>This helper learns that minute offset from observed stage transitions, and then provides
 * floor/ceil operations to snap estimates to the next plausible tick boundary.
 *
 * <p>Intentionally small and headless (no RuneLite types).
 */
public final class GrowthTickAligner
{
    private final long intervalMinutes;
    private Integer offsetMinutes; // 0..interval-1, null until learned

    public GrowthTickAligner(Duration interval)
    {
        long minutes = interval.toMinutes();
        if (minutes <= 0)
        {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.intervalMinutes = minutes;
    }

    public boolean hasOffset()
    {
        return offsetMinutes != null;
    }

    public OptionalInt getOffsetMinutes()
    {
        return offsetMinutes == null ? OptionalInt.empty() : OptionalInt.of(offsetMinutes);
    }

    /**
     * Learns the (minute-of-hour % interval) offset from an observed stage transition.
     */
    public void observeTick(Instant observedAt)
    {
        // We intentionally use UTC. OSRS farming ticks are server-driven, and UTC is the
        // most stable reference for cross-machine correctness.
        int minuteOfHour = ZonedDateTime.ofInstant(observedAt, ZoneOffset.UTC).getMinute();
        int off = Math.floorMod(minuteOfHour, (int) intervalMinutes);
        offsetMinutes = off;
    }

    /**
     * Returns the most recent tick boundary at or before {@code t}.
     *
     * <p>If the offset is unknown, returns {@code t} unchanged.
     */
    public Instant floorToTick(Instant t)
    {
        if (offsetMinutes == null)
        {
            return t;
        }

        long minutesSinceEpoch = Math.floorDiv(t.getEpochSecond(), 60L);
        long adjusted = minutesSinceEpoch - offsetMinutes;
        long remainder = Math.floorMod(adjusted, intervalMinutes);
        long flooredMinutes = minutesSinceEpoch - remainder;

        return Instant.ofEpochSecond(flooredMinutes * 60L);
    }

    /**
     * Returns the first tick boundary strictly after {@code t}.
     *
     * <p>If the offset is unknown, returns {@code t} unchanged.
     */
    public Instant nextTickAfter(Instant t)
    {
        if (offsetMinutes == null)
        {
            return t;
        }
        Instant floor = floorToTick(t);
        return floor.plus(Duration.ofMinutes(intervalMinutes));
    }

    /**
     * Returns the first tick boundary at or after {@code t}.
     *
     * <p>If the offset is unknown, returns {@code t} unchanged.
     */
    public Instant ceilToTickInclusive(Instant t)
    {
        if (offsetMinutes == null)
        {
            return t;
        }

        Instant floor = floorToTick(t);
        if (floor.equals(t))
        {
            return t;
        }
        return floor.plus(Duration.ofMinutes(intervalMinutes));
    }
}
