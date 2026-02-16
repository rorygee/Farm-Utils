package com.farmutils.infer;

import java.time.Instant;
import java.util.Objects;

/** Test clock. */
public class FixedPatchClock implements PatchClock
{
    private Instant now;

    public FixedPatchClock(Instant now)
    {
        this.now = Objects.requireNonNull(now, "now");
    }

    public void setNow(Instant now)
    {
        this.now = Objects.requireNonNull(now, "now");
    }

    @Override
    public Instant now()
    {
        return now;
    }
}
