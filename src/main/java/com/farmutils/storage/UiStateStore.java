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

	// Runtime-only: user-intent hide/disable state per patch.
	// Default is enabled (not present in the set).
    private final Set<PatchId> disabledPatches = new HashSet<>();

	// Runtime-only: user-intent hidden state per main heading group (e.g. Activity/Allotment/etc.).
	// Groups are identified by their canonical groupName String (PatchId::getGroup).
	// Default is visible (not present in the set).
	private final Set<String> hiddenGroups = new HashSet<>();

	// Runtime-only: governs whether disabled patches are rendered in the list.
	// When false, disabled patches are filtered out.
	private boolean showDisabledPatches = false;
	private ListViewMode viewMode = ListViewMode.ALL;

	// Runtime-only: governs list shape/presentation (grouped vs flat). Not persisted.
	private ViewMode patchListViewMode = ViewMode.DEFAULT;

	// Runtime-only: governs ordering without writing back into reorder data.
	private SortMode patchListSortMode = SortMode.DEFAULT;

	// Runtime-only: patch state indicator line presentation (cycled via toolbar "L").
	private StateIndicatorMode stateIndicatorMode = StateIndicatorMode.FULL_WIDTH;

    // Runtime-only (not persisted yet): gates group + row drag reordering.
    private boolean reorderModeEnabled = false;

    // Runtime-only: toolbar visibility (Option A). Not persisted.
    private boolean toolbarHidden = false;

	// --- Patch selection (runtime-only) ---
	// Selection is PatchId-based so it can survive list rebuilds (filters/view changes).
	private final Set<PatchId> selectedPatches = new HashSet<>();
	private PatchId selectionAnchor = null;
	// Last rendered visible order (post-filter/post-sort/post-group/flatten).
	// Used for shift-range selection and for returning selected patches in visible order.
	private List<PatchId> lastVisiblePatchOrder = List.of();

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
		ALPHABETICAL
	}


	/**
	 * Controls how patch state is indicated on each row.
	 * Runtime-only: no config persistence yet.
	 */
	public enum StateIndicatorMode
	{
		OFF,
		FULL_WIDTH,
		RIGHT_STRIP,
		TITLE_ONLY,
		FULL_AND_TITLE
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

	public StateIndicatorMode getStateIndicatorMode()
	{
		return stateIndicatorMode;
	}

	public void setStateIndicatorMode(StateIndicatorMode mode)
	{
		this.stateIndicatorMode = (mode != null) ? mode : StateIndicatorMode.FULL_WIDTH;
	}

	public StateIndicatorMode cycleStateIndicatorMode()
	{
		StateIndicatorMode[] modes = StateIndicatorMode.values();
		int idx = 0;
		StateIndicatorMode cur = getStateIndicatorMode();
		for (int i = 0; i < modes.length; i++)
		{
			if (modes[i] == cur)
			{
				idx = i;
				break;
			}
		}
		StateIndicatorMode next = modes[(idx + 1) % modes.length];
		setStateIndicatorMode(next);
		return next;
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
		return id != null && disabledPatches.contains(id);
	}

	public void setPatchDisabled(PatchId id, boolean disabled)
	{
		if (id == null)
		{
			return;
		}
		if (disabled)
		{
			disabledPatches.add(id);
		}
		else
		{
			disabledPatches.remove(id);
		}
	}

	public void togglePatchDisabled(PatchId id)
	{
		setPatchDisabled(id, !isPatchDisabled(id));
	}

	public Set<PatchId> getDisabledPatchesView()
	{
		return Collections.unmodifiableSet(disabledPatches);
	}

	public boolean isGroupHidden(String groupKey)
	{
		return groupKey != null && hiddenGroups.contains(groupKey);
	}

	public void setGroupHidden(String groupKey, boolean hidden)
	{
		if (groupKey == null)
		{
			return;
		}
		if (hidden)
		{
			hiddenGroups.add(groupKey);
		}
		else
		{
			hiddenGroups.remove(groupKey);
		}
	}

	public void toggleGroupHidden(String groupKey)
	{
		setGroupHidden(groupKey, !isGroupHidden(groupKey));
	}

	public Set<String> getHiddenGroupsView()
	{
		return Collections.unmodifiableSet(hiddenGroups);
	}

	public boolean isShowDisabledPatches()
	{
		return showDisabledPatches;
	}

	public void setShowDisabledPatches(boolean v)
	{
		this.showDisabledPatches = v;
	}

	public void toggleShowDisabledPatches()
	{
		this.showDisabledPatches = !this.showDisabledPatches;
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

	// -------------------------------------------------------------------------
	// Selection API (runtime-only)
	// -------------------------------------------------------------------------

	public boolean isSelected(PatchId id)
	{
		return id != null && selectedPatches.contains(id);
	}

	public Set<PatchId> getSelectedPatchesView()
	{
		return Collections.unmodifiableSet(selectedPatches);
	}

	public void clearSelection()
	{
		selectedPatches.clear();
		selectionAnchor = null;
	}

	public void selectOnly(PatchId id)
	{
		selectedPatches.clear();
		if (id != null)
		{
			selectedPatches.add(id);
		}
	}

	public void toggleSelected(PatchId id)
	{
		if (id == null)
		{
			return;
		}
		if (selectedPatches.contains(id))
		{
			selectedPatches.remove(id);
		}
		else
		{
			selectedPatches.add(id);
		}
	}

	public PatchId getSelectionAnchor()
	{
		return selectionAnchor;
	}

	public void setSelectionAnchor(PatchId id)
	{
		this.selectionAnchor = id;
	}

	public void setLastVisiblePatchOrder(List<PatchId> order)
	{
		if (order == null)
		{
			this.lastVisiblePatchOrder = List.of();
			return;
		}
		// Store a defensive copy (runtime only).
		this.lastVisiblePatchOrder = List.copyOf(order);
	}

	public List<PatchId> getLastVisiblePatchOrder()
	{
		return lastVisiblePatchOrder;
	}

	/**
	 * Shift-range selection.
	 *
	 * @param visibleOrder the list order the user can see (post-filter/post-sort)
	 * @param anchor starting point of the range
	 * @param end ending point of the range
	 * @param additive if true, adds the range to the existing selection; otherwise replaces it
	 */
	public void selectRange(List<PatchId> visibleOrder, PatchId anchor, PatchId end, boolean additive)
	{
		if (visibleOrder == null || visibleOrder.isEmpty() || end == null)
		{
			if (!additive)
			{
				selectOnly(end);
			}
			else
			{
				selectedPatches.add(end);
			}
			return;
		}

		int a = (anchor != null) ? visibleOrder.indexOf(anchor) : -1;
		int b = visibleOrder.indexOf(end);
		if (b < 0)
		{
			// End isn't currently visible; treat as a single selection.
			if (!additive)
			{
				selectOnly(end);
			}
			else
			{
				selectedPatches.add(end);
			}
			return;
		}

		if (a < 0)
		{
			// No valid anchor in the visible list.
			if (!additive)
			{
				selectOnly(end);
			}
			else
			{
				selectedPatches.add(end);
			}
			return;
		}

		int lo = Math.min(a, b);
		int hi = Math.max(a, b);
		if (!additive)
		{
			selectedPatches.clear();
		}
		for (int i = lo; i <= hi; i++)
		{
			PatchId id = visibleOrder.get(i);
			if (id != null)
			{
				selectedPatches.add(id);
			}
		}
	}

	/**
	 * Selected patches ordered as they appear in the last rendered list.
	 * Useful for future "Add selected patches to route" actions.
	 */
	public List<PatchId> getSelectedPatchesInVisibleOrder()
	{
		if (lastVisiblePatchOrder == null || lastVisiblePatchOrder.isEmpty() || selectedPatches.isEmpty())
		{
			return List.of();
		}
		List<PatchId> out = new ArrayList<>();
		for (PatchId id : lastVisiblePatchOrder)
		{
			if (selectedPatches.contains(id))
			{
				out.add(id);
			}
		}
		return out;
	}
}
