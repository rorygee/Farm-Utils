package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.config.NavColumns;
import com.farmutils.config.NavContent;
import com.farmutils.config.TextScale;
import com.farmutils.storage.UiStateStore;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.util.EnumMap;
import java.util.Map;

public class FarmRootPanel extends PluginPanel
{
    public enum Mode
    {
        PATCHES("Patches", "patches"),
        ROUTES("Routes", "routes"),
        CALC("Calc", "calc"),
        EXPORT("Export", "export");

        private final String label;
        private final String iconKey;

        Mode(String label, String iconKey)
        {
            this.label = label;
            this.iconKey = iconKey;
        }

        public String label()
        {
            return label;
        }

        public String iconKey()
        {
            return iconKey;
        }
    }

    private static final String FILTER_PLACEHOLDER = "Filter patches…";
    private static final String PROP_NAV_BASE_FONT = "farmutils.navBaseFont";
    private static final String PROP_FILTER_BASE_FONT = "farmutils.filterBaseFont";

    private static final class PreferredCardPanel extends JPanel
    {
        PreferredCardPanel(LayoutManager layout)
        {
            super(layout);
            setOpaque(true);
        }

        @Override
        public Dimension getPreferredSize()
        {
            for (Component c : getComponents())
            {
                if (c.isVisible())
                {
                    return c.getPreferredSize();
                }
            }
            return super.getPreferredSize();
        }

        @Override
        public Dimension getMinimumSize()
        {
            for (Component c : getComponents())
            {
                if (c.isVisible())
                {
                    return c.getMinimumSize();
                }
            }
            return super.getMinimumSize();
        }
    }

    private final FarmutilsConfig config;
    private final UiStateStore uiStateStore;
    private final FarmPanel farmPanel;
    private final ClientUI clientUI;

    private final JPanel nav = new JPanel(new GridBagLayout());

    private final JPanel chrome = new JPanel();
    private final JPanel filterRow = new JPanel(new BorderLayout());
    private final JPanel toolbarRow = new JPanel(new BorderLayout());
    private final JComponent chromeDivider = divider();

    private final JTextField filterField = new JTextField();
    // Updated in buildToolbar(); invoked after FarmPanel rebuild completes.
    private Runnable refreshCollapseAll = () -> {};
    private final JButton restoreToolbarButton = new JButton("▾");
    private JToggleButton hideToolbarToggle;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new PreferredCardPanel(cardLayout);

    private final Map<Mode, JToggleButton> buttons = new EnumMap<>(Mode.class);
    private Mode current = Mode.PATCHES;

    private KeyEventDispatcher findDispatcher;

    public FarmRootPanel(
            FarmutilsConfig config,
            ClientUI clientUI,
            UiStateStore uiStateStore,
            FarmPanel farmPanel,
            JComponent routesPanel,
            JComponent calcPanel,
            JComponent exportPanel)
    {
        super();

        this.config = config;
        this.clientUI = clientUI;
        this.uiStateStore = uiStateStore;
        this.farmPanel = farmPanel;

        // Painted “floor” so no white bleed-through anywhere
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        cards.setOpaque(true);
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);

        setLayout(new BorderLayout());

        buildNav();
        buildChrome();
        buildCards(farmPanel, routesPanel, calcPanel, exportPanel);

        add(chrome, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);

        showMode(Mode.PATCHES);
    }

    private void buildChrome()
    {
        chrome.setLayout(new BoxLayout(chrome, BoxLayout.Y_AXIS));
        chrome.setOpaque(true);
        chrome.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        chrome.add(nav);

        buildFilterRow();
        chrome.add(filterRow);

        buildToolbarRow();
        chrome.add(toolbarRow);

        chrome.add(chromeDivider);

        // initial visibility
        setPatchesChromeVisible(true);
    }

    private void buildFilterRow()
    {
        filterRow.setOpaque(true);
        // Give the filter row the same side padding feel as the nav without nesting.
        filterRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        filterRow.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        filterField.setOpaque(false);
        filterField.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        filterField.setCaretColor(ColorScheme.TEXT_COLOR);

        // placeholder
        filterField.setText(FILTER_PLACEHOLDER);
        filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        filterField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (FILTER_PLACEHOLDER.equals(filterField.getText()))
                {
                    filterField.setText("");
                    filterField.setForeground(ColorScheme.TEXT_COLOR);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e)
            {
                if (filterField.getText().isEmpty())
                {
                    filterField.setText(FILTER_PLACEHOLDER);
                    filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                }
            }
        });

        filterField.getDocument().addDocumentListener(new DocumentListener()
        {
            private void changed()
            {
                // Only apply filter when patches mode is active.
                if (current != Mode.PATCHES)
                {
                    return;
                }

                String t = filterField.getText();
                if (t == null)
                {
                    farmPanel.setFilterText("");
                    return;
                }

                String trimmed = t.trim();
                if (trimmed.isEmpty() || FILTER_PLACEHOLDER.equals(t))
                {
                    farmPanel.setFilterText("");
                }
                else
                {
                    farmPanel.setFilterText(trimmed);
                }
            }
            // Toolbar refresh is invoked after rebuild completes.

            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });

        // Backspace on an empty filter should unfocus (return focus to game) without
        // interfering with normal text editing.
        ActionMap am = filterField.getActionMap();
        Action original = am.get(DefaultEditorKit.deletePrevCharAction);
        if (original != null)
        {
            am.put(DefaultEditorKit.deletePrevCharAction, new AbstractAction()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    String t = filterField.getText();
                    boolean effectivelyEmpty = (t == null) || t.isEmpty() || FILTER_PLACEHOLDER.equals(t);
                    if (effectivelyEmpty)
                    {
                        clientUI.forceFocus();
                        return;
                    }

                    original.actionPerformed(e);
                }
            });
        }

        // ESC: if empty -> return focus to game; else -> clear filter
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        filterField.getInputMap(JComponent.WHEN_FOCUSED).put(esc, "farmutils.esc");
        filterField.getActionMap().put("farmutils.esc", new AbstractAction()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                String t = filterField.getText();
                boolean effectivelyEmpty = (t == null) || t.trim().isEmpty() || FILTER_PLACEHOLDER.equals(t);

                if (effectivelyEmpty)
                {
                    clientUI.forceFocus();
                    return;
                }

                filterField.setText("");
                filterField.setForeground(ColorScheme.TEXT_COLOR);
                farmPanel.setFilterText("");
            }
        });

        filterRow.add(filterField, BorderLayout.CENTER);

        // Restore toolbar button (only shown when toolbar is hidden).
        restoreToolbarButton.setFocusable(false);
        restoreToolbarButton.setMargin(new Insets(0, 6, 0, 6));
        restoreToolbarButton.setToolTipText("Show toolbar");
        restoreToolbarButton.setVisible(false);
        restoreToolbarButton.addActionListener(e ->
        {
            // Best-effort: if the filter was focused before the click, restore focus after showing.
            Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
            boolean refocusFilter = (focusOwner == filterField);

            if (uiStateStore != null)
            {
                uiStateStore.setToolbarHidden(false);
            }

            applyToolbarHiddenState();

            if (refocusFilter)
            {
                SwingUtilities.invokeLater(() -> filterField.requestFocusInWindow());
            }
        });
        filterRow.add(restoreToolbarButton, BorderLayout.EAST);
    }

    private void buildToolbarRow()
    {
        // Toolbar is part of the sticky chrome (non-scrolling).
        // Keep it permanently opaque with a consistent dark backdrop.
        toolbarRow.setOpaque(true);
        toolbarRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        toolbarRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new LayoutManager()
        {
            @Override public void addLayoutComponent(String name, Component comp) {}
            @Override public void removeLayoutComponent(Component comp) {}

            @Override
            public Dimension preferredLayoutSize(Container parent)
            {
                int count = parent.getComponentCount();
                int w = parent.getWidth();
                if (w <= 0)
                {
                    w = toolbarRow.getWidth();
                }
                if (w <= 0)
                {
                    Container trp = toolbarRow.getParent();
                    if (trp != null)
                    {
                        w = trp.getWidth();
                    }
                }

                if (count <= 0 || w <= 0)
                {
                    // Fallback while not yet laid out
                    return new Dimension(1, 28);
                }
                int size = Math.max(18, w / count);
                return new Dimension(w, size);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent)
            {
                return preferredLayoutSize(parent);
            }

            @Override
            public void layoutContainer(Container parent)
            {
                int count = parent.getComponentCount();
                if (count <= 0)
                {
                    return;
                }

                int w = parent.getWidth();
                if (w <= 0)
                {
                    w = toolbarRow.getWidth();
                }
                if (w <= 0)
                {
                    Container trp = toolbarRow.getParent();
                    if (trp != null)
                    {
                        w = trp.getWidth();
                    }
                }
                int base = w / count;
                int rem = w % count;

                int size = Math.max(18, base); // keep usable if very narrow

                // Make the toolbar row match the square height (so it “scales to box height”).
                int targetH = size;
                if (toolbarRow.getPreferredSize().height != targetH)
                {
                    toolbarRow.setPreferredSize(new Dimension(1, targetH));
                    toolbarRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetH));

                    // Ensure the sticky chrome re-lays out immediately; otherwise the height update
                    // may not take effect until some later UI interaction triggers a revalidate.
                    toolbarRow.revalidate();
                    Container p = toolbarRow.getParent();
                    if (p != null)
                    {
                        p.revalidate();
                    }
                    toolbarRow.repaint();
                }


                int x = 0;
                for (int i = 0; i < count; i++)
                {
                    int extra = (i < rem) ? 1 : 0; // distribute leftover pixels
                    int bw = base + extra;

                    Component c = parent.getComponent(i);
                    c.setBounds(x, 0, bw, targetH);
                    x += bw;
                }
            }
        });

        // Tooltip helper: for now name-only. Later can append description based on config.
        java.util.function.BiFunction<String, String, String> tooltip =
                (name, description) -> name; // TODO later: name + " — " + description (when enabled)

        java.util.function.Consumer<AbstractButton> styleButton = (b) ->
        {
            b.setFocusable(false);
            b.setMargin(new Insets(0, 0, 0, 0));
        };


        // --- Suggested non-toggle actions (JButton) ---
        JButton viewBtn = new JButton("V"); // View mode (cycle / open menu later)
        viewBtn.setToolTipText(tooltip.apply("View mode", "Change the list presentation"));
        styleButton.accept(viewBtn);

        JButton sortBtn = new JButton("S"); // Sort / ordering menu later (not DnD)
        sortBtn.setToolTipText(tooltip.apply("Sort / order", "Change ordering mode"));
        styleButton.accept(sortBtn);

        JButton collapseAllBtn = new JButton("C"); // Collapse groups/locations (future)
        collapseAllBtn.setToolTipText(tooltip.apply("Collapse / expand", "Collapse or expand sections"));
        styleButton.accept(collapseAllBtn);

        JButton refreshBtn = new JButton("↻"); // Manual refresh (future; maybe forces recalculation)
        refreshBtn.setToolTipText(tooltip.apply("Refresh", "Re-read state / repaint"));
        styleButton.accept(refreshBtn);

        // --- Suggested toggles (JToggleButton) ---
        JToggleButton reorderTgl = new JToggleButton("R");
        reorderTgl.setToolTipText(tooltip.apply("Reorder", "Enable drag reordering"));
        styleButton.accept(reorderTgl);

        // Single source of truth is UiStateStore.
        reorderTgl.setSelected(uiStateStore != null && uiStateStore.isReorderModeEnabled());

        // Contract: Reorder is only available when SortMode == DEFAULT and ViewMode != FLAT.
        if (uiStateStore != null && (uiStateStore.getPatchListSortMode() != UiStateStore.SortMode.DEFAULT
                || uiStateStore.getPatchListViewMode() == UiStateStore.ViewMode.FLAT))
        {
            reorderTgl.setEnabled(false);
            reorderTgl.setSelected(false);
        }


        // Collapse/Expand all is only meaningful when the current view renders collapsible headers.
        // Rules:
        // - If mixed open/closed states: default action is "Collapse all".
        // - Disabled while reordering is ON.
        Runnable refreshCollapseAll = () ->
        {
            if (uiStateStore == null)
            {
                collapseAllBtn.setEnabled(false);
                return;
            }

            java.util.List<String> groups = farmPanel.getVisibleCollapsibleGroups();
            boolean hasCollapsible = !groups.isEmpty();
            boolean enabled = hasCollapsible && !uiStateStore.isReorderModeEnabled();

            collapseAllBtn.setEnabled(enabled);

            if (!hasCollapsible)
            {
                collapseAllBtn.setToolTipText(tooltip.apply("Collapse / expand", "No collapsible sections in this view"));
                return;
            }

            if (uiStateStore.isReorderModeEnabled())
            {
                collapseAllBtn.setToolTipText(tooltip.apply("Collapse / expand", "Disabled while reordering"));
                return;
            }

            boolean anyCollapsed = false;
            boolean anyExpanded = false;

            for (String g : groups)
            {
                if (uiStateStore.isGroupCollapsed(g))
                {
                    anyCollapsed = true;
                }
                else
                {
                    anyExpanded = true;
                }
            }

            // Mixed or all expanded -> collapse all. All collapsed -> expand all.
            boolean willCollapse = anyExpanded;

            collapseAllBtn.setToolTipText(tooltip.apply(
                    willCollapse ? "Collapse all" : "Expand all",
                    willCollapse ? "Collapse all visible sections" : "Expand all visible sections"));
        };

        this.refreshCollapseAll = refreshCollapseAll;
        farmPanel.setOnAfterRebuild(refreshCollapseAll);


        collapseAllBtn.addActionListener(e ->
        {
            if (uiStateStore == null)
            {
                return;
            }

            java.util.List<String> groups = farmPanel.getVisibleCollapsibleGroups();
            if (groups.isEmpty())
            {
                return;
            }

            boolean anyExpanded = false;
            for (String g : groups)
            {
                if (!uiStateStore.isGroupCollapsed(g))
                {
                    anyExpanded = true;
                    break;
                }
            }

            // If anything is expanded, collapse all; otherwise expand all.
            boolean targetCollapsed = anyExpanded;

            for (String g : groups)
            {
                uiStateStore.setGroupCollapsed(g, targetCollapsed);
            }

            farmPanel.rebuild();
        });

        // Initial state; subsequent updates are driven by FarmPanel rebuilds.
        refreshCollapseAll.run();

        reorderTgl.addActionListener(e ->
        {
            if (uiStateStore != null)
            {
                // Contract: FLAT view does not support patch reordering.
                if (uiStateStore.getPatchListViewMode() == UiStateStore.ViewMode.FLAT)
                {
                    uiStateStore.setReorderModeEnabled(false);
                    reorderTgl.setSelected(false);
                    farmPanel.rebuild();
                    return;
                }

                uiStateStore.setReorderModeEnabled(reorderTgl.isSelected());
            }
            // Drag bindings are rebuilt with the list.
            farmPanel.rebuild();
        });

        // Now that reorderTgl exists, wire view + sort cycling so we can keep toggle state in sync.
        viewBtn.addActionListener(e ->
        {
            if (uiStateStore == null)
            {
                return;
            }

            UiStateStore.ViewMode cur = uiStateStore.getPatchListViewMode();
            UiStateStore.ViewMode next;

            switch (cur)
            {
                case DEFAULT:
                    next = UiStateStore.ViewMode.FLAT;
                    break;
                case FLAT:
                    next = UiStateStore.ViewMode.CLEAN;
                    break;
                default:
                    next = UiStateStore.ViewMode.DEFAULT;
                    break;
            }

            uiStateStore.setPatchListViewMode(next);

            // Enforce contracts in the UI immediately.
            boolean canReorder = uiStateStore.getPatchListSortMode() == UiStateStore.SortMode.DEFAULT
                    && uiStateStore.getPatchListViewMode() != UiStateStore.ViewMode.FLAT;
            reorderTgl.setEnabled(canReorder);
            if (!canReorder)
            {
                reorderTgl.setSelected(false);
            }

            // Collapse availability depends on current view.
            refreshCollapseAll.run();

            farmPanel.rebuild();
        });

        sortBtn.addActionListener(e ->
        {
            if (uiStateStore == null)
            {
                return;
            }

            UiStateStore.SortMode cur = uiStateStore.getPatchListSortMode();
            UiStateStore.SortMode next = (cur == UiStateStore.SortMode.DEFAULT)
                    ? UiStateStore.SortMode.ALPHABETICAL
                    : UiStateStore.SortMode.DEFAULT;

            uiStateStore.setPatchListSortMode(next);

            // Enforce Sort -> Reorder contract in the UI immediately.
            boolean canReorder = uiStateStore.getPatchListSortMode() == UiStateStore.SortMode.DEFAULT
                    && uiStateStore.getPatchListViewMode() != UiStateStore.ViewMode.FLAT;
            reorderTgl.setEnabled(canReorder);
            if (!canReorder)
            {
                reorderTgl.setSelected(false);
            }

            farmPanel.rebuild();
        });

        JToggleButton showDisabledTgl = new JToggleButton("👁");
        showDisabledTgl.getAccessibleContext().setAccessibleName("Show hidden patches");
        styleButton.accept(showDisabledTgl);

		if (uiStateStore != null)
		{
			showDisabledTgl.setSelected(uiStateStore.isShowDisabledPatches());
		}

		Runnable refreshShowDisabledTooltip = () ->
		{
			boolean on = showDisabledTgl.isSelected();
			showDisabledTgl.setToolTipText(tooltip.apply(
				on ? "Hide hidden patches" : "Show hidden patches",
				on ? "Hide disabled patches from the list" : "Show disabled patches in the list"));
		};
		refreshShowDisabledTooltip.run();

		showDisabledTgl.addActionListener(e ->
		{
			if (uiStateStore != null)
			{
				uiStateStore.setShowDisabledPatches(showDisabledTgl.isSelected());
			}
			refreshShowDisabledTooltip.run();
			farmPanel.rebuild();
		});

        JToggleButton highlightsTgl = new JToggleButton("H"); // highlight visibility (future)
        highlightsTgl.setToolTipText(tooltip.apply("Show highlights", "Toggle highlight indicators"));
        styleButton.accept(highlightsTgl);

        JToggleButton stateLinesTgl = new JToggleButton("L"); // state divider visibility (future)
        stateLinesTgl.setToolTipText(tooltip.apply("State indicators", "Cycle patch state indicator line mode"));
        styleButton.accept(stateLinesTgl);

        // Runtime-only: cycles how patch state is indicated on each row.
        if (uiStateStore != null)
        {
            stateLinesTgl.setSelected(uiStateStore.getStateIndicatorMode() != com.farmutils.storage.UiStateStore.StateIndicatorMode.OFF);
        }
        stateLinesTgl.addActionListener(e ->
        {
            if (uiStateStore == null)
            {
                return;
            }

            com.farmutils.storage.UiStateStore.StateIndicatorMode next = uiStateStore.cycleStateIndicatorMode();
            stateLinesTgl.setSelected(next != com.farmutils.storage.UiStateStore.StateIndicatorMode.OFF);
            stateLinesTgl.setToolTipText(tooltip.apply("State indicators", "Mode: " + next.name()));

            farmPanel.rebuild();
        });

        hideToolbarToggle = new JToggleButton("▴"); // Hide toolbar (arrow up)
        hideToolbarToggle.setToolTipText(tooltip.apply("Hide toolbar", "Collapse the toolbar row"));
        hideToolbarToggle.getAccessibleContext().setAccessibleName("Hide toolbar");
        styleButton.accept(hideToolbarToggle);

        hideToolbarToggle.setSelected(uiStateStore != null && uiStateStore.isToolbarHidden());
        hideToolbarToggle.addActionListener(e ->
        {
            if (uiStateStore != null)
            {
                uiStateStore.setToolbarHidden(hideToolbarToggle.isSelected());
            }
            applyToolbarHiddenState();
        });

        // --- Layout: left cluster, subtle spacing between “groups”, glue, right utilities ---
        content.add(viewBtn);
        content.add(sortBtn);
        content.add(reorderTgl);
        content.add(showDisabledTgl);
        content.add(highlightsTgl);
        content.add(stateLinesTgl);
        content.add(collapseAllBtn);
        content.add(refreshBtn);
        content.add(hideToolbarToggle);

        toolbarRow.add(content, BorderLayout.CENTER);

        toolbarRow.setPreferredSize(new Dimension(1, 28));
        toolbarRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
    }


    private void setPatchesChromeVisible(boolean visible)
    {
        filterRow.setVisible(visible);
        chromeDivider.setVisible(visible);

        // Toolbar visibility is controlled by runtime state (Option A).
        applyToolbarHiddenState();

        // Layout must recompute when hiding rows.
        chrome.revalidate();
        chrome.repaint();
    }

    private void applyToolbarHiddenState()
    {
        boolean patchesActive = (current == Mode.PATCHES);
        boolean hidden = uiStateStore != null && uiStateStore.isToolbarHidden();

        toolbarRow.setVisible(patchesActive && !hidden);
        restoreToolbarButton.setVisible(patchesActive && hidden);

        if (hideToolbarToggle != null && hideToolbarToggle.isSelected() != hidden)
        {
            hideToolbarToggle.setSelected(hidden);
        }

        // Keep BoxLayout from leaving stale space.
        chrome.revalidate();
        chrome.repaint();
    }

    private void applyNavFont(JToggleButton btn)
    {
        // Store a stable base font so we don't compound scaling every refresh.
        Font base = (Font) btn.getClientProperty(PROP_NAV_BASE_FONT);
        if (base == null)
        {
            // Use RuneLite font as base to match overall UI
            base = FontManager.getRunescapeFont();
            if (base == null)
            {
                base = btn.getFont();
            }
            btn.putClientProperty(PROP_NAV_BASE_FONT, base);
        }

        float scale = config.textScale().multiplier();

        // Clamp XL to behave like Large for the nav
        float effectiveScale = Math.min(scale, TextScale.LARGE.multiplier());

        // Gentler scaling for nav
        float navScale = 1.0f + (effectiveScale - 1.0f) * 0.65f;

        btn.setFont(UiFont.scaled(base, navScale, Font.PLAIN));
    }

    private void applyFilterSizing()
    {
        // Base font stored once to avoid compounding scaling.
        Font base = (Font) filterField.getClientProperty(PROP_FILTER_BASE_FONT);
        if (base == null)
        {
            base = filterField.getFont();
            filterField.putClientProperty(PROP_FILTER_BASE_FONT, base);
        }

        float scale = config.textScale().multiplier();
        filterField.setFont(UiFont.scaled(base, scale, Font.PLAIN));

        int h = Math.round(26 * scale);
        filterField.setPreferredSize(new Dimension(0, h));
        filterField.setMinimumSize(new Dimension(0, h));
        filterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));

        int padY = Math.max(4, Math.round(6 * scale));
        int padX = Math.max(6, Math.round(8 * scale));
        filterField.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

        // Keep placeholder styling correct
        if (FILTER_PLACEHOLDER.equals(filterField.getText()))
        {
            filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        }
        else
        {
            filterField.setForeground(ColorScheme.TEXT_COLOR);
        }

        filterRow.revalidate();
        filterRow.repaint();
    }

    private void applyToolbarBackground()
    {
        boolean solid = config.toolbarSolidBackground();
        toolbarRow.setOpaque(solid);
        if (solid)
        {
            toolbarRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        }
    }



    private void buildNav()
    {
        nav.removeAll();

        // Ensure selection always refers to the current button instances.
        buttons.clear();

        nav.setOpaque(true);
        nav.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nav.setBorder(new EmptyBorder(6, 6, 6, 6));
        nav.setLayout(new GridBagLayout());

        NavContent content = config.navContent();

        NavColumns columns = config.navColumns();


        ButtonGroup group = new ButtonGroup();

        Mode[] modes = { Mode.PATCHES, Mode.ROUTES, Mode.CALC, Mode.EXPORT };

        GridBagConstraints base = new GridBagConstraints();
        base.insets = new Insets(0, 0, 0, 0);
        base.weighty = 0;

        // 2x2 should fill horizontally so labels have room.
        base.fill = GridBagConstraints.HORIZONTAL;
        base.weightx = 1;

        for (int i = 0; i < modes.length; i++)
        {
            GridBagConstraints c = (GridBagConstraints) base.clone();
            c.gridx = colFor(i, columns);
            c.gridy = rowFor(i, columns);

            addButton(group, c, content, modes[i]);
        }

        // Preserve selected mode across rebuilds (config changes, plugin reload, etc.).
        JToggleButton selected = buttons.get(current);
        if (selected != null)
        {
            selected.setSelected(true);
        }

        // Divider under nav
        int rows = rowFor(3, columns) + 1;

        JComponent sep = UiTokens.divider(ColorScheme.DARKER_GRAY_COLOR);
        GridBagConstraints sepC = new GridBagConstraints();
        sepC.gridx = 0;
        sepC.gridy = rows;
        sepC.gridwidth = gridWidthFor(columns);
        sepC.fill = GridBagConstraints.HORIZONTAL;
        sepC.weightx = 1;
        nav.add(sep, sepC);

        nav.revalidate();
        nav.repaint();
    }

    private static int columnsPerRow(NavColumns c)
    {
        return c == NavColumns.TWO_PER_ROW ? 2 : 4;
    }

    private static int rowFor(int i, NavColumns c)
    {
        int cols = columnsPerRow(c);
        return i / cols;
    }

    private static int colFor(int i, NavColumns c)
    {
        int cols = columnsPerRow(c);
        return i % cols;
    }

    private static int gridWidthFor(NavColumns c)
    {
        return columnsPerRow(c);
    }


    private void addButton(ButtonGroup group, GridBagConstraints c, NavContent content, Mode mode)
    {
        JToggleButton btn = new JToggleButton();
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(Color.WHITE);

        // Always available for icon-only modes.
        btn.setToolTipText(mode.label());

        NavColumns columns = config.navColumns();

        switch (content)
        {
            case TEXT_ONLY:
            {
                btn.setText(mode.label());
                btn.setIcon(null);
                btn.setHorizontalAlignment(SwingConstants.CENTER);
                btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                applyNavFont(btn);
                break;
            }

            case SMALL_ICONS:
            {
                btn.setIcon(loadNavIcon(mode, 16));

                if (columns == NavColumns.TWO_PER_ROW)
                {
                    // 2-per-row: icon + label
                    btn.setText(mode.label());
                    btn.setIconTextGap(4);
                    btn.setHorizontalAlignment(SwingConstants.LEFT);
                    btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                    applyNavFont(btn);
                }
                else
                {
                    // 4-per-row: icon only
                    btn.setText("");
                    btn.setHorizontalAlignment(SwingConstants.CENTER);
                    btn.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                }
                break;
            }

            case LARGE_ICONS:
            default:
            {
                int iconPx = (columns == NavColumns.TWO_PER_ROW) ? 64 : 28;
                btn.setIcon(loadNavIcon(mode, iconPx));
                btn.setText("");
                btn.setHorizontalAlignment(SwingConstants.CENTER);

                int pad = (columns == NavColumns.FOUR_PER_ROW) ? 4 : 6;
                btn.setBorder(BorderFactory.createEmptyBorder(pad, pad, pad, pad));
                break;
            }
        }

        btn.addActionListener(e ->
        {
            if (current != mode)
            {
                showMode(mode);
            }
        });

        group.add(btn);
        buttons.put(mode, btn);
        nav.add(btn, c);
    }



    private void buildCards(FarmPanel farmPanel, JComponent routes, JComponent calc, JComponent export)
    {
        cards.add(farmPanel, Mode.PATCHES.name());
        cards.add(routes, Mode.ROUTES.name());
        cards.add(calc, Mode.CALC.name());
        cards.add(export, Mode.EXPORT.name());
    }

    private void showMode(Mode mode)
    {
        Mode prev = current;
        current = mode;

        JToggleButton btn = buttons.get(mode);
        if (btn != null && !btn.isSelected())
        {
            btn.setSelected(true);
        }

        boolean patchesActive = (mode == Mode.PATCHES);
        setPatchesChromeVisible(patchesActive);

        if (prev == Mode.PATCHES && !patchesActive)
        {
            // Leaving patches: clear filter + return focus to game.
            filterField.setText(FILTER_PLACEHOLDER);
            filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            farmPanel.setFilterText("");
            clientUI.forceFocus();
        }

        cardLayout.show(cards, mode.name());

        cards.revalidate();
        cards.repaint();
        revalidate();
        repaint();
    }

    public void rebuildNav()
    {
        buildNav();
        nav.revalidate();
        nav.repaint();
    }

    public void refreshUiFromConfig()
    {
        buildNav();              // <-- this is what you were missing
        applyFilterSizing();
        applyToolbarBackground();
        revalidate();
        repaint();
    }


    public void resetToDefault()
    {
        showMode(Mode.PATCHES);
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        installFindShortcut();
        installFilterBlurOnOutsideClick();
        installGlobalScrollFromChrome();
    }

    @Override
    public void removeNotify()
    {
        uninstallGlobalScrollFromChrome();
        uninstallFilterBlurOnOutsideClick();
        uninstallFindShortcut();
        super.removeNotify();
    }

    private javax.swing.Icon loadNavIcon(Mode mode, int px)
    {
        String path = "/nav/" + px + "/" + mode.iconKey() + ".png";
        java.net.URL url = FarmRootPanel.class.getResource(path);
        return url == null ? null : new javax.swing.ImageIcon(url);
    }

    private MouseWheelListener globalScrollListener;

    private void installGlobalScrollFromChrome()
    {
        if (globalScrollListener != null)
        {
            return;
        }

        globalScrollListener = e ->
        {
            if (current != Mode.PATCHES)
            {
                return;
            }
            if (!config.globalScroll())
            {
                return;
            }

            farmPanel.scrollByWheel(e);
        };

        nav.addMouseWheelListener(globalScrollListener);
        filterRow.addMouseWheelListener(globalScrollListener);
        toolbarRow.addMouseWheelListener(globalScrollListener);
        chromeDivider.addMouseWheelListener(globalScrollListener);
        filterField.addMouseWheelListener(globalScrollListener);
    }



    private void uninstallGlobalScrollFromChrome()
    {
        if (globalScrollListener == null)
        {
            return;
        }

        nav.removeMouseWheelListener(globalScrollListener);
        filterRow.removeMouseWheelListener(globalScrollListener);
        toolbarRow.removeMouseWheelListener(globalScrollListener);
        chromeDivider.removeMouseWheelListener(globalScrollListener);
        filterField.removeMouseWheelListener(globalScrollListener);

        globalScrollListener = null;
    }

    private void installFindShortcut()
    {
        if (findDispatcher != null)
        {
            return;
        }

        final int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        findDispatcher = e ->
        {
            if (!isShowing()) return false;
            if (current != Mode.PATCHES) return false;
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;

            if (e.getKeyCode() == KeyEvent.VK_F && (e.getModifiersEx() & menuMask) == menuMask)
            {
                if (filterField.isFocusOwner())
                {
                    clientUI.forceFocus();
                }
                else
                {
                    filterField.requestFocusInWindow();
                    if (FILTER_PLACEHOLDER.equals(filterField.getText()))
                    {
                        filterField.setText("");
                        filterField.setForeground(ColorScheme.TEXT_COLOR);
                    }
                    else
                    {
                        filterField.selectAll();
                    }
                }
                return true;
            }
            return false;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(findDispatcher);
    }

    private void uninstallFindShortcut()
    {
        if (findDispatcher == null) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(findDispatcher);
        findDispatcher = null;
    }

    private void installFilterBlurOnOutsideClick()
    {
        AWTEventListener listener = event ->
        {
            if (current != Mode.PATCHES) return;
            if (!(event instanceof MouseEvent)) return;

            MouseEvent me = (MouseEvent) event;
            if (me.getID() != MouseEvent.MOUSE_PRESSED) return;

            Object src = me.getSource();
            if (!(src instanceof Component)) return;

            Component c = (Component) src;
            Window w = SwingUtilities.getWindowAncestor(this);
            if (w != null && !SwingUtilities.isDescendingFrom(c, w))
            {
                return; // click was outside this client window
            }

            if (filterField.isFocusOwner() && !SwingUtilities.isDescendingFrom(c, filterField))
            {
                clientUI.forceFocus();
            }
        };

        putClientProperty("farmutils.filterBlurListener", listener);
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
    }

    private void uninstallFilterBlurOnOutsideClick()
    {
        Object o = getClientProperty("farmutils.filterBlurListener");
        if (o instanceof AWTEventListener)
        {
            Toolkit.getDefaultToolkit().removeAWTEventListener((AWTEventListener) o);
        }
        putClientProperty("farmutils.filterBlurListener", null);
    }


    private static JComponent divider()
    {
        JPanel p = new JPanel();
        p.setOpaque(true);
        p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        p.setPreferredSize(new Dimension(1, 1));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return p;
    }

    @Override
    public Dimension getPreferredSize()
    {
        // Gate 1/4: stop preferred-height "stickiness" across CardLayout tabs and
        // prevent the outer RuneLite sidebar scrollpane from taking over scrolling.
        // RuneLite wraps plugin panels in a JScrollPane; our immediate parent is not
        // always the JViewport, so we resolve the nearest viewport ancestor.
        Container viewportParent = (Container) SwingUtilities.getAncestorOfClass(JViewport.class, this);
        if (viewportParent != null)
        {
            Dimension viewport = viewportParent.getSize();
            if (viewport != null && viewport.width > 0 && viewport.height > 0)
            {
                return viewport;
            }
        }
        return super.getPreferredSize();
    }



    @Override
    public Dimension getMinimumSize()
    {
        return new Dimension(0, 0);
    }
}