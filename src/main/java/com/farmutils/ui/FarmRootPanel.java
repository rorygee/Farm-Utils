package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.config.TextScale;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.util.EnumMap;
import java.util.Map;

public class FarmRootPanel extends PluginPanel
{
    public enum Mode
    {
        PATCHES("Patches"),
        ROUTES("Routes"),
        CALC("Calc"),
        EXPORT("Export");

        private final String label;
        Mode(String label) { this.label = label; }
        public String label() { return label; }
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
    private final FarmPanel farmPanel;
    private final ClientUI clientUI;

    private final JPanel nav = new JPanel(new GridBagLayout());

    private final JPanel chrome = new JPanel();
    private final JPanel filterRow = new JPanel(new BorderLayout());
    private final JPanel toolbarRow = new JPanel(new BorderLayout());
    private final JComponent chromeDivider = divider();

    private final JTextField filterField = new JTextField();

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new PreferredCardPanel(cardLayout);

    private final Map<Mode, JToggleButton> buttons = new EnumMap<>(Mode.class);
    private Mode current = Mode.PATCHES;

    private KeyEventDispatcher findDispatcher;

    public FarmRootPanel(
            FarmutilsConfig config,
            ClientUI clientUI,
            FarmPanel farmPanel,
            JComponent routesPanel,
            JComponent calcPanel,
            JComponent exportPanel)
    {
        super();

        this.config = config;
        this.clientUI = clientUI;
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
        filterRow.setBackground(ColorScheme.DARK_GRAY_COLOR);

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
    }

    private void buildToolbarRow()
    {
        applyToolbarBackground();
        toolbarRow.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));

        // Inert placeholder — reserved space only.
        toolbarRow.setPreferredSize(new Dimension(1, 28));
        toolbarRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
    }

    private void setPatchesChromeVisible(boolean visible)
    {
        filterRow.setVisible(visible);
        toolbarRow.setVisible(visible);
        chromeDivider.setVisible(visible);

        // Layout must recompute when hiding rows.
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

        if (solid)
        {
            // Slightly lighter than the chrome background so it reads as a distinct band.
        }
        else
        {
            toolbarRow.setOpaque(false);
            toolbarRow.setBackground(new Color(0, 0, 0, 0));
        }
    }


    private void buildNav()
    {
        nav.setOpaque(true);
        nav.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nav.setBorder(new EmptyBorder(6, 6, 6, 6));

        ButtonGroup group = new ButtonGroup();

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weighty = 0;

        addButton(group, c, Mode.PATCHES, 0);
        addButton(group, c, Mode.ROUTES,  1);
        addButton(group, c, Mode.CALC,    2);
        addButton(group, c, Mode.EXPORT,  3);

        // Divider under tabs (always)
        JSeparator sep = new JSeparator();
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        sep.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);

        GridBagConstraints sepC = new GridBagConstraints();
        sepC.gridx = 0;
        sepC.gridy = 1;
        sepC.gridwidth = 4;
        sepC.fill = GridBagConstraints.HORIZONTAL;
        sepC.weightx = 1;

        nav.add(sep, sepC);
    }

    private void addButton(ButtonGroup group, GridBagConstraints base, Mode mode, int x)
    {
        JToggleButton btn = new JToggleButton(mode.label());
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Token colors only
        btn.setOpaque(true);
        btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        btn.setForeground(Color.WHITE);

        // Scale-safe: derive only (no size)
        applyNavFont(btn);

        btn.addActionListener(e ->
        {
            if (current != mode)
            {
                showMode(mode);
            }
        });

        group.add(btn);
        buttons.put(mode, btn);

        GridBagConstraints c = (GridBagConstraints) base.clone();
        c.gridx = x;
        c.weightx = 1;

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

    public void refreshUiFromConfig()
    {
        for (JToggleButton b : buttons.values())
        {
            applyNavFont(b);
        }
        applyFilterSizing();
        applyToolbarBackground();
        nav.revalidate();
        nav.repaint();
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