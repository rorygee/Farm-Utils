package com.farmutils.ui;

import com.farmutils.config.TextScale;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

import com.farmutils.FarmutilsConfig;
import net.runelite.client.ui.FontManager;

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


    private final JPanel nav = new JPanel(new GridBagLayout());
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new PreferredCardPanel(cardLayout);

    private final Map<Mode, JToggleButton> buttons = new EnumMap<>(Mode.class);
    private Mode current = Mode.PATCHES;

    private final FarmutilsConfig config;
    private static final String PROP_NAV_BASE_FONT = "farmutils.navBaseFont";

    public FarmRootPanel(
            FarmutilsConfig config,
            FarmPanel farmPanel,
            JComponent routesPanel,
            JComponent calcPanel,
            JComponent exportPanel
    )
    {
        super();

        this.config = config;

        // Painted “floor” so no white bleed-through anywhere
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        cards.setOpaque(true);
        cards.setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildNav();
        buildCards(farmPanel, routesPanel, calcPanel, exportPanel);

        add(nav, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);

        showMode(Mode.PATCHES);
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
        current = mode;

        JToggleButton btn = buttons.get(mode);
        if (btn != null && !btn.isSelected())
        {
            btn.setSelected(true);
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
        nav.revalidate();
        nav.repaint();
    }

    public void resetToDefault()
    {
        showMode(Mode.PATCHES);
    }
}
