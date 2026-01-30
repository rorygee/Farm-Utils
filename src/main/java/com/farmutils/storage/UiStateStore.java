package com.farmutils.storage;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

@Singleton
public class UiStateStore
{
    private static final String GROUP = "farmutils";
    private static final String KEY_PREFIX = "ui.groupCollapsed.";

    @Inject
    private ConfigManager configManager;

    public boolean isGroupCollapsed(String groupName)
    {
        Boolean v = configManager.getConfiguration(GROUP, KEY_PREFIX + groupName, Boolean.class);
        return v != null && v;
    }

    public void setGroupCollapsed(String groupName, boolean collapsed)
    {
        configManager.setConfiguration(GROUP, KEY_PREFIX + groupName, collapsed);
    }

    public void toggleGroupCollapsed(String groupName)
    {
        setGroupCollapsed(groupName, !isGroupCollapsed(groupName));
    }
}
