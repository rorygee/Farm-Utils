package com.farmutils.infer;

import java.time.Duration;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.*;

public class StageScheduleTest
{
    @Test
    public void stageAtProgressesOverTime()
    {
        StageSchedule schedule = new StageSchedule(Arrays.asList(
                new StageSchedule.Step(InferredStage.EMPTY, Duration.ZERO),
                new StageSchedule.Step(InferredStage.GROWING, Duration.ofMinutes(5)),
                new StageSchedule.Step(InferredStage.READY, Duration.ofMinutes(5))
        ));

        assertEquals(InferredStage.EMPTY, schedule.stageAt(Duration.ZERO));
        assertEquals(InferredStage.EMPTY, schedule.stageAt(Duration.ofMinutes(4).plusSeconds(59)));
        assertEquals(InferredStage.GROWING, schedule.stageAt(Duration.ofMinutes(5)));
        assertEquals(InferredStage.GROWING, schedule.stageAt(Duration.ofMinutes(9).plusSeconds(59)));
        assertEquals(InferredStage.READY, schedule.stageAt(Duration.ofMinutes(10)));
        assertEquals(InferredStage.READY, schedule.stageAt(Duration.ofMinutes(999)));
    }
}
