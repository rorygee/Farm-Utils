package com.farmutils.observe;

import com.farmutils.infer.InferenceEngine;
import com.farmutils.infer.Observation;
import com.farmutils.infer.ObservationSource;
import com.farmutils.model.PatchId;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/** Quest patch observer for Unferth's patch (varbit 1033). */
@Slf4j
@Singleton
public class Varbit1033UnferthsPatchVarbitObserver
{
    static final int VARBIT_ID = 1033;

    // Gate to Burthorpe footprint from the user-provided region overlay.
    private static final int REGION_ID = 11575;
    private static final int PLANE = 0;
    private static final WorldPoint ANCHOR = new WorldPoint(2918, 3564, 0);
    private static final int MAX_DIST = 20;

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;

    @Inject
    public Varbit1033UnferthsPatchVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
    {
        this.client = client;
        this.inferenceEngine = inferenceEngine;
    }

    public void reset()
    {
        lastRaw = null;
        lastState = null;
    }

    public void onGameTick()
    {
        if (!isInScope())
        {
            return;
        }

        final int raw = client.getVarbitValue(VARBIT_ID);
        if (lastRaw != null && raw == lastRaw)
        {
            return;
        }

        handleVarbitValue(PatchId.QUEST_UNFERTHS_PATCH, raw);
    }

    private void handleVarbitValue(final PatchId patch, final int raw)
    {
        final Instant now = Instant.now();
        final DecodedPatchState cur = QuestUnferthSlotDecoder.decode(raw);

        if (lastRaw == null)
        {
            lastRaw = raw;
            lastState = cur;
            log.debug("[varbit] {} v{} initial raw={} empty={} stage={} maxStage={} health={} crop={}",
                patch, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), cur.getMaxGrowthStageOrZero(), cur.getHealth(), cur.getCropNameOrNull());

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

        // Planting: fully raked -> planted.
        if (prev != null && prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.planted(patch, now, ObservationSource.VARBIT));
        }

        // Clear/dig up: crop -> empty.
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
        Integer cropItemId = cur.getCropItemIdOrNull();
        String cropName = cur.getCropNameOrNull();
        if (!cur.isEmpty() && cropItemId != null)
        {
            inferenceEngine.onObservation(Observation.cropObserved(patch, cropItemId, cropName, now, ObservationSource.VARBIT));
        }

        if (!cur.isEmpty() && cur.getHealth() == PatchHealth.HEALTHY && cur.getStage() >= 1)
        {
            Integer maxStageOrNull = cur.getMaxGrowthStageOrZero() > 0 ? cur.getMaxGrowthStageOrZero() : null;
            if (stageTickTransition)
            {
                inferenceEngine.onObservation(Observation.growthStageTransition(patch, cur.getStage(), maxStageOrNull, now, ObservationSource.VARBIT));
            }
            else
            {
                inferenceEngine.onObservation(Observation.growthStageObserved(patch, cur.getStage(), maxStageOrNull, now, ObservationSource.VARBIT));
            }
        }
    }

    private boolean isInScope()
    {
        final Player p = client.getLocalPlayer();
        if (p == null || p.getWorldLocation() == null)
        {
            return false;
        }
        WorldPoint w = p.getWorldLocation();
        if (w.getPlane() != PLANE)
        {
            return false;
        }
        if (w.getRegionID() != REGION_ID)
        {
            return false;
        }
        return w.distanceTo2D(ANCHOR) <= MAX_DIST;
    }
}
