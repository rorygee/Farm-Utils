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
 * Herb patch observer for FARMING_PATCH_STATUS_1 (varbit 4771).
 *
 * Known herb patches on this varbit:
 *  - Troll Stronghold herb patch (PatchId.HERB_TROLL_STRONGHOLD)
 *  - Weiss herb patch (PatchId.HERB_WEISS)
 */
@Slf4j
@Singleton
public class Varbit4771HerbVarbitObserver
{
	static final int VARBIT_ID = 4771;

	// Anchors taken from Varbit Explorer scopes in this project.
	static final WorldPoint TROLL_STRONGHOLD_ANCHOR = new WorldPoint(2828, 3694, 0);
	static final WorldPoint WEISS_ANCHOR = new WorldPoint(2847, 3934, 0);
	static final int MAX_ATTRIBUTION_DISTANCE_TILES = 48;

	private final Client client;
	private final InferenceEngine inferenceEngine;

	private Integer lastRaw;
	private DecodedPatchState lastState;
	private PatchId lastAttributedPatch;

	@Inject
	public Varbit4771HerbVarbitObserver(final Client client, final InferenceEngine inferenceEngine)
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
			// When we are away from either patch, attribution is unknown.
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
		final DecodedPatchState cur = StandardHerbSlotDecoder.decode(raw);

		if (lastRaw == null)
		{
			lastRaw = raw;
			lastState = cur;
			log.debug("[varbit] {} v{} initial raw={} empty={} stage={} health={} herb={}",
					patch, VARBIT_ID, raw, cur.isEmpty(), cur.getStage(), cur.getHealth(), cur.getHerbTypeOrNull());

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
				// "Empty" here includes weeds; both are unplanted.
				inferenceEngine.onObservation(Observation.harvested(patch, now, ObservationSource.VARBIT));
			}

			emitObservedStateIfUseful(patch, now, cur, false);
			return;
		}

		final DecodedPatchState prev = lastState;
		final int prevRaw = lastRaw;
		lastRaw = raw;
		lastState = cur;

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

		// Stage changes (debug only).
		if (prev.getStage() != cur.getStage() || prev.getHealth() != cur.getHealth())
		{
			log.debug("[varbit] {} state raw {} -> {} : stage {} -> {}, health {} -> {}, herb {} -> {}",
					patch,
					prevRaw,
					raw,
					prev.getStage(),
					cur.getStage(),
					prev.getHealth(),
					cur.getHealth(),
					prev.getHerbTypeOrNull(),
					cur.getHerbTypeOrNull());
		}

		final boolean stageChanged = prev.getStage() != cur.getStage();
		final boolean stageTickTransition = isNaturalGrowthTickTransition(prev, cur);
		emitObservedStateIfUseful(patch, now, cur, stageTickTransition);
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
			final Integer maxStageOrNull = 5;
		    if (stageTickTransition)
		    {
		        inferenceEngine.onObservation(Observation.growthStageTransition(patch, cur.getStage(), maxStageOrNull, now, ObservationSource.VARBIT));
		    }
		    else
		    {
		        inferenceEngine.onObservation(Observation.growthStageObserved(patch, cur.getStage(), maxStageOrNull, now, ObservationSource.VARBIT));
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

	private PatchId resolveAttributedPatch()
	{
		final Player p = client.getLocalPlayer();
		if (p == null)
		{
			return null;
		}

		final WorldPoint here = p.getWorldLocation();
		final int dStronghold = here.distanceTo2D(TROLL_STRONGHOLD_ANCHOR);
		final int dWeiss = here.distanceTo2D(WEISS_ANCHOR);
		final int min = Math.min(dStronghold, dWeiss);
		if (min > MAX_ATTRIBUTION_DISTANCE_TILES)
		{
			return null;
		}

		// If we can't clearly pick one, refuse to attribute.
		if (dStronghold == dWeiss)
		{
			return null;
		}

		return dStronghold < dWeiss ? PatchId.HERB_TROLL_STRONGHOLD : PatchId.HERB_WEISS;
	}
}
