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

/** Standard tree patch observer for FARMING_TRANSMIT_A (varbit 4771). */
@Slf4j
@Singleton
public class Varbit4771TreeVarbitObserver
{
    static final int VARBIT_ID = 4771;
    private static final Duration STAGE_CYCLE = Duration.ofMinutes(40);

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;
    private PatchId lastAttributedPatch;

    @Inject
    public Varbit4771TreeVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
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
        final DecodedPatchState cur = StandardTreeSlotDecoder.decode(raw);

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

        if (prev != null && prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.planted(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} planted (raw {} -> {})", patch, prevRaw, raw);
        }

        if (prev != null && !prev.isEmpty() && cur.isEmpty())
        {
            inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} harvested/cleared (raw {} -> {})", patch, prevRaw, raw);
        }

        if (prev != null && prev.getHealth() != PatchHealth.DISEASED && cur.getHealth() == PatchHealth.DISEASED)
        {
            inferenceEngine.onObservation(Observation.diseasedSet(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} diseased (raw {} -> {})", patch, prevRaw, raw);
        }
        if (prev != null && prev.getHealth() == PatchHealth.DISEASED && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.diseasedCleared(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} disease cleared (raw {} -> {})", patch, prevRaw, raw);
        }

        if (prev != null && prev.getHealth() != PatchHealth.DEAD && cur.getHealth() == PatchHealth.DEAD)
        {
            inferenceEngine.onObservation(Observation.deadSet(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} died (raw {} -> {})", patch, prevRaw, raw);
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
            Integer maxStageOrNull = cur.getMaxGrowthStageOrZero() > 0 ? cur.getMaxGrowthStageOrZero() : null;

            if (stageTickTransition)
            {
                inferenceEngine.onObservation(Observation.growthStageTransition(patch, cur.getStage(), maxStageOrNull, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }
            else
            {
                inferenceEngine.onObservation(Observation.growthStageObserved(patch, cur.getStage(), maxStageOrNull, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }

            Integer cropItemId = cur.getCropItemIdOrNull();
            String cropName = cur.getCropNameOrNull();
            if (cropItemId != null && cropName != null)
            {
                inferenceEngine.onObservation(Observation.cropObserved(patch, cropItemId, cropName, now, ObservationSource.VARBIT));
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

    static PatchId patchForRegionId(int regionId)
    {
        return REGION_ID_TO_PATCH.get(regionId);
    }

    private static final Map<Integer, PatchId> REGION_ID_TO_PATCH = new HashMap<>();

    static
    {
        // Lumbridge
        REGION_ID_TO_PATCH.put(12594, PatchId.TREE_LUMBRIDGE);
        REGION_ID_TO_PATCH.put(12850, PatchId.TREE_LUMBRIDGE);

        // Falador
        REGION_ID_TO_PATCH.put(11828, PatchId.TREE_FALADOR);
        REGION_ID_TO_PATCH.put(12084, PatchId.TREE_FALADOR);

        // Varrock
        REGION_ID_TO_PATCH.put(12854, PatchId.TREE_VARROCK);
        REGION_ID_TO_PATCH.put(12853, PatchId.TREE_VARROCK);

        // Taverley
        REGION_ID_TO_PATCH.put(11573, PatchId.TREE_TAVERLEY);
        REGION_ID_TO_PATCH.put(11829, PatchId.TREE_TAVERLEY);

        // Gnome Stronghold
        REGION_ID_TO_PATCH.put(9781, PatchId.TREE_GNOME_STRONGHOLD);
        REGION_ID_TO_PATCH.put(9782, PatchId.TREE_GNOME_STRONGHOLD);
        REGION_ID_TO_PATCH.put(9526, PatchId.TREE_GNOME_STRONGHOLD);
        REGION_ID_TO_PATCH.put(9525, PatchId.TREE_GNOME_STRONGHOLD);

        // Varlamore (Auburnvale / Nemus Retreat)
        REGION_ID_TO_PATCH.put(5427, PatchId.TREE_NEMUS_RETREAT);
        REGION_ID_TO_PATCH.put(5428, PatchId.TREE_NEMUS_RETREAT);
        REGION_ID_TO_PATCH.put(5684, PatchId.TREE_NEMUS_RETREAT);
    }
}
