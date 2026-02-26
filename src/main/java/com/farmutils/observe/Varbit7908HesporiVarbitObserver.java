package com.farmutils.observe;

import com.farmutils.infer.InferenceEngine;
import com.farmutils.infer.Observation;
import com.farmutils.infer.ObservationSource;
import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;

/** Hespori patch observer for FARMING_TRANSMIT_J (varbit 7908) in the Hespori instance. */
@Slf4j
@Singleton
public class Varbit7908HesporiVarbitObserver
{
    static final int VARBIT_ID = 7908;
    private static final Duration STAGE_CYCLE = Duration.ofMinutes(640);
    private static final int MAX_STAGE = 4;

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;
    private PatchId lastAttributedPatch;

    @Inject
    public Varbit7908HesporiVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
    {
        this.client = client;
        this.inferenceEngine = inferenceEngine;
    }

    public void reset()
    {
        lastRaw = null;
        lastState = null;
        lastAttributedPatch = null;
    }

    public void onGameTick()
    {
        final PatchId patch = resolveAttributedPatch();
        if (patch == null)
        {
            lastAttributedPatch = null;
            return;
        }

        if (lastAttributedPatch != patch)
        {
            lastAttributedPatch = patch;
            lastRaw = null;
            lastState = null;
        }

        final int raw = client.getVarbitValue(VARBIT_ID);
        if (lastRaw != null && raw == lastRaw)
        {
            return;
        }

        handleVarbitValue(patch, raw);
    }

    private void handleVarbitValue(final PatchId patch, final int raw)
    {
        final Instant now = Instant.now();
        final DecodedPatchState cur = StandardHesporiSlotDecoder.decode(raw);

        if (lastRaw == null)
        {
            lastRaw = raw;
            lastState = cur;
            log.debug("[varbit] {} v{} initial raw={} empty={} stage={} maxStage={} crop={}",
                patch, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), MAX_STAGE, cur.getCropNameOrNull());

            if (cur.isEmpty())
            {
                inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
            }

            emitObservedStateIfUseful(patch, now, cur, false);
            return;
        }

        final DecodedPatchState prev = lastState;
        final int prevRaw = lastRaw;
        lastRaw = raw;
        lastState = cur;

        if (prev != null && prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1)
        {
            inferenceEngine.onObservation(Observation.planted(patch, now, ObservationSource.VARBIT));
        }

        if (prev != null && !prev.isEmpty() && cur.isEmpty())
        {
            inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
        }

        final boolean stageTickTransition = isNaturalGrowthTickTransition(prev, cur);
        emitObservedStateIfUseful(patch, now, cur, stageTickTransition);
    }

    private static boolean isNaturalGrowthTickTransition(final DecodedPatchState prev, final DecodedPatchState cur)
    {
        if (prev == null || cur == null)
        {
            return false;
        }
        if (prev.isEmpty() || cur.isEmpty())
        {
            return false;
        }
        if (prev.getHealth() != PatchHealth.HEALTHY || cur.getHealth() != PatchHealth.HEALTHY)
        {
            return false;
        }
        int delta = cur.getStage() - prev.getStage();
        return delta == 1 && cur.getStage() >= 2;
    }

    private void emitObservedStateIfUseful(final PatchId patch, final Instant now, final DecodedPatchState cur, final boolean stageTickTransition)
    {
        if (!cur.isEmpty() && cur.getHealth() == PatchHealth.HEALTHY && cur.getStage() >= 1)
        {
            if (stageTickTransition)
            {
                inferenceEngine.onObservation(Observation.growthStageTransition(patch, cur.getStage(), MAX_STAGE, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }
            else
            {
                inferenceEngine.onObservation(Observation.growthStageObserved(patch, cur.getStage(), MAX_STAGE, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }

            Integer cropItemId = cur.getCropItemIdOrNull();
            String cropName = cur.getCropNameOrNull();
            if (cropItemId != null && cropName != null)
            {
                inferenceEngine.onObservation(Observation.cropObserved(patch, cropItemId, cropName, now, ObservationSource.VARBIT));
            }

            if (cur.getStage() == MAX_STAGE)
            {
                int harvestStage = StandardHesporiSlotDecoder.getHarvestStageOrZero(cur.getRaw());
                if (harvestStage > 0)
                {
                    inferenceEngine.onObservation(Observation.harvestStageObserved(patch, harvestStage, now, ObservationSource.VARBIT));
                }
            }
        }
    }

    private PatchId resolveAttributedPatch()
    {
        final Player p = client.getLocalPlayer();
        if (p == null || p.getWorldLocation() == null)
        {
            return null;
        }
        return REGION_ID_TO_PATCH.get(p.getWorldLocation().getRegionID());
    }

    private static final Map<Integer, PatchId> REGION_ID_TO_PATCH = new HashMap<>();

    static
    {
        // Farming guild Hespori instance region (RuneLite Time Tracking)
        REGION_ID_TO_PATCH.put(5021, PatchId.SPECIAL_HESPORI);
    }
}
