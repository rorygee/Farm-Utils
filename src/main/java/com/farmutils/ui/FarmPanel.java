package com.farmutils.ui;

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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class FarmPanel extends PluginPanel
{
    private static final String PLACEHOLDER = "Filter patches…";

    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;

    // 1px divider color (subtle but visible)
    private static final Color DIVIDER = ColorScheme.DARKER_GRAY_COLOR;

    private static final Color HEADER_ORANGE =
            hasBrandOrange() ? ColorScheme.BRAND_ORANGE : new Color(255, 152, 31);
    private static final Color TRI_DISABLED = ColorScheme.MEDIUM_GRAY_COLOR;

    @Inject private PatchStore store;
    @Inject private UiStateStore uiStateStore;
    @Inject private ClientUI clientUI;

    private final JPanel list = new JPanel();
    private final JTextField filterField = new JTextField();
    private String filterText = "";

    private JScrollPane scroll;
    private KeyEventDispatcher keyDispatcher;

    public FarmPanel()
    {
        super(false);

        setBorder(null);
        setLayout(new BorderLayout());
        setOpaque(false);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);

        scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JPanel filterRow = buildFilterRow();

        add(filterRow, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        rebuild();
    }

    private JPanel buildFilterRow()
    {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(true);
        top.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Filter field styling: consistent height, no "chin"
        filterField.setOpaque(false);
        filterField.setBorder(BorderFactory.createEmptyBorder(PAD_Y, PAD_X, PAD_Y, PAD_X));
        filterField.setForeground(ColorScheme.TEXT_COLOR);
        filterField.setCaretColor(ColorScheme.TEXT_COLOR);
        filterField.setText(PLACEHOLDER);
        filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        int h = 26;
        filterField.setPreferredSize(new Dimension(0, h));
        filterField.setMinimumSize(new Dimension(0, h));
        filterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));

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

        // ESC clears (field stays focused, placeholder comes back on focusLost)
        KeyStroke esc = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        filterField.getInputMap(JComponent.WHEN_FOCUSED).put(esc, "farmutils.clearFilter");
        filterField.getActionMap().put("farmutils.clearFilter", new AbstractAction()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                String t = filterField.getText();
                boolean effectivelyEmpty = (t == null) || t.trim().isEmpty() || PLACEHOLDER.equals(t);

                // If already empty, ESC acts like "return to game"
                if (effectivelyEmpty)
                {
                    clientUI.forceFocus();
                    return;
                }

                // Otherwise: clear filter
                filterField.setText("");
                filterField.setForeground(ColorScheme.TEXT_COLOR);
                filterText = "";
                rebuild();
            }
        });


        // Build filter row + 1px divider under it
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        top.add(filterField, BorderLayout.CENTER);

        container.add(top);
        container.add(divider());

        return container;
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        installFindShortcut();
    }

    @Override
    public void removeNotify()
    {
        uninstallFindShortcut();
        super.removeNotify();
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
                // Toggle: Ctrl/Cmd+F again returns focus to game
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

    public void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            list.removeAll();

            boolean hasFilter = filterText != null && !filterText.isEmpty();

            Map<String, List<PatchId>> grouped = Arrays.stream(PatchId.values())
                    .sorted(Comparator.comparing(PatchId::getGroup).thenComparing(PatchId::getLabel))
                    .collect(Collectors.groupingBy(
                            PatchId::getGroup,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            boolean firstRenderedGroup = true;

            for (Map.Entry<String, List<PatchId>> entry : grouped.entrySet())
            {
                String groupName = entry.getKey();

                List<PatchId> visibleIds = entry.getValue().stream()
                        .filter(id -> matchesFilter(id, groupName))
                        .collect(Collectors.toList());

                if (visibleIds.isEmpty())
                {
                    continue;
                }

                // One divider between groups (but not before the first group)
                if (!firstRenderedGroup)
                {
                    list.add(divider());
                }
                firstRenderedGroup = false;

                boolean collapsed = uiStateStore.isGroupCollapsed(groupName);

                // While filtering, always show as expanded (don’t imply hidden results)
                boolean collapsedForHeader = hasFilter ? false : collapsed;

                list.add(fullWidth(createGroupHeader(groupName, collapsedForHeader)));

                boolean showBody = (!collapsed || hasFilter);

                if (showBody)
                {
                    list.add(divider());

                    for (int i = 0; i < visibleIds.size(); i++)
                    {
                        PatchId id = visibleIds.get(i);
                        list.add(fullWidth(new PatchRow(id, store, this::rebuild)));

                        if (i < visibleIds.size() - 1)
                        {
                            list.add(divider());
                        }
                    }
                }

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

    private Component createGroupHeader(String groupName, boolean collapsed)
    {
        final String tri = collapsed ? "▸" : "▾";

        JLabel triLabel = new JLabel(tri);
        triLabel.setOpaque(false);
        triLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
        triLabel.setForeground(collapsed ? TRI_DISABLED : HEADER_ORANGE);

        JLabel textLabel = new JLabel(groupName);
        textLabel.setFont(textLabel.getFont().deriveFont(collapsed ? Font.PLAIN : Font.BOLD));
        textLabel.setOpaque(false);
        textLabel.setForeground(HEADER_ORANGE);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(true);
        header.setBackground(collapsed ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARK_GRAY_HOVER_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(PAD_Y, PAD_X, PAD_Y, PAD_X));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(triLabel);
        header.add(textLabel);

        java.awt.event.MouseAdapter click = new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                uiStateStore.toggleGroupCollapsed(groupName);
                rebuild();
            }
        };

        header.addMouseListener(click);
        triLabel.addMouseListener(click);
        textLabel.addMouseListener(click);

        return header;
    }

    // Guaranteed 1px divider (no LAF surprises)
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
}
