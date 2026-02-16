package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Test-only, deterministic schedule model. */
public final class SimpleStageScheduleModel implements PatchDurationModel
{
    private final Map<PatchId, StageSchedule> schedules;

    public SimpleStageScheduleModel(Map<PatchId, StageSchedule> schedules)
    {
        this.schedules = Collections.unmodifiableMap(new java.util.HashMap<>(Objects.requireNonNull(schedules, "schedules")));
    }

    @Override
    public Optional<Duration> durationToComplete(PatchId patchId)
    {
        // v0 callers: treat as total duration.
        return getTotalGrowthDuration(patchId);
    }

    @Override
    public Optional<StageSchedule> getStageSchedule(PatchId patchId)
    {
        return Optional.ofNullable(schedules.get(patchId));
    }

    @Override
    public Optional<Duration> getTotalGrowthDuration(PatchId patchId)
    {
        StageSchedule schedule = schedules.get(patchId);
        return schedule == null ? Optional.empty() : Optional.of(schedule.getTotalDuration());
    }
}
