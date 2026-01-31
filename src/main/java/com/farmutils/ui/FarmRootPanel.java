package com.farmutils.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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

    private final JPanel nav = new JPanel(new GridBagLayout());
    private final JPanel cards = new JPanel(new CardLayout());
    private final CardLayout cardLayout = (CardLayout) cards.getLayout();

    private final Map<Mode, JToggleButton> buttons = new EnumMap<>(Mode.class);
    private Mode current = Mode.PATCHES;

    public FarmRootPanel(
            FarmPanel farmPanel,
            JComponent routesPanel,
            JComponent calcPanel,
            JComponent exportPanel
    )
    {
        super();

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
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN));

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
    }

    public void resetToDefault()
    {
        showMode(Mode.PATCHES);
    }
}
