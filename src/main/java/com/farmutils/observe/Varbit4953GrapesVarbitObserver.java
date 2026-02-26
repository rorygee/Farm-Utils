package com.farmutils.observe;

import com.farmutils.infer.InferenceEngine;
import com.farmutils.infer.Observation;
import com.farmutils.infer.ObservationSource;
import com.farmutils.model.PatchId;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;

/**
 * Hosidius Vinery grape patch observer.
 *
 * <p>Time Tracking exposes these as dedicated varbits (A1..F2), so no proximity attribution
 * is required; we only gate updates to the vinery region for consistency with other observers.</p>
 */
@Slf4j
@Singleton
public class Varbit4953GrapesVarbitObserver
{
    private static final int VINERY_REGION_ID = 7223;

    private static final Duration STAGE_CYCLE = Duration.ofMinutes(5);

    // Time Tracking VarbitIDs: FARMING_TRANSMIT_A1..F2.
    static final int[] VARBIT_IDS = new int[]
        {
            4953, 4954, 4955, 4956, 4957, 4958,
            4959, 4960, 4961, 4962, 4963, 4964
        };

    static final PatchId[] PATCH_IDS = new PatchId[]
        {
            PatchId.SPECIAL_GRAPES_PLOT_1,
            PatchId.SPECIAL_GRAPES_PLOT_2,
            PatchId.SPECIAL_GRAPES_PLOT_3,
            PatchId.SPECIAL_GRAPES_PLOT_4,
            PatchId.SPECIAL_GRAPES_PLOT_5,
            PatchId.SPECIAL_GRAPES_PLOT_6,
            PatchId.SPECIAL_GRAPES_PLOT_7,
            PatchId.SPECIAL_GRAPES_PLOT_8,
            PatchId.SPECIAL_GRAPES_PLOT_9,
            PatchId.SPECIAL_GRAPES_PLOT_10,
            PatchId.SPECIAL_GRAPES_PLOT_11,
            PatchId.SPECIAL_GRAPES_PLOT_12
        };

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private final Integer[] lastRaw = new Integer[VARBIT_IDS.length];
    private final DecodedPatchState[] lastState = new DecodedPatchState[VARBIT_IDS.length];

    @Inject
    public Varbit4953GrapesVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
    {
        this.client = client;
        this.inferenceEngine = inferenceEngine;
    }

    public void reset()
    {
        for (int i = 0; i < lastRaw.length; i++)
        {
            lastRaw[i] = null;
            lastState[i] = null;
        }
    }

    public void onGameTick()
    {
        if (!isInVineryRegion(client))
        {
            return;
        }

        for (int i = 0; i < VARBIT_IDS.length; i++)
        {
            final int raw = client.getVarbitValue(VARBIT_IDS[i]);
            if (lastRaw[i] != null && raw == lastRaw[i])
            {
                continue;
            }
            handleVarbitValue(i, raw);
        }
    }

    private void handleVarbitValue(final int idx, final int raw)
    {
        final PatchId patch = PATCH_IDS[idx];
        final Instant now = Instant.now();
        final DecodedPatchState cur = StandardGrapesSlotDecoder.decode(raw);

        if (lastRaw[idx] == null)
        {
            lastRaw[idx] = raw;
            lastState[idx] = cur;
            log.debug("[varbit] {} initial raw={} empty={} stage={} maxStage={} health={} crop={}",
                patch, raw, cur.isEmpty(), cur.getStage(), cur.getMaxGrowthStageOrZero(), cur.getHealth(), cur.getCropNameOrNull());

            if (cur.isEmpty())
            {
                inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
            }

            emitObservedStateIfUseful(patch, now, cur, false);
            return;
        }

        final DecodedPatchState prev = lastState[idx];
        lastRaw[idx] = raw;
        lastState[idx] = cur;

        // Planting: empty -> stage 1.
        if (prev != null && prev.isEmpty() && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.planted(patch, now, ObservationSource.VARBIT));
        }

        // Harvest / clear.
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
        if (!cur.isEmpty() && cropItemId != null && cropName != null)
        {
            inferenceEngine.onObservation(Observation.cropObserved(patch, cropItemId, cropName, now, ObservationSource.VARBIT));
        }

        if (!cur.isEmpty() && cur.getHealth() == PatchHealth.HEALTHY && cur.getStage() >= 1)
        {
            Integer maxStageOrNull = cur.getMaxGrowthStageOrZero() > 0 ? cur.getMaxGrowthStageOrZero() : null;
            if (stageTickTransition)
            {
                inferenceEngine.onObservation(Observation.growthStageTransition(patch, cur.getStage(), maxStageOrNull, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }
            else
            {
                inferenceEngine.onObservation(Observation.growthStageObserved(patch, cur.getStage(), maxStageOrNull, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }
        }
    }

    static boolean isInVineryRegion(final Client client)
    {
        final Player p = client.getLocalPlayer();
        if (p == null || p.getWorldLocation() == null)
        {
            return false;
        }
        return p.getWorldLocation().getRegionID() == VINERY_REGION_ID;
    }

    static boolean isVineryRegionId(final int regionId)
    {
        return regionId == VINERY_REGION_ID;
    }
}
