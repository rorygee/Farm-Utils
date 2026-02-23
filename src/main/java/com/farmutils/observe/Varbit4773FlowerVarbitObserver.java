package com.farmutils.observe;

import com.farmutils.infer.InferenceEngine;
import com.farmutils.infer.Observation;
import com.farmutils.infer.ObservationSource;
import com.farmutils.model.PatchId;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;

/**
 * Flower patch observer for FARMING_PATCH_STATUS_3 (RuneLite Time Tracking: FARMING_TRANSMIT_C, varbit 4773).
 *
 * <p>This varbit is shared by multiple flower patch locations. We attribute the varbit to a patch id
 * by region id + proximity to an anchor point.</p>
 */
@Slf4j
@Singleton
public class Varbit4773FlowerVarbitObserver
{
    static final int VARBIT_ID = 4773;

    // Reuse herb patch anchors where the flower patch sits adjacent.
    static final WorldPoint FALADOR_ANCHOR = Varbit4774HerbVarbitObserver.FALADOR_ANCHOR;
    static final WorldPoint CATHERBY_ANCHOR = Varbit4774HerbVarbitObserver.CATHERBY_ANCHOR;
    static final WorldPoint ARDOUGNE_ANCHOR = Varbit4774HerbVarbitObserver.ARDOUGNE_ANCHOR;
    static final WorldPoint PHASMATYS_ANCHOR = Varbit4774HerbVarbitObserver.PHASMATYS_ANCHOR;
    static final WorldPoint HOSIDIUS_ANCHOR = Varbit4774HerbVarbitObserver.HOSIDIUS_ANCHOR;
    static final WorldPoint CIVITAS_ILLA_FORTIS_ANCHOR = Varbit4774HerbVarbitObserver.CIVITAS_ILLA_FORTIS_ANCHOR;

    // Prifddinas anchor sourced from RuneLite clue scrolls ("Elvish onions" onion patch adjacent to the farming area).
    static final WorldPoint PRIFDDINAS_ANCHOR = new WorldPoint(3303, 6092, 0);

    // Kastori (Varlamore) anchor is intentionally approximate; refine via Varbit Explorer if needed.
    static final WorldPoint KASTORI_ANCHOR = new WorldPoint(1358, 3058, 0);

    static final int MAX_ATTRIBUTION_DISTANCE_TILES = 48;

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;
    private PatchId lastAttributedPatch;

    @Inject
    public Varbit4773FlowerVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
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
            // Away from any known flower patch area.
            lastAttributedPatch = null;
            return;
        }

        // If we moved between patches, force a baseline read for the new patch.
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
        final DecodedPatchState cur = StandardFlowerSlotDecoder.decode(raw);

        if (lastRaw == null)
        {
            lastRaw = raw;
            lastState = cur;
            log.debug("[varbit] {} v{} initial raw={} empty={} stage={} health={} crop={}",
                patch, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), cur.getHealth(), cur.getCropNameOrNull());

            // Baseline: anchor what we can see immediately (no transition required).
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

        // Planting: empty -> stage 1..5.
        if (prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.planted(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} planted (raw {} -> {})", patch, prevRaw, raw);
        }

        // Harvest/clear: crop -> empty.
        if (!prev.isEmpty() && cur.isEmpty())
        {
            inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} harvested/cleared (raw {} -> {})", patch, prevRaw, raw);
        }

        // Disease set/cleared.
        if (prev.getHealth() != PatchHealth.DISEASED && cur.getHealth() == PatchHealth.DISEASED)
        {
            inferenceEngine.onObservation(Observation.diseasedSet(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} diseased (raw {} -> {})", patch, prevRaw, raw);
        }
        if (prev.getHealth() == PatchHealth.DISEASED && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.diseasedCleared(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} disease cleared (raw {} -> {})", patch, prevRaw, raw);
        }

        // Death.
        if (prev.getHealth() != PatchHealth.DEAD && cur.getHealth() == PatchHealth.DEAD)
        {
            inferenceEngine.onObservation(Observation.deadSet(patch, now, ObservationSource.VARBIT));
            log.debug("[varbit] {} died (raw {} -> {})", patch, prevRaw, raw);
        }

        if (prev.getStage() != cur.getStage() || prev.getHealth() != cur.getHealth())
        {
            log.debug("[varbit] {} state raw {} -> {} : stage {} -> {}, health {} -> {}, crop {} -> {}",
                patch,
                prevRaw,
                raw,
                prev.getStage(),
                cur.getStage(),
                prev.getHealth(),
                cur.getHealth(),
                prev.getCropNameOrNull(),
                cur.getCropNameOrNull());
        }

        final boolean stageTickTransition = isNaturalGrowthTickTransition(prev, cur);
        emitObservedStateIfUseful(patch, now, cur, stageTickTransition);
    }

    private void emitObservedStateIfUseful(final PatchId patch, final Instant now, final DecodedPatchState cur, final boolean stageTickTransition)
    {
        if (!cur.isEmpty() && cur.getHealth() == PatchHealth.HEALTHY && cur.getStage() >= 1)
        {
            if (stageTickTransition)
            {
                inferenceEngine.onObservation(Observation.growthStageTransition(patch, cur.getStage(), now, ObservationSource.VARBIT));
            }
            else
            {
                inferenceEngine.onObservation(Observation.growthStageObserved(patch, cur.getStage(), now, ObservationSource.VARBIT));
            }

            Integer cropItemId = cur.getCropItemIdOrNull();
            String cropName = cur.getCropNameOrNull();
            if (cropItemId != null && cropName != null)
            {
                inferenceEngine.onObservation(Observation.cropObserved(patch, cropItemId, cropName, now, ObservationSource.VARBIT));
            }
        }
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

    private PatchId resolveAttributedPatch()
    {
        final Player p = client.getLocalPlayer();
        if (p == null)
        {
            return null;
        }

        final WorldPoint wp = p.getWorldLocation();
        if (wp == null)
        {
            return null;
        }

        final int regionId = wp.getRegionID();
        final PatchId patch = REGION_ID_TO_PATCH.get(regionId);
        if (patch == null)
        {
            return null;
        }

        final WorldPoint anchor = REGION_ID_TO_ANCHOR.get(regionId);
        if (anchor == null)
        {
            return null;
        }

        if (wp.getPlane() != anchor.getPlane())
        {
            return null;
        }

        if (wp.distanceTo2D(anchor) > MAX_ATTRIBUTION_DISTANCE_TILES)
        {
            return null;
        }

        return patch;
    }

    static PatchId patchForRegionId(int regionId)
    {
        return REGION_ID_TO_PATCH.get(regionId);
    }

    private static final Map<Integer, PatchId> REGION_ID_TO_PATCH = new HashMap<>();
    private static final Map<Integer, WorldPoint> REGION_ID_TO_ANCHOR = new HashMap<>();

    private static void registerRegion(int regionId, PatchId patch, WorldPoint anchor)
    {
        REGION_ID_TO_PATCH.put(regionId, patch);
        REGION_ID_TO_ANCHOR.put(regionId, anchor);
    }

    static
    {
        // Ardougne (Hemenster)
        registerRegion(10548, PatchId.FLOWER_ARDOUGNE, ARDOUGNE_ANCHOR);

        // Catherby (+ adjacent regions)
        registerRegion(11062, PatchId.FLOWER_CATHERBY, CATHERBY_ANCHOR);
        registerRegion(11061, PatchId.FLOWER_CATHERBY, CATHERBY_ANCHOR);
        registerRegion(11318, PatchId.FLOWER_CATHERBY, CATHERBY_ANCHOR);
        registerRegion(11317, PatchId.FLOWER_CATHERBY, CATHERBY_ANCHOR);

        // Civitas illa Fortis (+ adjacent regions)
        registerRegion(6192, PatchId.FLOWER_CIVITAS_ILLA_FORTIS, CIVITAS_ILLA_FORTIS_ANCHOR);
        registerRegion(6447, PatchId.FLOWER_CIVITAS_ILLA_FORTIS, CIVITAS_ILLA_FORTIS_ANCHOR);
        registerRegion(6448, PatchId.FLOWER_CIVITAS_ILLA_FORTIS, CIVITAS_ILLA_FORTIS_ANCHOR);
        registerRegion(6449, PatchId.FLOWER_CIVITAS_ILLA_FORTIS, CIVITAS_ILLA_FORTIS_ANCHOR);
        registerRegion(6191, PatchId.FLOWER_CIVITAS_ILLA_FORTIS, CIVITAS_ILLA_FORTIS_ANCHOR);
        registerRegion(6193, PatchId.FLOWER_CIVITAS_ILLA_FORTIS, CIVITAS_ILLA_FORTIS_ANCHOR);

        // Falador
        registerRegion(12083, PatchId.FLOWER_FALADOR, FALADOR_ANCHOR);

        // Hosidius (Kourend)
        registerRegion(6967, PatchId.FLOWER_HOSIDIUS, HOSIDIUS_ANCHOR);
        registerRegion(6711, PatchId.FLOWER_HOSIDIUS, HOSIDIUS_ANCHOR);

        // Port Phasmatys
        registerRegion(14391, PatchId.FLOWER_PORT_PHASMATYS, PHASMATYS_ANCHOR);
        registerRegion(14390, PatchId.FLOWER_PORT_PHASMATYS, PHASMATYS_ANCHOR);

        // Prifddinas (+ underground adjacency)
        registerRegion(13151, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12895, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12894, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(13150, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12994, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12993, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12737, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12738, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12126, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(12127, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);
        registerRegion(13250, PatchId.FLOWER_PRIFDDINAS, PRIFDDINAS_ANCHOR);

        // Kastori (+ adjacent regions)
        registerRegion(5423, PatchId.FLOWER_KASTORI, KASTORI_ANCHOR);
        registerRegion(5167, PatchId.FLOWER_KASTORI, KASTORI_ANCHOR);
        registerRegion(5424, PatchId.FLOWER_KASTORI, KASTORI_ANCHOR);
    }
}
