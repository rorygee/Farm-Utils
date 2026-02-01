package com.farmutils.storage;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.farmutils.model.PatchId;
import net.runelite.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class UiStateStore
{
    private static final String GROUP = "farmutils";
    private static final String KEY_PREFIX = "ui.groupCollapsed.";

    // UI ordering — empty means canonical order
    // Groups are identified by their groupName String (PatchId::getGroup)
    private List<String> groupOrder = new ArrayList<>();

    // Per-group entry ordering: groupName -> ordered PatchIds
    private Map<String, List<PatchId>> entryOrder = new HashMap<>();

    @Inject
    private ConfigManager configManager;

    public boolean isGroupCollapsed(String groupName)
    {
        Boolean v = configManager.getConfiguration(GROUP, KEY_PREFIX + groupName, Boolean.class);
        return v != null && v;
    }

    public List<String> getGroupOrder()
    {
        return groupOrder;
    }

    public Map<String, List<PatchId>> getEntryOrder()
    {
        return entryOrder;
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
