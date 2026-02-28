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

import javax.imageio.ImageIO;
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
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;

public class FarmRootPanel extends PluginPanel
{

    /**
     * Border that reserves left padding for an optional icon and paints it inside the inset.
     * Keeps sizing logic centralized in applyFilterSizing() so text scale changes are consistent.
     */
    private static final class LeftIconBorder extends javax.swing.border.AbstractBorder
    {
        private final Insets insets;
        private final javax.swing.Icon icon;
        private final int iconX;

        LeftIconBorder(int top, int leftPad, int bottom, int right, javax.swing.Icon icon, int iconGap)
        {
            int extra = 0;
            if (icon != null)
            {
                extra = icon.getIconWidth() + Math.max(0, iconGap);
            }
            this.insets = new Insets(top, leftPad + extra, bottom, right);
            this.icon = icon;
            this.iconX = leftPad;
        }

        @Override
        public Insets getBorderInsets(Component c)
        {
            return (Insets) insets.clone();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets)
        {
            insets.top = this.insets.top;
            insets.left = this.insets.left;
            insets.bottom = this.insets.bottom;
            insets.right = this.insets.right;
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
        {
            if (icon == null)
            {
                return;
            }

            int iy = y + (height - icon.getIconHeight()) / 2;
            icon.paintIcon(c, g, x + iconX, iy);
        }
    }

    /**
     * Recolors an ARGB icon image to match the component foreground at paint-time.
     * This keeps the search glyph consistent with placeholder/text color without shipping
     * multiple pre-tinted assets.
     */
    private static final class ForegroundTintIcon implements javax.swing.Icon
    {
        private final BufferedImage base;
        private final int w;
        private final int h;
        private final Map<Integer, BufferedImage> cache = new HashMap<>();

        ForegroundTintIcon(BufferedImage base)
        {
            this.base = base;
            this.w = base.getWidth();
            this.h = base.getHeight();
        }

        @Override
        public int getIconWidth()
        {
            return w;
        }

        @Override
        public int getIconHeight()
        {
            return h;
        }

        private BufferedImage tinted(Color color)
        {
            int rgb = color.getRGB() & 0x00FFFFFF;
            return cache.computeIfAbsent(rgb, k ->
            {
                BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int yy = 0; yy < h; yy++)
                {
                    for (int xx = 0; xx < w; xx++)
                    {
                        int argb = base.getRGB(xx, yy);
                        int a = (argb >>> 24) & 0xFF;
                        if (a == 0)
                        {
                            out.setRGB(xx, yy, 0);
                            continue;
                        }
                        out.setRGB(xx, yy, (a << 24) | rgb);
                    }
                }
                return out;
            });
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Color fg = c == null ? ColorScheme.TEXT_COLOR : c.getForeground();
            if (fg == null)
            {
                fg = ColorScheme.TEXT_COLOR;
            }

            BufferedImage img = tinted(fg);
            g.drawImage(img, x, y, null);
        }
    }
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

    private static final String FILTER_PLACEHOLDER_PATCHES = "Filter patches…";
    private static final String FILTER_PLACEHOLDER_ROUTES = "Filter routes…";
    private static final String PROP_NAV_BASE_FONT = "farmutils.navBaseFont";
    private static final String PROP_FILTER_BASE_FONT = "farmutils.filterBaseFont";

    private static boolean isChromeMode(Mode mode)
    {
        return mode == Mode.PATCHES || mode == Mode.ROUTES;
    }

    private static boolean isFilterPlaceholder(String text)
    {
        return FILTER_PLACEHOLDER_PATCHES.equals(text) || FILTER_PLACEHOLDER_ROUTES.equals(text);
    }

    private static String placeholderFor(Mode mode)
    {
        return mode == Mode.ROUTES ? FILTER_PLACEHOLDER_ROUTES : FILTER_PLACEHOLDER_PATCHES;
    }

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
    private final RoutesPanel routesPanel;
    private final ClientUI clientUI;

    private final JPanel nav = new JPanel(new GridBagLayout());

    private final JPanel chrome = new JPanel();
    private final JPanel filterRow = new JPanel(new BorderLayout());
    private final JPanel toolbarRow = new JPanel(new BorderLayout());

    private static final String TOOLBAR_CARD_PATCHES = "patches";
    private static final String TOOLBAR_CARD_ROUTES = "routes";
    private final CardLayout toolbarCardLayout = new CardLayout();
    private final JPanel toolbarCards = new JPanel(toolbarCardLayout);

    // Visible toolbar content panels (used to recompute toolbar row height on card switches).
    private JPanel patchesToolbarContent;
    private JPanel routesToolbarContent;

    private final JComponent chromeDivider = divider();

    private final JTextField filterField = new JTextField();
    // Updated in buildToolbar(); invoked after FarmPanel rebuild completes.
    private Runnable refreshCollapseAll = () -> {};
    private Runnable refreshRoutesToolbar = () -> {};
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
        this.routesPanel = (routesPanel instanceof RoutesPanel) ? (RoutesPanel) routesPanel : null;

        // Painted “floor” so no white bleed-through anywhere
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        cards.setOpaque(true);
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);

        setLayout(new BorderLayout());

        buildNav();
        buildChrome();
        buildCards(farmPanel, routesPanel, calcPanel, exportPanel);

        // Allow the Routes panel to request toolbar refreshes without coupling to root state.
        if (this.routesPanel != null)
        {
            this.routesPanel.setOnUiStateChange(() -> refreshRoutesToolbar.run());
        }

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
        // Tighten left padding so the placeholder text aligns with other left-justified chrome elements.
        // Also zero the JTextField margin (LAF-dependent) so the EmptyBorder is the single source of truth.
        filterField.setMargin(new Insets(0, 0, 0, 0));
        filterField.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 8));
        filterField.setCaretColor(ColorScheme.TEXT_COLOR);

        // placeholder
        filterField.setText(placeholderFor(current));
        filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

// Help: fielded query syntax (no extra UI panels).
filterField.setToolTipText("<html>"
        + "Filter examples: <code>l:falador t:herb</code> &nbsp; <code>s:ready</code><br>"
        + "Quoted values: <code>l:\"farming guild\"</code><br>"
        + "Also supports <code>key=value</code> and comma lists: <code>l:falador,hosidius</code>"
        + "</html>");

        filterField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (isFilterPlaceholder(filterField.getText()))
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
                    filterField.setText(placeholderFor(current));
                    filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                }
            }
        });

        filterField.getDocument().addDocumentListener(new DocumentListener()
        {
            private void changed()
            {
                String t = filterField.getText();
                if (t == null)
                {
                    if (current == Mode.PATCHES)
                    {
                        farmPanel.setFilterText("");
                    }
                    else if (current == Mode.ROUTES && routesPanel != null)
                    {
                        routesPanel.setFilterText("");
                    }
                    return;
                }

                String trimmed = t.trim();
                if (trimmed.isEmpty() || isFilterPlaceholder(t))
                {
                    if (current == Mode.PATCHES)
                    {
                        farmPanel.setFilterText("");
                    }
                    else if (current == Mode.ROUTES && routesPanel != null)
                    {
                        routesPanel.setFilterText("");
                    }
                }
                else
                {
                    if (current == Mode.PATCHES)
                    {
                        farmPanel.setFilterText(trimmed);
                    }
                    else if (current == Mode.ROUTES && routesPanel != null)
                    {
                        routesPanel.setFilterText(trimmed);
                    }
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
                    boolean effectivelyEmpty = (t == null) || t.isEmpty() || isFilterPlaceholder(t);
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
                boolean effectivelyEmpty = (t == null) || t.trim().isEmpty() || isFilterPlaceholder(t);

                if (effectivelyEmpty)
                {
                    clientUI.forceFocus();
                    return;
                }

                filterField.setText("");
                filterField.setForeground(ColorScheme.TEXT_COLOR);
                if (current == Mode.PATCHES)
                {
                    farmPanel.setFilterText("");
                }
            }
        });

        filterRow.add(filterField, BorderLayout.CENTER);

        // Restore toolbar button (only shown when toolbar is hidden).
        restoreToolbarButton.setFocusable(false);
        restoreToolbarButton.setMargin(new Insets(0, 6, 0, 6));

        // Replace text placeholder with glyph icon (falls back to text if missing).
        javax.swing.Icon restoreIc = loadToolbarIcon("toolbar_toggle_down", 16);
        if (restoreIc != null)
        {
            restoreToolbarButton.setIcon(restoreIc);
            restoreToolbarButton.setText("");
            restoreToolbarButton.setHorizontalAlignment(SwingConstants.CENTER);
        }
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

        toolbarRow.removeAll();
        toolbarCards.removeAll();
        toolbarCards.setOpaque(false);
        toolbarRow.add(toolbarCards, BorderLayout.CENTER);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new ToolbarButtonRowLayout());

        // Tooltip helper: for now name-only. Later can append description based on config.
        java.util.function.BiFunction<String, String, String> tooltip =
                (name, description) -> name; // TODO later: name + " — " + description (when enabled)

        java.util.function.Consumer<AbstractButton> styleButton = (b) ->
        {
            b.setFocusable(false);
            b.setMargin(new Insets(0, 0, 0, 0));
        };

        java.util.function.Function<String, javax.swing.Icon> toolbarIcon =
                (key) -> loadToolbarIcon(key, 16);

        java.util.function.BiConsumer<AbstractButton, String> applyToolbarGlyph = (b, key) ->
        {
            javax.swing.Icon ic = toolbarIcon.apply(key);
            if (ic != null)
            {
                b.setIcon(ic);
                b.setText("");
                b.setHorizontalAlignment(SwingConstants.CENTER);
            }
        };


        // --- Suggested non-toggle actions (JButton) ---
        JButton viewBtn = new JButton("V"); // View mode (cycle / open menu later)
        viewBtn.setToolTipText(tooltip.apply("View mode", "Change the list presentation"));
        styleButton.accept(viewBtn);
        applyToolbarGlyph.accept(viewBtn, "view_mode");

        JButton sortBtn = new JButton("S"); // Sort / ordering menu later (not DnD)
        sortBtn.setToolTipText(tooltip.apply("Sort / order", "Change ordering mode"));
        styleButton.accept(sortBtn);
        applyToolbarGlyph.accept(sortBtn, "sort_order");

        JButton collapseAllBtn = new JButton("C"); // Collapse groups/locations (future)
        collapseAllBtn.setToolTipText(tooltip.apply("Collapse / expand", "Collapse or expand sections"));
        styleButton.accept(collapseAllBtn);
        applyToolbarGlyph.accept(collapseAllBtn, "collapse_expand");

        JButton refreshBtn = new JButton("↻"); // Manual refresh (future; maybe forces recalculation)
        refreshBtn.setToolTipText(tooltip.apply("Refresh", "Re-read state / repaint"));
        styleButton.accept(refreshBtn);
        applyToolbarGlyph.accept(refreshBtn, "refresh");

        // --- Suggested toggles (JToggleButton) ---
        JToggleButton reorderTgl = new JToggleButton("R");
        reorderTgl.setToolTipText(tooltip.apply("Reorder", "Enable drag reordering"));
        styleButton.accept(reorderTgl);
        applyToolbarGlyph.accept(reorderTgl, "reorder_drag");

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
        applyToolbarGlyph.accept(showDisabledTgl, "show_hidden");

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
        applyToolbarGlyph.accept(highlightsTgl, "highlights");

        JToggleButton stateLinesTgl = new JToggleButton("L"); // state divider visibility (future)
        stateLinesTgl.setToolTipText(tooltip.apply("State indicators", "Cycle patch state indicator line mode"));
        styleButton.accept(stateLinesTgl);
        applyToolbarGlyph.accept(stateLinesTgl, "state_indicators");

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
        applyToolbarGlyph.accept(hideToolbarToggle, "toolbar_toggle");

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

        this.patchesToolbarContent = content;
        toolbarCards.add(content, TOOLBAR_CARD_PATCHES);

        // Routes toolbar (R3b): session controls (tracking/guidance only; no automation).
        JPanel routesToolbar = new JPanel();
        routesToolbar.setOpaque(false);
        routesToolbar.setLayout(new ToolbarButtonRowLayout());

        JButton playBtn = new JButton("▶");
        playBtn.getAccessibleContext().setAccessibleName("Start route");
        playBtn.setToolTipText(tooltip.apply("Start tracking", "Start tracking the selected route"));
        styleButton.accept(playBtn);

        JButton pauseBtn = new JButton("❚❚");
        pauseBtn.getAccessibleContext().setAccessibleName("Pause route");
        pauseBtn.setToolTipText(tooltip.apply("Pause tracking", "Pause tracking the active route"));
        styleButton.accept(pauseBtn);

        JButton stopBtn = new JButton("■");
        stopBtn.getAccessibleContext().setAccessibleName("Stop route");
        stopBtn.setToolTipText(tooltip.apply("Stop tracking", "Stop tracking the active route"));
        styleButton.accept(stopBtn);

        JButton newRouteBtn = new JButton("+");
        newRouteBtn.getAccessibleContext().setAccessibleName("New route");
        newRouteBtn.setToolTipText(tooltip.apply("New route", "Create a new route"));
        styleButton.accept(newRouteBtn);

        // Parity actions from Patches toolbar (low rewiring): reorder toggle + collapse/expand all.
        JToggleButton routesReorderTgl = new JToggleButton("R");
        routesReorderTgl.getAccessibleContext().setAccessibleName("Reorder");
        routesReorderTgl.setToolTipText(tooltip.apply("Reorder", "Enable drag reordering"));
        styleButton.accept(routesReorderTgl);
        applyToolbarGlyph.accept(routesReorderTgl, "reorder_drag");

        JButton routesCollapseExpandBtn = new JButton("C");
        routesCollapseExpandBtn.getAccessibleContext().setAccessibleName("Collapse / expand");
        routesCollapseExpandBtn.setToolTipText(tooltip.apply("Collapse / expand", "Collapse or expand routes"));
        styleButton.accept(routesCollapseExpandBtn);
        applyToolbarGlyph.accept(routesCollapseExpandBtn, "collapse_expand");

        playBtn.addActionListener(e ->
        {
            if (FarmRootPanel.this.routesPanel != null)
            {
                FarmRootPanel.this.routesPanel.startSelectedRoute();
            }
            refreshRoutesToolbar.run();
        });

        pauseBtn.addActionListener(e ->
        {
            if (FarmRootPanel.this.routesPanel != null)
            {
                FarmRootPanel.this.routesPanel.pauseActiveRoute();
            }
            refreshRoutesToolbar.run();
        });

        stopBtn.addActionListener(e ->
        {
            if (FarmRootPanel.this.routesPanel != null)
            {
                FarmRootPanel.this.routesPanel.stopActiveRoute();
            }
            refreshRoutesToolbar.run();
        });

        newRouteBtn.addActionListener(e ->
        {
            if (FarmRootPanel.this.routesPanel != null)
            {
                FarmRootPanel.this.routesPanel.promptCreateRoute();
            }
            refreshRoutesToolbar.run();
        });

        routesReorderTgl.addActionListener(e ->
        {
            if (FarmRootPanel.this.routesPanel != null)
            {
                // Contract: reorder UI is disabled while filtering.
                if (FarmRootPanel.this.routesPanel.isFilterActive())
                {
                    routesReorderTgl.setSelected(false);
                    FarmRootPanel.this.routesPanel.setReorderModeEnabled(false);
                }
                else
                {
                    FarmRootPanel.this.routesPanel.setReorderModeEnabled(routesReorderTgl.isSelected());
                }
            }
            refreshRoutesToolbar.run();
        });

        routesCollapseExpandBtn.addActionListener(e ->
        {
            if (FarmRootPanel.this.routesPanel != null)
            {
                FarmRootPanel.this.routesPanel.toggleCollapseExpandAll();
            }
            refreshRoutesToolbar.run();
        });

        routesToolbar.add(playBtn);
        routesToolbar.add(pauseBtn);
        routesToolbar.add(stopBtn);
        routesToolbar.add(routesReorderTgl);
        routesToolbar.add(routesCollapseExpandBtn);
        routesToolbar.add(newRouteBtn);

        this.routesToolbarContent = routesToolbar;

        this.refreshRoutesToolbar = () ->
        {
            if (FarmRootPanel.this.routesPanel == null)
            {
                playBtn.setEnabled(false);
                pauseBtn.setEnabled(false);
                stopBtn.setEnabled(false);
                newRouteBtn.setEnabled(false);
                return;
            }

            boolean hasSelected = FarmRootPanel.this.routesPanel.hasSelectedRoute();
            boolean hasActive = FarmRootPanel.this.routesPanel.hasActiveRoute();
            boolean activeRunning = FarmRootPanel.this.routesPanel.isActiveRunning();
            boolean activePaused = FarmRootPanel.this.routesPanel.isActivePaused();

            boolean filterActive = FarmRootPanel.this.routesPanel.isFilterActive();
            boolean reorderEnabled = FarmRootPanel.this.routesPanel.isReorderModeEnabled();

            playBtn.setEnabled(hasSelected);
            pauseBtn.setEnabled(hasActive && activeRunning);
            stopBtn.setEnabled(hasActive);
            newRouteBtn.setEnabled(true);

            // Reorder toggle is available only when not filtering.
            routesReorderTgl.setEnabled(!filterActive);
            routesReorderTgl.setSelected(!filterActive && reorderEnabled);
            routesReorderTgl.setToolTipText(tooltip.apply(
                    "Reorder",
                    filterActive ? "Disabled while filtering" : "Enable drag reordering"));

            // Collapse/expand all: disabled while reordering or filtering.
            boolean canCollapseExpand = FarmRootPanel.this.routesPanel.canCollapseExpandAll();
            routesCollapseExpandBtn.setEnabled(canCollapseExpand);
            routesCollapseExpandBtn.setToolTipText(tooltip.apply(
                    FarmRootPanel.this.routesPanel.willCollapseAll() ? "Collapse all" : "Expand all",
                    filterActive ? "Disabled while filtering"
                            : (reorderEnabled ? "Disabled while reordering" : "Collapse or expand all routes")));

            playBtn.setToolTipText(tooltip.apply(
                    activePaused && hasSelected ? "Resume tracking" : "Start tracking",
                    "Start tracking the selected route"));
        };

        toolbarCards.add(routesToolbar, TOOLBAR_CARD_ROUTES);

        // Establish a conservative default height; actual height is derived from the visible card.
        toolbarRow.setPreferredSize(new Dimension(1, 28));
        toolbarRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        // Recompute height whenever the toolbar row is resized (e.g. panel width changes).
        toolbarRow.addComponentListener(new java.awt.event.ComponentAdapter()
        {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e)
            {
                updateToolbarRowHeight();
            }
        });
    }

    /**
     * Layout for the toolbar button row: equal-width columns and full-height buttons.
     * Height is controlled by updateToolbarRowHeight() at the root level.
     */
    private final class ToolbarButtonRowLayout implements LayoutManager
    {
        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}

        @Override
        public Dimension preferredLayoutSize(Container parent)
        {
            // Height is set at the toolbar row; this is just a safe fallback during first layout.
            return new Dimension(1, Math.max(18, toolbarRow.getPreferredSize().height));
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
            int h = parent.getHeight();
            if (w <= 0)
            {
                return;
            }

            int base = w / count;
            int rem = w % count;

            int x = 0;
            for (int i = 0; i < count; i++)
            {
                int extra = (i < rem) ? 1 : 0;
                int bw = base + extra;
                Component c = parent.getComponent(i);
                c.setBounds(x, 0, bw, h);
                x += bw;
            }
        }
    }

    private void updateToolbarRowHeight()
    {
        if (!isChromeMode(current) || !toolbarRow.isVisible())
        {
            return;
        }

        JPanel panel = (current == Mode.ROUTES) ? routesToolbarContent : patchesToolbarContent;
        if (panel == null)
        {
            return;
        }

        int count = panel.getComponentCount();
        if (count <= 0)
        {
            return;
        }

        int w = toolbarRow.getWidth();
        if (w <= 0)
        {
            w = chrome.getWidth();
        }
        if (w <= 0)
        {
            return;
        }

        int targetH = Math.max(18, w / count);
        if (toolbarRow.getPreferredSize().height == targetH)
        {
            return;
        }

        toolbarRow.setPreferredSize(new Dimension(1, targetH));
        toolbarRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, targetH));
        toolbarRow.revalidate();
        chrome.revalidate();
        toolbarRow.repaint();
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
        if (!isChromeMode(current))
        {
            toolbarRow.setVisible(false);
            restoreToolbarButton.setVisible(false);
            return;
        }

        if (current == Mode.ROUTES)
        {
            toolbarCardLayout.show(toolbarCards, TOOLBAR_CARD_ROUTES);
            toolbarRow.setVisible(true);
            restoreToolbarButton.setVisible(false);

            // Routes toolbar has no hide/show state yet.
            refreshRoutesToolbar.run();

            // Ensure toolbar height matches the visible card immediately after the switch.
            SwingUtilities.invokeLater(this::updateToolbarRowHeight);
            chrome.revalidate();
            chrome.repaint();
            return;
        }

        // PATCHES
        toolbarCardLayout.show(toolbarCards, TOOLBAR_CARD_PATCHES);

        boolean hidden = uiStateStore != null && uiStateStore.isToolbarHidden();
        toolbarRow.setVisible(!hidden);
        restoreToolbarButton.setVisible(hidden);

        if (hideToolbarToggle != null && hideToolbarToggle.isSelected() != hidden)
        {
            hideToolbarToggle.setSelected(hidden);
        }

        // Keep BoxLayout from leaving stale space.
        SwingUtilities.invokeLater(this::updateToolbarRowHeight);
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
        // Important: keep the left inset tight so the placeholder text aligns with other chrome
        // elements (nav + list content). Right inset stays roomier so the field doesn't feel cramped.
        int padLeftX = Math.max(2, Math.round(2 * scale));
        int padRightX = Math.max(6, Math.round(8 * scale));

        javax.swing.Icon searchIcon = null;
        if (config.showFilterSearchIcon())
        {
            // Pick the largest provided asset that fits comfortably within the field height.
            int maxPx = Math.max(12, h - Math.round(10 * scale));
            int px = (maxPx >= 64) ? 64 : (maxPx >= 28) ? 28 : 16;
            BufferedImage img = loadToolbarImage("search", px);
            if (img != null)
            {
                searchIcon = new ForegroundTintIcon(img);
            }
        }

        // More breathing room between the glyph and text, especially at 16px.
        int iconGap = Math.max(6, Math.round(6 * scale));
        filterField.setBorder(new LeftIconBorder(padY, padLeftX, padY, padRightX, searchIcon, iconGap));

        // Keep placeholder styling correct
        if (isFilterPlaceholder(filterField.getText()))
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
        // Slightly reduce bottom padding so the filter row sits closer to the nav.
        nav.setBorder(new EmptyBorder(6, 6, 2, 6));
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

        boolean nextChrome = isChromeMode(mode);
        setPatchesChromeVisible(nextChrome);

        // Switching away from a chrome mode: clear and return focus to game.
        if (isChromeMode(prev) && !nextChrome)
        {
            filterField.setText(placeholderFor(Mode.PATCHES));
            filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            if (prev == Mode.PATCHES)
            {
                farmPanel.setFilterText("");
            }
            clientUI.forceFocus();
        }

        // Switching between chrome modes (or entering a chrome mode): reset placeholder for the target mode.
        if (nextChrome && prev != mode)
        {
            filterField.setText(placeholderFor(mode));
            filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

            if (prev == Mode.PATCHES && mode != Mode.PATCHES)
            {
                // Preserve existing behavior: leaving patches clears its filter and refocuses the game.
                farmPanel.setFilterText("");
                clientUI.forceFocus();
            }
        }

        cardLayout.show(cards, mode.name());

        // Ensure Routes reflects any store changes that could have occurred while the tab was hidden
        // (e.g. adding patches to routes from the Patches context menu).
        if (mode == Mode.ROUTES && routesPanel != null && prev != Mode.ROUTES)
        {
            routesPanel.refreshFromStore();
        }

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
        refreshActivePanelFromConfig();
        revalidate();
        repaint();
    }

    /**
     * Refresh only the active content panel in response to config changes.
     * Keeps Routes consistent with Patches (immediate application while visible).
     */
    public void refreshActivePanelFromConfig()
    {
        if (current == Mode.ROUTES && routesPanel != null)
        {
            routesPanel.refreshUiFromConfig();
        }
    }

    public boolean isRoutesActive()
    {
        return current == Mode.ROUTES;
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

    private javax.swing.Icon loadToolbarIcon(String key, int px)
    {
        String path = "/toolbar/" + px + "/" + key + ".png";
        java.net.URL url = FarmRootPanel.class.getResource(path);
        return url == null ? null : new javax.swing.ImageIcon(url);
    }

    private BufferedImage loadToolbarImage(String key, int px)
    {
        String path = "/toolbar/" + px + "/" + key + ".png";
        java.net.URL url = FarmRootPanel.class.getResource(path);
        if (url == null)
        {
            return null;
        }

        try
        {
            return ImageIO.read(url);
        }
        catch (IOException e)
        {
            return null;
        }
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
            if (!isChromeMode(current)) return false;
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
                    if (isFilterPlaceholder(filterField.getText()))
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
            if (!isChromeMode(current)) return;
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