package com.farmutils;

import com.farmutils.ui.FarmPanel;
import com.farmutils.ui.FarmRootPanel;
import com.farmutils.ui.FarmStubPanel;
import com.farmutils.ui.RoutesPanel;
import com.farmutils.ui.CalcPanel;
import com.farmutils.route.RouteStore;
import com.farmutils.route.RouteSessionStore;
import com.farmutils.route.RouteSession;
import com.farmutils.route.RouteSessionState;
import com.farmutils.route.Route;
import com.farmutils.storage.UiStateStore;
import com.farmutils.infer.InferenceEngine;
import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchSource;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchView;
import com.farmutils.observe.Varbit4771AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4771BelladonnaVarbitObserver;
import com.farmutils.observe.Varbit4771BushVarbitObserver;
import com.farmutils.observe.Varbit4771CactusVarbitObserver;
import com.farmutils.observe.Varbit4771CalquatVarbitObserver;
import com.farmutils.observe.Varbit4771CoralVarbitObserver;
import com.farmutils.observe.Varbit4771HopsVarbitObserver;
import com.farmutils.observe.Varbit4771MushroomVarbitObserver;
import com.farmutils.observe.Varbit4771SeaweedVarbitObserver;
import com.farmutils.observe.Varbit4771TreeVarbitObserver;
import com.farmutils.observe.Varbit4771FruitTreeVarbitObserver;
import com.farmutils.observe.Varbit4772CoralVarbitObserver;
import com.farmutils.observe.Varbit4772BelladonnaVarbitObserver;
import com.farmutils.observe.Varbit4772BushVarbitObserver;
import com.farmutils.observe.Varbit4772SeaweedVarbitObserver;
import com.farmutils.observe.Varbit4772FruitTreeVarbitObserver;
import com.farmutils.observe.Varbit4773CalquatVarbitObserver;
import com.farmutils.observe.Varbit4953GrapesVarbitObserver;
import com.farmutils.observe.Varbit7904CactusVarbitObserver;
import com.farmutils.observe.Varbit7905TreeVarbitObserver;
import com.farmutils.observe.Varbit4771HerbVarbitObserver;
import com.farmutils.observe.Varbit4772AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4773AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4773FlowerVarbitObserver;
import com.farmutils.observe.Varbit4774AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4774HerbVarbitObserver;
import com.farmutils.observe.Varbit4775HerbVarbitObserver;
import com.farmutils.observe.Varbit7906FlowerVarbitObserver;
import com.farmutils.observe.Varbit7908HesporiVarbitObserver;
import com.farmutils.observe.Varbit7909FruitTreeVarbitObserver;
import com.farmutils.observe.Varbit7911AnimaVarbitObserver;
import com.farmutils.observe.Varbit4771HardwoodVarbitObserver;
import com.farmutils.observe.Varbit4772HardwoodVarbitObserver;
import com.farmutils.observe.Varbit4773HardwoodVarbitObserver;
import com.farmutils.observe.Varbit4771SpiritTreeVarbitObserver;
import com.farmutils.observe.Varbit4772SpiritTreeVarbitObserver;
import com.farmutils.observe.Varbit7904SpiritTreeVarbitObserver;
import com.farmutils.observe.Varbit4775CrystalTreeVarbitObserver;
import com.farmutils.observe.Varbit7910CelastrusVarbitObserver;
import com.farmutils.observe.Varbit7907RedwoodVarbitObserver;
import com.farmutils.observe.Varbit1033UnferthsPatchVarbitObserver;
import com.farmutils.observe.Varbit3714MagicBeansVarbitObserver;
import com.farmutils.observe.Varbit823KeldaHopsVarbitObserver;
import com.farmutils.observe.Varbit9016ElderCadantineVarbitObserver;
import com.farmutils.observe.Varbit10781EnrichedSnapdragonVarbitObserver;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.swing.SwingUtilities;

@Slf4j
@PluginDescriptor(
	name = "Farm Utils",
	description = "Quality-of-life farming patch state tracker",
	tags = {"farming", "utility", "qol"}
)
public class FarmutilsPlugin extends Plugin
{
	/**
	 * RuneLite API event availability can vary across versions/build tooling.
	 * Instead of subscribing to MapRegionChanged, detect region transitions on-tick
	 * by tracking the local player's region id.
	 */
	private int lastRegionId = -1;

	@Inject private FarmutilsConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientUI clientUI;

	@Inject
	private FarmPanel farmPanel;

	@Inject
	private UiStateStore uiStateStore;

	private FarmRootPanel rootPanel;
	private RoutesPanel routesPanel;
	private CalcPanel calcPanel;
	private RouteStore routeStore;
	private RouteSessionStore routeSessionStore;

	private NavigationButton navButton;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private InferenceEngine inferenceEngine;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private com.farmutils.overlay.PatchHighlightOverlay patchHighlightOverlay;

	// Track inference updates so the UI can refresh only when outputs change.
	private long lastInferenceChangeCounter = -1;

	// Paint-only refresh cadence for time-driven progress (bounded to <= 1/min).
	private long lastRepaintEpochMinute = -1;

	// Route run cursor auto-advance tracking (runtime-only).
	private com.farmutils.route.RouteId lastAutoAdvanceRouteId;
	private int lastAutoAdvanceCursorIndex = -1;
	private PatchId lastAutoAdvancePatchId;
	private PatchState lastAutoAdvancePatchState;
	private PatchSource lastAutoAdvancePatchSource;


	@Inject
	private Varbit4771HerbVarbitObserver varbit4771HerbVarbitObserver;

	@Inject
	private Varbit4771AllotmentVarbitObserver varbit4771AllotmentVarbitObserver;

	@Inject
	private Varbit4771BelladonnaVarbitObserver varbit4771BelladonnaVarbitObserver;

	@Inject
	private Varbit4771BushVarbitObserver varbit4771BushVarbitObserver;

	@Inject
	private Varbit4771CactusVarbitObserver varbit4771CactusVarbitObserver;

	@Inject
	private Varbit4771CalquatVarbitObserver varbit4771CalquatVarbitObserver;

	@Inject
	private Varbit4771HopsVarbitObserver varbit4771HopsVarbitObserver;

	@Inject
	private Varbit4771CoralVarbitObserver varbit4771CoralVarbitObserver;

	@Inject
	private Varbit4771SeaweedVarbitObserver varbit4771SeaweedVarbitObserver;

	@Inject
	private Varbit4772SeaweedVarbitObserver varbit4772SeaweedVarbitObserver;

	@Inject
	private Varbit4771MushroomVarbitObserver varbit4771MushroomVarbitObserver;

	@Inject
	private Varbit4771TreeVarbitObserver varbit4771TreeVarbitObserver;

	@Inject
	private Varbit7905TreeVarbitObserver varbit7905TreeVarbitObserver;

	@Inject
	private Varbit4771FruitTreeVarbitObserver varbit4771FruitTreeVarbitObserver;

	@Inject
	private Varbit4772FruitTreeVarbitObserver varbit4772FruitTreeVarbitObserver;

	@Inject
	private Varbit7909FruitTreeVarbitObserver varbit7909FruitTreeVarbitObserver;

	@Inject
	private Varbit4953GrapesVarbitObserver varbit4953GrapesVarbitObserver;

	@Inject
	private Varbit4772CoralVarbitObserver varbit4772CoralVarbitObserver;

	@Inject
	private Varbit4772BelladonnaVarbitObserver varbit4772BelladonnaVarbitObserver;

	@Inject
	private Varbit4772BushVarbitObserver varbit4772BushVarbitObserver;

	@Inject
	private Varbit4773CalquatVarbitObserver varbit4773CalquatVarbitObserver;

	@Inject
	private Varbit7904CactusVarbitObserver varbit7904CactusVarbitObserver;

	@Inject
	private Varbit4772AllotmentVarbitObserver varbit4772AllotmentVarbitObserver;

	@Inject
	private Varbit4773AllotmentVarbitObserver varbit4773AllotmentVarbitObserver;

	@Inject
	private Varbit4773FlowerVarbitObserver varbit4773FlowerVarbitObserver;

	@Inject
	private Varbit4774AllotmentVarbitObserver varbit4774AllotmentVarbitObserver;

	@Inject
	private Varbit4774HerbVarbitObserver varbit4774HerbVarbitObserver;

	@Inject
	private Varbit4775HerbVarbitObserver varbit4775HerbVarbitObserver;

	@Inject
	private Varbit7906FlowerVarbitObserver varbit7906FlowerVarbitObserver;

	@Inject
	private Varbit7908HesporiVarbitObserver varbit7908HesporiVarbitObserver;

	@Inject
	private Varbit7911AnimaVarbitObserver varbit7911AnimaVarbitObserver;

	
	@Inject
	private Varbit4771HardwoodVarbitObserver varbit4771HardwoodVarbitObserver;

	
	@Inject
	private Varbit4772HardwoodVarbitObserver varbit4772HardwoodVarbitObserver;

	
	@Inject
	private Varbit4773HardwoodVarbitObserver varbit4773HardwoodVarbitObserver;

	
	@Inject
	private Varbit4771SpiritTreeVarbitObserver varbit4771SpiritTreeVarbitObserver;

	
	@Inject
	private Varbit4772SpiritTreeVarbitObserver varbit4772SpiritTreeVarbitObserver;

	
	@Inject
	private Varbit7904SpiritTreeVarbitObserver varbit7904SpiritTreeVarbitObserver;

	
	@Inject
	private Varbit4775CrystalTreeVarbitObserver varbit4775CrystalTreeVarbitObserver;

	
	@Inject
	private Varbit7910CelastrusVarbitObserver varbit7910CelastrusVarbitObserver;

	
	@Inject
	private Varbit7907RedwoodVarbitObserver varbit7907RedwoodVarbitObserver;

	// --- Quest patch observers ---
	@Inject
	private Varbit1033UnferthsPatchVarbitObserver varbit1033UnferthsPatchVarbitObserver;

	@Inject
	private Varbit3714MagicBeansVarbitObserver varbit3714MagicBeansVarbitObserver;

	@Inject
	private Varbit823KeldaHopsVarbitObserver varbit823KeldaHopsVarbitObserver;

	@Inject
	private Varbit9016ElderCadantineVarbitObserver varbit9016ElderCadantineVarbitObserver;

	@Inject
	private Varbit10781EnrichedSnapdragonVarbitObserver varbit10781EnrichedSnapdragonVarbitObserver;

	@Override
	protected void startUp()
	{
		routeStore = new RouteStore();
		routeSessionStore = new RouteSessionStore();
		farmPanel.setRouteStore(routeStore);

		if (patchHighlightOverlay != null)
		{
			patchHighlightOverlay.setRouteContext(routeStore, routeSessionStore);
		}

		if (overlayManager != null && patchHighlightOverlay != null)
		{
			overlayManager.add(patchHighlightOverlay);
		}

		routesPanel = new RoutesPanel(config, routeStore, routeSessionStore, farmPanel.getPatchStore(), farmPanel.getItemManager(), uiStateStore);

		calcPanel = new CalcPanel(config, routeStore, itemManager, clientThread, client);

		rootPanel = new FarmRootPanel(
				config,
				clientUI,
				uiStateStore,
				farmPanel,
				routesPanel,
				calcPanel,
				new FarmStubPanel("Export", "Export and sharing features will be added later.")
		);

		navButton = NavigationButton.builder()
				.tooltip("Farm Utils")
				.icon(ImageUtil.loadImageResource(getClass(), "/icon.png"))
				.panel(rootPanel)
				.build();


		clientToolbar.addNavigation(navButton);
		farmPanel.refreshUiFromConfig();
		lastInferenceChangeCounter = inferenceEngine != null ? inferenceEngine.getChangeCounter() : -1;

		rootPanel.refreshUiFromConfig();
	}

	@Override
	protected void shutDown()
	{
		if (overlayManager != null && patchHighlightOverlay != null)
		{
			overlayManager.remove(patchHighlightOverlay);
		}

		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}

		calcPanel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		// Reset live observers on login / world hops to avoid stale baselines.
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN
				|| gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			lastRegionId = -1;
			varbit4771HerbVarbitObserver.reset();
			varbit4771AllotmentVarbitObserver.reset();
			varbit4772AllotmentVarbitObserver.reset();
			varbit4773AllotmentVarbitObserver.reset();
			varbit4773FlowerVarbitObserver.reset();
			varbit4774AllotmentVarbitObserver.reset();
			varbit4774HerbVarbitObserver.reset();
			varbit4775HerbVarbitObserver.reset();
			varbit7906FlowerVarbitObserver.reset();
			varbit4771BelladonnaVarbitObserver.reset();
			varbit4772BelladonnaVarbitObserver.reset();
			varbit4771BushVarbitObserver.reset();
			varbit4772BushVarbitObserver.reset();
			varbit4771CactusVarbitObserver.reset();
			varbit7904CactusVarbitObserver.reset();
			varbit4771CalquatVarbitObserver.reset();
			varbit4771HopsVarbitObserver.reset();
			varbit4771CoralVarbitObserver.reset();
			varbit4772CoralVarbitObserver.reset();
			varbit4773CalquatVarbitObserver.reset();
			varbit4771SeaweedVarbitObserver.reset();
			varbit4772SeaweedVarbitObserver.reset();
			varbit4771MushroomVarbitObserver.reset();
			varbit4771TreeVarbitObserver.reset();
			varbit7905TreeVarbitObserver.reset();
			varbit4771FruitTreeVarbitObserver.reset();
			varbit4772FruitTreeVarbitObserver.reset();
			varbit7909FruitTreeVarbitObserver.reset();
			varbit4953GrapesVarbitObserver.reset();
			varbit7908HesporiVarbitObserver.reset();
			varbit7911AnimaVarbitObserver.reset();
			varbit4771HardwoodVarbitObserver.reset();
			varbit4772HardwoodVarbitObserver.reset();
			varbit4773HardwoodVarbitObserver.reset();
			varbit4771SpiritTreeVarbitObserver.reset();
			varbit4772SpiritTreeVarbitObserver.reset();
			varbit7904SpiritTreeVarbitObserver.reset();
			varbit4775CrystalTreeVarbitObserver.reset();
			varbit7910CelastrusVarbitObserver.reset();
			varbit7907RedwoodVarbitObserver.reset();
			varbit1033UnferthsPatchVarbitObserver.reset();
			varbit3714MagicBeansVarbitObserver.reset();
			varbit823KeldaHopsVarbitObserver.reset();
			varbit9016ElderCadantineVarbitObserver.reset();
			varbit10781EnrichedSnapdragonVarbitObserver.reset();

			if (gameStateChanged.getGameState() == GameState.LOGGED_IN && calcPanel != null)
			{
				SwingUtilities.invokeLater(calcPanel::refreshForLogin);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}


		// Region changes can briefly surface default/placeholder varbit values (often 0) before
		// the real state arrives. Resetting our baselines when the region id changes prevents
		// false transitions like "weeds -> ready" being interpreted as a planted event.
		if (client.getLocalPlayer() != null)
		{
			final int regionId = client.getLocalPlayer().getWorldLocation().getRegionID();
			if (regionId != lastRegionId)
			{
				lastRegionId = regionId;
				varbit4771HerbVarbitObserver.reset();
				varbit4771AllotmentVarbitObserver.reset();
				varbit4772AllotmentVarbitObserver.reset();
				varbit4773AllotmentVarbitObserver.reset();
				varbit4773FlowerVarbitObserver.reset();
				varbit4774AllotmentVarbitObserver.reset();
				varbit4774HerbVarbitObserver.reset();
				varbit4775HerbVarbitObserver.reset();
				varbit7906FlowerVarbitObserver.reset();
				varbit4771BelladonnaVarbitObserver.reset();
				varbit4772BelladonnaVarbitObserver.reset();
				varbit4771BushVarbitObserver.reset();
				varbit4772BushVarbitObserver.reset();
				varbit4771CactusVarbitObserver.reset();
				varbit7904CactusVarbitObserver.reset();
				varbit4771CalquatVarbitObserver.reset();
				varbit4771HopsVarbitObserver.reset();
				varbit4771CoralVarbitObserver.reset();
				varbit4772CoralVarbitObserver.reset();
				varbit4773CalquatVarbitObserver.reset();
				varbit4771SeaweedVarbitObserver.reset();
				varbit4772SeaweedVarbitObserver.reset();
				varbit4771MushroomVarbitObserver.reset();
				varbit4771TreeVarbitObserver.reset();
				varbit7905TreeVarbitObserver.reset();
				varbit4771FruitTreeVarbitObserver.reset();
				varbit4772FruitTreeVarbitObserver.reset();
				varbit7909FruitTreeVarbitObserver.reset();
				varbit4953GrapesVarbitObserver.reset();
				varbit7908HesporiVarbitObserver.reset();
				varbit7911AnimaVarbitObserver.reset();
				varbit4771HardwoodVarbitObserver.reset();
				varbit4772HardwoodVarbitObserver.reset();
				varbit4773HardwoodVarbitObserver.reset();
				varbit4771SpiritTreeVarbitObserver.reset();
				varbit4772SpiritTreeVarbitObserver.reset();
				varbit7904SpiritTreeVarbitObserver.reset();
				varbit4775CrystalTreeVarbitObserver.reset();
				varbit7910CelastrusVarbitObserver.reset();
				varbit7907RedwoodVarbitObserver.reset();
				varbit1033UnferthsPatchVarbitObserver.reset();
				varbit3714MagicBeansVarbitObserver.reset();
				varbit823KeldaHopsVarbitObserver.reset();
				varbit9016ElderCadantineVarbitObserver.reset();
				varbit10781EnrichedSnapdragonVarbitObserver.reset();
			}
		}

		long before = inferenceEngine.getChangeCounter();

		// v4: first real observation source + time progression spine.
		varbit4771HerbVarbitObserver.onGameTick();
		varbit4771AllotmentVarbitObserver.onGameTick();
		varbit4772AllotmentVarbitObserver.onGameTick();
		varbit4773AllotmentVarbitObserver.onGameTick();
		varbit4773FlowerVarbitObserver.onGameTick();
		varbit4774AllotmentVarbitObserver.onGameTick();
		varbit4774HerbVarbitObserver.onGameTick();
		varbit4775HerbVarbitObserver.onGameTick();
		varbit7906FlowerVarbitObserver.onGameTick();
		varbit4771BelladonnaVarbitObserver.onGameTick();
		varbit4772BelladonnaVarbitObserver.onGameTick();
		varbit4771BushVarbitObserver.onGameTick();
		varbit4772BushVarbitObserver.onGameTick();
		varbit4771CactusVarbitObserver.onGameTick();
		varbit7904CactusVarbitObserver.onGameTick();
		varbit4771CalquatVarbitObserver.onGameTick();
		varbit4771HopsVarbitObserver.onGameTick();
		varbit4771CoralVarbitObserver.onGameTick();
		varbit4772CoralVarbitObserver.onGameTick();
		varbit4773CalquatVarbitObserver.onGameTick();
		varbit4771SeaweedVarbitObserver.onGameTick();
		varbit4772SeaweedVarbitObserver.onGameTick();
		varbit4771MushroomVarbitObserver.onGameTick();
		varbit4771TreeVarbitObserver.onGameTick();
		varbit7905TreeVarbitObserver.onGameTick();
		varbit4771FruitTreeVarbitObserver.onGameTick();
		varbit4772FruitTreeVarbitObserver.onGameTick();
		varbit7909FruitTreeVarbitObserver.onGameTick();
		varbit4953GrapesVarbitObserver.onGameTick();
		varbit7908HesporiVarbitObserver.onGameTick();
		varbit7911AnimaVarbitObserver.onGameTick();
		varbit4771HardwoodVarbitObserver.onGameTick();
		varbit4772HardwoodVarbitObserver.onGameTick();
		varbit4773HardwoodVarbitObserver.onGameTick();
		varbit4771SpiritTreeVarbitObserver.onGameTick();
		varbit4772SpiritTreeVarbitObserver.onGameTick();
		varbit7904SpiritTreeVarbitObserver.onGameTick();
		varbit4775CrystalTreeVarbitObserver.onGameTick();
		varbit7910CelastrusVarbitObserver.onGameTick();
		varbit7907RedwoodVarbitObserver.onGameTick();
		varbit1033UnferthsPatchVarbitObserver.onGameTick();
		varbit3714MagicBeansVarbitObserver.onGameTick();
		varbit823KeldaHopsVarbitObserver.onGameTick();
		varbit9016ElderCadantineVarbitObserver.onGameTick();
		varbit10781EnrichedSnapdragonVarbitObserver.onGameTick();
		inferenceEngine.tick();

		// Cursor progression: if the current route cursor patch transitions EMPTY -> GROWING
		// while a session is RUNNING, advance the cursor to the next patch.
		maybeAutoAdvanceRouteCursor();

		long after = inferenceEngine.getChangeCounter();
		if (after != before && after != lastInferenceChangeCounter)
		{
			lastInferenceChangeCounter = after;
			if (farmPanel != null)
			{
				farmPanel.rebuild();
			}

			if (routesPanel != null && rootPanel != null && rootPanel.isRoutesActive())
			{
				routesPanel.refreshFromStore();
			}
		}

		// Even when inference outputs are unchanged, progress bars may advance due to time-driven
		// stage estimates. Repaint at most once per minute (no rebuild).
		long epochMinute = System.currentTimeMillis() / 60000L;
		if (epochMinute != lastRepaintEpochMinute)
		{
			lastRepaintEpochMinute = epochMinute;
			if (farmPanel != null)
			{
				SwingUtilities.invokeLater(farmPanel::repaint);
			}

			if (routesPanel != null && rootPanel != null && rootPanel.isRoutesActive())
			{
				SwingUtilities.invokeLater(routesPanel::repaint);
			}
		}
	}

	private void maybeAutoAdvanceRouteCursor()
	{
		if (routeSessionStore == null || routeStore == null || farmPanel == null)
		{
			clearAutoAdvanceTracking();
			return;
		}

		final java.util.Optional<RouteSession> sessionOpt = routeSessionStore.getActiveSession();
		if (!sessionOpt.isPresent())
		{
			clearAutoAdvanceTracking();
			return;
		}

		final RouteSession session = sessionOpt.get();
		if (session.getState() != RouteSessionState.RUNNING)
		{
			// Track baseline, but do not auto-advance when paused.
			updateAutoAdvanceBaseline(session);
			return;
		}

		final Route route = routeStore.get(session.getRouteId()).orElse(null);
		if (route == null || route.getPatchIds().isEmpty())
		{
			clearAutoAdvanceTracking();
			return;
		}

		final int routeSize = route.getPatchIds().size();
		final int maxIndex = routeSize - 1;
		final int cursorIndex = Math.max(0, Math.min(maxIndex, session.getCursorIndex()));
		final PatchId cursorPatchId = route.getPatchIds().get(cursorIndex);

		final PatchView view = farmPanel.getPatchStore().view(cursorPatchId);
		final PatchState nowState = view.getRecord().map(PatchRecord::getState).orElse(null);
		final PatchSource nowSource = view.getSource();

		// First tick for this session/cursor: establish baseline only.
		if (lastAutoAdvanceRouteId == null
				|| !session.getRouteId().equals(lastAutoAdvanceRouteId)
				|| cursorIndex != lastAutoAdvanceCursorIndex
				|| cursorPatchId != lastAutoAdvancePatchId)
		{
			lastAutoAdvanceRouteId = session.getRouteId();
			lastAutoAdvanceCursorIndex = cursorIndex;
			lastAutoAdvancePatchId = cursorPatchId;
			lastAutoAdvancePatchState = nowState;
			lastAutoAdvancePatchSource = nowSource;
			return;
		}

		final boolean shouldAdvance =
				lastAutoAdvancePatchSource == PatchSource.INFERRED
						&& nowSource == PatchSource.INFERRED
						&& lastAutoAdvancePatchState == PatchState.EMPTY
						&& nowState == PatchState.GROWING;

		// Update baseline before any cursor mutation (prevents double-advance on same tick).
		lastAutoAdvancePatchState = nowState;
		lastAutoAdvancePatchSource = nowSource;

		if (!shouldAdvance)
		{
			return;
		}

		final boolean advanced = routeSessionStore.advanceCursor(routeSize);
		if (!advanced)
		{
			// If we're already at the end of the route, treat the EMPTY -> GROWING transition
			// as route completion and auto-stop the run.
			if (cursorIndex >= maxIndex)
			{
				routeSessionStore.stopActive();
				clearAutoAdvanceTracking();

				// Session changes are not part of inferenceEngine's change counter; refresh routes UI immediately.
				if (routesPanel != null && rootPanel != null && rootPanel.isRoutesActive())
				{
					routesPanel.refreshFromStore();
					SwingUtilities.invokeLater(routesPanel::repaint);
				}
			}
			return;
		}

		// Refresh baseline to the new cursor position.
		final java.util.Optional<RouteSession> afterOpt = routeSessionStore.getActiveSession();
		if (afterOpt.isPresent())
		{
			updateAutoAdvanceBaseline(afterOpt.get());
		}
		else
		{
			clearAutoAdvanceTracking();
		}

		// Cursor changes are not part of inferenceEngine's change counter; refresh routes UI immediately.
		if (routesPanel != null && rootPanel != null && rootPanel.isRoutesActive())
		{
			routesPanel.refreshFromStore();
			SwingUtilities.invokeLater(routesPanel::repaint);
		}
	}

	private void updateAutoAdvanceBaseline(final RouteSession session)
	{
		if (session == null || routeStore == null || farmPanel == null)
		{
			clearAutoAdvanceTracking();
			return;
		}

		final Route route = routeStore.get(session.getRouteId()).orElse(null);
		if (route == null || route.getPatchIds().isEmpty())
		{
			clearAutoAdvanceTracking();
			return;
		}

		final int routeSize = route.getPatchIds().size();
		final int maxIndex = routeSize - 1;
		final int cursorIndex = Math.max(0, Math.min(maxIndex, session.getCursorIndex()));
		final PatchId cursorPatchId = route.getPatchIds().get(cursorIndex);
		final PatchView view = farmPanel.getPatchStore().view(cursorPatchId);
		final PatchState state = view.getRecord().map(PatchRecord::getState).orElse(null);

		lastAutoAdvanceRouteId = session.getRouteId();
		lastAutoAdvanceCursorIndex = cursorIndex;
		lastAutoAdvancePatchId = cursorPatchId;
		lastAutoAdvancePatchState = state;
		lastAutoAdvancePatchSource = view.getSource();
	}

	private void clearAutoAdvanceTracking()
	{
		lastAutoAdvanceRouteId = null;
		lastAutoAdvanceCursorIndex = -1;
		lastAutoAdvancePatchId = null;
		lastAutoAdvancePatchState = null;
		lastAutoAdvancePatchSource = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("farmutils"))
		{
			return;
		}

		final String key = event.getKey();
		if (key == null)
		{
			return;
		}

		switch (key)
		{
			// Content filtering
			case "hideQuestPatches":
			if (farmPanel != null)
				{
					farmPanel.refreshUiFromConfig();
				}
				if (rootPanel != null)
				{
					rootPanel.refreshActivePanelFromConfig();
				}
				break;
			// Navigation chrome
			case "navContent":
			case "navColumns":
				if (rootPanel != null)
				{
					// Only rebuild the nav grid; avoid touching unrelated chrome.
					rootPanel.rebuildNav();
				}
				break;

			// Text scale affects nav/filter chrome + patch list
			case "textScale":
				if (rootPanel != null)
				{
					rootPanel.refreshUiFromConfig();
				}
				if (farmPanel != null)
				{
					farmPanel.refreshUiFromConfig();
				}
				break;

			case "showFilterSearchIcon":
				if (rootPanel != null)
				{
					rootPanel.refreshUiFromConfig();
				}
				break;

			// Patch list appearance / scrollbar options
			case "emphasizeHeaders":
				case "largerHeadings":
			case "showPatchCategoryPrefix":
				case "secondaryTextIndentPx":

				// State + caret colours
				case "stateColorUnknown":
				case "stateColorEmpty":
				case "stateColorGrowing":
				case "stateColorReady":
				case "stateColorDiseased":
				case "stateColorDead":
				case "expandedCaretMode":
				case "expandedCaretCustomColor":
				case "collapsedCaretMode":
				case "collapsedCaretCustomColor":

			case "scrollbarVisibility":
			case "scrollbarWidth":
			case "scrollbarOutlineStyle":
			case "scrollbarColor":
			case "showScrollButtons":
			case "scrollbarWellBackground":
			case "indentSingleLocationRows":
				if (farmPanel != null)
				{
					farmPanel.refreshUiFromConfig();
				}
				if (rootPanel != null)
				{
					rootPanel.refreshActivePanelFromConfig();
				}
				break;

			// Toolbar chrome background option
			case "toolbarSolidBackground":
				if (rootPanel != null)
				{
					rootPanel.refreshUiFromConfig();
				}
				break;
		}
	}



	@Provides
	FarmutilsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmutilsConfig.class);
	}
}
