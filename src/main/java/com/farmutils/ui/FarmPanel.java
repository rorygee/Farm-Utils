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
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class FarmPanel extends PluginPanel
{
    private static final String PLACEHOLDER = "Filter patches…";

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

    private final Font baseFilterFont;

    private final JPanel list = new JPanel();
    private final JTextField filterField = new JTextField();
    private String filterText = "";

    private KeyEventDispatcher keyDispatcher;

    @Inject
    public FarmPanel(PatchStore store, UiStateStore uiStateStore, ClientUI clientUI, FarmutilsConfig config)
    {
        super(false);

        this.store = store;
        this.uiStateStore = uiStateStore;
        this.clientUI = clientUI;
        this.config = config;

        this.baseFilterFont = filterField.getFont();

        setBorder(null);
        setLayout(new BorderLayout());
        setOpaque(false);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(buildFilterRow(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        rebuild();
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
    }

    @Override
    public void removeNotify()
    {
        uninstallFindShortcut();
        super.removeNotify();
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

            boolean firstGroup = true;

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

                if (!firstGroup)
                {
                    list.add(divider());
                }
                firstGroup = false;

                boolean collapsed = uiStateStore.isGroupCollapsed(groupName);

                // while filtering, show as expanded (don’t imply hidden results)
                boolean collapsedForHeader = hasFilter ? false : collapsed;

                list.add(fullWidth(createGroupHeader(groupName, collapsedForHeader)));

                boolean showBody = (!collapsed || hasFilter);
                if (showBody)
                {
                    list.add(divider());

                    for (int i = 0; i < visibleIds.size(); i++)
                    {
                        PatchId id = visibleIds.get(i);
                        list.add(fullWidth(new PatchRow(id, store, config, this::rebuild)));

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

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setOpaque(true);

        // collapsed vs expanded differentiation
        header.setBackground(collapsed ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARK_GRAY_HOVER_COLOR);

        float groupScale = config.textScale().multiplier();
        int padY = Math.max(4, Math.round(PAD_Y * groupScale));
        int padX = Math.max(6, Math.round(PAD_X * groupScale));
        header.setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

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
