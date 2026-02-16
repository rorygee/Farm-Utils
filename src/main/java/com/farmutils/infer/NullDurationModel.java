package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.util.Optional;

/** v0 placeholder duration model: always empty. */
public class NullDurationModel implements PatchDurationModel
{
    @Override
    public Optional<Duration> durationToComplete(PatchId patchId)
    {
        return Optional.empty();
    }
}
