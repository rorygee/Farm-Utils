package com.farmutils.infer;

import java.time.Instant;

public class SystemPatchClock implements PatchClock
{
    @Override
    public Instant now()
    {
        return Instant.now();
    }
}
