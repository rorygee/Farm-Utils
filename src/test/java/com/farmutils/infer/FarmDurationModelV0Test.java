package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.time.Duration;
import org.junit.Test;

import static org.junit.Assert.*;

public class FarmDurationModelV0Test
{
    @Test
    public void herbCatherbyHasBoundedWindow()
    {
        FarmDurationModelV0 model = new FarmDurationModelV0();
        ReadyWindow w = model.getReadyWindow(PatchId.HERB_CATHERBY).orElseThrow(AssertionError::new);
        assertEquals(Duration.ofMinutes(60), w.getMin());
        assertEquals(Duration.ofMinutes(80), w.getMax());
    }
}
