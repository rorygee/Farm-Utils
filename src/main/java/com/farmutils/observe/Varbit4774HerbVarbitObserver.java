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
import net.runelite.api.coords.WorldPoint;

/**
 * Herb patch observer for FARMING_PATCH_STATUS_4 (varbit 4774).
 *
 * Known herb patches on this varbit:
 * - Falador (object #8150)
 * - Catherby (object #8151)
 * - Ardougne (object #8152)
 * - Port Phasmatys (object #8153)
 * - Hosidius (object #27115)
 * - Civitas illa Fortis (Varlamore)
 */
@Slf4j
@Singleton
public class Varbit4774HerbVarbitObserver
{
    static final int VARBIT_ID = 4774;

    // Anchors taken from Varbit Explorer scopes in this project.
    static final WorldPoint FALADOR_ANCHOR = new WorldPoint(3058, 3311, 0);
    static final WorldPoint CATHERBY_ANCHOR = new WorldPoint(2811, 3463, 0);
    static final WorldPoint ARDOUGNE_ANCHOR = new WorldPoint(2670, 3374, 0);
    static final WorldPoint PHASMATYS_ANCHOR = new WorldPoint(3604, 3526, 0);
    static final WorldPoint HOSIDIUS_ANCHOR = new WorldPoint(1741, 3552, 0);
    static final WorldPoint CIVITAS_ILLA_FORTIS_ANCHOR = new WorldPoint(1587, 3101, 0);

    static final int MAX_ATTRIBUTION_DISTANCE_TILES = 48;

    private final Client client;
    private final InferenceEngine inferenceEngine;

    private Integer lastRaw;
    private DecodedPatchState lastState;
    private PatchId lastAttributedPatch;

    @Inject
    public Varbit4774HerbVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
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
        final PatchId attributed = resolveAttributedPatch();
        if (attributed == null)
        {
			// When we are away from any mapped herb patch for this varbit, attribution is unknown.
			// Keep lastRaw/state intact; we will force a baseline read when we next re-enter a mapped scope.
			lastAttributedPatch = null;
            return;
        }

		// If we moved between patches (or are returning after being out of scope), force a baseline read.
		if (lastAttributedPatch != attributed)
		{
			lastAttributedPatch = attributed;
			lastRaw = null;
			lastState = null;
		}

		final int raw = client.getVarbitValue(VARBIT_ID);
		if (lastRaw != null && lastRaw == raw)
		{
			return;
		}

		final Instant now = Instant.now();
		final DecodedPatchState cur = StandardHerbSlotDecoder.decode(raw);

		if (lastRaw == null)
		{
			lastRaw = raw;
			lastState = cur;
			log.debug("[varbit] {} v{} initial raw={} empty={} stage={} health={} herb={}",
				attributed, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), cur.getHealth(), cur.getHerbTypeOrNull());

			// Baseline: anchor what we can see immediately (no transition required).
			if (cur.getHealth() == PatchHealth.DISEASED)
			{
				inferenceEngine.onObservation(Observation.diseasedSet(attributed, now, ObservationSource.VARBIT));
			}
			else if (cur.getHealth() == PatchHealth.DEAD)
			{
				inferenceEngine.onObservation(Observation.deadSet(attributed, now, ObservationSource.VARBIT));
			}
			else if (cur.isEmpty())
			{
				// "Empty" here includes weeds; both are unplanted.
				inferenceEngine.onObservation(Observation.harvested(attributed, now, ObservationSource.VARBIT));
			}

			emitObservedStateIfUseful(attributed, now, cur, false);
			return;
		}

		final DecodedPatchState prev = lastState;
		final int prevRaw = lastRaw;
		lastRaw = raw;
		lastState = cur;

		if (prev != null)
		{
			emitTransitions(attributed, prev, cur, prevRaw, raw, now);
		}

		// Stage changes (debug only).
		if (prev != null && (prev.getStage() != cur.getStage() || prev.getHealth() != cur.getHealth()))
		{
			log.debug("[varbit] {} state raw {} -> {} : stage {} -> {}, health {} -> {}, herb {} -> {}",
				attributed,
				prevRaw,
				raw,
				prev.getStage(),
				cur.getStage(),
				prev.getHealth(),
				cur.getHealth(),
				prev.getHerbTypeOrNull(),
				cur.getHerbTypeOrNull());
		}

		emitObservedStateIfUseful(attributed, now, cur, isNaturalGrowthTickTransition(prev, cur));
    }

    private PatchId resolveAttributedPatch()
    {
        final net.runelite.api.Player p = client.getLocalPlayer();
        if (p == null)
        {
            return null;
        }

        final WorldPoint wp = p.getWorldLocation();
        final int regionId = wp.getRegionID();

        final WorldPoint anchor = REGION_ID_TO_ANCHOR.get(regionId);
        final PatchId patch = REGION_ID_TO_PATCH.get(regionId);
        if (anchor == null || patch == null)
        {
			// This observer is intentionally region-bound. Unmapped regions are expected and not actionable.
			// Keep this at trace to avoid confusing normal debug logs when standing in unrelated regions.
			log.trace("[varbit] v{} unknown regionId={} at {} (unmapped herb patch region)", VARBIT_ID, regionId, wp);
            return null;
        }

        if (wp.distanceTo(anchor) > MAX_ATTRIBUTION_DISTANCE_TILES)
        {
            return null;
        }

        return patch;
    }

	private void emitTransitions(final PatchId patch, final DecodedPatchState prev, final DecodedPatchState cur, final int prevRaw, final int raw, final Instant now)
    {
		// Planting: empty -> stage 1..5 (ignore harvest transients).
		if (prev.isEmpty() && prevRaw == 3 && !cur.isEmpty() && cur.getStage() == 1 && cur.getHealth() == PatchHealth.HEALTHY && !StandardHerbSlotDecoder.isHealthyHarvestTransient(raw))
		{
			inferenceEngine.onObservation(Observation.planted(patch, now, ObservationSource.VARBIT));
			log.debug("[varbit] {} planted (raw {} -> {})", patch, prevRaw, raw);
		}

		// Harvest/clear: herb -> empty.
		if (!prev.isEmpty() && cur.isEmpty())
		{
			inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
			log.debug("[varbit] {} harvested/cleared (raw {} -> {})", patch, prevRaw, raw);
		}

		// Death
        if (prev.getHealth() != PatchHealth.DEAD && cur.getHealth() == PatchHealth.DEAD)
        {
            inferenceEngine.onObservation(Observation.deadSet(patch, now, ObservationSource.VARBIT));
        }

        // Disease / recovery
        if (prev.getHealth() != PatchHealth.DISEASED && cur.getHealth() == PatchHealth.DISEASED)
        {
            inferenceEngine.onObservation(Observation.diseasedSet(patch, now, ObservationSource.VARBIT));
        }
        else if (prev.getHealth() == PatchHealth.DISEASED && cur.getHealth() == PatchHealth.HEALTHY)
        {
            inferenceEngine.onObservation(Observation.diseasedCleared(patch, now, ObservationSource.VARBIT));
        }

		// No additional transitions.
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

		    // When harvestable, the herb patch has a small set of visual variants that reflect
		    // fewer picks remaining. Capture this as a depletion stage so UI can show "lives left".
		    if (cur.getStage() == 5)
		    {
		        final int harvestStage = StandardHerbSlotDecoder.getHarvestStageOrZero(cur.getRaw());
		        if (harvestStage > 0)
		        {
		            inferenceEngine.onObservation(Observation.harvestStageObserved(patch, harvestStage, now, ObservationSource.VARBIT));
		        }
		    }
		}
	}

    private static final Map<Integer, PatchId> REGION_ID_TO_PATCH = new HashMap<>();
    private static final Map<Integer, WorldPoint> REGION_ID_TO_ANCHOR = new HashMap<>();

    static
    {
        // Falador
        REGION_ID_TO_PATCH.put(FALADOR_ANCHOR.getRegionID(), PatchId.HERB_FALADOR);
        REGION_ID_TO_ANCHOR.put(FALADOR_ANCHOR.getRegionID(), FALADOR_ANCHOR);

        // Catherby
        REGION_ID_TO_PATCH.put(CATHERBY_ANCHOR.getRegionID(), PatchId.HERB_CATHERBY);
        REGION_ID_TO_ANCHOR.put(CATHERBY_ANCHOR.getRegionID(), CATHERBY_ANCHOR);

        // Ardougne
        REGION_ID_TO_PATCH.put(ARDOUGNE_ANCHOR.getRegionID(), PatchId.HERB_ARDOUGNE);
        REGION_ID_TO_ANCHOR.put(ARDOUGNE_ANCHOR.getRegionID(), ARDOUGNE_ANCHOR);

        // Port Phasmatys
        REGION_ID_TO_PATCH.put(PHASMATYS_ANCHOR.getRegionID(), PatchId.HERB_PORT_PHASMATYS);
        REGION_ID_TO_ANCHOR.put(PHASMATYS_ANCHOR.getRegionID(), PHASMATYS_ANCHOR);

        // Hosidius
        REGION_ID_TO_PATCH.put(HOSIDIUS_ANCHOR.getRegionID(), PatchId.HERB_HOSIDIUS);
        REGION_ID_TO_ANCHOR.put(HOSIDIUS_ANCHOR.getRegionID(), HOSIDIUS_ANCHOR);

        // Civitas illa Fortis (Varlamore)
        // RuneLite Time Tracking registers this farming area under multiple adjacent regions.
        // Map them all to the same patch + anchor so attribution survives boundary crossings.
        final int[] civitasRegions = new int[]
        {
            6191, 6192, 6193,
            6447, 6448, 6449
        };
        for (int regionId : civitasRegions)
        {
            REGION_ID_TO_PATCH.put(regionId, PatchId.HERB_CIVITAS_ILLA_FORTIS);
            REGION_ID_TO_ANCHOR.put(regionId, CIVITAS_ILLA_FORTIS_ANCHOR);
        }

    }

    // Package-private helpers (tests)
    static PatchId patchForRegionId(int regionId)
    {
        return REGION_ID_TO_PATCH.get(regionId);
    }
}
