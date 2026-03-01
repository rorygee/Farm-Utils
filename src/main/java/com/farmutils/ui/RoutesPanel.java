package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchId;
import com.farmutils.model.PatchView;
import com.farmutils.model.PatchState;
import com.farmutils.infer.GrowthProgress;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ItemID;
import net.runelite.client.util.AsyncBufferedImage;
import com.farmutils.route.Route;
import com.farmutils.route.RouteId;
import com.farmutils.route.RouteSession;
import com.farmutils.route.RouteSessionState;
import com.farmutils.route.RouteSessionStore;
import com.farmutils.route.RouteStore;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Routes panel.
 *
 * R1: shell + chrome parity
 * R3: minimal route CRUD
 * R3b: collapsible containers + explicit selection vs active-route (session) semantics
 */
@Slf4j
public class RoutesPanel extends JPanel
{
    private static final Color DIVIDER = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TRI_DISABLED = ColorScheme.MEDIUM_GRAY_COLOR;

    // Keep compatibility with older RL builds.
    private static final Color HEADER_ORANGE = hasBrandOrange() ? ColorScheme.BRAND_ORANGE : new Color(255, 152, 31);

    // Shared drop indicator used for drag-reorder interactions in this panel.
    private final JPanel dropLine = new JPanel();
    {
        dropLine.setOpaque(true);
        dropLine.setBackground(HEADER_ORANGE);
        dropLine.setPreferredSize(new Dimension(1, 2));
        dropLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
    }

    private final FarmutilsConfig config;
    private final RouteStore routeStore;
    private final RouteSessionStore sessionStore;

    private final PatchStore patchStore;
    private final ItemManager itemManager;
    private final UiStateStore uiStateStore;

    // “Selection” is an editing affordance (outline), not the active-route/session indicator.
    private RouteId selectedRouteId;
    private final Set<RouteId> expandedRoutes = new HashSet<>();

    // Patch-row selection within a route (multi-select) for bulk actions.
    private RouteId selectedRoutePatchRouteId;
    private final LinkedHashSet<PatchId> selectedRoutePatchIds = new LinkedHashSet<>();
    private PatchId routePatchSelectionAnchor;

    // Free-text filter applied by FarmRootPanel when Routes is active.
    private String filterText = "";

	// Runtime-only: governs whether drag handles / DnD reorder are shown.
	// (Independent of the patches panel reorder mode.)
	private boolean reorderModeEnabled = false;

    private Runnable onUiStateChange = () -> {};

    private final JPanel list = new JPanel();
    private final JScrollPane scrollPane;

	/**
	 * Aggregate state used for collapsed caret colouring (mirrors FarmPanel group heading logic).
	 */
	private enum AggregateState
	{
		UNKNOWN,
		EMPTY,
		GROWING,
		READY,
		DISEASED,
		DEAD
	}

	private boolean isActiveCursorPatch(final RouteId routeId, final PatchId patchId)
	{
		if (routeId == null || patchId == null || sessionStore == null || routeStore == null)
		{
			return false;
		}

		final RouteSession active = sessionStore.getActiveSession().orElse(null);
		if (active == null)
		{
			return false;
		}
		if (!routeId.equals(active.getRouteId()))
		{
			return false;
		}

		final Route route = routeStore.get(routeId).orElse(null);
		if (route == null)
		{
			return false;
		}
		final List<PatchId> ids = route.getPatchIds();
		if (ids == null || ids.isEmpty())
		{
			return false;
		}

		int cursor = active.getCursorIndex();
		if (cursor < 0)
		{
			cursor = 0;
		}
		if (cursor >= ids.size())
		{
			cursor = ids.size() - 1;
		}

		final PatchId current = ids.get(cursor);
		return patchId.equals(current);
	}

    public RoutesPanel(FarmutilsConfig config, RouteStore routeStore, RouteSessionStore sessionStore, PatchStore patchStore, ItemManager itemManager, UiStateStore uiStateStore)
    {
        super(new BorderLayout());

        this.config = config;
        this.routeStore = routeStore;
        this.sessionStore = sessionStore;

        this.patchStore = patchStore;
        this.itemManager = itemManager;
        this.uiStateStore = uiStateStore;

        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(null);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);
        container.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        container.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        container.add(list);

        // Click empty space to clear selection (does NOT stop an active route).
        container.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
                {
                    return;
                }

                // Click empty space: clear patch-row selection first, then route selection.
                if (hasRoutePatchSelection())
                {
                    clearRoutePatchSelectionAndRepaint();
                    return;
                }

                if (selectedRouteId != null)
                {
                    setSelectedRouteId(null);
                    rebuild();
                }
            }
        });

        scrollPane = new JScrollPane(
                container,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        UiScrollbars.apply(scrollPane, config);

        add(scrollPane, BorderLayout.CENTER);
        rebuild();
    }

    /**
     * Re-render from the current RouteStore snapshot.
     *
     * Used by the root panel on tab switches so cross-panel changes (e.g. adding patches to a route
     * from the Patches context menu) become visible without requiring extra clicks.
     */
    public void refreshFromStore()
    {
        rebuild();
    }

    /**
     * Apply visual config changes immediately (text scale, headings, scrollbars, etc).
     * Mirrors FarmPanel.refreshUiFromConfig() semantics.
     */
    public void refreshUiFromConfig()
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            UiScrollbars.apply(scrollPane, config);
            rebuild();
            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            UiScrollbars.apply(scrollPane, config);
            rebuild();
        });
    }

    /**
     * Apply a free-text filter for Routes.
     * Mirrors FarmPanel.setFilterText() semantics (trimmed, blank => clear).
     */
    public void setFilterText(String text)
    {
        String next = (text == null) ? "" : text.trim();
        if (next.isEmpty())
        {
            next = "";
        }

        if (next.equals(filterText))
        {
            return;
        }

        filterText = next;

		// Contract: drag reordering is disabled while filtering.
		if (hasFilter() && reorderModeEnabled)
		{
			reorderModeEnabled = false;
		}
        rebuild();
    }

    public void setOnUiStateChange(Runnable onUiStateChange)
    {
        this.onUiStateChange = (onUiStateChange != null) ? onUiStateChange : () -> {};
    }

	// --- Routes toolbar parity ---

	public boolean isFilterActive()
	{
		return hasFilter();
	}

	public boolean isReorderModeEnabled()
	{
		return reorderModeEnabled;
	}

	public void setReorderModeEnabled(boolean enabled)
	{
		boolean next = enabled;
		if (hasFilter())
		{
			// Contract: reordering is not available while filtering.
			next = false;
		}
		if (this.reorderModeEnabled == next)
		{
			return;
		}
		this.reorderModeEnabled = next;
		rebuild();
	}

	public boolean canCollapseExpandAll()
	{
		if (hasFilter() || reorderModeEnabled)
		{
			return false;
		}
		return routeStore != null && !routeStore.list().isEmpty();
	}

	public boolean willCollapseAll()
	{
		if (routeStore == null)
		{
			return true;
		}
		for (Route r : routeStore.list())
		{
			if (expandedRoutes.contains(r.getId()))
			{
				return true; // any expanded -> collapse all
			}
		}
		return false; // all collapsed -> expand all
	}

	public void toggleCollapseExpandAll()
	{
		if (!canCollapseExpandAll())
		{
			return;
		}

		boolean anyExpanded = false;
		java.util.List<Route> routes = routeStore.list();
		for (Route r : routes)
		{
			if (expandedRoutes.contains(r.getId()))
			{
				anyExpanded = true;
				break;
			}
		}

		if (anyExpanded)
		{
			expandedRoutes.clear();
		}
		else
		{
			for (Route r : routes)
			{
				expandedRoutes.add(r.getId());
			}
		}
		rebuild();
	}

    /**
     * Invoked by the Routes toolbar (+) button.
     * Runtime-only; no persistence.
     */
    public void promptCreateRoute()
    {
        onNewRoute();
    }

    // --- Toolbar integration (invoked by FarmRootPanel) ---

    public boolean hasSelectedRoute()
    {
        return selectedRouteId != null;
    }

    public boolean hasActiveRoute()
    {
        return sessionStore != null && sessionStore.getActiveRouteId().isPresent();
    }

    public boolean isActiveRunning()
    {
        if (sessionStore == null)
        {
            return false;
        }
        return sessionStore.getActiveSession().map(s -> s.getState() == RouteSessionState.RUNNING).orElse(false);
    }

    public boolean isActivePaused()
    {
        if (sessionStore == null)
        {
            return false;
        }
        return sessionStore.getActiveSession().map(s -> s.getState() == RouteSessionState.PAUSED).orElse(false);
    }

	/**
	 * True if the active session cursor can advance to the next patch.
	 * Used by the Routes toolbar fast-forward control.
	 */
	public boolean canFastForwardCursor()
	{
		if (sessionStore == null)
		{
			return false;
		}
		final RouteSession active = sessionStore.getActiveSession().orElse(null);
		if (active == null)
		{
			return false;
		}
		final Route route = routeStore.get(active.getRouteId()).orElse(null);
		if (route == null || route.getPatchIds() == null || route.getPatchIds().isEmpty())
		{
			return false;
		}

		int cursor = active.getCursorIndex();
		if (cursor < 0)
		{
			cursor = 0;
		}
		return cursor < (route.getPatchIds().size() - 1);
	}

	/**
	 * Manual cursor advance by one. Runtime-only; does not depend on inference.
	 */
	public void fastForwardCursor()
	{
		if (sessionStore == null)
		{
			return;
		}
		final RouteSession active = sessionStore.getActiveSession().orElse(null);
		if (active == null)
		{
			return;
		}
		final Route route = routeStore.get(active.getRouteId()).orElse(null);
		if (route == null || route.getPatchIds() == null)
		{
			return;
		}

		final int size = route.getPatchIds().size();
		if (size <= 0)
		{
			return;
		}

		final boolean changed = sessionStore.advanceCursor(size);
		if (changed)
		{
			onUiStateChange.run();
		}
		rebuild();
	}

    public void startSelectedRoute()
    {
        if (selectedRouteId == null || sessionStore == null)
        {
            return;
        }

        RouteId prev = sessionStore.getActiveRouteId().orElse(null);
        sessionStore.start(selectedRouteId);

        if (prev == null || !prev.equals(selectedRouteId))
        {
            log.info("[routes] start tracking: {}", selectedRouteId);
        }
        else
        {
            log.info("[routes] resume tracking: {}", selectedRouteId);
        }

        rebuild();
    }

    public void pauseActiveRoute()
    {
        if (sessionStore == null)
        {
            return;
        }
        if (!sessionStore.getActiveRouteId().isPresent())
        {
            return;
        }
        sessionStore.pauseActive();
        log.info("[routes] pause tracking");
        rebuild();
    }

    public void stopActiveRoute()
    {
        if (sessionStore == null)
        {
            return;
        }
        if (!sessionStore.getActiveRouteId().isPresent())
        {
            return;
        }
        sessionStore.stopActive();
        log.info("[routes] stop tracking");
        rebuild();
    }

    private void rebuild()
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            rebuildNow();
            return;
        }

        SwingUtilities.invokeLater(this::rebuildNow);
    }

    private void rebuildNow()
    {
        list.removeAll();
		removeDropLineIfPresent();

		List<Route> routes = routeStore.list();

        final boolean hasFilter = hasFilter();
        final String needle = hasFilter ? filterText.toLowerCase(Locale.ROOT) : "";

        // Drop expanded state for deleted routes.
        expandedRoutes.removeIf(id -> routeStore.get(id).isEmpty());

        // Clear selection if it no longer exists.
        if (selectedRouteId != null && routeStore.get(selectedRouteId).isEmpty())
        {
            setSelectedRouteId(null);
        }
        // Clear route patch-row selection if it no longer exists.
        if (hasRoutePatchSelection())
        {
            Route sel = routeStore.get(selectedRoutePatchRouteId).orElse(null);
            if (sel == null)
            {
                clearRoutePatchSelectionInternal();
            }
            else
            {
                selectedRoutePatchIds.retainAll(sel.getPatchIds());
                if (selectedRoutePatchIds.isEmpty())
                {
                    clearRoutePatchSelectionInternal();
                }
                else if (routePatchSelectionAnchor != null && !selectedRoutePatchIds.contains(routePatchSelectionAnchor))
                {
                    routePatchSelectionAnchor = selectedRoutePatchIds.iterator().next();
                }
            }
        }

        // Stop active session if the underlying route was deleted.
        if (sessionStore != null)
        {
            sessionStore.getActiveRouteId().ifPresent(activeId ->
            {
                if (routeStore.get(activeId).isEmpty())
                {
                    sessionStore.stopActive();
                }
            });
        }

		if (routes.isEmpty())
        {
            list.add(fullWidth(new InfoRow("No routes yet", "Click \"+\" to create one.")));
        }
        else
        {
            List<RouteView> visible = new ArrayList<>();
            for (Route r : routes)
            {
                FilterMatch m = hasFilter ? filterMatch(r, needle) : FilterMatch.NONE;
                if (!hasFilter || m != FilterMatch.NONE)
                {
                    visible.add(new RouteView(r, m, hasFilter));
                }
            }

            if (hasFilter && visible.isEmpty())
            {
                list.add(fullWidth(new InfoRow("No matching routes", "Clear the filter to see all routes.")));
            }
            else
            {
				final boolean reorderEnabled = reorderModeEnabled && !hasFilter && visible.size() > 1;
				final List<RouteItem> items = new ArrayList<>();
                for (int i = 0; i < visible.size(); i++)
                {
                    RouteView v = visible.get(i);
					RouteContainer container = new RouteContainer(v, reorderEnabled);
					JComponent wrap = fullWidth(container);
					items.add(new RouteItem(v.route.getId(), container, wrap));
					list.add(wrap);
                    if (i < visible.size() - 1)
                    {
                        list.add(divider());
                    }
                }

				if (reorderEnabled)
				{
					for (RouteItem it : items)
					{
						it.container.bindRouteReorder(items, list);
					}
				}
            }
        }

        list.add(divider());
        list.add(fullWidth(new InfoRow("Runtime-only", "Routes are not saved yet.")));

        list.revalidate();
        list.repaint();

        onUiStateChange.run();
    }

	private void placeDropLine(Container parent, int index)
	{
		index = Math.max(0, Math.min(index, parent.getComponentCount()));

		int current = indexOf(parent, dropLine);
		if (current == index)
		{
			return;
		}

		if (dropLine.getParent() != null)
		{
			dropLine.getParent().remove(dropLine);
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

	private static int indexOf(Container parent, Component child)
	{
		for (int i = 0; i < parent.getComponentCount(); i++)
		{
			if (parent.getComponent(i) == child)
			{
				return i;
			}
		}
		return -1;
	}

    private void onNewRoute()
    {
        String suggested = suggestNewRouteName();
        String name = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Route name:",
                suggested
        );

        if (name == null)
        {
            return;
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty())
        {
            trimmed = suggested;
        }

        try
        {
            Route created = routeStore.create(trimmed);
            setSelectedRouteId(created.getId());
            expandedRoutes.add(created.getId());
            log.info("[routes] created: {} ({})", created.getName(), created.getId());
        }
        catch (IllegalArgumentException ex)
        {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Route name cannot be blank.",
                    "New route",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        rebuild();
    }

    private void onRename(Route route)
    {
        String input = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Rename route:",
                "Rename route",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                route.getName()
        );

        if (input == null)
        {
            return;
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty() || trimmed.equals(route.getName()))
        {
            return;
        }

        try
        {
            routeStore.rename(route.getId(), trimmed);
            log.info("[routes] renamed: {} -> {} ({})", route.getName(), trimmed, route.getId());
        }
        catch (IllegalArgumentException ex)
        {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Route name cannot be blank.",
                    "Rename route",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        rebuild();
    }

    private void onDelete(Route route)
    {
        int res = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                "Delete route \"" + route.getName() + "\"?",
                "Delete route",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (res != JOptionPane.YES_OPTION)
        {
            return;
        }

        // Deleting an active route should stop tracking.
        if (sessionStore != null)
        {
            sessionStore.stopIfActive(route.getId());
        }

        routeStore.delete(route.getId());
        log.info("[routes] deleted: {} ({})", route.getName(), route.getId());

        if (route.getId().equals(selectedRouteId))
        {
            setSelectedRouteId(null);
        }
        expandedRoutes.remove(route.getId());

        rebuild();
    }

    private String suggestNewRouteName()
    {
        List<Route> routes = routeStore.list();
        Set<String> names = new HashSet<>();
        for (Route r : routes)
        {
            names.add(r.getName().toLowerCase(Locale.ROOT));
        }

        String base = "New route";
        if (!names.contains(base.toLowerCase(Locale.ROOT)))
        {
            return base;
        }

        for (int i = 2; i < 1000; i++)
        {
            String candidate = base + " " + i;
            if (!names.contains(candidate.toLowerCase(Locale.ROOT)))
            {
                return candidate;
            }
        }
        return base + " (copy)";
    }

    private boolean isSelected(RouteId id)
    {
        return selectedRouteId != null && selectedRouteId.equals(id);
    }

    private boolean hasRoutePatchSelection()
    {
        return selectedRoutePatchRouteId != null && !selectedRoutePatchIds.isEmpty();
    }

    private boolean isRoutePatchSelected(RouteId routeId, PatchId patchId)
    {
        if (routeId == null || patchId == null)
        {
            return false;
        }
        return selectedRoutePatchRouteId != null
                && selectedRoutePatchRouteId.equals(routeId)
                && selectedRoutePatchIds.contains(patchId);
    }

    private void clearRoutePatchSelectionInternal()
    {
        selectedRoutePatchRouteId = null;
        selectedRoutePatchIds.clear();
        routePatchSelectionAnchor = null;
    }

    private void clearRoutePatchSelectionAndRepaint()
    {
        if (!hasRoutePatchSelection())
        {
            return;
        }
        clearRoutePatchSelectionInternal();
        repaintSelectionSurface();
    }

    private void repaintSelectionSurface()
    {
        // Paint-only: selection is visual and should not require rebuild.
        SwingUtilities.invokeLater(() ->
        {
            Component c = RoutesPanel.this;
            while (c != null)
            {
                c.repaint();
                c = c.getParent();
            }
        });
    }

    private void setSelectedRouteId(RouteId id)
    {
        if (id == null)
        {
            selectedRouteId = null;
            clearRoutePatchSelectionInternal();
            repaintSelectionSurface();
			onUiStateChange.run();
            return;
        }

        if (selectedRouteId == null || !selectedRouteId.equals(id))
        {
            selectedRouteId = id;

            // Patch-row selection is scoped to a single route.
            if (selectedRoutePatchRouteId != null && !selectedRoutePatchRouteId.equals(id))
            {
                clearRoutePatchSelectionInternal();
            }
            repaintSelectionSurface();
			onUiStateChange.run();
        }
    }

    private boolean isExpanded(RouteId id)
    {
        return expandedRoutes.contains(id);
    }

    private boolean hasFilter()
    {
        return filterText != null && !filterText.isBlank();
    }

    private enum FilterMatch
    {
        NONE,
        NAME,
        PATCHES
    }

    private static final class RouteView
    {
        final Route route;
        final FilterMatch match;
        final boolean filterActive;

        RouteView(Route route, FilterMatch match, boolean filterActive)
        {
            this.route = route;
            this.match = match;
            this.filterActive = filterActive;
        }
    }

    private FilterMatch filterMatch(Route route, String needleLower)
    {
        if (route == null || needleLower == null || needleLower.isEmpty())
        {
            return FilterMatch.NONE;
        }

        String rn = route.getName() != null ? route.getName().toLowerCase(Locale.ROOT) : "";
        if (!rn.isEmpty() && rn.contains(needleLower))
        {
            return FilterMatch.NAME;
        }

        for (PatchId pid : route.getPatchIds())
        {
            String text = patchDisplayText(pid);
            if (text != null && text.toLowerCase(Locale.ROOT).contains(needleLower))
            {
                return FilterMatch.PATCHES;
            }
        }
        return FilterMatch.NONE;
    }

    private boolean isActive(RouteId id)
    {
        if (sessionStore == null)
        {
            return false;
        }
        return sessionStore.getActiveRouteId().map(id::equals).orElse(false);
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
        return wrap;
    }

    private final class RouteContainer extends JPanel
    {
        private final RouteView view;
        private final RouteHeaderRow header;
        private final JComponent body;

        RouteContainer(RouteView view, boolean reorderEnabled)
        {
            super();
            this.view = view;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            header = new RouteHeaderRow(view, reorderEnabled);
            body = UiTokens.withLeftIndent(new RouteBody(view), Math.max(8, Math.round(14 * (config != null ? config.textScale().multiplier() : 1f))));
            body.setVisible(view.filterActive || isExpanded(view.route.getId()));

            add(header);
            add(body);

            Dimension pref = getPreferredSize();
            setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }

		void bindRouteReorder(List<RouteItem> items, JComponent listPanel)
		{
			header.bindRouteReorder(items, listPanel);
		}
    }

	private static final class RouteItem
	{
		final RouteId routeId;
		final RouteContainer container;
		final JComponent wrap;

		RouteItem(RouteId routeId, RouteContainer container, JComponent wrap)
		{
			this.routeId = routeId;
			this.container = container;
			this.wrap = wrap;
		}
	}

    private final class RouteHeaderRow extends JPanel
    {
        private final RouteView view;
        private final JLabel triLabel;
        private final JLabel nameLabel;
        private final JLabel statusLabel;
		private final JLabel countLabel;
		private final JLabel dragHandle;
		private final boolean reorderEnabled;

		RouteHeaderRow(RouteView view, boolean reorderEnabled)
        {
            super();
            this.view = view;
			this.reorderEnabled = reorderEnabled;
            final Route route = view.route;

            float scale = config != null ? config.textScale().multiplier() : 1.0f;
            boolean emphasize = config != null && config.emphasizeHeaders();
            boolean largerHeadings = config != null && config.largerHeadings();
            float headerScale = emphasize ? (scale * 1.05f) : scale;
            int style = emphasize ? Font.BOLD : Font.PLAIN;

            // Avoid preferred-width inflation at large text scales.
            // We deliberately allow clipping (like PatchRow) rather than spilling the RuneLite panel.
            setLayout(new GridBagLayout());
            setOpaque(true);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            Color bg = headerBackground(route.getId());
            setBackground(bg);

            int padY = Math.max(2, Math.round(6 * scale));
            int padX = Math.max(6, Math.round(8 * scale));
            setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

            final boolean expandedForDisplay = view.filterActive || isExpanded(route.getId());
            final String tri = expandedForDisplay ? "▾" : "▸";
            triLabel = new JLabel(tri);
            triLabel.setOpaque(false);
			AggregateState aggregate = aggregateForRoute(route);
			triLabel.setForeground(expandedForDisplay ? expandedCaretColor() : collapsedCaretColor(aggregate, bg));
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
            triLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            nameLabel = new JLabel(route.getName());
            nameLabel.setOpaque(false);
            nameLabel.setForeground(HEADER_ORANGE);
            Font nameFont = UiFont.scaled(nameLabel.getFont(), headerScale, style);
            if (largerHeadings)
            {
                nameFont = nameFont.deriveFont(nameFont.getSize2D() + 1f);
            }
            nameLabel.setFont(nameFont);
			nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            // Prevent the route name from forcing the panel wider than the viewport.
            // Full text remains available via tooltip.
            Dimension namePref = nameLabel.getPreferredSize();
            int nameH = namePref.height;
            nameLabel.setPreferredSize(new Dimension(0, nameH));
            nameLabel.setMinimumSize(new Dimension(0, nameH));
            nameLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, nameH));

            statusLabel = new JLabel(routeStatusText(route.getId()));
            statusLabel.setOpaque(false);
            statusLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            statusLabel.setFont(UiFont.scaled(statusLabel.getFont(), scale * 0.90f, Font.PLAIN));
			Dimension stp = statusLabel.getPreferredSize();
			int sth = stp.height;
			statusLabel.setPreferredSize(new Dimension(0, sth));
			statusLabel.setMinimumSize(new Dimension(0, sth));
			statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, sth));

			countLabel = new JLabel(route.getPatchIds().size() + (route.getPatchIds().size() == 1 ? " patch" : " patches"));
            countLabel.setOpaque(false);
            countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            countLabel.setFont(UiFont.scaled(countLabel.getFont(), scale * 0.92f, Font.PLAIN));
			Dimension ctp = countLabel.getPreferredSize();
			int cth = ctp.height;
			countLabel.setPreferredSize(new Dimension(0, cth));
			countLabel.setMinimumSize(new Dimension(0, cth));
			countLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, cth));

			dragHandle = createRouteDragHandle();
			dragHandle.setVisible(reorderEnabled);
			dragHandle.setCursor(reorderEnabled ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor());

			String tooltip = routeTooltipHtml(route);
			setToolTipText(tooltip);
			triLabel.setToolTipText(tooltip);
			nameLabel.setToolTipText(tooltip);
			statusLabel.setToolTipText(tooltip);
			countLabel.setToolTipText(tooltip);
			dragHandle.setToolTipText(tooltip);

            int gapAfterStatus = Math.max(6, Math.round(8 * scale));
            int gapAfterCount = Math.max(2, Math.round(4 * scale));

            GridBagConstraints c = new GridBagConstraints();
            c.gridy = 0;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;

            // Caret
            c.gridx = 0;
            c.insets = new Insets(0, 0, 0, gapAfterIcon);
            add(triLabel, c);

            // Name (flex)
            c.gridx = 1;
            c.insets = new Insets(0, 0, 0, 0);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            add(nameLabel, c);

            // Status
            c.gridx = 2;
            c.insets = new Insets(0, 0, 0, gapAfterStatus);
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            add(statusLabel, c);

            // Count
            c.gridx = 3;
            c.insets = new Insets(0, 0, 0, 0);
            add(countLabel, c);

			// Drag handle (route reorder)
			c.gridx = 4;
			c.insets = new Insets(0, gapAfterCount, 0, 0);
			add(dragHandle, c);

            installInteractions(route);

            Dimension pref = getPreferredSize();
            setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }

        private void installInteractions(Route route)
        {
            final JPopupMenu menu = new JPopupMenu();

            JMenuItem addPatches = new JMenuItem("Add patches…");
            addPatches.addActionListener(e -> onAddPatches(route));
            menu.add(addPatches);

            JMenuItem removePatches = new JMenuItem("Remove patches…");
            removePatches.addActionListener(e -> onRemovePatches(route));
            menu.add(removePatches);

            menu.addSeparator();

			JMenuItem moveUp = new JMenuItem("Move up");
			JMenuItem moveDown = new JMenuItem("Move down");
			moveUp.addActionListener(e ->
			{
				int idx = routeStore.indexOf(route.getId());
				if (idx > 0)
				{
					routeStore.moveRoute(idx, idx - 1);
					rebuild();
				}
			});
			moveDown.addActionListener(e ->
			{
				int idx = routeStore.indexOf(route.getId());
				int size = routeStore.list().size();
				if (idx >= 0 && idx < size - 1)
				{
					routeStore.moveRoute(idx, idx + 1);
					rebuild();
				}
			});
			menu.add(moveUp);
			menu.add(moveDown);

			menu.addSeparator();

            JMenuItem rename = new JMenuItem("Rename");
            rename.addActionListener(e -> onRename(route));
            menu.add(rename);

            JMenuItem delete = new JMenuItem("Delete…");
            delete.addActionListener(e -> onDelete(route));
            menu.add(delete);

            MouseAdapter headerMouse = new MouseAdapter()
            {
                private void maybeShow(MouseEvent e)
                {
                    if (!e.isPopupTrigger())
                    {
                        return;
                    }

					// Keep enablement in sync with current route/order state.
					removePatches.setEnabled(!route.getPatchIds().isEmpty());
					final boolean canReorder = !hasFilter() && routeStore.list().size() > 1;
					int idx = routeStore.indexOf(route.getId());
					int size = routeStore.list().size();
					moveUp.setEnabled(canReorder && idx > 0);
					moveDown.setEnabled(canReorder && idx >= 0 && idx < size - 1);

                    // Desktop norm: right-click selects first (outline), then shows menu.
                    if (!isSelected(route.getId()))
                    {
                        setSelectedRouteId(route.getId());
                        rebuild();
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

                @Override
                public void mouseClicked(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
                    {
                        return;
                    }

                    // Caret is a view-state control only.
					if (e.getSource() == triLabel || e.getSource() == nameLabel)
                    {
                        if (!view.filterActive)
                        {
                            toggleExpanded(route.getId());
                        }
						// Clicking the heading should also make the route the current selection target.
						if (!isSelected(route.getId()))
						{
							selectedRouteId = route.getId();
						}
                        rebuild();
                        return;
                    }

                    // Header body selects (outline). Clicking again clears selection.
                    if (isSelected(route.getId()))
                    {
                        setSelectedRouteId(null);
                    }
                    else
                    {
                        setSelectedRouteId(route.getId());
                    }
                    rebuild();
                }
            };

            // Attach the same listener to header + caret so right-click works anywhere.
            this.addMouseListener(headerMouse);
            triLabel.addMouseListener(headerMouse);
            nameLabel.addMouseListener(headerMouse);
            statusLabel.addMouseListener(headerMouse);
			countLabel.addMouseListener(headerMouse);
			dragHandle.addMouseListener(headerMouse);
        }

		void bindRouteReorder(List<RouteItem> items, JComponent listPanel)
		{
			if (!reorderEnabled || items == null || items.size() < 2)
			{
				return;
			}

			MouseAdapter drag = new MouseAdapter()
			{
				private static final int DRAG_THRESHOLD_PX = 6;

				private boolean dragging;
				private int pressYScreen;
				private int lastSlot = -1;

				@Override
				public void mousePressed(MouseEvent e)
				{
					if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
					{
						return;
					}
					dragging = false;
					lastSlot = -1;
					pressYScreen = e.getYOnScreen();
					removeDropLineIfPresent();
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
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
					}

					Point p = SwingUtilities.convertPoint(dragHandle, e.getPoint(), listPanel);
					int slot = computeRouteDropSlot(items, p.y);
					if (slot != lastSlot)
					{
						lastSlot = slot;
						int componentIndex = routeSlotToComponentIndex(slot, items.size());
						placeDropLine(listPanel, componentIndex);
					}
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					try
					{
						if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
						{
							return;
						}

						if (!dragging)
						{
							return;
						}

						final RouteId rid = view != null ? view.route.getId() : null;
						if (rid == null)
						{
							return;
						}
						final int fromIndex = routeStore.indexOf(rid);
						if (fromIndex < 0)
						{
							return;
						}

						Point p = SwingUtilities.convertPoint(dragHandle, e.getPoint(), listPanel);
						int slot = computeRouteDropSlot(items, p.y);
						int size = routeStore.list().size();

						// Dropping immediately before or after itself is a no-op.
						if (slot == fromIndex || slot == fromIndex + 1)
						{
							return;
						}

						int toIndex = (slot > fromIndex) ? (slot - 1) : slot;
						toIndex = Math.max(0, Math.min(toIndex, size - 1));

						routeStore.moveRoute(fromIndex, toIndex);
						rebuild();
					}
					finally
					{
						dragging = false;
						lastSlot = -1;
						removeDropLineIfPresent();
					}
				}
			};
			dragHandle.addMouseListener(drag);
			dragHandle.addMouseMotionListener(drag);
		}

		private int computeRouteDropSlot(List<RouteItem> items, int y)
		{
			for (int i = 0; i < items.size(); i++)
			{
				Rectangle b = items.get(i).wrap.getBounds();
				int mid = b.y + (b.height / 2);
				if (y < mid)
				{
					return i;
				}
			}
			return items.size();
		}

		private int routeSlotToComponentIndex(int slot, int itemCount)
		{
			// Route list uses: wrap, divider, wrap, divider..., then a bottom divider + info rows.
			// Slot is insertion position between wraps (0..itemCount).
			if (slot >= itemCount)
			{
				return Math.max(0, (2 * itemCount) - 1);
			}
			return Math.max(0, 2 * slot);
		}

        private void toggleExpanded(RouteId id)
        {
            if (id == null)
            {
                return;
            }

            boolean wasExpanded = expandedRoutes.contains(id);
            if (wasExpanded)
            {
                expandedRoutes.remove(id);

                // Avoid "hidden" multi-selection when collapsing the route body.
                if (selectedRoutePatchRouteId != null && selectedRoutePatchRouteId.equals(id))
                {
                    clearRoutePatchSelectionInternal();
                }
            }
            else
            {
                expandedRoutes.add(id);
            }
            rebuild();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);

            Route r = view != null ? view.route : null;
            if (r != null && isSelected(r.getId()))
            {
                Graphics2D sg = (Graphics2D) g.create();
                try
                {
                    sg.setColor(selectionOutlineColor());
                    int w = getWidth();
                    int h = getHeight();
                    sg.drawRect(0, 0, Math.max(0, w - 1), Math.max(0, h - 1));
                }
                finally
                {
                    sg.dispose();
                }
            }
        }
    }

    private Color headerBackground(RouteId id)
    {
        Color base = ColorScheme.DARKER_GRAY_COLOR;
        if (isActive(id))
        {
            // Active route is a stateful session indicator, not selection.
            return base.darker();
        }
        return base;
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

	private AggregateState aggregateForRoute(Route route)
	{
		if (route == null || patchStore == null)
		{
			return AggregateState.UNKNOWN;
		}

		boolean hasDead = false;
		boolean hasDiseased = false;
		boolean hasReady = false;
		boolean hasGrowing = false;
		boolean hasEmpty = false;

		for (PatchId id : route.getPatchIds())
		{
			PatchView view = patchStore.view(id);
			if (view == null || view.getRecord() == null || !view.getRecord().isPresent())
			{
				continue; // Unknown/unobserved
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
		if (hasDead)
		{
			return AggregateState.DEAD;
		}
		if (hasDiseased)
		{
			return AggregateState.DISEASED;
		}
		if (hasReady)
		{
			return AggregateState.READY;
		}
		if (hasGrowing)
		{
			return AggregateState.GROWING;
		}
		if (hasEmpty)
		{
			return AggregateState.EMPTY;
		}

		return AggregateState.UNKNOWN;
	}

	

	private static final class RouteStateCounts
	{
		int unknown;
		int empty;
		int growing;
		int ready;
		int diseased;
		int dead;
	}

	private RouteStateCounts stateCountsFor(Iterable<PatchId> patchIds)
	{
		RouteStateCounts c = new RouteStateCounts();
		if (patchIds == null || patchStore == null)
		{
			return c;
		}

		for (PatchId id : patchIds)
		{
			PatchView v = patchStore.view(id);
			if (v == null || v.getRecord() == null || !v.getRecord().isPresent())
			{
				c.unknown++;
				continue;
			}
			PatchState state = v.getRecord().get().getState();
			if (state == null)
			{
				c.unknown++;
				continue;
			}
			switch (state)
			{
				case READY:
					c.ready++;
					break;
				case GROWING:
					c.growing++;
					break;
				case DISEASED:
					c.diseased++;
					break;
				case DEAD:
					c.dead++;
					break;
				case EMPTY:
					c.empty++;
					break;
				default:
					c.unknown++;
					break;
			}
		}
		return c;
	}

	private String routeSummaryText(RouteStateCounts c)
	{
		if (c == null)
		{
			return "";
		}

		StringBuilder sb = new StringBuilder();
		appendSummaryPart(sb, "Ready", c.ready);
		appendSummaryPart(sb, "Growing", c.growing);
		appendSummaryPart(sb, "Diseased", c.diseased);
		appendSummaryPart(sb, "Dead", c.dead);
		appendSummaryPart(sb, "Empty", c.empty);
		appendSummaryPart(sb, "Unknown", c.unknown);
		return sb.toString();
	}

	private static void appendSummaryPart(StringBuilder sb, String label, int count)
	{
		if (count <= 0)
		{
			return;
		}
		if (sb.length() > 0)
		{
			sb.append(" · ");
		}
		sb.append(label).append(' ').append(count);
	}

	private String routeTooltipHtml(Route route)
	{
		if (route == null)
		{
			return null;
		}
		RouteStateCounts c = stateCountsFor(route.getPatchIds());
		String name = htmlEscape(route.getName());
		return "<html><b>" + name + "</b>"
				+ "<br>Ready: " + c.ready
				+ "<br>Growing: " + c.growing
				+ "<br>Diseased: " + c.diseased
				+ "<br>Dead: " + c.dead
				+ "<br>Empty: " + c.empty
				+ "<br>Unknown: " + c.unknown
				+ "</html>";
	}

	private static String htmlEscape(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	private JLabel createRouteDragHandle()
	{
		JLabel handle = new JLabel("⋮⋮");
		handle.setOpaque(false);
		handle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
		handle.setForeground(HEADER_ORANGE);
		return handle;
	}

    private String routeStatusText(RouteId id)
    {
        if (sessionStore == null)
        {
            return "";
        }

        return sessionStore.getState(id)
                .map(s -> s == RouteSessionState.RUNNING ? "Running" : "Paused")
                .orElse("");
    }

    private Color selectionOutlineColor()
    {
        FarmutilsConfig.SelectionOutlineColor mode = (config != null)
                ? config.selectionOutlineColor()
                : FarmutilsConfig.SelectionOutlineColor.WHITE;

        switch (mode)
        {
            case ACCENT_ORANGE:
                return new Color(ColorScheme.BRAND_ORANGE.getRed(), ColorScheme.BRAND_ORANGE.getGreen(), ColorScheme.BRAND_ORANGE.getBlue(), 200);
            case WHITE:
            default:
                return new Color(255, 255, 255, 180);
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

    private final class RouteBody extends JPanel
    {
        RouteBody(RouteView view)
        {
            super(new BorderLayout());

            final Route route = view.route;

            float scale = config != null ? config.textScale().multiplier() : 1.0f;
            setOpaque(true);
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            int padY = Math.max(4, Math.round(6 * scale));
            int padX = Math.max(6, Math.round(8 * scale));
            setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

            if (route.getPatchIds().isEmpty())
            {
                JLabel label = new JLabel("No patches in this route");
                label.setOpaque(false);
                label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                label.setFont(UiFont.scaled(label.getFont(), scale * 0.92f, Font.PLAIN));

                // Prevent placeholder text from inflating preferred width at large scales.
                Dimension lp = label.getPreferredSize();
                int lh = lp.height;
                label.setPreferredSize(new Dimension(0, lh));
                label.setMinimumSize(new Dimension(0, lh));
                label.setMaximumSize(new Dimension(Integer.MAX_VALUE, lh));
                add(label, BorderLayout.CENTER);
            }
            else
            {
                List<PatchId> patchIds = new ArrayList<>(route.getPatchIds());
                List<PatchId> visiblePatchIds = patchIds;
                if (view.filterActive && view.match == FilterMatch.PATCHES)
                {
                    final String needle = filterText == null ? "" : filterText.toLowerCase(Locale.ROOT);
                    visiblePatchIds = patchIds.stream()
                            .filter(pid ->
                            {
                                String t = patchDisplayText(pid);
                                return t != null && t.toLowerCase(Locale.ROOT).contains(needle);
                            })
                            .collect(Collectors.toList());
                }

                if (visiblePatchIds.isEmpty())
                {
                    JLabel label = new JLabel("No matching patches");
                    label.setOpaque(false);
                    label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                    label.setFont(UiFont.scaled(label.getFont(), scale * 0.92f, Font.PLAIN));
                    Dimension lp = label.getPreferredSize();
                    int lh = lp.height;
                    label.setPreferredSize(new Dimension(0, lh));
                    label.setMinimumSize(new Dimension(0, lh));
                    label.setMaximumSize(new Dimension(Integer.MAX_VALUE, lh));
                    add(label, BorderLayout.CENTER);
                }
                else
                {
                    RoutePatchList entries = new RoutePatchList(route, visiblePatchIds, view.filterActive);

                    // Compact state breakdown (fits in the body, not the header).
                    String summaryText = routeSummaryText(stateCountsFor(visiblePatchIds));
                    if (summaryText != null && !summaryText.isBlank())
                    {
                        JLabel summary = new JLabel(summaryText);
                        summary.setOpaque(false);
                        summary.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                        summary.setFont(UiFont.scaled(summary.getFont(), scale * 0.90f, Font.PLAIN));
                        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
                        summary.setBorder(BorderFactory.createEmptyBorder(0, 0, Math.max(4, Math.round(6 * scale)), 0));

                        Dimension sp = summary.getPreferredSize();
                        int sh = sp.height;
                        summary.setPreferredSize(new Dimension(0, sh));
                        summary.setMinimumSize(new Dimension(0, sh));
                        summary.setMaximumSize(new Dimension(Integer.MAX_VALUE, sh));

                        add(summary, BorderLayout.NORTH);
                    }

                    add(entries, BorderLayout.CENTER);
                }
            }

            Dimension pref = getPreferredSize();
            setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }
    }

    private final class RoutePatchList extends JPanel
    {
        private final Route route;
        private final List<PatchId> visiblePatchIds;
        private final boolean filterActive;

        RoutePatchList(Route route, List<PatchId> visiblePatchIds, boolean filterActive)
        {
            super();
            this.route = route;
            this.visiblePatchIds = visiblePatchIds;
            this.filterActive = filterActive;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

			boolean reorderEnabled = reorderModeEnabled && !filterActive && route.getPatchIds().size() > 1;

            List<RoutePatchRow> rows = new ArrayList<>();
            for (PatchId pid : visiblePatchIds)
            {
                RoutePatchRow row = new RoutePatchRow(route.getId(), pid, this.visiblePatchIds, reorderEnabled);
                rows.add(row);
            }

            for (int i = 0; i < rows.size(); i++)
            {
                RoutePatchRow row = rows.get(i);
                add(row);
                if (i < rows.size() - 1)
                {
                    add(divider());
                }
            }

            if (reorderEnabled)
            {
                for (RoutePatchRow row : rows)
                {
                    row.bindReorder(rows, this);
                }
            }
        }
    }

    private final class RoutePatchRow extends JPanel
    {
        private final RouteId routeId;
        private final PatchId patchId;
        private final List<PatchId> visibleOrder;
        private final PatchView view;

        private final JLabel titleLabel;
        private final JLabel indicatorLabel;
        private final JLabel dragHandle;
        private final JLabel iconLabel;
        private final JPanel swatch;

        private final boolean reorderEnabled;

        private static final int SELECTION_DRAG_THRESHOLD_PX = 10;

        // Click vs drag guard (row background) to avoid accidental selection while dragging.
        private Point selectionPressPoint;

        // Drag state (handle only).
        private Point dragPressPoint;

        RoutePatchRow(RouteId routeId, PatchId patchId, List<PatchId> visibleOrder, boolean reorderEnabled)
        {
            super(new BorderLayout());
            this.routeId = routeId;
            this.patchId = patchId;
            this.visibleOrder = (visibleOrder != null) ? visibleOrder : java.util.Collections.emptyList();
            this.reorderEnabled = reorderEnabled;
            this.view = (patchStore != null && patchId != null) ? patchStore.view(patchId) : null;

            float scale = config != null ? config.textScale().multiplier() : 1.0f;
            setOpaque(true);
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            int padY = Math.max(4, Math.round(6 * scale));
            int padX = Math.max(6, Math.round(8 * scale));
            setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

            // --- Left column (title + state indicator) ---
            JPanel leftCol = new JPanel();
            leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
            leftCol.setOpaque(false);
            leftCol.setAlignmentX(Component.LEFT_ALIGNMENT);

            String titleText = patchDisplayText(patchId);
            JLabel title = new JLabel(titleText);
            this.titleLabel = title;
            title.setOpaque(false);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            title.setForeground(ColorScheme.TEXT_COLOR);
            title.setFont(UiFont.scaled(title.getFont(), scale, Font.PLAIN));

            // Make the current route cursor row obvious (visual-only; no behaviour change).
            if (isActiveCursorPatch(routeId, patchId))
            {
                title.setFont(title.getFont().deriveFont(Font.BOLD));
            }

            String indicatorText = indicatorText(view);
            JLabel indicator = new JLabel(indicatorText);
            this.indicatorLabel = indicator;
            indicator.setOpaque(false);
            indicator.setAlignmentX(Component.LEFT_ALIGNMENT);
            indicator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            indicator.setFont(UiFont.scaled(indicator.getFont(), scale * 0.95f, Font.PLAIN));

            // Optional secondary-line indent (shared with PatchRow).
            int secondaryIndentPx = (config != null) ? Math.max(0, config.secondaryTextIndentPx()) : 0;
            if (secondaryIndentPx > 0)
            {
                indicator.setBorder(BorderFactory.createEmptyBorder(0, secondaryIndentPx, 0, 0));
            }

            // Prevent width inflation at large scales (match PatchRow behavior).
            Dimension tp = title.getPreferredSize();
            int th = tp.height;
            title.setPreferredSize(new Dimension(0, th));
            title.setMinimumSize(new Dimension(0, th));
            title.setMaximumSize(new Dimension(Integer.MAX_VALUE, th));

            Dimension ip = indicator.getPreferredSize();
            int ih = ip.height;
            indicator.setPreferredSize(new Dimension(0, ih));
            indicator.setMinimumSize(new Dimension(0, ih));
            indicator.setMaximumSize(new Dimension(Integer.MAX_VALUE, ih));

            leftCol.add(title);
            leftCol.add(indicator);
            add(leftCol, BorderLayout.CENTER);

            // --- Right column (icon + highlight swatch + drag handle) ---
            JPanel rightCol = new JPanel();
            rightCol.setOpaque(false);
            rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.X_AXIS));

            int contentH = th + ih;
            int iconSize = clamp(Math.round((contentH + padY) * 0.90f), 16, 32);
            int gapAfterIcon = clamp(Math.round(3 * scale), 2, 6);

            JLabel icon = new JLabel();
            this.iconLabel = icon;
            icon.setOpaque(false);
            icon.setAlignmentY(0.5f);

            Dimension iconDim = new Dimension(iconSize, iconSize);
            icon.setPreferredSize(iconDim);
            icon.setMinimumSize(iconDim);
            icon.setMaximumSize(iconDim);

            icon.setText("?");
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setVerticalAlignment(SwingConstants.CENTER);
            icon.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            icon.setFont(UiFont.scaled(icon.getFont(), scale * 0.9f, Font.PLAIN));

            int iconItemId = ItemID.WEEDS;
            OptionalInt cropItem = (patchStore != null) ? patchStore.getCropItemId(patchId) : OptionalInt.empty();
            if (cropItem.isPresent())
            {
                iconItemId = cropItem.getAsInt();
            }

            AsyncBufferedImage asyncImg = (itemManager != null) ? itemManager.getImage(iconItemId) : null;
            if (asyncImg != null)
            {
                asyncImg.onLoaded(() ->
                {
                    Image scaled = asyncImg.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
                    SwingUtilities.invokeLater(() ->
                    {
                        icon.setText(null);
                        icon.setIcon(new ImageIcon(scaled));
                        icon.revalidate();
                        icon.repaint();
                    });
                });
            }

            int swatchWidth = clamp(Math.round(6 * scale), 4, 10);
            JPanel sw = new JPanel();
            this.swatch = sw;
            sw.setOpaque(true);
            sw.setBorder(null);
            sw.setPreferredSize(new Dimension(swatchWidth, 1));
            sw.setMinimumSize(new Dimension(swatchWidth, 1));
            sw.setMaximumSize(new Dimension(swatchWidth, Integer.MAX_VALUE));

            int slot = (patchStore != null) ? patchStore.getHighlightSlot(patchId) : 0;
            applySwatchColor(sw, slot);

            JLabel handle = new JLabel("⋮⋮");
            this.dragHandle = handle;
            handle.setOpaque(false);
            handle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
            handle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            handle.setCursor(reorderEnabled ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR) : Cursor.getDefaultCursor());
            handle.setVisible(reorderEnabled);

            rightCol.add(icon);
            rightCol.add(Box.createRigidArea(new Dimension(gapAfterIcon, 1)));
            rightCol.add(sw);
            rightCol.add(handle);

            add(rightCol, BorderLayout.EAST);

            String tooltip = patchDisplayText(patchId);
            setToolTipText(tooltip);
            title.setToolTipText(tooltip);
            indicator.setToolTipText(tooltip);

            applyIndicatorColorFromMode();
            installSelectionAndContextHandlers();

            Dimension pref = getPreferredSize();
            setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }

        
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);

            if (isRoutePatchSelected(routeId, patchId))
            {
                Graphics2D sg = (Graphics2D) g.create();
                try
                {
                    sg.setColor(selectionOutlineColor());
                    int w = getWidth();
                    int h = getHeight();
                    sg.drawRect(0, 0, Math.max(0, w - 1), Math.max(0, h - 1));
                }
                finally
                {
                    sg.dispose();
                }
            }

            if (uiStateStore == null)
            {
                return;
            }

            UiStateStore.StateIndicatorMode mode = uiStateStore.getStateIndicatorMode();
            if (mode == UiStateStore.StateIndicatorMode.OFF)
            {
                return;
            }

            Color c = stateColor(view);
            if (c == null)
            {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setColor(c);

                int y = Math.max(0, getHeight() - 2);

                int margin = computeContentMargin();
                int xL = margin;
                int xR = getWidth() - margin - 1;

                Optional<GrowthProgress> progressOpt = Optional.empty();
                int xProgressEnd = xL;
                Color remainder = UiColors.remainderColor(c, getBackground());
                if (remainder == null)
                {
                    remainder = new Color(
                            ColorScheme.DARKER_GRAY_COLOR.getRed(),
                            ColorScheme.DARKER_GRAY_COLOR.getGreen(),
                            ColorScheme.DARKER_GRAY_COLOR.getBlue(),
                            180);
                }

                if (mode == UiStateStore.StateIndicatorMode.FULL_WIDTH || mode == UiStateStore.StateIndicatorMode.FULL_AND_TITLE)
                {
                    PatchState s = (view != null && view.getRecord() != null && view.getRecord().isPresent())
                            ? view.getRecord().get().getState()
                            : null;
                    boolean allowProgress = (s == PatchState.GROWING || s == PatchState.READY);

                    progressOpt = (allowProgress && patchStore != null) ? patchStore.getGrowthProgress(patchId) : Optional.empty();

                    if (!progressOpt.isPresent())
                    {
                        g2.drawLine(xL, y, xR, y);
                    }
                    else
                    {
                        GrowthProgress gp = progressOpt.get();

                        g2.setColor(remainder);
                        g2.drawLine(xL, y, xR, y);

                        g2.setColor(c);
                        int w = Math.max(0, xR - xL);
                        float p01 = gp.getProgress01();
                        if (p01 < 0f) p01 = 0f;
                        if (p01 > 1f) p01 = 1f;

                        int px = Math.round(w * p01);
                        xProgressEnd = Math.min(xR, xL + px);

                        if (xProgressEnd > xL)
                        {
                            g2.drawLine(xL, y, xProgressEnd, y);
                        }
                    }
                }

                if (mode == UiStateStore.StateIndicatorMode.RIGHT_STRIP)
                {
                    drawRightStripLine(g2, y);
                }

                if (mode == UiStateStore.StateIndicatorMode.FULL_AND_TITLE)
                {
                    if (!progressOpt.isPresent())
                    {
                        drawTitleLine(g2, y);
                    }
                    else
                    {
                        if (titleLabel != null)
                        {
                            Rectangle r = SwingUtilities.convertRectangle(titleLabel.getParent(), titleLabel.getBounds(), this);
                            int prefW = titleLabel.getPreferredSize() != null ? titleLabel.getPreferredSize().width : r.width;
                            int w = Math.min(prefW, r.width);

                            int tx1 = r.x;
                            int tx2 = r.x + Math.max(0, w);

                            g2.setColor(remainder);
                            g2.drawLine(tx1, y, tx2, y);

                            int end = Math.min(tx2, xProgressEnd);
                            if (end > tx1)
                            {
                                g2.setColor(c);
                                g2.drawLine(tx1, y, end, y);
                            }
                        }
                    }
                }
            }
            finally
            {
                g2.dispose();
            }
        }

        private void applyIndicatorColorFromMode()
        {
            if (uiStateStore == null || indicatorLabel == null)
            {
                return;
            }

            UiStateStore.StateIndicatorMode mode = uiStateStore.getStateIndicatorMode();
            Color c = stateColor(view);

            // Allow Unknown to be configured for state-text colouring, while keeping the indicator-line calm.
            if (c == null && config != null && view != null && (view.getRecord() == null || !view.getRecord().isPresent()))
            {
                c = config.stateColorUnknown();
            }

            if (c != null && (mode == UiStateStore.StateIndicatorMode.TITLE_ONLY || mode == UiStateStore.StateIndicatorMode.FULL_AND_TITLE))
            {
                indicatorLabel.setForeground(c);
            }
            else
            {
                indicatorLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            }
        }

        private Color stateColor(PatchView view)
        {
            return UiColors.stateColorOrNull(view, config);
        }

        private int computeContentMargin()
        {
            if (titleLabel != null)
            {
                Rectangle r = SwingUtilities.convertRectangle(titleLabel.getParent(), titleLabel.getBounds(), this);
                return Math.max(0, r.x);
            }

            Insets in = getInsets();
            return (in != null) ? in.left : 0;
        }

        private void drawRightStripLine(Graphics2D g2, int y)
        {
            if (iconLabel == null)
            {
                return;
            }

            Rectangle iconR = SwingUtilities.convertRectangle(iconLabel.getParent(), iconLabel.getBounds(), this);
            int x1 = iconR.x;
            int x2 = iconR.x + iconR.width - 1;
            g2.drawLine(x1, y, x2, y);
        }

        private void drawTitleLine(Graphics2D g2, int y)
        {
            if (titleLabel == null)
            {
                return;
            }

            Rectangle r = SwingUtilities.convertRectangle(titleLabel.getParent(), titleLabel.getBounds(), this);
            int prefW = titleLabel.getPreferredSize() != null ? titleLabel.getPreferredSize().width : r.width;
            int w = Math.min(prefW, r.width);

            int x1 = r.x;
            int x2 = r.x + Math.max(0, w);
            g2.drawLine(x1, y, x2, y);
        }

        private void installSelectionAndContextHandlers()
        {
            final JPopupMenu menu = new JPopupMenu();

            final JMenuItem remove = new JMenuItem("Remove from route");
            remove.addActionListener(e ->
            {
                Set<PatchId> targets = getActionTargetPatchIds();
                int removed = 0;
                for (PatchId pid : targets)
                {
                    if (routeStore.removePatch(routeId, pid))
                    {
                        removed++;
                    }
                }

                if (removed > 0)
                {
                    log.info("[routes] removed {} patch{} from route {}", removed, removed == 1 ? "" : "es", routeId);
                    clearRoutePatchSelectionInternal();
                    rebuild();
                }
            });
            menu.add(remove);

            menu.addSeparator();

            final JMenuItem up = new JMenuItem("Move up");
            up.addActionListener(e ->
            {
                Route current = routeStore.get(routeId).orElse(null);
                if (current == null)
                {
                    return;
                }

                int idx = current.getPatchIds().indexOf(patchId);
                if (idx > 0)
                {
                    routeStore.movePatch(routeId, idx, idx - 1);
                    rebuild();
                }
            });
            menu.add(up);

            final JMenuItem down = new JMenuItem("Move down");
            down.addActionListener(e ->
            {
                Route current = routeStore.get(routeId).orElse(null);
                if (current == null)
                {
                    return;
                }

                int idx = current.getPatchIds().indexOf(patchId);
                int size = current.getPatchIds().size();
                if (idx >= 0 && idx < size - 1)
                {
                    routeStore.movePatch(routeId, idx, idx + 1);
                    rebuild();
                }
            });
            menu.add(down);

            MouseAdapter adapter = new MouseAdapter()
            {
                private void maybeShow(MouseEvent e)
                {
                    if (!e.isPopupTrigger())
                    {
                        return;
                    }

                    showContextMenuRespectingSelection(e, menu, remove, up, down);
                }

                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (e.isConsumed())
                    {
                        return;
                    }

                    if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger())
                    {
                        selectionPressPoint = e.getPoint();
                    }

                    maybeShow(e);
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (e.isConsumed())
                    {
                        return;
                    }

                    if (e.isPopupTrigger())
                    {
                        maybeShow(e);
                        return;
                    }

                    if (!SwingUtilities.isLeftMouseButton(e))
                    {
                        return;
                    }

                    // Drag guard: if the user moved significantly, do not toggle selection.
                    if (selectionPressPoint != null)
                    {
                        int dx = Math.abs(e.getX() - selectionPressPoint.x);
                        int dy = Math.abs(e.getY() - selectionPressPoint.y);
                        if (dx + dy >= SELECTION_DRAG_THRESHOLD_PX)
                        {
                            selectionPressPoint = null;
                            return;
                        }
                    }
                    selectionPressPoint = null;

                    handleSelectionClick(e);
                }
            };

            addMouseListener(adapter);
            titleLabel.addMouseListener(adapter);
            indicatorLabel.addMouseListener(adapter);
            iconLabel.addMouseListener(adapter);
            swatch.addMouseListener(adapter);

            // Drag handle: do not toggle selection on left click, but still allow context menu.
            MouseAdapter popupOnly = new MouseAdapter()
            {
                private void maybeShow(MouseEvent e)
                {
                    if (!e.isConsumed() && e.isPopupTrigger())
                    {
                        showContextMenuRespectingSelection(e, menu, remove, up, down);
                    }
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
            dragHandle.addMouseListener(popupOnly);
        }

        private void showContextMenuRespectingSelection(MouseEvent e, JPopupMenu menu, JMenuItem removeItem, JMenuItem up, JMenuItem down)
        {
            setSelectedRouteId(routeId);

            // Desktop norm: right-click on unselected row selects it first.
            // Right-click on selected row preserves multi-selection.
            if (!isRoutePatchSelected(routeId, patchId))
            {
                clearRoutePatchSelectionInternal();
                selectedRoutePatchRouteId = routeId;
                selectedRoutePatchIds.add(patchId);
                routePatchSelectionAnchor = patchId;
                repaintSelectionSurface();
            }
            else
            {
                // Ensure route-scoped selection is active.
                selectedRoutePatchRouteId = routeId;
            }

            // Enablement + labels are computed at show-time so they stay correct without rebuild.
            Route current = routeStore.get(routeId).orElse(null);
            int idx = current != null ? current.getPatchIds().indexOf(patchId) : -1;
            int size = current != null ? current.getPatchIds().size() : 0;

            up.setEnabled(idx > 0);
            down.setEnabled(idx >= 0 && idx < size - 1);

            int targetCount = getActionTargetPatchIds().size();
            removeItem.setText(targetCount > 1 ? "Remove selected from route" : "Remove from route");

            menu.show(e.getComponent(), e.getX(), e.getY());
        }

        private Set<PatchId> getActionTargetPatchIds()
        {
            if (isRoutePatchSelected(routeId, patchId) && selectedRoutePatchRouteId != null && selectedRoutePatchRouteId.equals(routeId))
            {
                // Never expose the live selection set directly.
                return new LinkedHashSet<>(selectedRoutePatchIds);
            }
            return java.util.Collections.singleton(patchId);
        }

        private void handleSelectionClick(MouseEvent e)
        {
            setSelectedRouteId(routeId);

            boolean ctrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;
            boolean shift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;

            // Selection is scoped to a single route.
            if (selectedRoutePatchRouteId == null || !selectedRoutePatchRouteId.equals(routeId))
            {
                clearRoutePatchSelectionInternal();
                selectedRoutePatchRouteId = routeId;
            }

            PatchId anchor = routePatchSelectionAnchor;

            if (!ctrl && !shift)
            {
                if (selectedRoutePatchIds.contains(patchId))
                {
                    clearRoutePatchSelectionInternal();
                }
                else
                {
                    selectedRoutePatchIds.clear();
                    selectedRoutePatchIds.add(patchId);
                    routePatchSelectionAnchor = patchId;
                }
                repaintSelectionSurface();
                return;
            }

            if (ctrl && !shift)
            {
                if (selectedRoutePatchIds.contains(patchId))
                {
                    selectedRoutePatchIds.remove(patchId);
                    if (selectedRoutePatchIds.isEmpty())
                    {
                        clearRoutePatchSelectionInternal();
                    }
                    else
                    {
                        routePatchSelectionAnchor = patchId;
                    }
                }
                else
                {
                    selectedRoutePatchIds.add(patchId);
                    routePatchSelectionAnchor = patchId;
                }
                repaintSelectionSurface();
                return;
            }

            if (!ctrl && shift)
            {
                if (anchor == null)
                {
                    selectedRoutePatchIds.clear();
                    selectedRoutePatchIds.add(patchId);
                    routePatchSelectionAnchor = patchId;
                }
                else
                {
                    selectRange(anchor, patchId, false);
                }
                repaintSelectionSurface();
                return;
            }

            // ctrl + shift
            if (anchor == null)
            {
                if (selectedRoutePatchIds.contains(patchId))
                {
                    selectedRoutePatchIds.remove(patchId);
                }
                else
                {
                    selectedRoutePatchIds.add(patchId);
                }
                routePatchSelectionAnchor = patchId;
            }
            else
            {
                selectRange(anchor, patchId, true);
            }
            repaintSelectionSurface();
        }

        private void selectRange(PatchId a, PatchId b, boolean additive)
        {
            if (visibleOrder == null || visibleOrder.isEmpty())
            {
                selectedRoutePatchIds.clear();
                selectedRoutePatchIds.add(b);
                routePatchSelectionAnchor = b;
                return;
            }

            int ia = visibleOrder.indexOf(a);
            int ib = visibleOrder.indexOf(b);
            if (ia < 0 || ib < 0)
            {
                selectedRoutePatchIds.clear();
                selectedRoutePatchIds.add(b);
                routePatchSelectionAnchor = b;
                return;
            }

            int lo = Math.min(ia, ib);
            int hi = Math.max(ia, ib);

            if (!additive)
            {
                selectedRoutePatchIds.clear();
            }

            for (int i = lo; i <= hi; i++)
            {
                selectedRoutePatchIds.add(visibleOrder.get(i));
            }
            routePatchSelectionAnchor = b;
        }

        void bindReorder(List<RoutePatchRow> allRows, JComponent listPanel)
        {
            if (!reorderEnabled)
            {
                return;
            }

            MouseAdapter drag = new MouseAdapter()
            {
                private static final int DRAG_THRESHOLD_PX = 6;
                private boolean dragging;
                private int pressYScreen;
                private int lastSlot = -1;

                
                public void mousePressed(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
                    {
                        return;
                    }
                    dragging = false;
                    lastSlot = -1;
                    pressYScreen = e.getYOnScreen();
                    dragPressPoint = e.getPoint();
                    removeDropLineIfPresent();
                }

                
                public void mouseDragged(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
                    {
                        return;
                    }
                    if (dragPressPoint == null)
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
                    }

                    Point p = SwingUtilities.convertPoint(dragHandle, e.getPoint(), listPanel);
                    int slot = computePatchDropSlot(allRows, p.y);
                    if (slot != lastSlot)
                    {
                        lastSlot = slot;
                        int componentIndex = patchSlotToComponentIndex(slot, allRows.size());
                        placeDropLine((Container) listPanel, componentIndex);
                    }
                }

                
                public void mouseReleased(MouseEvent e)
                {
                    try
                    {
                        if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger())
                        {
                            return;
                        }
                        if (dragPressPoint == null)
                        {
                            return;
                        }

                        if (!dragging)
                        {
                            return;
                        }

                        Point p = SwingUtilities.convertPoint(dragHandle, e.getPoint(), listPanel);
                        int slot = computePatchDropSlot(allRows, p.y);

                        Route current = routeStore.get(routeId).orElse(null);
                        if (current == null)
                        {
                            return;
                        }

                        int fromIndex = current.getPatchIds().indexOf(patchId);
                        if (fromIndex < 0)
                        {
                            return;
                        }

                        if (slot == fromIndex || slot == fromIndex + 1)
                        {
                            return;
                        }

                        int toIndex = (slot > fromIndex) ? (slot - 1) : slot;
                        toIndex = Math.max(0, Math.min(toIndex, current.getPatchIds().size() - 1));

                        routeStore.movePatch(routeId, fromIndex, toIndex);
                        rebuild();
                    }
                    finally
                    {
                        dragging = false;
                        lastSlot = -1;
                        dragPressPoint = null;
                        removeDropLineIfPresent();
                    }
                }
            };

            dragHandle.addMouseListener(drag);
            dragHandle.addMouseMotionListener(drag);
        }

        private int computePatchDropSlot(List<RoutePatchRow> rows, int y)
        {
            for (int i = 0; i < rows.size(); i++)
            {
                Rectangle b = rows.get(i).getBounds();
                int mid = b.y + (b.height / 2);
                if (y < mid)
                {
                    return i;
                }
            }
            return rows.size();
        }

        private int patchSlotToComponentIndex(int slot, int rowCount)
        {
            if (slot >= rowCount)
            {
                return Math.max(0, (2 * rowCount) - 1);
            }
            return Math.max(0, 2 * slot);
        }

        private void applySwatchColor(JPanel swatch, int slot)
        {
            if (swatch == null)
            {
                return;
            }

            if (slot <= 0)
            {
                swatch.setBackground(getBackground());
                return;
            }

            switch (slot)
            {
                case 1:
                    swatch.setBackground(new Color(120, 160, 255));
                    break;
                case 2:
                    swatch.setBackground(new Color(140, 200, 140));
                    break;
                case 3:
                    swatch.setBackground(new Color(220, 180, 120));
                    break;
                case 4:
                    swatch.setBackground(new Color(200, 120, 200));
                    break;
                default:
                    swatch.setBackground(getBackground());
            }
        }

        private int clamp(int value, int min, int max)
        {
            return Math.max(min, Math.min(max, value));
        }

        private String indicatorText(PatchView view)
        {
            if (view == null || view.getRecord() == null || !view.getRecord().isPresent())
            {
                return "Unknown";
            }

            String base = pretty(view.getRecord().get().getState());
            return view.isStale() ? base + " · Stale" : base;
        }

        private String pretty(PatchState state)
        {
            if (state == null)
            {
                return "Unknown";
            }

            switch (state)
            {
                case GROWING:
                    return "Growing";
                case READY:
                    return "Ready";
                case DISEASED:
                    return "Diseased";
                case EMPTY:
                    return "Empty";
                case DEAD:
                    return "Dead";
                default:
                    return state.name();
            }
        }
    }

    // --- R4 Stage 2 (Routes panel patch add/remove) ---

    private void onAddPatches(Route route)
    {
        if (route == null)
        {
            return;
        }

        Set<PatchId> existing = new HashSet<>(route.getPatchIds());
        List<PatchId> candidates = Arrays.stream(PatchId.values())
                .filter(pid -> !existing.contains(pid))
                .sorted(patchSortComparator())
                .collect(Collectors.toList());

        if (candidates.isEmpty())
        {
            JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "This route already contains every patch.",
                    "Add patches",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        List<PatchId> chosen = PatchPickerDialog.show(
                SwingUtilities.getWindowAncestor(this),
                config,
                "Add patches to \"" + route.getName() + "\"",
                candidates,
                "Add"
        );

        if (chosen == null || chosen.isEmpty())
        {
            return;
        }

        int added = 0;
        int dupes = 0;
        for (PatchId pid : chosen)
        {
            if (routeStore.addPatch(route.getId(), pid))
            {
                added++;
            }
            else
            {
                dupes++;
            }
        }

        log.info("[routes] added {} patches to route '{}' (ignored {} duplicates)", added, route.getName(), dupes);
        rebuild();
    }

    private void onRemovePatches(Route route)
    {
        if (route == null)
        {
            return;
        }

        List<PatchId> candidates = new ArrayList<>(route.getPatchIds());
        if (candidates.isEmpty())
        {
            return;
        }

        List<PatchId> chosen = PatchPickerDialog.show(
                SwingUtilities.getWindowAncestor(this),
                config,
                "Remove patches from \"" + route.getName() + "\"",
                candidates,
                "Remove"
        );

        if (chosen == null || chosen.isEmpty())
        {
            return;
        }

        int removed = 0;
        for (PatchId pid : chosen)
        {
            if (routeStore.removePatch(route.getId(), pid))
            {
                removed++;
            }
        }

        log.info("[routes] removed {} patches from route '{}'", removed, route.getName());
        rebuild();
    }

    private Comparator<PatchId> patchSortComparator()
    {
        // Sort like the Patches panel: by type, then location, then slot/row label.
        return Comparator
                .comparing(PatchId::getGroup, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(pid -> safeLower(locationDisplay(pid)))
                .thenComparing(pid -> safeLower(rowTitleDisplay(pid)))
                .thenComparing(PatchId::name, String.CASE_INSENSITIVE_ORDER);
    }

    private static String locationDisplay(PatchId pid)
    {
        if (pid == null)
        {
            return "";
        }
        return pid.getLocationName() != null ? pid.getLocationName() : pid.getLabel();
    }

    private static String rowTitleDisplay(PatchId pid)
    {
        if (pid == null)
        {
            return "";
        }

        // For grouped locations, prefer the per-row title (Plot 1 / Herb patch / etc.)
        if (pid.getLocationKey() != null && pid.getLocationName() != null)
        {
            if (pid.getSlotLabel() != null && !pid.getSlotLabel().isBlank())
            {
                return pid.getSlotLabel();
            }
            if (pid.getLabel() != null && !pid.getLabel().isBlank())
            {
                return pid.getLabel();
            }
            if (pid.getQualifierDetail() != null && !pid.getQualifierDetail().isBlank())
            {
                return pid.getQualifierDetail();
            }
        }

        // Non-grouped rows: no separate row-title concept.
        return "";
    }

    private static String patchDisplayText(PatchId pid)
    {
        if (pid == null)
        {
            return "";
        }

        // Disambiguation for picker + Routes body:
        // Type - Location - Row title (only when it adds information).
        String type = pid.getGroup();
        String location = locationDisplay(pid);

        String rowTitle = "";
        if (pid.getLocationKey() != null && pid.getLocationName() != null)
        {
            if (pid.getSlotLabel() != null && !pid.getSlotLabel().isBlank())
            {
                rowTitle = pid.getSlotLabel();
            }
            else if (pid.getLabel() != null && !pid.getLabel().isBlank() && !pid.getLabel().equalsIgnoreCase(location))
            {
                rowTitle = pid.getLabel();
            }
            else if (pid.getQualifierDetail() != null && !pid.getQualifierDetail().isBlank() && !pid.getQualifierDetail().equalsIgnoreCase(location))
            {
                rowTitle = pid.getQualifierDetail();
            }
        }

        if (rowTitle != null && !rowTitle.isBlank())
        {
            return type + " - " + location + " - " + rowTitle;
        }
        return type + " - " + location;
    }

    private static String safeLower(String s)
    {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static final class PatchPickerDialog extends JDialog
    {
        private final JTextField filter = new JTextField();
        private final DefaultListModel<PatchId> model = new DefaultListModel<>();
        private final JList<PatchId> list = new JList<>(model);
        private final List<PatchId> all;
        private List<PatchId> result;

        static List<PatchId> show(Window owner, FarmutilsConfig config, String title, List<PatchId> items, String okLabel)
        {
            PatchPickerDialog d = new PatchPickerDialog(owner, config, title, items, okLabel);
            d.setVisible(true);
            return d.result;
        }

        PatchPickerDialog(Window owner, FarmutilsConfig config, String title, List<PatchId> items, String okLabel)
        {
            super(owner, title, ModalityType.APPLICATION_MODAL);
            this.all = new ArrayList<>(items);

            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setLayout(new BorderLayout());

            float scale = config != null ? config.textScale().multiplier() : 1.0f;

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(true);
            top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            top.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            filter.setOpaque(false);
            filter.setForeground(ColorScheme.TEXT_COLOR);
            filter.setCaretColor(ColorScheme.TEXT_COLOR);
            filter.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
            filter.setFont(UiFont.scaled(filter.getFont(), scale, Font.PLAIN));
            filter.setToolTipText("Type to filter");
            top.add(filter, BorderLayout.CENTER);
            add(top, BorderLayout.NORTH);

            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            list.setBackground(ColorScheme.DARK_GRAY_COLOR);
            list.setForeground(ColorScheme.TEXT_COLOR);
            list.setFont(UiFont.scaled(list.getFont(), scale * 0.98f, Font.PLAIN));
            list.setCellRenderer(new PatchCellRenderer(config));

            JScrollPane sp = new JScrollPane(list);
            sp.setBorder(null);
            sp.setViewportBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            sp.setOpaque(false);
            sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
            sp.getVerticalScrollBar().setUnitIncrement(16);
            add(sp, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            bottom.setOpaque(true);
            bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            bottom.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            JButton cancel = new JButton("Cancel");
            JButton ok = new JButton(okLabel);
            bottom.add(cancel);
            bottom.add(ok);
            add(bottom, BorderLayout.SOUTH);

            cancel.addActionListener(e ->
            {
                result = null;
                dispose();
            });

            ok.addActionListener(e ->
            {
                List<PatchId> selected = list.getSelectedValuesList();
                result = (selected == null) ? null : new ArrayList<>(selected);
                dispose();
            });

            // ESC closes (cancel)
            getRootPane().registerKeyboardAction(
                    e ->
                    {
                        result = null;
                        dispose();
                    },
                    KeyStroke.getKeyStroke("ESCAPE"),
                    JComponent.WHEN_IN_FOCUSED_WINDOW
            );

            // Enter = OK
            getRootPane().setDefaultButton(ok);

            // Populate
            for (PatchId pid : all)
            {
                model.addElement(pid);
            }

            // Filter
            filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
            {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e)
                {
                    applyFilter(config);
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e)
                {
                    applyFilter(config);
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e)
                {
                    applyFilter(config);
                }
            });

            addWindowListener(new WindowAdapter()
            {
                @Override
                public void windowOpened(WindowEvent e)
                {
                    SwingUtilities.invokeLater(filter::requestFocusInWindow);
                }
            });

            setMinimumSize(new Dimension(320, 420));
            pack();
            setLocationRelativeTo(owner);
        }

        private void applyFilter(FarmutilsConfig config)
        {
            String q = filter.getText();
            String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

            model.clear();
            for (PatchId pid : all)
            {
                String text = PatchCellRenderer.displayText(pid);
                if (needle.isEmpty() || (text != null && text.toLowerCase(Locale.ROOT).contains(needle)))
                {
                    model.addElement(pid);
                }
            }
        }

        private static final class PatchCellRenderer extends DefaultListCellRenderer
        {
            PatchCellRenderer(FarmutilsConfig config)
            {
                // config currently unused; retained to keep renderer signature stable.
            }

            static String displayText(PatchId pid)
            {
                return RoutesPanel.patchDisplayText(pid);
            }

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                JLabel c = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                PatchId pid = (PatchId) value;
                String text = displayText(pid);
                c.setText(text);
                c.setToolTipText(text);

                if (isSelected)
                {
                    c.setBackground(ColorScheme.BRAND_ORANGE.darker());
                    c.setForeground(ColorScheme.TEXT_COLOR);
                }
                else
                {
                    c.setBackground(ColorScheme.DARK_GRAY_COLOR);
                    c.setForeground(ColorScheme.TEXT_COLOR);
                }
                return c;
            }
        }
    }

    private final class InfoRow extends JPanel
    {
        InfoRow(String titleText, String secondaryText)
        {
            super(new BorderLayout());

            float scale = config != null ? config.textScale().multiplier() : 1.0f;

            setOpaque(true);
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            setAlignmentX(Component.LEFT_ALIGNMENT);

            int padY = Math.max(4, Math.round(6 * scale));
            int padX = Math.max(6, Math.round(8 * scale));
            setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

            JPanel leftCol = new JPanel();
            leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
            leftCol.setOpaque(false);
            leftCol.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel title = new JLabel(titleText);
            title.setOpaque(false);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            title.setFont(UiFont.scaled(title.getFont(), scale * 0.98f, Font.PLAIN));

            // Prevent preferred-width inflation (match PatchRow behavior).
            Dimension tp = title.getPreferredSize();
            int th = tp.height;
            title.setPreferredSize(new Dimension(0, th));
            title.setMinimumSize(new Dimension(0, th));
            title.setMaximumSize(new Dimension(Integer.MAX_VALUE, th));

            JLabel secondary = new JLabel(secondaryText);
            secondary.setOpaque(false);
            secondary.setAlignmentX(Component.LEFT_ALIGNMENT);
            secondary.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            secondary.setFont(UiFont.scaled(secondary.getFont(), scale * 0.92f, Font.PLAIN));

            Dimension sp = secondary.getPreferredSize();
            int sh = sp.height;
            secondary.setPreferredSize(new Dimension(0, sh));
            secondary.setMinimumSize(new Dimension(0, sh));
            secondary.setMaximumSize(new Dimension(Integer.MAX_VALUE, sh));

            leftCol.add(title);
            leftCol.add(secondary);

            add(leftCol, BorderLayout.CENTER);

            Dimension pref = getPreferredSize();
            setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        }
    }
}
