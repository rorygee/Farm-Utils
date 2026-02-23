package com.farmutils;

import com.farmutils.ui.FarmPanel;
import com.farmutils.ui.FarmRootPanel;
import com.farmutils.ui.FarmStubPanel;
import com.farmutils.storage.UiStateStore;
import com.farmutils.infer.InferenceEngine;
import com.farmutils.observe.Varbit4771AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4771BelladonnaVarbitObserver;
import com.farmutils.observe.Varbit4771BushVarbitObserver;
import com.farmutils.observe.Varbit4771CactusVarbitObserver;
import com.farmutils.observe.Varbit4771CalquatVarbitObserver;
import com.farmutils.observe.Varbit4771CoralVarbitObserver;
import com.farmutils.observe.Varbit4771HopsVarbitObserver;
import com.farmutils.observe.Varbit4772CoralVarbitObserver;
import com.farmutils.observe.Varbit4772BelladonnaVarbitObserver;
import com.farmutils.observe.Varbit4772BushVarbitObserver;
import com.farmutils.observe.Varbit4773CalquatVarbitObserver;
import com.farmutils.observe.Varbit7904CactusVarbitObserver;
import com.farmutils.observe.Varbit4771HerbVarbitObserver;
import com.farmutils.observe.Varbit4772AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4773AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4773FlowerVarbitObserver;
import com.farmutils.observe.Varbit4774AllotmentVarbitObserver;
import com.farmutils.observe.Varbit4774HerbVarbitObserver;
import com.farmutils.observe.Varbit4775HerbVarbitObserver;
import com.farmutils.observe.Varbit7906FlowerVarbitObserver;
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
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

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

	private NavigationButton navButton;

	@Inject
	private Client client;

	@Inject
	private InferenceEngine inferenceEngine;

	// Track inference updates so the UI can refresh only when outputs change.
	private long lastInferenceChangeCounter = -1;

	// Paint-only refresh cadence for time-driven progress (bounded to <= 1/min).
	private long lastRepaintEpochMinute = -1;


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

	@Override
	protected void startUp()
	{
		rootPanel = new FarmRootPanel(
				config,
				clientUI,
				uiStateStore,
				farmPanel,
				new FarmStubPanel("Routes", "Route planning will be added later."),
				new FarmStubPanel("Calc", "Profit and XP calculations will be added later."),
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
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
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
		inferenceEngine.tick();

		long after = inferenceEngine.getChangeCounter();
		if (after != before && after != lastInferenceChangeCounter)
		{
			lastInferenceChangeCounter = after;
			if (farmPanel != null)
			{
				farmPanel.rebuild();
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
		}
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
				if (farmPanel != null)
				{
					farmPanel.refreshUiFromConfig();
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
