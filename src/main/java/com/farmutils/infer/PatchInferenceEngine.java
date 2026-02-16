package com.farmutils.infer;

import com.farmutils.model.PatchId;
import java.util.Map;

public interface PatchInferenceEngine
{
    PatchInference get(PatchId patchId);

    Map<PatchId, PatchInference> getAll();

    void onObservation(Observation observation);
}
