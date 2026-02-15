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

	// Runtime-only: governs list shape/presentation (grouped vs flat). Not persisted.
	private ViewMode patchListViewMode = ViewMode.DEFAULT;

	// Runtime-only: governs ordering without writing back into reorder data.
	private SortMode patchListSortMode = SortMode.DEFAULT;

    // Runtime-only (not persisted yet): gates group + row drag reordering.
    private boolean reorderModeEnabled = false;

    // Runtime-only: toolbar visibility (Option A). Not persisted.
    private boolean toolbarHidden = false;

	public enum ListViewMode
{
        ALL,
                ACTIVE // placeholder for future (e.g., hide disabled/irrelevant)
                    }

	/**
	 * View mode for the patches list UI.
	 *
	 * DEFAULT: current grouped list.
	 * FLAT: no grouping; a single flat list.
	 */
	public enum ViewMode
	{
		DEFAULT,
		FLAT,
		CLEAN
	}

	/**
	 * Sort mode for the patches list.
	 *
	 * DEFAULT: respects user drag order (stored in UiStateStore ordering).
	 * ALPHABETICAL: transient sort; does not write back.
	 */
	public enum SortMode
	{
		DEFAULT,
		ALPHABETICAL,
		PATCH_LABEL
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

	public ViewMode getPatchListViewMode()
	{
		return patchListViewMode;
	}

	/**
	 * Sets the patches list view mode.
	 *
	 * Contract: changing ViewMode resets collapse state.
	 */
	public void setPatchListViewMode(ViewMode next)
	{
		ViewMode resolved = (next != null) ? next : ViewMode.DEFAULT;
		if (this.patchListViewMode == resolved)
		{
			return;
		}

		this.patchListViewMode = resolved;

		// Contract: FLAT view is scan-first and does not support patch reordering.
		if (this.patchListViewMode == ViewMode.FLAT)
		{
			this.reorderModeEnabled = false;
		}

		// Collapse is currently persisted via config; for now we reset it by clearing the stored flags.
		// (Later phases can move collapse to runtime-only state without changing callers.)
		resetAllGroupCollapse();
	}

	public SortMode getPatchListSortMode()
	{
		return patchListSortMode;
	}

	/**
	 * Sets the patches list sort mode.
	 *
	 * Contract: if sort != DEFAULT then reorder is forced OFF.
	 */
	public void setPatchListSortMode(SortMode next)
	{
		SortMode resolved = (next != null) ? next : SortMode.DEFAULT;
		if (this.patchListSortMode == resolved)
		{
			return;
		}

		this.patchListSortMode = resolved;
		if (this.patchListSortMode != SortMode.DEFAULT)
		{
			this.reorderModeEnabled = false;
		}
	}

	private void resetAllGroupCollapse()
	{
		// Best-effort; if ConfigManager isn't injected yet, just no-op.
		if (configManager == null)
		{
			return;
		}

		// Collapse keys are keyed by PatchId::getGroup.
		Set<String> groups = new LinkedHashSet<>();
		for (PatchId id : PatchId.values())
		{
			if (id.getGroup() != null)
			{
				groups.add(id.getGroup());
			}
		}

		for (String g : groups)
		{
			setGroupCollapsed(g, false);
		}
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

    public boolean isToolbarHidden()
    {
        return toolbarHidden;
    }

    public void setToolbarHidden(boolean hidden)
    {
        this.toolbarHidden = hidden;
    }
}
