package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchId;
import com.farmutils.model.PatchQualifier;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchView;
import com.farmutils.route.RouteStore;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.Scrollable;
import javax.swing.JViewport;
import java.awt.Rectangle;


import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;

import java.awt.event.MouseWheelEvent;
import javax.swing.JScrollBar;
import javax.swing.Scrollable;

import java.awt.MouseInfo;
import java.awt.PointerInfo;
import javax.swing.Timer;
import javax.swing.JViewport;

import java.util.Locale;


public class FarmPanel extends JPanel
{

    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;

    // 1px divider color
    private static final Color DIVIDER = ColorScheme.DARKER_GRAY_COLOR;

    private static final Color HEADER_ORANGE =
            hasBrandOrange() ? ColorScheme.BRAND_ORANGE : new Color(255, 152, 31);
    private static final Color TRI_DISABLED = ColorScheme.MEDIUM_GRAY_COLOR;

    private final PatchStore store;
    private final UiStateStore uiStateStore;
    private final ClientUI clientUI;
    private final FarmutilsConfig config;
    private final ItemManager itemManager;

    /** Runtime-only: optional route store used for patch context menu actions. */
    private RouteStore routeStore;

    private final JScrollPane scrollPane;

    private final JPanel container = new ScrollContentPanel.ViewportWidthPanel();

    private final JPanel list = new JPanel();
    private String filterText = "";

    private FilterCaseMode filterCaseMode = FilterCaseMode.INSENSITIVE;

    // Parsed representation of the current filterText. Updated in setFilterQuery().
    private FilterQuery parsedFilter = FilterQuery.empty();

    private List<String> lastCanonicalGroupOrder = Collections.emptyList();

    // Updated each rebuild: groups that currently render a collapsible header (after filters/view mode).
    private final java.util.List<String> visibleCollapsibleGroups = new java.util.ArrayList<>();

    /**
     * Optional callback invoked on the EDT after a rebuild finishes.
     * Used by the toolbar to refresh enabled/disabled states based on what is currently visible.
     */
    private Runnable onAfterRebuild;

    public void setOnAfterRebuild(Runnable onAfterRebuild)
    {
        this.onAfterRebuild = onAfterRebuild;
    }


    private static final String PROP_GROUP_DRAG_HANDLE = "farmutils.groupDragHandle";
    private static final String PROP_PATCH_DRAG_HANDLE = "farmutils.patchDragHandle";

    private static final String PROP_LOCATION_KEY = "farmutils.locationKey";
    private static final String PROP_GROUP_NAME = "farmutils.groupName";
    private static final String PROP_PATCH_ID = "farmutils.patchId";

    private enum DragMode
    {
        ROW,
        LOCATION_BLOCK
    }

    private static class BlockStart
    {
        final JComponent component;
        final PatchId firstPatchId;
        final String locationKeyOrNull;

        BlockStart(JComponent component, PatchId firstPatchId, String locationKeyOrNull)
        {
            this.component = component;
            this.firstPatchId = firstPatchId;
            this.locationKeyOrNull = locationKeyOrNull;
        }
    }

    private final GroupDragController groupDrag = new GroupDragController();

    private final PatchDragController patchDrag = new PatchDragController();

    private final JPanel dropLine = new JPanel();
    {
        dropLine.setOpaque(true);
        dropLine.setBackground(HEADER_ORANGE); // reuse existing color
        dropLine.setPreferredSize(new Dimension(1, 2));
        dropLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
    }


    @Inject
    public FarmPanel(PatchStore store, UiStateStore uiStateStore, ClientUI clientUI, FarmutilsConfig config, ItemManager itemManager)
    {
        super(new BorderLayout());

        this.store = store;
        this.uiStateStore = uiStateStore;
        this.clientUI = clientUI;
        this.config = config;
        this.itemManager = itemManager;

        // Painted “floor” for this view
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(null);

        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        // Keep a stable top inset for the first group header regardless of whether
        // the scroll bar gutter is currently visible.
        container.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        list.setFocusable(true);
        // then the list
        container.add(list);

        // Gate 3: only the patch list scrolls (internal scroll pane).
        this.scrollPane = new JScrollPane(
                container,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );

        this.scrollPane.setViewportBorder(
                BorderFactory.createEmptyBorder(0, 0, 0, 0)
        );
        this.scrollPane.setBorder(null);
        this.scrollPane.setOpaque(false);
        this.scrollPane.getViewport().setOpaque(false);
        this.scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        UiScrollbars.apply(this.scrollPane, config);

        installClickOutsideSelectionClear();

        add(this.scrollPane, BorderLayout.CENTER);

        JPanel bottomDivider = new JPanel();


        JScrollBar vbar = this.scrollPane.getVerticalScrollBar();

        Runnable updateBottomDivider = () ->
        {
            int max = vbar.getMaximum();
            int visible = vbar.getVisibleAmount();
            boolean scrollable = visible > 0 && max > visible;
            bottomDivider.setVisible(scrollable);
        };

        vbar.getModel().addChangeListener(e -> updateBottomDivider.run());
        SwingUtilities.invokeLater(updateBottomDivider);

        bottomDivider.setPreferredSize(new Dimension(1, 1));
        bottomDivider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        bottomDivider.setOpaque(true);
        bottomDivider.setBackground(DIVIDER); // reuse your existing divider color
        bottomDivider.setVisible(false);

        add(bottomDivider, BorderLayout.SOUTH);

        applyScrollbarConfig();

        // Ensure the list is populated immediately on first open.
        rebuild();
    }

    /**
     * Provides access to the runtime-only RouteStore so patch context-menu actions can add patches
     * to routes without introducing persistence or cross-panel coupling.
     */
    public void setRouteStore(RouteStore routeStore)
    {
        this.routeStore = routeStore;

        // Rebuild so newly-created PatchRow instances can surface the Routes context-menu.
        if (SwingUtilities.isEventDispatchThread())
        {
            rebuild();
        }
        else
        {
            SwingUtilities.invokeLater(this::rebuild);
        }
    }

    /**
     * Runtime-only accessors for sibling panels (e.g. Routes) that need to
     * render patch-derived visuals without duplicating ownership of stores.
     *
     * Contract: read-only usage; do not mutate selection semantics from other panels.
     */
    public PatchStore getPatchStore()
    {
        return store;
    }

    public ItemManager getItemManager()
    {
        return itemManager;
    }

    /**
     * Task F: Clicking empty space in the list viewport clears selection.
     *
     * Notes:
     * - Only triggers on left-click.
     * - Mouse events only reach the viewport when clicking on "empty" space (not on child rows/headers).
     * - Does not interfere with scrolling, drag handles, right-click menus, or row hover.
     */
    private void installClickOutsideSelectionClear()
    {
        JViewport vp = this.scrollPane != null ? this.scrollPane.getViewport() : null;
        if (vp == null)
        {
            return;
        }

        vp.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (!SwingUtilities.isLeftMouseButton(e))
                {
                    return;
                }

                if (uiStateStore == null || uiStateStore.getSelectedPatchesView().isEmpty())
                {
                    return;
                }

                uiStateStore.clearSelection();
                // Paint-only: selection is purely visual; no rebuild needed.
                list.repaint();
            }
        });
    }

    public void refreshUiFromConfig()
    {
        applyScrollbarConfig();
        rebuild();
    }

    public void setFilterText(String text)
    {
        setFilterQuery(text, FilterCaseMode.INSENSITIVE);
    }

    /**
     * Filter query contract:
     * - default: case-insensitive
     * - override: case-sensitive (future UI/query language)
     */
    public void setFilterQuery(String text, FilterCaseMode caseMode)
    {
        this.filterText = (text == null) ? "" : text.trim();
        this.filterCaseMode = (caseMode == null) ? FilterCaseMode.INSENSITIVE : caseMode;

        this.parsedFilter = FilterQuery.parse(this.filterText, this.filterCaseMode);
        rebuild();
    }


    /**
     * Groups that currently render a collapsible header (after filters + current view mode).
     * Used by toolbar "Collapse/Expand all" to operate only on visible sections.
     */
    public java.util.List<String> getVisibleCollapsibleGroups()
    {
        return new java.util.ArrayList<>(visibleCollapsibleGroups);
    }

    public boolean hasCollapsibleHeaders()
    {
        return !visibleCollapsibleGroups.isEmpty();
    }

    public void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            list.removeAll();
            visibleCollapsibleGroups.clear();

            boolean hasFilter = filterText != null && !filterText.isEmpty();

			boolean showDisabled = uiStateStore != null && uiStateStore.isShowDisabledPatches();

			// Runtime-only: capture the exact visible render order of PatchIds for shift-range selection
			// and future Routes panel actions ("selected patches in visible order").
			java.util.List<PatchId> visiblePatchOrder = new java.util.ArrayList<>();

            Map<String, List<PatchId>> grouped = Arrays.stream(PatchId.values())
                .filter(id -> !(config.hideQuestPatches() && id.getQualifier() == PatchQualifier.QUEST))
				// Centralized include decision: when show-hidden is OFF, filter out both disabled patches
				// and patches whose main heading group is hidden.
				.filter(id -> shouldIncludePatch(id, showDisabled))

                .sorted(
                            Comparator.comparing(PatchId::getGroup)
                                    .thenComparing(id -> {
                                        // Stable primary ordering for multi-slot locations
                                        String k = id.getLocationName();
                                        if (k != null) return k;
                                        k = id.getLocationKey();
                                        if (k != null) return k;
                                        return id.getLabel();
                                    })
                                    .thenComparing((PatchId id) ->
                                    {
                                        String s = id.getSlotLabel();
                                        if (s == null)
                                        {
                                            s = id.getLabel();
                                        }

                                        Integer n = tryParsePlotNumber(s);
                                        // Sort numeric plots first by number, otherwise by string.
                                        // We return a compound key by encoding "hasNumber" and the number.
                                        // (0, n) comes before (1, 0).
                                        if (n != null)
                                        {
                                            return String.format("0:%03d", n);
                                        }
                                        return "1:" + s;
                                    })

                    )

                    .collect(Collectors.groupingBy(
                            PatchId::getGroup,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            boolean firstGroup = true;

            List<Map.Entry<String, List<PatchId>>> orderedGroups =
                    PatchOrderingResolver.resolveGroupOrder(grouped, uiStateStore);

            boolean showIndicator = uiStateStore.getPatchListViewMode() != UiStateStore.ViewMode.CLEAN;
            boolean showLocationHeaders = true;

            // ViewMode: DEFAULT is grouped (current behavior). FLAT is a single list without group headers.
            if (uiStateStore.getPatchListViewMode() == UiStateStore.ViewMode.FLAT)
            {
                java.util.List<PatchId> flatVisible = new java.util.ArrayList<>();
                for (Map.Entry<String, List<PatchId>> entry : orderedGroups)
                {
                    String groupName = entry.getKey();
                    List<PatchId> orderedIds =
                            PatchOrderingResolver.resolveEntryOrder(groupName, entry.getValue(), uiStateStore);

                    orderedIds = applySort(orderedIds);

                    for (PatchId id : orderedIds)
                    {
                        if (matchesFilter(id, groupName))
                        {
                            flatVisible.add(id);
                        }
                    }
                }

                if (!flatVisible.isEmpty())
                {
                    JPanel entriesPanel = new JPanel();
                    entriesPanel.setLayout(new BoxLayout(entriesPanel, BoxLayout.Y_AXIS));
                    entriesPanel.setOpaque(false);
                    entriesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    entriesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

                    // Render-only location headers: shown only when 2+ entries share the same locationKey.
                    Map<String, Long> locationCounts = flatVisible.stream()
                            .map(PatchId::getLocationKey)
                            .filter(k -> k != null)
                            .collect(Collectors.groupingBy(k -> k, Collectors.counting()));

                    java.util.Set<String> seenLocations = new java.util.HashSet<>();
                    java.util.List<JComponent> bodyItems = new java.util.ArrayList<>();

                    for (PatchId id : flatVisible)
                    {
                        String groupName = id.getGroup();
                        String locationKey = id.getLocationKey();
                        boolean isMultiSlot = showLocationHeaders && locationKey != null && locationCounts.getOrDefault(locationKey, 0L) >= 2;
                        boolean forceLocationHeader = showLocationHeaders && locationKey != null && (
                                "Activity".equals(groupName) || id == PatchId.QUEST_UNFERTHS_PATCH
                        );

                        if ((isMultiSlot || forceLocationHeader) && seenLocations.add(locationKey))
                        {
                            String locationName = id.getLocationName() != null ? id.getLocationName() : id.getLabel();
                            LocationHeaderRow locHeader = new LocationHeaderRow(locationName, config);
                            locHeader.setReorderHandleVisible(false); // flat view does not support location-block ordering yet
                            JComponent headerWrap = fullWidth(locHeader);
                            headerWrap.putClientProperty(PROP_GROUP_NAME, groupName);
                            headerWrap.putClientProperty(PROP_LOCATION_KEY, locationKey);
                            bodyItems.add(headerWrap);
                        }

                        PatchRow row = new PatchRow(
                                id,
                                store,
                                routeStore,
                                uiStateStore,
                                itemManager,
                                config,
                                showIndicator,
                                null,
                                null,
                                this::rebuild);
						// Record visible order in the exact order rows are rendered.
						visiblePatchOrder.add(id);
                        // No patch reordering in FLAT view.
                        row.setReorderHandleVisible(false);

                        JComponent rowWrap = fullWidth(row);
                        rowWrap.putClientProperty(PROP_GROUP_NAME, groupName);
                        rowWrap.putClientProperty(PROP_PATCH_ID, id);
                        rowWrap.putClientProperty(PROP_LOCATION_KEY, locationKey);

                        // Indent child rows under a location header. Optionally, also indent single-row patches
                        // so they align with child rows even when no location header is rendered.
                        boolean indentRow = (isMultiSlot || forceLocationHeader) ||
                                (showLocationHeaders && config != null && config.indentSingleLocationRows());
                        if (indentRow)
                        {
                            int indent = Math.max(8, Math.round(10 * config.textScale().multiplier()));
                            rowWrap.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0));
                        }

                        bodyItems.add(rowWrap);
                    }

                    for (int i = 0; i < bodyItems.size(); i++)
                    {
                        entriesPanel.add(bodyItems.get(i));
                        if (i < bodyItems.size() - 1)
                        {
                            entriesPanel.add(divider());
                        }
                    }

                    list.add(entriesPanel);
                }

				// Selection uses visible order; keep it fresh even in early-return branches.
				if (uiStateStore != null)
				{
					uiStateStore.setLastVisiblePatchOrder(visiblePatchOrder);
				}

                list.revalidate();
                list.repaint();
                return;
            }

            for (Map.Entry<String, List<PatchId>> entry : orderedGroups)
            {
                String groupName = entry.getKey();

                List<PatchId> orderedIds =
                        PatchOrderingResolver.resolveEntryOrder(groupName, entry.getValue(), uiStateStore);

                orderedIds = applySort(orderedIds);

                List<PatchId> visibleIds = orderedIds.stream()
                        .filter(id -> matchesFilter(id, groupName))
                        .collect(Collectors.toList());

				// Header suppression rule:
				// - show-hidden OFF: only render group header if it has at least one visible child
				// - show-hidden ON: always render the group header (even if filter yields zero children)
				if (visibleIds.isEmpty() && !showDisabled)
				{
					continue;
				}

                JPanel groupBlock = new JPanel();
                groupBlock.setLayout(new BoxLayout(groupBlock, BoxLayout.Y_AXIS));
                groupBlock.setOpaque(false);

                // IMPORTANT: prevent centering/shrink during relayout
                groupBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
                groupBlock.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

                groupBlock.putClientProperty("farmutils.groupName", groupName);


                if (!firstGroup)
                {
                    JPanel entriesPanel = new JPanel();
                    entriesPanel.setLayout(new BoxLayout(entriesPanel, BoxLayout.Y_AXIS));
                    entriesPanel.setOpaque(false);
                    entriesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    entriesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
                    // First divider between header and body
                    entriesPanel.add(divider());
                }
                firstGroup = false;

                boolean collapsed = uiStateStore.isGroupCollapsed(groupName);

                // while filtering, show as expanded (don’t imply hidden results)
                boolean collapsedForHeader = hasFilter ? false : collapsed;

                AggregateState aggregate = collapsedForHeader
                        ? aggregateStateForCollapsedHeader(visibleIds)
                        : AggregateState.UNKNOWN;

                boolean reorderEnabled = uiStateStore.isReorderModeEnabled();
                visibleCollapsibleGroups.add(groupName);
				// Pass the full set of patches in this main group so "Hide group" can disable all inner patches.
				// IMPORTANT: use orderedIds (pre-filter) so the action is not dependent on search/view filters.
				JComponent header = fullWidth(createGroupHeader(groupName, orderedIds, collapsedForHeader, aggregate, reorderEnabled, showDisabled));
                groupBlock.add(header);

                // Bind drag handle for this group (handle-only; does not interfere with expand/collapse).
                // Reorder mode is runtime-only and gates *all* drag behaviors.
                if (!hasFilter && uiStateStore.isReorderModeEnabled())
                {
                    Object h = header.getClientProperty(PROP_GROUP_DRAG_HANDLE);
                    if (h instanceof JComponent)
                    {
                        groupDrag.bind((JComponent) h, list, groupBlock);
                    }
                }

                boolean showBody = (!collapsed || hasFilter);
				// Avoid dangling dividers / empty body panels when a group header is shown due to
				// show-hidden override but has zero visible children under current filters.
                if (showBody && !visibleIds.isEmpty())
                {
                    groupBlock.add(divider());

                    JPanel entriesPanel = new JPanel();
                    entriesPanel.setLayout(new BoxLayout(entriesPanel, BoxLayout.Y_AXIS));
                    entriesPanel.setOpaque(false);
                    entriesPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    entriesPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

                    // Render-only location headers: shown only when 2+ entries share the same locationKey.
                    Map<String, Long> locationCounts = visibleIds.stream()
                            .map(PatchId::getLocationKey)
                            .filter(k -> k != null)
                            .collect(Collectors.groupingBy(k -> k, Collectors.counting()));

                    java.util.Set<String> seenLocations = new java.util.HashSet<>();
                    java.util.List<JComponent> bodyItems = new java.util.ArrayList<>();

                    for (PatchId id : visibleIds)
                    {
                        String locationKey = id.getLocationKey();
                        boolean isMultiSlot = showLocationHeaders && locationKey != null && locationCounts.getOrDefault(locationKey, 0L) >= 2;
                        boolean forceLocationHeader = showLocationHeaders && locationKey != null && (
                                "Activity".equals(groupName) || id == PatchId.QUEST_UNFERTHS_PATCH
                        );

                        if ((isMultiSlot || forceLocationHeader) && seenLocations.add(locationKey))
                        {
                            String locationName = id.getLocationName() != null ? id.getLocationName() : id.getLabel();
                            LocationHeaderRow locHeader = new LocationHeaderRow(locationName, config);
                            locHeader.setReorderHandleVisible(!hasFilter && uiStateStore.isReorderModeEnabled());
                            JComponent headerWrap = fullWidth(locHeader);
                            headerWrap.putClientProperty(PROP_GROUP_NAME, groupName);
                            headerWrap.putClientProperty(PROP_LOCATION_KEY, locationKey);

                            // Reorder mode: header drags the entire location block (header + all rows under it).
                            if (!hasFilter && uiStateStore.isReorderModeEnabled())
                            {
                                Object hh = headerWrap.getClientProperty(PROP_PATCH_DRAG_HANDLE);
                                if (hh instanceof JComponent)
                                {
                                    patchDrag.bindLocationHeader((JComponent) hh, headerWrap, entriesPanel, groupName, visibleIds, locationKey);
                                }
                            }

                            bodyItems.add(headerWrap);
                        }

                        PatchRow row = new PatchRow(
                                id,
                                store,
                                routeStore,
                                uiStateStore,
                                itemManager,
                                config,
                                showIndicator,
                                null,
                                null,
                                this::rebuild);
						// Record visible order in the exact order rows are rendered.
						visiblePatchOrder.add(id);
                        boolean headerOwnsThisRow = (isMultiSlot || forceLocationHeader);
                        row.setReorderHandleVisible(reorderEnabled && !headerOwnsThisRow);

                        JComponent rowWrap = fullWidth(row);


                        rowWrap.putClientProperty(PROP_GROUP_NAME, groupName);
                        rowWrap.putClientProperty(PROP_PATCH_ID, id);
                        rowWrap.putClientProperty(PROP_LOCATION_KEY, locationKey);

                        // Filter mode disables dragging (avoids hidden rows).
                        // Reorder mode gates row dragging.
                        // For multi-slot / forced-header locations, prefer dragging the header so the header
                        // stays attached to its patch(es).
                        if (!hasFilter && uiStateStore.isReorderModeEnabled() && !(isMultiSlot || forceLocationHeader))
                        {
                            Object rh = rowWrap.getClientProperty(PROP_PATCH_DRAG_HANDLE);
                            if (rh instanceof JComponent)
                            {
                                patchDrag.bindRow((JComponent) rh, rowWrap, entriesPanel, groupName, visibleIds);
                            }
                        }
                        // Indent child rows under a location header. Optionally, also indent single-row patches
                        // so they align with child rows even when no location header is rendered.
                        boolean indentRow = (isMultiSlot || forceLocationHeader) ||
                                (showLocationHeaders && config != null && config.indentSingleLocationRows());
                        if (indentRow)
                        {
                            // Subtle indent under a location header (or for single-location rows when enabled).
                            // Wrapper is used to preserve PatchRow layout contract.
                            int indent = Math.max(8, Math.round(10 * config.textScale().multiplier()));
                            rowWrap.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0));
                        }
                        bodyItems.add(rowWrap);
                    }

                    for (int i = 0; i < bodyItems.size(); i++)
                    {
                        entriesPanel.add(bodyItems.get(i));
                        if (i < bodyItems.size() - 1)
                        {
                            entriesPanel.add(divider());
                        }
                    }
                    groupBlock.add(entriesPanel);
                }


                Dimension pref = groupBlock.getPreferredSize();
                groupBlock.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

// ⬅️ THIS is the key difference
                list.add(groupBlock);

            }


            list.revalidate();
            list.repaint();

			// Persist last visible render order for range selection + future Routes integration.
			if (uiStateStore != null)
			{
				uiStateStore.setLastVisiblePatchOrder(visiblePatchOrder);
			}

            if (onAfterRebuild != null)
            {
                onAfterRebuild.run();
            }
        });
    }

    private boolean matchesFilter(PatchId id, String groupName)
{
    if (parsedFilter == null || parsedFilter.isEmpty())
    {
        return true;
    }

    // Candidate strings must track what the renderer uses, plus PatchId.name().
    String patchTypeLabel = groupName;
    String locationDisplay = (id.getLocationName() != null) ? id.getLocationName() : id.getLabel();
    String slotDisplay = (id.getSlotLabel() != null) ? id.getSlotLabel() : id.getQualifierDetail();

    String stateDisplay = "UNKNOWN";
    try
    {
        PatchView view = store != null ? store.view(id) : null;
        if (view != null && view.getRecord().isPresent() && view.getRecord().get().getState() != null)
        {
            stateDisplay = view.getRecord().get().getState().name();
        }
    }
    catch (Exception ignored)
    {
        // Robustness: filter must never break rendering.
    }

    return parsedFilter.matches(
            patchTypeLabel,
            locationDisplay,
            slotDisplay,
            id.name(),
            stateDisplay
    );
}


private enum FilterField
{
    TYPE,
    LOCATION,
    STATE
}

/**
 * Parsed filter query supporting simple fielded tokens (Google-style):
 * - loc:value / l:value
 * - type:value / t:value
 * - state:value / s:value
 *
 * Supports:
 * - key:value and key=value
 * - quoted values with spaces: loc:"farming guild"
 * - multiple values via commas: loc:falador,hosidius
 * - graceful fallback: invalid tokens become free-text terms
 */
private static final class FilterQuery
{
    private final FilterCaseMode caseMode;
    private final EnumMap<FilterField, java.util.List<String>> fieldValues;
    private final java.util.List<String> freeTerms;

    private FilterQuery(FilterCaseMode caseMode, EnumMap<FilterField, java.util.List<String>> fieldValues, java.util.List<String> freeTerms)
    {
        this.caseMode = caseMode == null ? FilterCaseMode.INSENSITIVE : caseMode;
        this.fieldValues = fieldValues == null ? new EnumMap<>(FilterField.class) : fieldValues;
        this.freeTerms = freeTerms == null ? java.util.Collections.emptyList() : freeTerms;
    }

    static FilterQuery empty()
    {
        return new FilterQuery(FilterCaseMode.INSENSITIVE, new EnumMap<>(FilterField.class), java.util.Collections.emptyList());
    }

    boolean isEmpty()
    {
        return (fieldValues == null || fieldValues.isEmpty())
                && (freeTerms == null || freeTerms.isEmpty());
    }

    static FilterQuery parse(String input, FilterCaseMode caseMode)
    {
        String raw = input == null ? "" : input.trim();
        FilterCaseMode cm = caseMode == null ? FilterCaseMode.INSENSITIVE : caseMode;

        if (raw.isEmpty())
        {
            return new FilterQuery(cm, new EnumMap<>(FilterField.class), java.util.Collections.emptyList());
        }

        EnumMap<FilterField, java.util.List<String>> fields = new EnumMap<>(FilterField.class);
        java.util.List<String> free = new java.util.ArrayList<>();

        for (String token : tokenizeQuery(raw))
        {
            if (token == null)
            {
                continue;
            }
            String t = token.trim();
            if (t.isEmpty())
            {
                continue;
            }

            ParsedFieldToken parsed = tryParseFieldToken(t, cm);
            if (parsed != null)
            {
                java.util.List<String> list = fields.computeIfAbsent(parsed.field, k -> new java.util.ArrayList<>());
                list.addAll(parsed.values);
                continue;
            }

            // Graceful fallback: treat as free-text
            String term = normalize(unquote(t), cm);
            if (!term.isEmpty())
            {
                free.add(term);
            }
        }

        // Ensure all collected values are normalized, trimmed, and non-empty.
        fields.entrySet().removeIf(e -> e.getValue() == null || e.getValue().isEmpty());

        return new FilterQuery(cm, fields, free);
    }

    boolean matches(String patchTypeLabel, String locationDisplay, String slotDisplay, String patchIdName, String stateDisplay)
    {
        // Pre-normalize candidates once.
        String nType = normalize(patchTypeLabel, caseMode);
        String nLoc = normalize(locationDisplay, caseMode);
        String nSlot = normalize(slotDisplay, caseMode);
        String nId = normalize(patchIdName, caseMode);
        String nState = normalize(stateDisplay, caseMode);

        // Fielded terms: AND across fields, OR within a field.
        for (Map.Entry<FilterField, java.util.List<String>> e : fieldValues.entrySet())
        {
            FilterField f = e.getKey();
            java.util.List<String> needles = e.getValue();
            if (needles == null || needles.isEmpty())
            {
                continue;
            }

            String candidate;
            switch (f)
            {
                case TYPE:
                    candidate = nType;
                    break;
                case LOCATION:
                    candidate = nLoc;
                    break;
                case STATE:
                    candidate = nState;
                    break;
                default:
                    candidate = "";
            }

            boolean any = false;
            for (String needle : needles)
            {
                if (needle == null || needle.isEmpty())
                {
                    continue;
                }
                if (candidate.contains(needle))
                {
                    any = true;
                    break;
                }
            }

            if (!any)
            {
                return false;
            }
        }

        // Free terms: AND across terms; each term can match any default field.
        for (String term : freeTerms)
        {
            if (term == null || term.isEmpty())
            {
                continue;
            }

            boolean hit = nType.contains(term)
                    || nLoc.contains(term)
                    || nSlot.contains(term)
                    || nId.contains(term);

            if (!hit)
            {
                return false;
            }
        }

        return true;
    }

    private static final class ParsedFieldToken
    {
        final FilterField field;
        final java.util.List<String> values;

        ParsedFieldToken(FilterField field, java.util.List<String> values)
        {
            this.field = field;
            this.values = values;
        }
    }

    private static ParsedFieldToken tryParseFieldToken(String token, FilterCaseMode cm)
    {
        if (token == null)
        {
            return null;
        }

        // Find first ':' or '=' outside of quotes.
        int delim = -1;
        boolean inQuotes = false;
        for (int i = 0; i < token.length(); i++)
        {
            char ch = token.charAt(i);
            if (ch == '"')
            {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && (ch == ':' || ch == '='))
            {
                delim = i;
                break;
            }
        }

        if (delim <= 0 || delim >= token.length() - 1)
        {
            return null;
        }

        String key = token.substring(0, delim).trim();
        String rawValue = token.substring(delim + 1).trim();
        if (key.isEmpty() || rawValue.isEmpty())
        {
            return null;
        }

        // Only treat as a field token when the key is alphabetic.
        for (int i = 0; i < key.length(); i++)
        {
            if (!Character.isLetter(key.charAt(i)))
            {
                return null;
            }
        }

        FilterField field = resolveKey(key);
        if (field == null)
        {
            return null;
        }

        java.util.List<String> values = new java.util.ArrayList<>();
        for (String part : splitCsvValues(rawValue))
        {
            if (part == null)
            {
                continue;
            }
            String v = unquote(part.trim());
            if (v.isEmpty())
            {
                continue;
            }
            values.add(normalize(v, cm));
        }

        if (values.isEmpty())
        {
            return null;
        }

        return new ParsedFieldToken(field, values);
    }

    private static FilterField resolveKey(String key)
    {
        if (key == null)
        {
            return null;
        }

        String k = key.trim().toLowerCase(Locale.ROOT);
        switch (k)
        {
            case "t":
            case "type":
                return FilterField.TYPE;

            case "l":
            case "loc":
            case "location":
                return FilterField.LOCATION;

            case "s":
            case "state":
                return FilterField.STATE;

            default:
                return null;
        }
    }

    private static java.util.List<String> tokenizeQuery(String input)
    {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (input == null || input.isEmpty())
        {
            return out;
        }

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++)
        {
            char ch = input.charAt(i);

            // Support escaped quotes inside quoted values: "
            if (ch == '\\' && i + 1 < input.length() && input.charAt(i + 1) == '"')
            {
                sb.append('"');
                i++;
                continue;
            }

            if (ch == '"')
            {
                inQuotes = !inQuotes;
                sb.append(ch);
                continue;
            }

            if (!inQuotes && Character.isWhitespace(ch))
            {
                if (sb.length() > 0)
                {
                    out.add(sb.toString());
                    sb.setLength(0);
                }
                continue;
            }

            sb.append(ch);
        }

        if (sb.length() > 0)
        {
            out.add(sb.toString());
        }

        return out;
    }

    private static java.util.List<String> splitCsvValues(String input)
    {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (input == null)
        {
            return out;
        }

        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++)
        {
            char ch = input.charAt(i);

            // Support escaped quotes: "
            if (ch == '\\' && i + 1 < input.length() && input.charAt(i + 1) == '"')
            {
                sb.append('"');
                i++;
                continue;
            }

            if (ch == '"')
            {
                inQuotes = !inQuotes;
                sb.append(ch);
                continue;
            }

            if (!inQuotes && ch == ',')
            {
                out.add(sb.toString());
                sb.setLength(0);
                continue;
            }

            sb.append(ch);
        }

        out.add(sb.toString());
        return out;
    }

    private static String unquote(String s)
    {
        if (s == null)
        {
            return "";
        }

        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\""))
        {
            return t.substring(1, t.length() - 1).trim();
        }
        return t;
    }
}

    private boolean contains(String candidate, String normalizedQuery)
    {
        if (candidate == null || candidate.isEmpty())
        {
            return false;
        }

        String c = normalize(candidate, filterCaseMode);
        return c.contains(normalizedQuery);
    }

    private static String normalize(String s, FilterCaseMode caseMode)
    {
        if (s == null)
        {
            return "";
        }

        if (caseMode == FilterCaseMode.SENSITIVE)
        {
            return s;
        }

        return s.toLowerCase(Locale.ROOT);
    }

    private JLabel createGroupDragHandle()
    {
        JLabel handle = new JLabel("⋮⋮");
        handle.setOpaque(false);
        handle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        handle.setForeground(HEADER_ORANGE); // existing color
        return handle;
    }


    private enum AggregateState
    {
        DEAD,
        DISEASED,
        READY,
        GROWING,
        EMPTY,
        UNKNOWN
    }

	/**
	 * Centralized include decision for PatchIds.
	 *
	 * When show-hidden is OFF, patches are filtered out if:
	 * - the patch itself is disabled, OR
	 * - its main heading group is hidden.
	 *
	 * When show-hidden is ON, everything is included (visibility is handled via styling).
	 */
	private boolean shouldIncludePatch(PatchId id, boolean showDisabled)
	{
		if (id == null)
		{
			return false;
		}
		if (showDisabled || uiStateStore == null)
		{
			return true;
		}
		if (uiStateStore.isPatchDisabled(id))
		{
			return false;
		}
		String groupKey = id.getGroup();
		return !uiStateStore.isGroupHidden(groupKey);
	}


	private Component createGroupHeader(String groupName, List<PatchId> groupPatchIds, boolean collapsed, AggregateState aggregate, boolean reorderEnabled, boolean showDisabled)
{
        float scale = config.textScale().multiplier();
        boolean emphasize = config.emphasizeHeaders();
		boolean largerHeadings = config.largerHeadings();

        float headerScale = emphasize ? (scale * 1.05f) : scale;
        int style = emphasize ? Font.BOLD : Font.PLAIN;

        final String tri = collapsed ? "▸" : "▾";

        JLabel triLabel = new JLabel(tri);
        triLabel.setOpaque(false);

		Color headerBg = ColorScheme.DARKER_GRAY_COLOR;
		Color expandedCaret = expandedCaretColor();
		Color collapsedCaret = collapsedCaretColor(aggregate, headerBg);
		triLabel.setForeground(collapsed ? collapsedCaret : expandedCaret);

		Font triFont = UiFont.scaled(triLabel.getFont(), headerScale, style);
		if (largerHeadings)
		{
			triFont = triFont.deriveFont(triFont.getSize2D() + 1f);
		}
		triLabel.setFont(triFont);

        int iconSize = UiRowMetrics.iconSize(scale);
        int gapAfterIcon = UiRowMetrics.iconGap(scale);

        Dimension triDim = new Dimension(iconSize, iconSize);
        triLabel.setPreferredSize(triDim);
        triLabel.setMinimumSize(triDim);
        triLabel.setMaximumSize(triDim);
        triLabel.setHorizontalAlignment(SwingConstants.CENTER);


	JLabel textLabel = new JLabel(groupName);
        textLabel.setOpaque(false);
        textLabel.setForeground(HEADER_ORANGE);
		Font textFont = UiFont.scaled(textLabel.getFont(), headerScale, style);
		if (largerHeadings)
		{
			textFont = textFont.deriveFont(textFont.getSize2D() + 1f);
		}
		textLabel.setFont(textFont);

		boolean groupHidden = uiStateStore != null && uiStateStore.isGroupHidden(groupName);
		if (showDisabled && groupHidden)
		{
			textLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			textLabel.setText(groupName + " · Hidden");
			triLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		}

        // Outer row
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(true);
		header.setBackground(headerBg);

        int padY = Math.max(2, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));
        header.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

        // LEFT: clickable region that EXPANDS to fill space (so hand cursor is consistent)
        JPanel clickable = new JPanel();
        clickable.setLayout(new BoxLayout(clickable, BoxLayout.X_AXIS));
        clickable.setOpaque(false);
        clickable.setAlignmentX(Component.LEFT_ALIGNMENT);

        clickable.add(triLabel);
        clickable.add(Box.createRigidArea(new Dimension(gapAfterIcon, 1)));
        clickable.add(textLabel);
        clickable.add(Box.createHorizontalGlue());

        // Ensure BoxLayout lets it stretch wide
        Dimension pref = clickable.getPreferredSize();
        clickable.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

        clickable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clickable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
				// Left-click toggles collapse. Right-click should only open the context menu.
				if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
				{
					return;
				}
                uiStateStore.toggleGroupCollapsed(groupName);
                rebuild();
            }
        });

		// Header context menu: Hide group / Unhide group
		JPopupMenu menu = new JPopupMenu();
		JMenuItem hideGroupItem = new JMenuItem();
		JMenuItem unhideAllItem = new JMenuItem("Unhide all");
		hideGroupItem.addActionListener(e ->
		{
			if (FarmPanel.this.uiStateStore != null)
			{
				boolean hidden = FarmPanel.this.uiStateStore.isGroupHidden(groupName);
				if (!hidden)
				{
					// Hiding a group is a bulk action: mark the group hidden AND disable all patches within it.
					FarmPanel.this.uiStateStore.setGroupHidden(groupName, true);
					if (groupPatchIds != null)
					{
						for (PatchId id : groupPatchIds)
						{
							FarmPanel.this.uiStateStore.setPatchDisabled(id, true);
						}
					}
				}
				else
				{
					// Unhiding a group is also a bulk action: clear the group-hidden flag AND enable all patches within it.
					FarmPanel.this.uiStateStore.setGroupHidden(groupName, false);
					if (groupPatchIds != null)
					{
						for (PatchId id : groupPatchIds)
						{
							FarmPanel.this.uiStateStore.setPatchDisabled(id, false);
						}
					}
				}
				rebuild();
			}
		});
		menu.add(hideGroupItem);
		unhideAllItem.addActionListener(e ->
		{
			if (FarmPanel.this.uiStateStore == null || groupPatchIds == null)
			{
				return;
			}
			// Unhide all patches in this group. If the group itself is hidden, clear that too so the result is visible.
			FarmPanel.this.uiStateStore.setGroupHidden(groupName, false);
			for (PatchId id : groupPatchIds)
			{
				FarmPanel.this.uiStateStore.setPatchDisabled(id, false);
			}
			rebuild();
		});
		menu.add(unhideAllItem);

		MouseAdapter popupListener = new MouseAdapter()
		{
			private void maybeShow(MouseEvent e)
			{
				if (e.isConsumed() || !e.isPopupTrigger())
				{
					return;
				}
				if (hideGroupItem != null && FarmPanel.this.uiStateStore != null)
				{
					boolean hidden = FarmPanel.this.uiStateStore.isGroupHidden(groupName);
					hideGroupItem.setText(hidden ? "Unhide group" : "Hide group");

					// Only show "Unhide all" if there is at least one disabled patch in this group.
					boolean anyDisabled = false;
					if (groupPatchIds != null)
					{
						for (PatchId id : groupPatchIds)
						{
							if (FarmPanel.this.uiStateStore.isPatchDisabled(id))
							{
								anyDisabled = true;
								break;
							}
						}
					}
					unhideAllItem.setVisible(anyDisabled);
				}
				menu.show(e.getComponent(), e.getX(), e.getY());
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				maybeShow(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				maybeShow(e);
			}
		};
		header.addMouseListener(popupListener);
		clickable.addMouseListener(popupListener);

        // RIGHT: drag handle only
        JComponent dragHandle = createGroupDragHandle();
        dragHandle.setVisible(reorderEnabled);
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        header.putClientProperty(PROP_GROUP_DRAG_HANDLE, dragHandle);

        header.add(clickable);
        header.add(dragHandle);

        return header;
    }

    private AggregateState aggregateStateForCollapsedHeader(List<PatchId> visibleIds)
    {
        boolean hasDead = false;
        boolean hasDiseased = false;
        boolean hasReady = false;
        boolean hasGrowing = false;
        boolean hasEmpty = false;

        for (PatchId id : visibleIds)
        {
            PatchView view = store.view(id);
            if (!view.getRecord().isPresent())
            {
                continue; // Unknown
            }
            PatchState state = view.getRecord().get().getState();

            if (state == null)
            {
                continue;
            }

            switch (state)
            {
                case DEAD:
                    hasDead = true;
                    break;
                case DISEASED:
                    hasDiseased = true;
                    break;
                case READY:
                    hasReady = true;
                    break;
                case GROWING:
                    hasGrowing = true;
                    break;
                case EMPTY:
                    hasEmpty = true;
                    break;
                default:
                    break;
            }
        }

        // Explicit precedence (strongest first)
        if (hasDead)    return AggregateState.DEAD;
        if (hasDiseased) return AggregateState.DISEASED;
        if (hasReady)   return AggregateState.READY;
        if (hasGrowing) return AggregateState.GROWING;
        if (hasEmpty)   return AggregateState.EMPTY;

        return AggregateState.UNKNOWN;
    }

    private Color expandedCaretColor()
    {
        if (config == null)
        {
            return HEADER_ORANGE;
        }

        FarmutilsConfig.ExpandedCaretMode mode = config.expandedCaretMode();
        if (mode == FarmutilsConfig.ExpandedCaretMode.CUSTOM)
        {
            Color c = config.expandedCaretCustomColor();
            return (c != null) ? c : HEADER_ORANGE;
        }

        return HEADER_ORANGE;
    }

    private Color collapsedCaretColor(AggregateState aggregate, Color headerBackground)
    {
        if (config == null)
        {
            return TRI_DISABLED;
        }

        FarmutilsConfig.CollapsedCaretMode mode = config.collapsedCaretMode();
        switch (mode)
        {
            case CUSTOM:
                Color cc = config.collapsedCaretCustomColor();
                return (cc != null) ? cc : TRI_DISABLED;
            case STATE:
                Color baseStrong = aggregateToConfiguredStateColor(aggregate);
                return (baseStrong != null) ? baseStrong : TRI_DISABLED;
            case STATE_OVERVIEW:
                Color base = aggregateToConfiguredStateColor(aggregate);
                Color hint = UiColors.mutedHint(base, headerBackground);
                return (hint != null) ? hint : TRI_DISABLED;
            case GREY:
            default:
                return TRI_DISABLED;
        }
    }

    private Color aggregateToConfiguredStateColor(AggregateState aggregate)
    {
        if (config == null)
        {
            return null;
        }
        if (aggregate == null)
        {
            return config.stateColorUnknown();
        }

        switch (aggregate)
        {
            case DEAD:
                return config.stateColorDead();
            case DISEASED:
                return config.stateColorDiseased();
            case READY:
                return config.stateColorReady();
            case GROWING:
                return config.stateColorGrowing();
            case EMPTY:
                return config.stateColorEmpty();
            case UNKNOWN:
            default:
                return config.stateColorUnknown();
        }
    }



    private static JComponent divider()
    {
        JPanel d = new JPanel();
        d.setOpaque(true);
        d.setBackground(DIVIDER);
        d.setMinimumSize(new Dimension(0, 1));
        d.setPreferredSize(new Dimension(0, 1));
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        d.setAlignmentX(Component.LEFT_ALIGNMENT);
        return d;
    }

    private static JComponent fullWidth(Component child)
    {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(child, BorderLayout.CENTER);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Propagate group drag handle property so callers can bind on the wrapper.
        if (child instanceof JComponent)
        {
            Object h = ((JComponent) child).getClientProperty(PROP_GROUP_DRAG_HANDLE);
            if (h != null)
            {
                wrap.putClientProperty(PROP_GROUP_DRAG_HANDLE, h);
            }

            Object ph = ((JComponent) child).getClientProperty(PROP_PATCH_DRAG_HANDLE);
            if (ph != null)
            {
                        wrap.putClientProperty(PROP_PATCH_DRAG_HANDLE, ph);
            }
        }

        return wrap;
    }


    private int indexOf(Container parent, Component child)
    {
        Component[] comps = parent.getComponents();
        for (int i = 0; i < comps.length; i++)
        {
            if (comps[i] == child)
            {
                return i;
            }
        }
        return -1;
    }

    private int computeDropIndex(Container parent, int mouseYScreen, Component exclude)
    {
        Component[] comps = parent.getComponents();

        for (int i = 0; i < comps.length; i++)
        {
            Component c = comps[i];
            if (c == exclude || c == dropLine)
            {
                continue;
            }

            Point p = c.getLocationOnScreen();
            int midY = p.y + c.getHeight() / 2;
            if (mouseYScreen < midY)
            {
                return i;
            }
        }

        return parent.getComponentCount();
    }

    private void applyScrollbarConfig()
    {
        // Scroll must remain functional even when the scrollbar is hidden.
        // So we keep AS_NEEDED and hide the gutter visually when disabled.
        FarmutilsConfig.ScrollbarVisibility visibility = config.scrollbarVisibility();

        scrollPane.setVerticalScrollBarPolicy(
                visibility == FarmutilsConfig.ScrollbarVisibility.ALWAYS
                        ? ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
                        : ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        );

        // Re-apply UI each time so button visibility / colours update live.
        UiScrollbars.apply(scrollPane, config);

        JScrollBar vbar = scrollPane.getVerticalScrollBar();

        if (visibility == FarmutilsConfig.ScrollbarVisibility.NO)
        {
            // Keep model alive so wheel/trackpoint scrolling still works; just remove the gutter.
            vbar.setPreferredSize(new Dimension(0, Integer.MAX_VALUE));
            vbar.setVisible(false);
        }
        else
        {
            int clampedWidth = Math.max(6, Math.min(16, config.scrollbarWidth()));
            vbar.setPreferredSize(new Dimension(clampedWidth, Integer.MAX_VALUE));
            vbar.setVisible(true);
        }

        scrollPane.revalidate();
        scrollPane.repaint();

    }



    private void placeDropLine(Container parent, int index)
    {
        index = Math.max(0, Math.min(index, parent.getComponentCount()));

        int current = indexOf(parent, dropLine);
        if (current == index)
        {
            return;
        }

        if (dropLine.getParent() == parent)
        {
            parent.remove(dropLine);
        }

        parent.add(dropLine, index);
        parent.revalidate();
        parent.repaint();
    }

    private void removeDropLineIfPresent()
    {
        Container p = dropLine.getParent();
        if (p != null)
        {
            p.remove(dropLine);
            p.revalidate();
            p.repaint();
        }
    }

    public void scrollByWheel(MouseWheelEvent e) {


            if (e == null)
            {
                return;
            }

            JScrollBar bar = scrollPane.getVerticalScrollBar();
            if (bar == null) return;


            int rotation = e.getWheelRotation();
            int increment =
                    (e.getScrollType() == MouseWheelEvent.WHEEL_BLOCK_SCROLL)
                            ? bar.getBlockIncrement(rotation)
                            : bar.getUnitIncrement(rotation);

            int delta = increment * e.getUnitsToScroll();
            int max = bar.getMaximum() - bar.getVisibleAmount();
            int next = Math.max(0, Math.min(max, bar.getValue() + delta));

            bar.setValue(next);
            e.consume();

    }


    private static final class GroupBlockPanel extends JPanel
    {
        final String groupName;
        final JPanel entriesPanel; // holds entry rows only

        private GroupBlockPanel(String groupName, JPanel header, JPanel entriesPanel)
        {
            super();
            this.groupName = groupName;
            this.entriesPanel = entriesPanel;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);

            add(header);
            add(entriesPanel);
        }
    }

    private static Integer tryParsePlotNumber(String s)
    {
        // Accept "Plot 1", "plot 12", etc.
        if (s == null)
        {
            return null;
        }

        String t = s.trim();
        int space = t.lastIndexOf(' ');
        if (space <= 0 || space == t.length() - 1)
        {
            return null;
        }

        String prefix = t.substring(0, space).trim();
        if (!prefix.equalsIgnoreCase("Plot"))
        {
            return null;
        }

        String num = t.substring(space + 1).trim();
        try
        {
            return Integer.parseInt(num);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }


    private static boolean hasBrandOrange()
    {
        try
        {
            ColorScheme.class.getField("BRAND_ORANGE");
            return true;
        }
        catch (NoSuchFieldException e)
        {
            return false;
        }
    }

    private List<PatchId> applySort(List<PatchId> ids)
    {
        if (ids == null || ids.size() <= 1)
        {
            return ids;
        }

        UiStateStore.SortMode mode = uiStateStore.getPatchListSortMode();
        if (mode == UiStateStore.SortMode.DEFAULT)
        {
            return ids;
        }

        // ALPHABETICAL: location-block alphabetical; preserves location header semantics.
        List<PatchId> copy = new ArrayList<>(ids);
        copy.sort(Comparator
                .comparing((PatchId p) -> safeLower(p.getLocationKey()))
                .thenComparing(p -> safeLower(p.getLocationName()))
                .thenComparing(p -> naturalPlotKey(p.getLabel()))
                .thenComparing(Enum::name));
        return copy;
    }

    private static String safeLower(String s)
    {
        return (s == null) ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * Hotfix: numeric plot ordering for string sorts (e.g. "Plot 10" should come after "Plot 2").
     * Applies to all sort modes that compare labels.
     */
    private static String naturalPlotKey(String s)
    {
        if (s == null)
        {
            return "";
        }

        String lower = s.toLowerCase(Locale.ROOT);
        Integer n = tryParsePlotNumber(lower);
        if (n != null)
        {
            return String.format("plot:%03d", n);
        }

        return lower;
    }

    private final class GroupDragController extends MouseAdapter
    {
        private static final int DRAG_THRESHOLD_PX = 6;

        private boolean dragging = false;
        private int pressYScreen = 0;

        private JPanel listPanel;    // your "list" panel from rebuild()
        private JPanel draggedBlock; // your per-group block panel

        private static final double BAND_TOP = 0.35;
        private static final double BAND_BOTTOM = 0.65;

        private List<Integer> dropThresholds = null; // size == number of drop slots (including end)

        private int lastTargetIndex = -1;
        private static final int MIDPOINT_DEADZONE_PX = 6; // tune 4–10

        private static final int AUTO_SCROLL_MARGIN_PX = 24;
        private static final int AUTO_SCROLL_STEP_PX = 18;
        private static final int AUTO_SCROLL_PERIOD_MS = 40;

        private Timer autoScrollTimer;
        private JViewport viewport;

        void bind(JComponent handle, JPanel listPanel, JPanel groupBlock)
        {
            handle.addMouseListener(this);
            handle.addMouseMotionListener(this);

            handle.putClientProperty("farmutils.listPanel", listPanel);
            handle.putClientProperty("farmutils.groupBlock", groupBlock);
        }

        @Override
        public void mousePressed(MouseEvent e)
        {
            JComponent h = (JComponent) e.getComponent();
            listPanel = (JPanel) h.getClientProperty("farmutils.listPanel");
            draggedBlock = (JPanel) h.getClientProperty("farmutils.groupBlock");
            pressYScreen = e.getYOnScreen();
            lastTargetIndex = -1;
            dragging = false;
        }

        @Override
        public void mouseDragged(MouseEvent e)
        {
            if (listPanel == null || draggedBlock == null)
            {
                return;
            }

            if (!dragging)
            {
                if (Math.abs(e.getYOnScreen() - pressYScreen) < DRAG_THRESHOLD_PX)
                {
                    return;
                }

                dragging = true;

                startAutoScroll();

                snapshotDropThresholds();
                lastTargetIndex = -1;
            }

            // 1) snapshot-space index (list without draggedBlock)
            int targetIndex = computeDropIndexFromSnapshot(e);

            // 2) translate to live listPanel index
            int listIndex = toListPanelIndex(targetIndex);

            // 3) skip no-op slot (prevents sticky behavior when dragging down)
            if (wouldBeNoOp(listIndex))
            {
                listIndex = Math.min(listPanel.getComponentCount(), listIndex + 1);
            }

            // 4) move indicator only if changed
            if (listIndex != lastTargetIndex)
            {
                lastTargetIndex = listIndex;
                placeDropLine(listPanel, listIndex);
            }
        }

        private void refreshHoverAfterReorder()
        {
            PointerInfo pi = MouseInfo.getPointerInfo();
            if (pi == null)
            {
                return;
            }

            Point screen = pi.getLocation();
            Point inList = new Point(screen);
            SwingUtilities.convertPointFromScreen(inList, listPanel);

            // Find the actual component under the mouse now
            Component target = SwingUtilities.getDeepestComponentAt(listPanel, inList.x, inList.y);
            if (target == null)
            {
                target = listPanel;
            }

            Point inTarget = SwingUtilities.convertPoint(listPanel, inList, target);

            long now = System.currentTimeMillis();
            target.dispatchEvent(new MouseEvent(
                    target,
                    MouseEvent.MOUSE_MOVED,
                    now,
                    0,
                    inTarget.x,
                    inTarget.y,
                    0,
                    false
            ));
        }

        private void startAutoScroll()
        {
            if (autoScrollTimer != null)
            {
                return;
            }

            viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, listPanel);
            if (viewport == null)
            {
                return; // RuneLite scroll wrapper not present
            }

            autoScrollTimer = new Timer(AUTO_SCROLL_PERIOD_MS, ev ->
            {
                if (!dragging || viewport == null || listPanel == null)
                {
                    return;
                }

                PointerInfo pi = MouseInfo.getPointerInfo();
                if (pi == null)
                {
                    return;
                }

                // Pointer in listPanel coords
                Point p = new Point(pi.getLocation());
                SwingUtilities.convertPointFromScreen(p, listPanel);

                // Viewport rect is in viewport view coords, not listPanel coords.
                // Convert pointer to viewport view coords (FarmPanel coords).
                Component view = viewport.getView(); // should be FarmPanel
                Point pInView = new Point(pi.getLocation());
                SwingUtilities.convertPointFromScreen(pInView, view);

                Rectangle viewRect = viewport.getViewRect();

                int dy = 0;
                if (pInView.y < viewRect.y + AUTO_SCROLL_MARGIN_PX)
                {
                    dy = -AUTO_SCROLL_STEP_PX;
                }
                else if (pInView.y > viewRect.y + viewRect.height - AUTO_SCROLL_MARGIN_PX)
                {
                    dy = AUTO_SCROLL_STEP_PX;
                }

                if (dy == 0)
                {
                    return;
                }

                Point pos = viewport.getViewPosition();

                int maxY = Math.max(0, view.getHeight() - viewport.getExtentSize().height);
                int newY = Math.max(0, Math.min(pos.y + dy, maxY));

                if (newY != pos.y)
                {
                    viewport.setViewPosition(new Point(pos.x, newY));

                    // Keep indicator in sync even if mouse hasn't moved
                    updateDropLineAtPointerInList(p);
                }
            });

            autoScrollTimer.start();
        }

        private void stopAutoScroll()
        {
            if (autoScrollTimer != null)
            {
                autoScrollTimer.stop();
                autoScrollTimer = null;
            }
            viewport = null;
        }

        private void updateDropLineAtPointerInList(Point pInListPanel)
        {
            if (dropThresholds == null || dropThresholds.isEmpty())
            {
                return;
            }

            int mouseY = pInListPanel.y;

            int snapshotIndex = 0;
            for (int i = 0; i < dropThresholds.size(); i++)
            {
                if (mouseY < dropThresholds.get(i))
                {
                    snapshotIndex = i;
                    break;
                }
            }

            int listIndex = toListPanelIndex(snapshotIndex);

            if (wouldBeNoOp(listIndex))
            {
                listIndex = Math.min(listPanel.getComponentCount(), listIndex + 1);
            }

            if (listIndex != lastTargetIndex)
            {
                lastTargetIndex = listIndex;
                placeDropLine(listPanel, listIndex);
            }
        }




        private boolean wouldBeNoOp(int dropLineIndex)
        {
            int fromIndex = indexOf(listPanel, draggedBlock);
            if (fromIndex < 0) return false;

            int toIndex = dropLineIndex;
            if (fromIndex < toIndex)
            {
                toIndex--;
            }

            return toIndex == fromIndex;
        }


        private void commitGroupOrderFromListPanel()
        {
            List<String> order = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (Component c : listPanel.getComponents())
            {
                if (!(c instanceof JComponent))
                {
                    continue;
                }

                Object name = ((JComponent) c).getClientProperty("farmutils.groupName");
                if (name instanceof String)
                {
                    String g = (String) name;
                    if (seen.add(g))
                    {
                        order.add(g);
                    }
                }
            }

            // Append any groups not currently rendered (filtered/hidden),
            // preserving canonical order (or use existing uiState order if you prefer).
            for (String g : lastCanonicalGroupOrder)
            {
                if (seen.add(g))
                {
                    order.add(g);
                }
            }

            uiStateStore.getGroupOrder().clear();
            uiStateStore.getGroupOrder().addAll(order);
        }


        @Override
        public void mouseReleased(MouseEvent e)
        {
            if (!dragging || listPanel == null || draggedBlock == null)
            {
                cleanup();
                return;
            }

            int dropIndex = indexOf(listPanel, dropLine);
            int fromIndex = indexOf(listPanel, draggedBlock);

            removeDropLineIfPresent();

            if (dropIndex >= 0 && fromIndex >= 0)
            {
                int toIndex = dropIndex;
                if (fromIndex < toIndex)
                {
                    toIndex--; // adjust because dragged block will be removed first
                }

                if (toIndex != fromIndex)
                {
                    listPanel.remove(draggedBlock);
                    listPanel.add(draggedBlock, toIndex);
                    listPanel.revalidate();
                    listPanel.repaint();
                    commitGroupOrderFromListPanel();

                    // Step 2: do NOT persist yet.
                    // Step 3 will update uiStateStore.getGroupOrder() based on listPanel order.
                }
            }

            cleanup();

            // NEW: make cursor/hover update without requiring the user to wiggle the mouse
            SwingUtilities.invokeLater(this::refreshHoverAfterReorder);
        }

        private void cleanup()
        {
            removeDropLineIfPresent();
            if (draggedBlock != null)
            {

            }
            dragging = false;
            pressYScreen = 0;
            listPanel = null;
            lastTargetIndex = -1;
            draggedBlock = null;
            dropThresholds = null;

            stopAutoScroll();

        }

        private int toListPanelIndex(int snapshotIndex)
        {
            // Compute dragged index ignoring the dropLine (because it's transient)
            int draggedIndex = indexOfIgnoringDropLine(draggedBlock);

            // snapshotIndex is in "list without draggedBlock"
            // If the dragged block is originally before the slot, shift by +1 to account for it still being present.
            if (draggedIndex != -1 && snapshotIndex > draggedIndex)
            {
                return snapshotIndex + 1;
            }

            return snapshotIndex;
        }

        private int indexOfIgnoringDropLine(Component target)
        {
            Component[] comps = listPanel.getComponents();
            int logicalIndex = 0;

            for (Component c : comps)
            {
                if (c == dropLine)
                {
                    continue;
                }
                if (c == target)
                {
                    return logicalIndex;
                }
                logicalIndex++;
            }

            return -1;
        }



        private int computeDropIndexWithDeadzone(int mouseYScreen)
        {
            Component[] comps = listPanel.getComponents();

            for (int i = 0; i < comps.length; i++)
            {
                Component c = comps[i];

                if (c == draggedBlock || c == dropLine)
                {
                    continue;
                }

                Point p = c.getLocationOnScreen();
                int mid = p.y + c.getHeight() / 2;

                // Deadzone: keep the existing target stable while hovering around midpoint
                if (Math.abs(mouseYScreen - mid) <= MIDPOINT_DEADZONE_PX)
                {
                    if (lastTargetIndex != -1)
                    {
                        // Clamp just in case list size changed while dragging
                        return Math.max(0, Math.min(lastTargetIndex, listPanel.getComponentCount()));
                    }
                    // If no prior target, default to "after" by continuing scan.
                    continue;
                }

                if (mouseYScreen < mid - MIDPOINT_DEADZONE_PX)
                {
                    return i;
                }

                // mouseY is below mid + deadzone: keep scanning
            }

            return listPanel.getComponentCount();
        }

        private void snapshotDropThresholds()
        {
            // Ensure layout is up to date before we read bounds
            listPanel.doLayout();

            Component[] comps = listPanel.getComponents();
            dropThresholds = new ArrayList<>();

            // We are choosing *boundaries between blocks*.
            // For each block, define a "decision Y" in listPanel coordinates.
            // We'll use a band so the middle doesn't trigger changes.
            for (Component c : comps)
            {
                if (c == dropLine || c == draggedBlock)
                {
                    continue;
                }

                Rectangle r = c.getBounds(); // listPanel coordinates
                int y = r.y;
                int h = r.height;

                // Decision point: top band boundary.
                // If mouse is above this, we drop before this component.
                int decisionY = y + (int) Math.round(h * BAND_TOP);

                dropThresholds.add(decisionY);
            }

            // Add an end threshold so dropping past the last element works.
            // Any mouse Y beyond this will map to the end.
            // Allow dropping after the last block
            dropThresholds.add(Integer.MAX_VALUE); // this now maps to slot == size()-1

        }



        private int computeDropIndexFromSnapshot(MouseEvent e)
        {
            if (dropThresholds == null || dropThresholds.isEmpty())
            {
                return listPanel.getComponentCount();
            }

            // Convert mouse point into listPanel coordinates
            Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), listPanel);
            int mouseY = p.y;

            // Find first threshold greater than mouseY
            for (int i = 0; i < dropThresholds.size(); i++)
            {
                if (mouseY < dropThresholds.get(i))
                {
                    return i;
                }
            }

            return dropThresholds.size() - 1;
        }

    }
    private static final class ScrollContentPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return Math.max(visibleRect.height - 16, 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }

        private static final class ViewportWidthPanel extends JPanel implements Scrollable
        {
            @Override
            public Dimension getPreferredScrollableViewportSize()
            {
                return getPreferredSize();
            }

            @Override
            public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
            {
                return 16;
            }

            @Override
            public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
            {
                return Math.max(visibleRect.height - 16, 16);
            }

            @Override
            public boolean getScrollableTracksViewportWidth()
            {
                return true; // <-- the important bit
            }

            @Override
            public boolean getScrollableTracksViewportHeight()
            {
                return false;
            }
        }

    }

    // -------------------------------------------------------------
// Patch Row Drag Controller (localized, no persistence yet)
// -------------------------------------------------------------
    private class PatchDragController
    {
        private static final String PROP_PATCH_ID = "farmutils.patchId";
        private static final String PROP_LOCATION_KEY = "farmutils.locationKey";

        private JComponent dragging;
        private JPanel container;
        private String groupName;
        private List<PatchId> currentOrder;

        private DragMode dragMode;
        private String draggingLocationKey;
        private List<PatchId> draggingBlockIds;

        private int startY;
        private boolean active;

        void bindRow(JComponent handle, JComponent rowWrap, JPanel container, String groupName, List<PatchId> visibleIds)
        {
            // Mouse events are delivered to the deepest child component, not the parent.
            // Bind listeners to the row wrapper *and* all descendants so dragging works anywhere on the row.
            final Cursor moveCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

            MouseAdapter mouse = new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e))
                    {
                        return;
                    }

                    dragging = rowWrap;
                    PatchDragController.this.container = container;
                    PatchDragController.this.groupName = groupName;
                    currentOrder = new ArrayList<>(visibleIds);

                    dragMode = DragMode.ROW;
                    draggingLocationKey = null;
                    draggingBlockIds = null;

                    startY = e.getYOnScreen();
                    active = false;

                    // Allow other listeners to ignore drag-in-progress.
                    e.consume();
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e))
                    {
                        reset();
                        return;
                    }

                    if (!active || dragging == null)
                    {
                        reset();
                        return;
                    }

                    container.remove(dropLine);
                    container.revalidate();
                    container.repaint();

                    // Convert to container coordinate space (row events are row-relative).
                    Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), container);
                    commitDrop(p);

                    // Prevent any click actions from firing after a drag.
                    e.consume();

                    reset();
                }
            };

            MouseMotionAdapter motion = new MouseMotionAdapter()
            {
                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if (dragging == null)
                    {
                        return;
                    }

                    int dy = Math.abs(e.getYOnScreen() - startY);

                    if (!active && dy > 4)
                    {
                        active = true;
                    }

                    if (!active)
                    {
                        return;
                    }

                    // Convert to container coordinate space (row events are row-relative).
                    Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), container);
                    updateDropIndicator(p.y);

                    // Suppress any other click/press behaviors while dragging.
                    e.consume();
                }
            };

            bindDragSurface(handle, mouse, motion, moveCursor);
        }

        void bindLocationHeader(JComponent handle, JComponent headerWrap, JPanel container, String groupName, List<PatchId> visibleIds, String locationKey)
        {
            final Cursor moveCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);

            MouseAdapter mouse = new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e))
                    {
                        return;
                    }

                    dragging = headerWrap;
                    PatchDragController.this.container = container;
                    PatchDragController.this.groupName = groupName;
                    currentOrder = new ArrayList<>(visibleIds);

                    dragMode = DragMode.LOCATION_BLOCK;
                    draggingLocationKey = locationKey;
                    draggingBlockIds = visibleIds.stream()
                            .filter(id -> Objects.equals(locationKey, id.getLocationKey()))
                            .collect(Collectors.toList());

                    startY = e.getYOnScreen();
                    active = false;

                    e.consume();
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e))
                    {
                        reset();
                        return;
                    }

                    if (!active || dragging == null)
                    {
                        reset();
                        return;
                    }

                    container.remove(dropLine);
                    container.revalidate();
                    container.repaint();

                    Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), container);
                    commitDrop(p);

                    e.consume();
                    reset();
                }
            };

            MouseMotionAdapter motion = new MouseMotionAdapter()
            {
                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if (dragging == null)
                    {
                        return;
                    }

                    int dy = Math.abs(e.getYOnScreen() - startY);
                    if (!active && dy > 4)
                    {
                        active = true;
                    }
                    if (!active)
                    {
                        return;
                    }

                    Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), container);
                    updateDropIndicator(p.y);

                    e.consume();
                }
            };

            bindDragSurface(handle, mouse, motion, moveCursor);
        }

        private void bindDragSurface(JComponent root,
                                     MouseListener mouse,
                                     MouseMotionListener motion,
                                     Cursor cursor)
        {
            root.setCursor(cursor);
            root.addMouseListener(mouse);
            root.addMouseMotionListener(motion);

            for (Component c : root.getComponents())
            {
                if (c instanceof JComponent)
                {
                    bindDragSurface((JComponent) c, mouse, motion, cursor);
                }
            }
        }

        private void updateDropIndicator(int mouseYInContainer)
        {
            container.remove(dropLine);

            int componentIndex = (dragMode == DragMode.LOCATION_BLOCK)
                    ? resolveBlockDropComponentIndex(mouseYInContainer)
                    : resolveDropComponentIndex(mouseYInContainer);

            container.add(dropLine, componentIndex);
            container.revalidate();
            container.repaint();
        }

        /**
         * Returns the *component index* into {@code container} for where to insert the dropLine.
         * Only Patch rows (components with PROP_PATCH_ID) are valid targets.
         * Location headers and dividers are ignored.
         */
        private int resolveDropComponentIndex(int mouseY)
        {
            java.util.List<JComponent> rows = validRowComponentsInOrder();

            if (rows.isEmpty())
            {
                return container.getComponentCount();
            }

            for (JComponent row : rows)
            {
                Rectangle b = row.getBounds();
                int mid = b.y + (b.height / 2);
                if (mouseY < mid)
                {
                    return container.getComponentZOrder(row);
                }
            }

            return container.getComponentCount();
        }

        private int resolveDropSlotIndex(int mouseY)
        {
            java.util.List<JComponent> rows = validRowComponentsInOrder();
            int slot = 0;
            for (JComponent row : rows)
            {
                Rectangle b = row.getBounds();
                int mid = b.y + (b.height / 2);
                if (mouseY < mid)
                {
                    return slot;
                }
                slot++;
            }
            return slot;
        }

        private java.util.List<JComponent> validRowComponentsInOrder()
        {
            java.util.List<JComponent> out = new java.util.ArrayList<>();
            for (Component c : container.getComponents())
            {
                if (!(c instanceof JComponent))
                {
                    continue;
                }

                if (c == dragging || c == dropLine)
                {
                    continue;
                }

                PatchId pid = (PatchId) ((JComponent) c).getClientProperty(PROP_PATCH_ID);
                if (pid == null)
                {
                    // Location headers/dividers have no patchId and are not valid drop targets.
                    continue;
                }

                out.add((JComponent) c);
            }
            return out;
        }

        private void commitDrop(Point p)
        {
            if (dragMode == DragMode.LOCATION_BLOCK)
            {
                commitLocationBlockDrop(p);
                return;
            }

            int dropIndex = resolveDropSlotIndex(p.y);

            PatchId draggedId = (PatchId) dragging.getClientProperty(PROP_PATCH_ID);
            if (draggedId == null)
            {
                return;
            }

            List<PatchId> next = new ArrayList<>(currentOrder);
            next.remove(draggedId);

            dropIndex = Math.min(dropIndex, next.size());
            next.add(dropIndex, draggedId);

            uiStateStore.getEntryOrder().put(groupName, next);

            rebuild();
        }

        private void commitLocationBlockDrop(Point p)
        {
            if (draggingLocationKey == null || draggingBlockIds == null || draggingBlockIds.isEmpty())
            {
                return;
            }

            // Determine which block start we are dropping before.
            java.util.List<BlockStart> starts = blockStartsInOrder();

            int targetStartIdx = resolveBlockDropStartIndex(starts, p.y);

            List<PatchId> next = new ArrayList<>(currentOrder);
            next.removeAll(draggingBlockIds);

            int insertAt = next.size();
            if (targetStartIdx >= 0 && targetStartIdx < starts.size())
            {
                BlockStart target = starts.get(targetStartIdx);
                PatchId anchor = target.firstPatchId;
                int idx = next.indexOf(anchor);
                if (idx >= 0)
                {
                    insertAt = idx;
                }
            }

            insertAt = Math.max(0, Math.min(insertAt, next.size()));
            next.addAll(insertAt, draggingBlockIds);

            uiStateStore.getEntryOrder().put(groupName, next);
            rebuild();
        }



        private java.util.List<BlockStart> blockStartsInOrder()
        {
            java.util.List<BlockStart> out = new java.util.ArrayList<>();

            String activeHeaderKey = null;
            boolean skippingDraggedBlock = false;

            for (Component c : container.getComponents())
            {
                if (!(c instanceof JComponent))
                {
                    continue;
                }

                if (c == dropLine)
                {
                    continue;
                }

                JComponent jc = (JComponent) c;

                PatchId pid = (PatchId) jc.getClientProperty(PROP_PATCH_ID);
                String key = (String) jc.getClientProperty(PROP_LOCATION_KEY);

                // Location header: starts a block.
                if (pid == null && key != null)
                {
                    activeHeaderKey = key;
                    skippingDraggedBlock = Objects.equals(draggingLocationKey, key);

                    if (skippingDraggedBlock)
                    {
                        continue;
                    }

                    PatchId first = currentOrder.stream()
                            .filter(id -> Objects.equals(key, id.getLocationKey()))
                            .findFirst()
                            .orElse(null);

                    if (first != null)
                    {
                        out.add(new BlockStart(jc, first, key));
                    }

                    continue;
                }

                if (pid == null)
                {
                    continue;
                }

                // If this row belongs to an active headered block, it is not a block start.
                if (activeHeaderKey != null && Objects.equals(activeHeaderKey, key))
                {
                    continue;
                }

                // Block end: if we see a row that does not match the active header key, clear it.
                activeHeaderKey = null;

                // Standalone patch row is a block start.
                out.add(new BlockStart(jc, pid, key));
            }

            return out;
        }

        private int resolveBlockDropStartIndex(java.util.List<BlockStart> starts, int mouseY)
        {
            if (starts.isEmpty())
            {
                return -1;
            }

            for (int i = 0; i < starts.size(); i++)
            {
                Rectangle b = starts.get(i).component.getBounds();
                int mid = b.y + (b.height / 2);
                if (mouseY < mid)
                {
                    return i;
                }
            }

            return -1; // end
        }

        private int resolveBlockDropComponentIndex(int mouseY)
        {
            java.util.List<BlockStart> starts = blockStartsInOrder();
            int startIdx = resolveBlockDropStartIndex(starts, mouseY);

            if (startIdx < 0)
            {
                return container.getComponentCount();
            }

            return container.getComponentZOrder(starts.get(startIdx).component);
        }

        private void reset()
        {
            dragging = null;
            container = null;
            groupName = null;
            currentOrder = null;
            dragMode = null;
            draggingLocationKey = null;
            draggingBlockIds = null;
            active = false;
        }
    }


}