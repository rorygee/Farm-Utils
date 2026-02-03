package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchId;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;
import java.awt.event.AWTEventListener;


import java.awt.MouseInfo;
import java.awt.PointerInfo;
import javax.swing.Timer;
import javax.swing.JViewport;


public class FarmPanel extends JPanel
{
    private static final String PLACEHOLDER = "Filter patches…";

    private Component filterBlurScope;

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

    private final JPanel container = new JPanel();

    private final Font baseFilterFont;

    private final JPanel list = new JPanel();
    private final JTextField filterField = new JTextField();
    private String filterText = "";

    private List<String> lastCanonicalGroupOrder = Collections.emptyList();

    private KeyEventDispatcher keyDispatcher;


    private static final String PROP_GROUP_DRAG_HANDLE = "farmutils.groupDragHandle";

    private final GroupDragController groupDrag = new GroupDragController();

    private final JPanel dropLine = new JPanel();
    {
        dropLine.setOpaque(true);
        dropLine.setBackground(HEADER_ORANGE); // reuse existing color
        dropLine.setPreferredSize(new Dimension(1, 2));
        dropLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        dropLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
    }


    @Inject
    public FarmPanel(PatchStore store, UiStateStore uiStateStore, ClientUI clientUI, FarmutilsConfig config)
    {
        super(new BorderLayout());

        this.store = store;
        this.uiStateStore = uiStateStore;
        this.clientUI = clientUI;
        this.config = config;

        this.baseFilterFont = filterField.getFont();

        // Painted “floor” for this view
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(null);

        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.setFocusable(true);

        // filter row first
        container.add(buildFilterRow());

        // then the list
        container.add(list);

        // IMPORTANT: NORTH so the content grows vertically and the sidebar owns scrolling
        add(container, BorderLayout.NORTH);
    }


    private JComponent buildFilterRow()
    {
        float scale = config.textScale().multiplier();
        int h = Math.round(26 * scale);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(true);
        bar.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // field blends into the bar (no LAF border/chin)
        filterField.setOpaque(false);
        int padY = Math.max(4, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));
        filterField.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

        filterField.setCaretColor(ColorScheme.TEXT_COLOR);

        filterField.setFont(UiFont.scaled(filterField.getFont(), scale, Font.PLAIN));

        filterField.setPreferredSize(new Dimension(0, h));
        filterField.setMinimumSize(new Dimension(0, h));
        filterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));

        // placeholder
        filterField.setText(PLACEHOLDER);
        filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        filterField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            @Override
            public void focusGained(java.awt.event.FocusEvent e)
            {
                if (PLACEHOLDER.equals(filterField.getText()))
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
                    filterField.setText(PLACEHOLDER);
                    filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                }
            }
        });

        filterField.getDocument().addDocumentListener(new DocumentListener()
        {
            private void changed()
            {
                String t = filterField.getText();
                if (t == null || t.trim().isEmpty() || PLACEHOLDER.equals(t))
                {
                    filterText = "";
                }
                else
                {
                    filterText = t.trim();
                }
                rebuild();
            }

            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });

        // ESC: if empty -> return focus to game; else -> clear filter
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        filterField.getInputMap(JComponent.WHEN_FOCUSED).put(esc, "farmutils.esc");
        filterField.getActionMap().put("farmutils.esc", new AbstractAction()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                String t = filterField.getText();
                boolean effectivelyEmpty = (t == null) || t.trim().isEmpty() || PLACEHOLDER.equals(t);

                if (effectivelyEmpty)
                {
                    clientUI.forceFocus();
                    return;
                }

                filterField.setText("");
                filterField.setForeground(ColorScheme.TEXT_COLOR);
                filterText = "";
                rebuild();
            }
        });

        bar.add(filterField, BorderLayout.CENTER);

        // bar + 1px divider under it
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        container.add(bar);
        container.add(divider());

        return container;
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        installFindShortcut();

        // Robust scope: whatever this panel is mounted into at the top.
        filterBlurScope = SwingUtilities.getWindowAncestor(this);
        if (filterBlurScope == null)
        {
            // Fallback: at least scope to the top-level Swing ancestor if window not available yet
            filterBlurScope = getTopLevelAncestor();
        }
        if (filterBlurScope == null)
        {
            filterBlurScope = this;
        }

        installFilterBlurOnOutsideClick();
    }




    @Override
    public void removeNotify()
    {
        uninstallFilterBlurOnOutsideClick();
        uninstallFindShortcut();
        filterBlurScope = null;
        super.removeNotify();
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

    public void refreshUiFromConfig()
    {
        float scale = config.textScale().multiplier();

        // IMPORTANT: always scale from the unscaled base font
        filterField.setFont(UiFont.scaled(baseFilterFont, scale, Font.PLAIN));

        int h = Math.round(26 * scale);
        filterField.setPreferredSize(new Dimension(0, h));
        filterField.setMinimumSize(new Dimension(0, h));
        filterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));

        int padY = Math.max(4, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));
        filterField.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

        // Keep placeholder style correct
        if (PLACEHOLDER.equals(filterField.getText()))
        {
            filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        }
        else
        {
            filterField.setForeground(ColorScheme.TEXT_COLOR);
        }

        // Force layout refresh
        filterField.revalidate();
        filterField.repaint();
        revalidate();
        repaint();

        // Rebuild list so headers/rows also pick up scale
        rebuild();
    }


    private void installFindShortcut()
    {
        if (keyDispatcher != null)
        {
            return;
        }

        final int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        keyDispatcher = e ->
        {
            if (!isShowing()) return false;
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;

            if (e.getKeyCode() == KeyEvent.VK_F && (e.getModifiersEx() & menuMask) == menuMask)
            {
                // toggle back to game if filter already focused
                if (filterField.isFocusOwner())
                {
                    clientUI.forceFocus();
                    e.consume();
                    return true;
                }

                filterField.requestFocusInWindow();

                if (PLACEHOLDER.equals(filterField.getText()))
                {
                    filterField.setText("");
                    filterField.setForeground(ColorScheme.TEXT_COLOR);
                }
                else
                {
                    filterField.selectAll();
                }

                e.consume();
                return true;
            }

            return false;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyDispatcher);
    }

    private void uninstallFindShortcut()
    {
        if (keyDispatcher == null) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyDispatcher);
        keyDispatcher = null;
    }

    private void installFilterBlurOnOutsideClick()
    {
        final AWTEventListener listener = event ->
        {
            if (!(event instanceof MouseEvent))
            {
                return;
            }

            MouseEvent me = (MouseEvent) event;

            // Use MOUSE_PRESSED so focus changes before mouseReleased actions fire.
            if (me.getID() != MouseEvent.MOUSE_PRESSED)
            {
                return;
            }

            Component src = me.getComponent();
            if (src == null)
            {
                return;
            }

            // Only react to clicks inside this FarmPanel tree (don’t steal focus globally).
            Component scope = (filterBlurScope != null) ? filterBlurScope : SwingUtilities.getWindowAncestor(this);

            // If the click is in a different window than this panel, ignore it.
            Window srcWindow = SwingUtilities.getWindowAncestor(src);
            Window scopeWindow = (scope instanceof Window) ? (Window) scope : SwingUtilities.getWindowAncestor(scope);

            if (scopeWindow != null && srcWindow != scopeWindow)
            {
                return;
            }


            // Ignore clicks on the filter (or any of its children).
            if (SwingUtilities.isDescendingFrom(src, filterField))
            {
                return;
            }

            // Only do work if the filter actually owns focus.
            if (!filterField.isFocusOwner())
            {
                return;
            }

            // Move focus somewhere inert in this panel.
            // list is a good default; fallback to the panel itself.
            if (!list.requestFocusInWindow())
            {
                requestFocusInWindow();
            }
        };

        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
        putClientProperty("farmutils.filterBlurListener", listener);
    }

    public void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            list.removeAll();

            boolean hasFilter = filterText != null && !filterText.isEmpty();

            Map<String, List<PatchId>> grouped = Arrays.stream(PatchId.values())
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

            for (Map.Entry<String, List<PatchId>> entry : orderedGroups)
            {
                String groupName = entry.getKey();

                List<PatchId> orderedIds =
                        PatchOrderingResolver.resolveEntryOrder(groupName, entry.getValue(), uiStateStore);

                List<PatchId> visibleIds = orderedIds.stream()
                        .filter(id -> matchesFilter(id, groupName))
                        .collect(Collectors.toList());


                if (visibleIds.isEmpty())
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
                    groupBlock.add(divider());
                }
                firstGroup = false;

                boolean collapsed = uiStateStore.isGroupCollapsed(groupName);

                // while filtering, show as expanded (don’t imply hidden results)
                boolean collapsedForHeader = hasFilter ? false : collapsed;

                JComponent header = fullWidth(createGroupHeader(groupName, collapsedForHeader));
                groupBlock.add(header);

// Bind drag handle for this group (handle-only; does not interfere with expand/collapse).
                Object h = header.getClientProperty(PROP_GROUP_DRAG_HANDLE);
                if (h instanceof JComponent)
                {
                    groupDrag.bind((JComponent) h, list, groupBlock);
                }

                boolean showBody = (!collapsed || hasFilter);
                if (showBody)
                {
                    groupBlock.add(divider());

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
                        boolean isMultiSlot = locationKey != null && locationCounts.getOrDefault(locationKey, 0L) >= 2;

                        if (isMultiSlot && seenLocations.add(locationKey))
                        {
                            String locationName = id.getLocationName() != null ? id.getLocationName() : id.getLabel();
                            bodyItems.add(fullWidth(new LocationHeaderRow(locationName, config)));
                        }

                        JComponent rowWrap = fullWidth(new PatchRow(id, store, config, this::rebuild));
                        if (isMultiSlot)
                        {
                            // Subtle indent under a location header. Wrapper is used to preserve PatchRow layout contract.
                            int indent = Math.max(8, Math.round(10 * config.textScale().multiplier()));
                            rowWrap.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0));
                        }
                        bodyItems.add(rowWrap);
                    }

                    for (int i = 0; i < bodyItems.size(); i++)
                    {
                        groupBlock.add(bodyItems.get(i));
                        if (i < bodyItems.size() - 1)
                        {
                            groupBlock.add(divider());
                        }
                    }
                }

                Dimension pref = groupBlock.getPreferredSize();
                groupBlock.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

// ⬅️ THIS is the key difference
                list.add(groupBlock);

            }


            list.revalidate();
            list.repaint();
        });
    }

    private boolean matchesFilter(PatchId id, String groupName)
    {
        if (filterText == null || filterText.isEmpty())
        {
            return true;
        }

        String q = filterText.toLowerCase();
        return groupName.toLowerCase().contains(q)
                || id.getLabel().toLowerCase().contains(q)
                || id.name().toLowerCase().contains(q);
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

    private Component createGroupHeader(String groupName, boolean collapsed)
    {
        float scale = config.textScale().multiplier();
        boolean emphasize = config.emphasizeHeaders();

        float headerScale = emphasize ? (scale * 1.05f) : scale;
        int style = emphasize ? Font.BOLD : Font.PLAIN;

        final String tri = collapsed ? "▸" : "▾";

        JLabel triLabel = new JLabel(tri);
        triLabel.setOpaque(false);
        triLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        triLabel.setForeground(collapsed ? TRI_DISABLED : HEADER_ORANGE);
        triLabel.setFont(UiFont.scaled(triLabel.getFont(), headerScale, style));

        JLabel textLabel = new JLabel(groupName);
        textLabel.setOpaque(false);
        textLabel.setForeground(HEADER_ORANGE);
        textLabel.setFont(UiFont.scaled(textLabel.getFont(), headerScale, style));

        // Outer row
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(true);
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        int padY = Math.max(2, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));
        header.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

        // LEFT: clickable region that EXPANDS to fill space (so hand cursor is consistent)
        JPanel clickable = new JPanel();
        clickable.setLayout(new BoxLayout(clickable, BoxLayout.X_AXIS));
        clickable.setOpaque(false);
        clickable.setAlignmentX(Component.LEFT_ALIGNMENT);

        clickable.add(triLabel);
        clickable.add(textLabel);
        clickable.add(Box.createHorizontalGlue()); // <-- KEY: this makes clickable consume remaining space

        // Ensure BoxLayout lets it stretch wide
        Dimension pref = clickable.getPreferredSize();
        clickable.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));

        clickable.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clickable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                uiStateStore.toggleGroupCollapsed(groupName);
                rebuild();
            }
        });

        // RIGHT: drag handle only
        JComponent dragHandle = createGroupDragHandle();
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        header.putClientProperty(PROP_GROUP_DRAG_HANDLE, dragHandle);

        header.add(clickable);
        header.add(dragHandle);

        return header;
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

}
