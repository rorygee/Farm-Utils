package com.farmutils.storage;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.farmutils.model.PatchId;
import net.runelite.client.config.ConfigManager;

import java.util.*;

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

    private final Set<PatchId> disabledPatches = new HashSet<>();
private ListViewMode viewMode = ListViewMode.ALL;

    // Runtime-only (not persisted yet): gates group + row drag reordering.
    private boolean reorderModeEnabled = false;

        public enum ListViewMode
{
        ALL,
                ACTIVE // placeholder for future (e.g., hide disabled/irrelevant)
                    }

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
    public ListViewMode getViewMode()
{
        return viewMode;
    }

        public void setViewMode(ListViewMode viewMode)
{
        this.viewMode = viewMode != null ? viewMode : ListViewMode.ALL;
    }

        public boolean isPatchDisabled(PatchId id)
{
        return disabledPatches.contains(id);
    }

        public void setPatchDisabled(PatchId id, boolean disabled)
{
        if (disabled)
            {
                        disabledPatches.add(id);
        }
        else
        {
                    disabledPatches.remove(id);
        }
    }

    public boolean isReorderModeEnabled()
    {
        return reorderModeEnabled;
    }

    public void setReorderModeEnabled(boolean enabled)
    {
        this.reorderModeEnabled = enabled;
    }
}
