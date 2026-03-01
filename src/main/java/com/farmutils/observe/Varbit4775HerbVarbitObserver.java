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

/**
 * Herb patch observer for FARMING_PATCH_STATUS_5 (varbit 4775).
 *
 * <p>Known herb patch on this varbit:
 *  - Farming Guild herb patch (PatchId.HERB_FARMING_GUILD)
 */
@Slf4j
@Singleton
public class Varbit4775HerbVarbitObserver {
    static final int VARBIT_ID = 4775;

    // Anchor taken from Varbit Explorer scopes in this project.
    static final WorldPoint FARMING_GUILD_ANCHOR = new WorldPoint(1249, 3724, 0);
    static final int MAX_ATTRIBUTION_DISTANCE_TILES = 48;

    private static final PatchId PATCH_ID = PatchId.HERB_FARMING_GUILD;

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;

    @Inject
    public Varbit4775HerbVarbitObserver(final Client client, final InferenceEngine inferenceEngine) {
        this.client = client;
        this.inferenceEngine = inferenceEngine;
    }

    public void reset() {
        lastRaw = null;
        lastState = null;
    }

    public void onGameTick() {
        if (!isInScope()) {
            // Dropping state here ensures a later return re-calibrates baseline.
            reset();
            return;
        }

        final int raw = client.getVarbitValue(VARBIT_ID);
        if (lastRaw != null && raw == lastRaw) {
            return;
        }

        handleVarbitValue(raw);
    }

    /**
     * Simple proximity gate to avoid “sticky” baselines when the player is nowhere near the patch.
     *
     * <p>This observer is patch-specific (Farming Guild herb patch), so attribution is not ambiguous,
     * but gating keeps the observer quiet unless the relevant region is plausibly loaded.
     */
    private boolean isInScope() {
        final Player p = client.getLocalPlayer();
        if (p == null) {
            return false;
        }

        final WorldPoint here = p.getWorldLocation();
        if (here == null) {
            return false;
        }

        if (here.getPlane() != FARMING_GUILD_ANCHOR.getPlane()) {
            return false;
        }

        return here.distanceTo2D(FARMING_GUILD_ANCHOR) <= MAX_ATTRIBUTION_DISTANCE_TILES;
    }

    private void handleVarbitValue(final int raw) {
        final Instant now = Instant.now();
        final DecodedPatchState cur = StandardHerbSlotDecoder.decode(raw);

        if (lastRaw == null) {
            lastRaw = raw;
            lastState = cur;
            log.debug("[varbit] {} v{} initial raw={} empty={} stage={} health={} herb={}",
                    PATCH_ID, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), cur.getHealth(), cur.getHerbTypeOrNull());
            if (cur.getHealth() == PatchHealth.DISEASED) {
                inferenceEngine.onObservation(Observation.diseasedSet(PATCH_ID, now, ObservationSource.VARBIT));
            } else if (cur.getHealth() == PatchHealth.DEAD) {
                inferenceEngine.onObservation(Observation.deadSet(PATCH_ID, now, ObservationSource.VARBIT));
            }
            emitObservedStateIfUseful(now, cur, false);
            return;
        }

        final DecodedPatchState prev = lastState;
        final int prevRaw = lastRaw;
        lastRaw = raw;
        lastState = cur;

        // Planting: empty -> stage 1..5 (ignore harvest transients).
        if (prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY && !StandardHerbSlotDecoder.isHealthyHarvestTransient(raw)) {
            inferenceEngine.onObservation(Observation.planted(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} planted (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        // Harvest/clear: herb -> empty.
        if (!prev.isEmpty() && cur.isEmpty()) {
            inferenceEngine.onObservation(Observation.harvested(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} harvested/cleared (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        // Disease set/cleared.
        if (prev.getHealth() != PatchHealth.DISEASED && cur.getHealth() == PatchHealth.DISEASED) {
            inferenceEngine.onObservation(Observation.diseasedSet(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} diseased (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }
        if (prev.getHealth() == PatchHealth.DISEASED && cur.getHealth() == PatchHealth.HEALTHY) {
            inferenceEngine.onObservation(Observation.diseasedCleared(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} disease cleared (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        // Death.
        if (prev.getHealth() != PatchHealth.DEAD && cur.getHealth() == PatchHealth.DEAD) {
            inferenceEngine.onObservation(Observation.deadSet(PATCH_ID, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} died (raw {} -> {})", PATCH_ID, prevRaw, raw);
        }

        final boolean stageChanged = prev.getStage() != cur.getStage();
        final boolean stageTickTransition = isNaturalGrowthTickTransition(prev, cur);

        // Stage changes (debug only).
        if (stageChanged || prev.getHealth() != cur.getHealth()) {
            log.debug("[varbit] {} state raw {} -> {} : stage {} -> {}, health {} -> {}, herb {} -> {}",
                    PATCH_ID,
                    prevRaw,
                    raw,
                    prev.getStage(),
                    cur.getStage(),
                    prev.getHealth(),
                    cur.getHealth(),
                    prev.getHerbTypeOrNull(),
                    cur.getHerbTypeOrNull());
        }

        emitObservedStateIfUseful(now, cur, stageTickTransition);
    }


    private static boolean isNaturalGrowthTickTransition(final DecodedPatchState prev, final DecodedPatchState cur)
    {
        if (prev == null || cur == null)
        {
            return false;
        }
        // Only treat a +1 stage change on a non-empty, healthy patch as a server-driven growth tick.
        // This deliberately excludes baseline reads (e.g. empty -> stage 4 on region load) and planting.
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

    private void emitObservedStateIfUseful(Instant now, DecodedPatchState cur, boolean stageTickTransition) {

        if (!cur.isEmpty() && cur.getHealth() == PatchHealth.HEALTHY && cur.getStage() >= 1) {
            final Integer maxStageOrNull = 5;
            if (stageTickTransition) {
                inferenceEngine.onObservation(Observation.growthStageTransition(PATCH_ID, cur.getStage(), maxStageOrNull, now, ObservationSource.VARBIT));
            } else {
                inferenceEngine.onObservation(Observation.growthStageObserved(PATCH_ID, cur.getStage(), maxStageOrNull, now, ObservationSource.VARBIT));
            }

            Integer cropItemId = cur.getCropItemIdOrNull();
            String cropName = cur.getCropNameOrNull();
            if (cropItemId != null && cropName != null)
            {
                inferenceEngine.onObservation(Observation.cropObserved(PATCH_ID, cropItemId, cropName, now, ObservationSource.VARBIT));
            }

            // When harvestable, the herb patch has a small set of visual variants that reflect
            // fewer picks remaining. Capture this as a depletion stage so UI can show "lives left".
            if (cur.getStage() == 5) {
                final int harvestStage = StandardHerbSlotDecoder.getHarvestStageOrZero(cur.getRaw());
                if (harvestStage > 0) {
                    inferenceEngine.onObservation(Observation.harvestStageObserved(PATCH_ID, harvestStage, now, ObservationSource.VARBIT));
                }
            }
        }
    }
}
