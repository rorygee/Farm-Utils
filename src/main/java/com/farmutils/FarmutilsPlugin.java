package com.farmutils;

import com.farmutils.ui.FarmPanel;
import com.farmutils.ui.FarmRootPanel;
import com.farmutils.ui.FarmStubPanel;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
	name = "Farm Utils",
	description = "Quality-of-life farming patch state tracker",
	tags = {"farming", "utility", "qol"}
)
public class FarmutilsPlugin extends Plugin
{
	@Inject private FarmutilsConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private FarmPanel farmPanel;

	private FarmRootPanel rootPanel;

	private NavigationButton navButton;

	@Inject
	private Client client;

	@Override
	protected void startUp()
	{
		rootPanel = new FarmRootPanel(
				config,
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

	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!"farmutils".equals(e.getGroup()))
		{
			return;
		}

		if (farmPanel != null)
		{
			farmPanel.refreshUiFromConfig();
		}

		if (rootPanel != null)
		{
			rootPanel.refreshUiFromConfig();
		}
	}


	@Provides
	FarmutilsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmutilsConfig.class);
	}
}
