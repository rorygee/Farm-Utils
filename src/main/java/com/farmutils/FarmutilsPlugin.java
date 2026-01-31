package com.farmutils;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.farmutils.ui.FarmPanel;
import javax.inject.Inject;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

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

	private NavigationButton navButton;

	@Inject
	private Client client;

	@Override
	protected void startUp()
	{
		navButton = NavigationButton.builder()
				.tooltip("Farm Utils")
				.icon(ImageUtil.loadImageResource(getClass(), "/icon.png"))
				.panel(farmPanel)
				.build();

		clientToolbar.addNavigation(navButton);
		farmPanel.refreshUiFromConfig();
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
	}


	@Provides
	FarmutilsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmutilsConfig.class);
	}
}
