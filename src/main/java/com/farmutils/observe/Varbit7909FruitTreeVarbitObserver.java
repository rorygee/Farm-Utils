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
import net.runelite.api.coords.WorldPoint;

/** Fruit tree patch observer for FARMING_TRANSMIT_K (varbit 7909) at Farming Guild. */
@Slf4j
@Singleton
public class Varbit7909FruitTreeVarbitObserver
{
    static final int VARBIT_ID = 7909;
    private static final Duration STAGE_CYCLE = Duration.ofMinutes(160);

    static final WorldPoint FARMING_GUILD_ANCHOR = Varbit4775HerbVarbitObserver.FARMING_GUILD_ANCHOR;
    static final int MAX_ATTRIBUTION_DISTANCE_TILES = 48;

    private static final PatchId PATCH_ID = PatchId.FRUIT_TREE_FARMING_GUILD;

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;

    @Inject
    public Varbit7909FruitTreeVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
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
            reset();
            return;
        }

        final int raw = client.getVarbitValue(VARBIT_ID);
        if (lastRaw != null && raw == lastRaw)
        {
            return;
        }

        handleVarbitValue(raw);
    }

    private boolean isInScope()
    {
        final Player p = client.getLocalPlayer();
        if (p == null)
        {
            return false;
        }

        final WorldPoint here = p.getWorldLocation();
        if (here == null)
        {
            return false;
        }

        if (here.getPlane() != FARMING_GUILD_ANCHOR.getPlane())
        {
            return false;
        }

        return here.distanceTo2D(FARMING_GUILD_ANCHOR) <= MAX_ATTRIBUTION_DISTANCE_TILES;
    }

    private void handleVarbitValue(final int raw)
    {
        final Instant now = Instant.now();
        final DecodedPatchState cur = StandardFruitTreeSlotDecoder.decode(raw);

        if (lastRaw == null)
        {
            lastRaw = raw;
            lastState = cur;
            log.debug("[varbit] {} v{} initial raw={} empty={} stage={} maxStage={} health={} crop={}",
                PATCH_ID, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), cur.getMaxGrowthStageOrZero(), cur.getHealth(), cur.getCropNameOrNull());

            if (cur.getHealth() == PatchHealth.DISEASED)
            {
                inferenceEngine.onObservation(Observation.diseasedSet(PATCH_ID, now, ObservationSource.VARBIT));
            }
            else if (cur.getHealth() == PatchHealth.DEAD)
            {
                inferenceEngine.onObservation(Observation.deadSet(PATCH_ID, now, ObservationSource.VARBIT));
            }
            else if (cur.isEmpty())
            {
                inferenceEngine.onObservation(Observation.harvested(PATCH_ID, now, ObservationSource.VARBIT));
            }

            emitObservedStateIfUseful(now, cur, false);
            return;
        }

        final DecodedPatchState prev = lastState;
        final int prevRaw = lastRaw;
        lastRaw = raw;
        lastState = cur;

        if (prev != null && prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.planted(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} planted (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        if (prev != null && !prev.isEmpty() && cur.isEmpty())
        {
            inferenceEngine.onObservation(Observation.harvested(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} harvested/cleared (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        if (prev != null && prev.getHealth() != PatchHealth.DISEASED && cur.getHealth() == PatchHealth.DISEASED)
        {
            inferenceEngine.onObservation(Observation.diseasedSet(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} diseased (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }
        if (prev != null && prev.getHealth() == PatchHealth.DISEASED && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.diseasedCleared(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} disease cleared (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        if (prev != null && prev.getHealth() != PatchHealth.DEAD && cur.getHealth() == PatchHealth.DEAD)
        {
            inferenceEngine.onObservation(Observation.deadSet(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} died (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        final boolean stageTickTransition = isNaturalGrowthTickTransition(prev, cur);
        emitObservedStateIfUseful(now, cur, stageTickTransition);
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

    private void emitObservedStateIfUseful(final Instant now, final DecodedPatchState cur, final boolean stageTickTransition)
    {
        if (!cur.isEmpty() && cur.getHealth() == PatchHealth.HEALTHY && cur.getStage() >= 1)
        {
            Integer maxStageOrNull = cur.getMaxGrowthStageOrZero() > 0 ? cur.getMaxGrowthStageOrZero() : null;

            if (stageTickTransition)
            {
                inferenceEngine.onObservation(Observation.growthStageTransition(PATCH_ID, cur.getStage(), maxStageOrNull, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }
            else
            {
                inferenceEngine.onObservation(Observation.growthStageObserved(PATCH_ID, cur.getStage(), maxStageOrNull, STAGE_CYCLE, now, ObservationSource.VARBIT));
            }

            Integer cropItemId = cur.getCropItemIdOrNull();
            String cropName = cur.getCropNameOrNull();
            if (cropItemId != null && cropName != null)
            {
                inferenceEngine.onObservation(Observation.cropObserved(PATCH_ID, cropItemId, cropName, now, ObservationSource.VARBIT));
            }
        }
    }
}
