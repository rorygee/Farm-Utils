package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchView;
import com.farmutils.infer.GrowthProgress;
import com.farmutils.infer.PatchInference;
import com.farmutils.infer.InferredStage;
import com.farmutils.route.Route;
import com.farmutils.route.RouteId;
import com.farmutils.route.RouteStore;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ItemID;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Set;
import java.util.Optional;
import java.util.OptionalInt;

import net.runelite.client.util.AsyncBufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class PatchRow extends JPanel
{
    private static final Logger log = LoggerFactory.getLogger(PatchRow.class);
    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;

    public static final String PROP_PATCH_DRAG_HANDLE = "farmutils.patchDragHandle";

    private final UiStateStore uiStateStore;
    private final PatchStore store;
    private final RouteStore routeStore;
    private final PatchId patchId;
    private final PatchView view;
    private final FarmutilsConfig config;
    private JLabel titleLabel;
    private JLabel indicatorLabel;
    private JComponent iconComponent;
    private JComponent swatchComponent;

	// Click vs drag guard (row background) to avoid accidental selection while dragging.
	private Point selectionPressPoint;
	private static final int SELECTION_DRAG_THRESHOLD_PX = 5;

    public PatchRow(PatchId id, PatchStore store, RouteStore routeStore, UiStateStore uiStateStore, ItemManager itemManager, FarmutilsConfig config, Runnable onChange)
    {
        this(id, store, routeStore, uiStateStore, itemManager, config, true, null, null, onChange);
    }

    public PatchRow(PatchId id, PatchStore store, RouteStore routeStore, UiStateStore uiStateStore, ItemManager itemManager, FarmutilsConfig config, boolean showIndicator, Runnable onChange)
    {
        this(id, store, routeStore, uiStateStore, itemManager, config, showIndicator, null, null, onChange);
    }

    /**
     * @param titleSuffix Optional suffix appended to the primary title line (e.g. " · Catherby").
     * @param indicatorOverride Optional replacement for the secondary/indicator line.
     *                          If null, the indicator shows patch state (Unknown/Growing/etc.).
     */
    public PatchRow(PatchId id, PatchStore store, RouteStore routeStore, UiStateStore uiStateStore, ItemManager itemManager, FarmutilsConfig config, boolean showIndicator, String titleSuffix, String indicatorOverride, Runnable onChange)
    {
        this.uiStateStore = uiStateStore;
        this.store = store;
        this.routeStore = routeStore;
        this.patchId = id;
        this.view = store.view(id);
        this.config = config;

        PatchView view = this.view;

        float scale = config.textScale().multiplier();

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        int padY = Math.max(4, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));

        setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);
        leftCol.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel();
        this.titleLabel = title;
        title.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setFont(UiFont.scaled(title.getFont(), scale, Font.PLAIN));

        String titleText;
        if (config.showPatchCategoryPrefix())
        {
            titleText = id.getGroup() + " - " + id.getLabel();
        }
        else
        {
            titleText = id.getLabel();
        }

        if (titleSuffix != null && !titleSuffix.isBlank())
        {
            titleText = titleText + titleSuffix;
        }
        title.setText(titleText);

        String secondaryText = (indicatorOverride != null) ? indicatorOverride : indicatorText(view);

		boolean showHidden = uiStateStore != null && uiStateStore.isShowDisabledPatches();
		boolean hiddenByPatch = uiStateStore != null && uiStateStore.isPatchDisabled(id);
		boolean hiddenByGroup = uiStateStore != null && uiStateStore.isGroupHidden(id.getGroup());
		boolean hiddenVisible = showHidden && (hiddenByPatch || hiddenByGroup);
		if (hiddenVisible && showIndicator)
		{
			// Suffix only on the indicator line to avoid layout shifts.
			secondaryText = secondaryText + " · Hidden";
		}
        JLabel indicator = new JLabel(secondaryText);
        this.indicatorLabel = indicator;
        indicator.setOpaque(false);
        indicator.setAlignmentX(Component.LEFT_ALIGNMENT);
        indicator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        indicator.setFont(UiFont.scaled(indicator.getFont(), scale * 0.95f, Font.PLAIN));

        // Optional secondary-line indent (visual hierarchy), kept layout-local (no rewrites).
        int secondaryIndentPx = (config != null) ? Math.max(0, config.secondaryTextIndentPx()) : 0;
        if (secondaryIndentPx > 0)
        {
            indicator.setBorder(BorderFactory.createEmptyBorder(0, secondaryIndentPx, 0, 0));
        }

        // Prevent long titles from inflating preferred width (causes RuneLite viewport inset/border flips).
// We deliberately allow clipping for now; full text remains available via tooltip.
        Dimension titlePref = title.getPreferredSize();
        int titleH = titlePref.height;

        Dimension indPref = indicator.getPreferredSize();
        int indH = indPref.height;

        Dimension pref = getPreferredSize();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        indicator.setMinimumSize(new Dimension(0, indH));


        title.setPreferredSize(new Dimension(0, titleH));
        title.setMinimumSize(new Dimension(0, titleH));
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleH));



        leftCol.add(title);
        if (showIndicator)
        {
            leftCol.add(indicator);
        }

        // Icon is on the right side (left of swatch). Size it relative to the row's text stack
        // so it feels "row-attached" and roughly matches the swatch's visual height.
        int contentH = titleH + (showIndicator ? indH : 0);
        int iconSize = clamp(Math.round((contentH + padY) * 0.90f), 16, 32);

        // Small horizontal gap between icon and swatch.
        int gapAfterIcon = clamp(Math.round(3 * scale), 2, 6);

        setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));





        JLabel iconLabel = new JLabel();
        this.iconComponent = iconLabel;
        iconLabel.setOpaque(false);
        iconLabel.setAlignmentY(0.5f);

// Keep layout stable whether the sprite is available or not.
        Dimension iconDim = new Dimension(iconSize, iconSize);
        iconLabel.setPreferredSize(iconDim);
        iconLabel.setMinimumSize(iconDim);
        iconLabel.setMaximumSize(iconDim);

// Default placeholder (stable layout).
        iconLabel.setText("?");
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        iconLabel.setFont(UiFont.scaled(iconLabel.getFont(), scale * 0.9f, Font.PLAIN));

// Async item sprite pipeline (RuneLite): getImage returns an AsyncBufferedImage.
        int iconItemId = ItemID.WEEDS;
        OptionalInt cropItem = (store != null) ? store.getCropItemId(id) : OptionalInt.empty();
        if (cropItem.isPresent())
        {
            iconItemId = cropItem.getAsInt();
        }

        // Async item sprite pipeline (RuneLite): getImage returns an AsyncBufferedImage.
        AsyncBufferedImage asyncImg = (itemManager != null) ? itemManager.getImage(iconItemId) : null;
        if (asyncImg != null)
        {
            // Keep the label repainting while the image loads.
            asyncImg.onLoaded(() ->
            {
                Image scaled = asyncImg.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
                SwingUtilities.invokeLater(() ->
                {
                    iconLabel.setText(null);
                    iconLabel.setIcon(new ImageIcon(scaled));
                    iconLabel.revalidate();
                    iconLabel.repaint();
                });
            });
        }
        add(leftCol, BorderLayout.CENTER);


// Drag handle (hidden unless reorder mode is enabled by caller).
        JLabel dragHandle = new JLabel("⋮⋮");
        dragHandle.setOpaque(false);
        dragHandle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        dragHandle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        dragHandle.setVisible(false);
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        putClientProperty(PROP_PATCH_DRAG_HANDLE, dragHandle);

// Highlight swatch: a row-attached vertical bar just left of the drag handle.
        int swatchWidth = clamp(Math.round(6 * scale), 4, 10);
        JPanel swatch = new JPanel();
        this.swatchComponent = swatch;
        swatch.setOpaque(true);
        swatch.setBorder(null);
        swatch.setPreferredSize(new Dimension(swatchWidth, 1));
        swatch.setMinimumSize(new Dimension(swatchWidth, 1));
        swatch.setMaximumSize(new Dimension(swatchWidth, Integer.MAX_VALUE));

        applySwatchColor(swatch, store.getHighlightSlot(id));

// Swatch cycles highlight slot on left click. Right-click should fall through to row menu.
        swatch.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger())
                {
                    // Modifier-click on the swatch should batch-cycle the current multi-selection.
                    // This mirrors the PatchRow context menu batching semantics, but is opt-in via
                    // Ctrl/Shift to preserve the fast single-patch cycle default.
                    if ((e.isControlDown() || e.isShiftDown()))
                    {
                        Set<PatchId> targets = getActionTargetPatchIds();
                        if (targets.size() > 1)
                        {
                            store.cycleHighlightSlot(id);
                            int newSlot = store.getHighlightSlot(id);
                            for (PatchId pid : targets)
                            {
                                if (!pid.equals(id))
                                {
                                    store.setHighlightSlot(pid, newSlot);
                                }
                            }
                            onChange.run();
                            e.consume();
                            return;
                        }
                    }

                    store.cycleHighlightSlot(id);
                    onChange.run();
                    e.consume();
                }
            }
        });

        JPanel rightCol = new JPanel();
        rightCol.setOpaque(false);
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.X_AXIS));

        swatch.setAlignmentY(0.5f);
        dragHandle.setAlignmentY(0.5f);

        // Icon sits immediately left of the swatch to avoid left-column alignment drift across views.
        iconLabel.setAlignmentY(0.5f);
        swatch.setAlignmentY(0.5f);
        dragHandle.setAlignmentY(0.5f);

        rightCol.add(iconLabel);
        rightCol.add(Box.createRigidArea(new Dimension(gapAfterIcon, 1)));
        rightCol.add(swatch);
        rightCol.add(dragHandle);

        add(rightCol, BorderLayout.EAST);
        String tooltip = buildTooltip(view);
        setToolTipText(tooltip);
        title.setToolTipText(tooltip);
        indicator.setToolTipText(tooltip);

        // Apply state-text coloring for the modes that are meant to do so.
        applyIndicatorColorFromMode();

        // Patch state manual overrides are only available via the context menu ("Set: …").
        // The indicator label participates in row selection via installSelectionAndContextHandlers().
		// If this patch is hidden (but currently visible due to the toggle), reduce emphasis.
		if (hiddenVisible)
		{
			if (titleLabel != null)
			{
				titleLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
			if (indicatorLabel != null && indicatorLabel.getForeground().equals(ColorScheme.LIGHT_GRAY_COLOR))
			{
				indicatorLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
			}
			// Tooltip remains unchanged.
		}

        JPopupMenu menu = new JPopupMenu();

        for (PatchState state : PatchState.values())
        {
            JMenuItem item = new JMenuItem("Set: " + pretty(state));
            item.addActionListener(e ->
            {
                // Batch injection point: patch-level actions invoked from a selected row should apply to the
                // whole selection. This gives stable multi-select semantics for future features (e.g. routes).
                for (PatchId pid : getActionTargetPatchIds())
                {
                    store.save(pid, state);
                }
                onChange.run();
            });
            menu.add(item);
        }

        menu.addSeparator();

        JMenuItem clear = new JMenuItem("Clear (Unknown)");
        clear.addActionListener(e ->
        {
            for (PatchId pid : getActionTargetPatchIds())
            {
                store.clear(pid);
            }
            onChange.run();
        });
        menu.add(clear);

        menu.addSeparator();

        JMenuItem highlightNone = new JMenuItem("Highlight: None");
        highlightNone.addActionListener(e ->
        {
            for (PatchId pid : getActionTargetPatchIds())
            {
                store.setHighlightSlot(pid, 0);
            }
            onChange.run();
        });
        menu.add(highlightNone);

        for (int slot = 1; slot <= 4; slot++)
        {
            final int s = slot;
            JMenuItem item = new JMenuItem("Highlight: Slot " + slot);
            item.addActionListener(e ->
            {
                for (PatchId pid : getActionTargetPatchIds())
                {
                    store.setHighlightSlot(pid, s);
                }
                onChange.run();
            });
            menu.add(item);
        }

		menu.addSeparator();

		// Routes: runtime-only patch grouping. Keep this lightweight and non-invasive.
		// Keep nesting shallow: main menu -> Add to route -> <routes...>
		if (routeStore != null)
		{
			JMenu addToRoute = new JMenu("Add to route");

			List<Route> routes = new ArrayList<>(routeStore.list());
			routes.sort(
				Comparator
					.comparing((Route r) -> r.getName().toLowerCase(Locale.ROOT))
					.thenComparing(r -> r.getId().toString())
			);

			if (routes.isEmpty())
			{
				JMenuItem none = new JMenuItem("(No routes yet)");
				none.setEnabled(false);
				addToRoute.add(none);
			}
			else
			{
				for (Route r : routes)
				{
					final RouteId rid = r.getId();
					final String routeName = r.getName();
					JMenuItem item = new JMenuItem(routeName);
					item.addActionListener(ev ->
					{
						List<PatchId> targets = getActionTargetPatchIdsInVisibleOrder();
						int added = 0;
						int dupes = 0;
						for (PatchId pid : targets)
						{
							if (pid == null)
							{
								continue;
							}
							boolean ok = routeStore.addPatch(rid, pid);
							if (ok)
							{
								added++;
							}
							else
							{
								dupes++;
							}
						}
						log.info("[routes] added {} patches to route '{}' (ignored {} duplicates)", added, routeName, dupes);
					});
					addToRoute.add(item);
				}
			}

			addToRoute.addSeparator();
			// Disambiguate from a user-created route named "New route".
			JMenuItem newRoute = new JMenuItem("Create new route…");
			newRoute.addActionListener(ev ->
			{
				String name = JOptionPane.showInputDialog(
						PatchRow.this,
						"Route name:",
						"Create route",
						JOptionPane.PLAIN_MESSAGE
				);
				if (name == null)
				{
					return;
				}
				name = name.trim();
				if (name.isEmpty())
				{
					return;
				}

				Route created = routeStore.create(name);
				List<PatchId> targets = getActionTargetPatchIdsInVisibleOrder();
				int added = 0;
				for (PatchId pid : targets)
				{
					if (pid == null)
					{
						continue;
					}
					if (routeStore.addPatch(created.getId(), pid))
					{
						added++;
					}
				}
				log.info("[routes] created route '{}' and added {} patches", created.getName(), added);
			});
			addToRoute.add(newRoute);

			menu.add(addToRoute);
			menu.addSeparator();
		}

		JMenuItem hidePatchItem = new JMenuItem();
		hidePatchItem.addActionListener(e ->
		{
			if (this.uiStateStore != null)
			{
				// Compute targets at action time so right-click selection rules remain authoritative.
				Set<PatchId> targets = getActionTargetPatchIds();
				boolean clickedDisabled = this.uiStateStore.isPatchDisabled(this.patchId);
				boolean disable = !clickedDisabled;
				for (PatchId pid : targets)
				{
					if (pid == null)
					{
						continue;
					}
					if (!disable)
					{
						// Unhiding a patch that lives inside a hidden group should also unhide the group,
						// but leave other patches in that group hidden.
						String groupKey = pid.getGroup();
						if (groupKey != null && this.uiStateStore.isGroupHidden(groupKey))
						{
							this.uiStateStore.setGroupHidden(groupKey, false);
						}
					}
					this.uiStateStore.setPatchDisabled(pid, disable);
				}
				onChange.run();
			}
		});
		menu.add(hidePatchItem);

		// Selection + context menu handling lives here (PatchRow), because PatchId is the stable identity.
		// This avoids coupling selection behavior to the wrapper panels created during FarmPanel.rebuild().
		//
		// Interaction table:
		// - LClick: select only (clears others)
		// - Ctrl+LClick: toggle
		// - Shift+LClick: range from anchor
		// - Ctrl+Shift+LClick: additive range
		// - RClick: if row unselected => select only then show menu; if selected => keep selection
		installSelectionAndContextHandlers(menu, hidePatchItem);
    }


	/**
	 * Determines which PatchIds a context-menu action should apply to.
	 *
	 * If the right-clicked row is part of a multi-selection, patch-level actions should apply to the whole
	 * selection; otherwise they apply only to the clicked patch.
	 */
	private Set<PatchId> getActionTargetPatchIds()
	{
		if (uiStateStore == null || patchId == null)
		{
			return Set.of(patchId);
		}

		Set<PatchId> selected = uiStateStore.getSelectedPatchesView();
		if (selected != null && selected.size() > 1 && selected.contains(patchId))
		{
			// Never expose/mutate the live selection set directly.
			return Set.copyOf(selected);
		}

		return Set.of(patchId);
	}

	/**
	 * Like getActionTargetPatchIds(), but returns targets in the last visible UI order.
	 *
	 * This is used for route insertion so multi-select adds are deterministic and match what the user
	 * sees on screen.
	 */
	private List<PatchId> getActionTargetPatchIdsInVisibleOrder()
	{
		if (uiStateStore == null || patchId == null)
		{
			return List.of(patchId);
		}

		Set<PatchId> selected = uiStateStore.getSelectedPatchesView();
		if (selected != null && selected.size() > 1 && selected.contains(patchId))
		{
			List<PatchId> visibleOrder = uiStateStore.getLastVisiblePatchOrder();
			if (visibleOrder != null && !visibleOrder.isEmpty())
			{
				List<PatchId> out = new ArrayList<>();
				for (PatchId pid : visibleOrder)
				{
					if (selected.contains(pid))
					{
						out.add(pid);
					}
				}
				return out;
			}

			// Fallback: deterministic iteration order is not guaranteed, but at least we don't expose the live set.
			return new ArrayList<>(Set.copyOf(selected));
		}

		return List.of(patchId);
	}

	private void installSelectionAndContextHandlers(JPopupMenu menu, JMenuItem hidePatchItem)
	{
		MouseAdapter adapter = new MouseAdapter()
		{
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
				if (e.isPopupTrigger())
				{
					showContextMenuRespectingSelection(e, menu, hidePatchItem);
				}
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
					showContextMenuRespectingSelection(e, menu, hidePatchItem);
					return;
				}
				if (!SwingUtilities.isLeftMouseButton(e) || uiStateStore == null || patchId == null)
				{
					return;
				}

				// Drag guard: if the user moved the pointer significantly, do not toggle selection.
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

		// Attach to the row and the "content" children so normal clicks work anywhere on the row.
		// Swatch/drag-handle should not affect selection, but should still support right-click menus.
		addMouseListener(adapter);
		if (titleLabel != null) titleLabel.addMouseListener(adapter);
		if (indicatorLabel != null) indicatorLabel.addMouseListener(adapter);
		if (iconComponent != null) iconComponent.addMouseListener(adapter);

		MouseAdapter popupOnly = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!e.isConsumed() && e.isPopupTrigger())
				{
					showContextMenuRespectingSelection(e, menu, hidePatchItem);
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (!e.isConsumed() && e.isPopupTrigger())
				{
					showContextMenuRespectingSelection(e, menu, hidePatchItem);
				}
			}
		};
		if (swatchComponent != null) swatchComponent.addMouseListener(popupOnly);
		Object h = getClientProperty(PROP_PATCH_DRAG_HANDLE);
		if (h instanceof JComponent)
		{
			((JComponent) h).addMouseListener(popupOnly);
		}
	}

	private void showContextMenuRespectingSelection(MouseEvent e, JPopupMenu menu, JMenuItem hidePatchItem)
	{
		if (uiStateStore != null && patchId != null)
		{
			// Desktop norm: right-click on unselected row selects it first.
			// Right-click on selected row preserves multi-selection.
			if (!uiStateStore.isSelected(patchId))
			{
				uiStateStore.selectOnly(patchId);
				uiStateStore.setSelectionAnchor(patchId);
				repaintSelectionSurface();
			}
		}
		// Update context item label at show time so it always reflects current state.
		if (hidePatchItem != null && uiStateStore != null)
		{
			boolean disabled = uiStateStore.isPatchDisabled(patchId);
			hidePatchItem.setText(disabled ? "Unhide patch" : "Hide patch");
		}
		menu.show(e.getComponent(), e.getX(), e.getY());
	}

	private void handleSelectionClick(MouseEvent e)
	{
		boolean ctrl = (e.getModifiersEx() & MouseEvent.CTRL_DOWN_MASK) != 0;
		boolean shift = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) != 0;

		List<PatchId> visibleOrder = uiStateStore.getLastVisiblePatchOrder();
		PatchId anchor = uiStateStore.getSelectionAnchor();

		if (!ctrl && !shift)
		{
			// Desktop norm (as requested): clicking an already-selected row clears selection.
			if (uiStateStore.isSelected(patchId))
			{
				uiStateStore.clearSelection();
				uiStateStore.setSelectionAnchor(null);
			}
			else
			{
				uiStateStore.selectOnly(patchId);
				uiStateStore.setSelectionAnchor(patchId);
			}
			repaintSelectionSurface();
			return;
		}

		if (ctrl && !shift)
		{
			uiStateStore.toggleSelected(patchId);
			uiStateStore.setSelectionAnchor(patchId);
			repaintSelectionSurface();
			return;
		}

		if (!ctrl && shift)
		{
			if (anchor == null)
			{
				uiStateStore.selectOnly(patchId);
				uiStateStore.setSelectionAnchor(patchId);
			}
			else
			{
				uiStateStore.selectRange(visibleOrder, anchor, patchId, false);
			}
			repaintSelectionSurface();
			return;
		}

		// ctrl + shift
		if (anchor == null)
		{
			uiStateStore.toggleSelected(patchId);
			uiStateStore.setSelectionAnchor(patchId);
		}
		else
		{
			uiStateStore.selectRange(visibleOrder, anchor, patchId, true);
		}
		repaintSelectionSurface();
	}

	private void repaintSelectionSurface()
	{
		// Selection visuals are painted directly from UiStateStore state.
		// Repaint up the tree so other rows reflect selection changes without a full rebuild.
		SwingUtilities.invokeLater(() ->
		{
			Component c = PatchRow.this;
			while (c != null)
			{
				c.repaint();
				c = c.getParent();
			}
		});
	}

    public void setReorderHandleVisible(boolean visible)
    {
        Object h = getClientProperty(PROP_PATCH_DRAG_HANDLE);
        if (h instanceof JComponent)
        {
            ((JComponent) h).setVisible(visible);
        }
    }

    private static String indicatorText(PatchView view)
    {
        if (!view.getRecord().isPresent())
        {
            return "Unknown";
        }

        String base = pretty(view.getRecord().get().getState());
        return view.isStale() ? base + " · Stale" : base;
    }

    
    private String buildTooltip(PatchView view)
    {
        String updated;
        if (!view.getRecord().isPresent())
        {
            updated = "Updated: Never.";
        }
        else
        {
            PatchRecord record = view.getRecord().get();
            updated = "Updated: " + timeAgo(record.getUpdatedAtMillis()) + ".";
        }

        // Crop context (when known). Keep calm: no 'State' or 'Source' language.
        String crop = (store != null) ? store.getCropName(patchId).orElse(null) : null;
        String cropPrefix = (crop != null && !crop.isBlank()) ? (crop + ". ") : "";

        String etaPart = "";
        if (store != null && config != null)
        {
            Optional<PatchInference> infOpt = store.getInference(patchId);
            if (infOpt.isPresent())
            {
                PatchInference inf = infOpt.get();
                if (inf.getStage() == InferredStage.GROWING)
                {
                    Instant now = Instant.now();
                    Instant est = config.conservativeTimeEstimates() ? inf.getLatestReadyAt() : inf.getEarliestReadyAt();
                    if (est == null)
                    {
                        // Fallback if only one bound is available.
                        est = (inf.getLatestReadyAt() != null) ? inf.getLatestReadyAt() : inf.getEarliestReadyAt();
                    }
                    if (est != null)
                    {
                        etaPart = "Est completion: " + formatEta(est, now) + ".";
                    }
                }
            }
        }

        if (etaPart.isBlank())
        {
            return cropPrefix + updated;
        }

        return cropPrefix + updated + " " + etaPart;
    }

    private static String formatEta(Instant eta, Instant now)
    {
        if (eta == null)
        {
            return "";
        }
        if (now == null)
        {
            now = Instant.now();
        }

        String hhmm = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(eta);
        long mins = ChronoUnit.MINUTES.between(now, eta);
        if (mins <= 0)
        {
            return hhmm + " (now)";
        }
        return hhmm + " (in " + mins + "m)";
    }

    private static String timeAgo(long updatedAtMillis)
    {
        Instant then = Instant.ofEpochMilli(updatedAtMillis);
        Instant now = Instant.now();

        long minutes = ChronoUnit.MINUTES.between(then, now);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";

        long hours = ChronoUnit.HOURS.between(then, now);
        if (hours < 24) return hours + "h ago";

        long days = ChronoUnit.DAYS.between(then, now);
        return days + "d ago";
    }

    private static String pretty(PatchState state)
    {
        switch (state)
        {
            case GROWING: return "Growing";
            case READY: return "Ready";
            case DISEASED: return "Diseased";
            case EMPTY: return "Empty";
            case DEAD: return "Dead";
            default: return state.name();
        }
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static PatchState nextState(PatchState current)
    {
        PatchState[] values = PatchState.values();
        if (values.length == 0)
        {
            return current;
        }

        if (current == null)
        {
            return values[0];
        }

        for (int i = 0; i < values.length; i++)
        {
            if (values[i] == current)
            {
                return values[(i + 1) % values.length];
            }
        }

        // Unknown enum value (shouldn't happen) – fall back to first.
        return values[0];
    }

    private void applySwatchColor(JPanel swatch, int slot)
    {
        if (slot <= 0)
        {
            // Slot 0 = no highlight
            swatch.setBackground(getBackground());
            return;
        }

        // Simple muted palette (temporary, runtime only)
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


    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

		// Selected styling: paint-only (no insets/pref-size changes) to avoid layout jitter.
		if (uiStateStore != null && patchId != null && uiStateStore.isSelected(patchId))
		{
			Graphics2D sg = (Graphics2D) g.create();
			try
			{
				sg.setColor(selectionOutlineColor());
				int w = getWidth();
				int h = getHeight();
				// Single, uniform outline (no left accent) to avoid "thicker" edges on some DPI scales.
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

            // Draw just above the bottom divider line so we don't get visually "merged" or overdrawn.
            int y = Math.max(0, getHeight() - 2);

            // Canonical margins: match the title text indent (grouped rows have additional indent).
            // This keeps grouped/non-grouped rows consistent relative to their visible content.
            int margin = computeContentMargin();
            int xL = margin;
            int xR = getWidth() - margin - 1;

            // When progress is available, keep the computed end so FULL_AND_TITLE can render
            // a consistent title underline (no "full length" overwrite).
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
                // Progress remainder shading is only meaningful for growing/ready.
                PatchState s = view != null && view.getRecord().isPresent() ? view.getRecord().get().getState() : null;
                boolean allowProgress = (s == PatchState.GROWING || s == PatchState.READY);

                progressOpt = (allowProgress && store != null)
                        ? store.getGrowthProgress(patchId)
                        : Optional.empty();

                if (!progressOpt.isPresent())
                {
                    // Current behavior: solid semantic line across full width.
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
                    // Mirror the same remainder+progress semantics within the title region.
                    // This avoids overwriting the full-width progress line with a "full-length" underline.
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

	private Color selectionOutlineColor()
	{
		FarmutilsConfig.SelectionOutlineColor mode = (config != null)
				? config.selectionOutlineColor()
				: FarmutilsConfig.SelectionOutlineColor.WHITE;

		// Keep alpha so selection reads as a UI affordance, not a semantic highlight.
		switch (mode)
		{
			case ACCENT_ORANGE:
				return new Color(ColorScheme.BRAND_ORANGE.getRed(), ColorScheme.BRAND_ORANGE.getGreen(), ColorScheme.BRAND_ORANGE.getBlue(), 200);
			case WHITE:
			default:
				return new Color(255, 255, 255, 180);
		}
	}

    private int computeContentMargin()
    {
        // Prefer the actual title label x-position (stable across views and reflects grouping indent).
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
        // Right-strip mode: only under the icon region (explicitly *not* including the swatch).
        if (iconComponent == null)
        {
            return;
        }

        Rectangle iconR = SwingUtilities.convertRectangle(iconComponent.getParent(), iconComponent.getBounds(), this);
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

    private Color stateColor(PatchView view)
    {
        // Unknown uses null for indicator-line painting (keeps the list calm).
        return UiColors.stateColorOrNull(view, config);
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

}