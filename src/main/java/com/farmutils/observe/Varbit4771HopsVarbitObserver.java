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
import net.runelite.api.gameval.ItemID;

/** Hops patch observer for FARMING_TRANSMIT_A (varbit 4771). */
@Slf4j
@Singleton
public class Varbit4771HopsVarbitObserver
{
    static final int VARBIT_ID = 4771;

    private static final Duration CYCLE_10 = Duration.ofMinutes(10);
    private static final Duration CYCLE_20 = Duration.ofMinutes(20);

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;
    private PatchId lastAttributedPatch;

    @Inject
    public Varbit4771HopsVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
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
        final DecodedPatchState cur = StandardHopsSlotDecoder.decode(raw);

        if (lastRaw == null)
        {
            lastRaw = raw;
            lastState = cur;
            log.debug("[varbit] {} v{} initial raw={} empty={} stage={} maxStage={} health={} crop={}",
                patch, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), cur.getMaxGrowthStageOrZero(), cur.getHealth(), cur.getCropNameOrNull());

            if (cur.getHealth() == PatchHealth.DISEASED)
            {
                inferenceEngine.onObservation(Observation.diseasedSet(patch, now, ObservationSource.VARBIT));
            }
            else if (cur.getHealth() == PatchHealth.DEAD)
            {
                inferenceEngine.onObservation(Observation.deadSet(patch, now, ObservationSource.VARBIT));
            }
            else if (cur.isEmpty())
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

        // Planting: empty -> stage 1 (use the fully-raked sentinel raw=3 to avoid false transitions on region load).
        if (prev != null && prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.planted(patch, now, ObservationSource.VARBIT));
        }

        // Harvest/clear: crop -> empty.
        if (prev != null && !prev.isEmpty() && cur.isEmpty())
        {
            inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
        }

        // Disease set/cleared.
        if (prev != null && prev.getHealth() != PatchHealth.DISEASED && cur.getHealth() == PatchHealth.DISEASED)
        {
            inferenceEngine.onObservation(Observation.diseasedSet(patch, now, ObservationSource.VARBIT));
        }
        if (prev != null && prev.getHealth() == PatchHealth.DISEASED && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.diseasedCleared(patch, now, ObservationSource.VARBIT));
        }

        // Death.
        if (prev != null && prev.getHealth() != PatchHealth.DEAD && cur.getHealth() == PatchHealth.DEAD)
        {
            inferenceEngine.onObservation(Observation.deadSet(patch, now, ObservationSource.VARBIT));
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
        // Always emit crop identity when known.
        Integer cropItemId = cur.getCropItemIdOrNull();
        String cropName = cur.getCropNameOrNull();
        if (!cur.isEmpty() && cropItemId != null && cropName != null)
        {
            inferenceEngine.onObservation(Observation.cropObserved(patch, cropItemId, cropName, now, ObservationSource.VARBIT));
        }

        if (!cur.isEmpty() && cur.getHealth() == PatchHealth.HEALTHY && cur.getStage() >= 1)
        {
            Integer maxStageOrNull = cur.getMaxGrowthStageOrZero() > 0 ? cur.getMaxGrowthStageOrZero() : null;
            Duration cycle = resolveCycleDurationFromCropItemId(cropItemId);

            if (stageTickTransition)
            {
                inferenceEngine.onObservation(Observation.growthStageTransition(patch, cur.getStage(), maxStageOrNull, cycle, now, ObservationSource.VARBIT));
            }
            else
            {
                inferenceEngine.onObservation(Observation.growthStageObserved(patch, cur.getStage(), maxStageOrNull, cycle, now, ObservationSource.VARBIT));
            }
        }
    }

    private static Duration resolveCycleDurationFromCropItemId(final Integer cropItemId)
    {
        if (cropItemId == null)
        {
            return CYCLE_10;
        }

        // Time Tracking: flax/hemp/cotton use 20-minute ticks, other hops use 10-minute.
        if (cropItemId == ItemID.FLAX || cropItemId == ItemID.HEMP || cropItemId == ItemID.COTTON_BOLL)
        {
            return CYCLE_20;
        }
        return CYCLE_10;
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

    static PatchId patchForRegionId(int regionId)
    {
        return REGION_ID_TO_PATCH.get(regionId);
    }

    private static final Map<Integer, PatchId> REGION_ID_TO_PATCH = new HashMap<>();

    static
    {
        // Aldarin
        int[] aldarin = new int[] { 5421, 5165, 5166, 5422, 5677, 5678 };
        for (int r : aldarin)
        {
            REGION_ID_TO_PATCH.put(r, PatchId.HOPS_ALDARIN);
        }

        // Entrana
        int[] entrana = new int[] { 11060, 11316 };
        for (int r : entrana)
        {
            REGION_ID_TO_PATCH.put(r, PatchId.HOPS_ENTRANA);
        }

        // Lumbridge
        REGION_ID_TO_PATCH.put(12851, PatchId.HOPS_LUMBRIDGE);

        // Seers' Village / McGrubor's Wood
        REGION_ID_TO_PATCH.put(10551, PatchId.HOPS_MCGUBORS_WOOD);
        REGION_ID_TO_PATCH.put(10550, PatchId.HOPS_MCGUBORS_WOOD);

        // Yanille
        REGION_ID_TO_PATCH.put(10288, PatchId.HOPS_YANILLE);
    }
}
