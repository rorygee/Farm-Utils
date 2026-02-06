package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.ui.ColorScheme;

public final class UiScrollbars
{
    private UiScrollbars() {}

    public static void apply(JScrollPane pane, FarmutilsConfig config)
    {
        if (pane == null || config == null)
        {
            return;
        }

        JScrollBar v = pane.getVerticalScrollBar();
        if (v != null)
        {
            style(v, true, config);
        }

        JScrollBar h = pane.getHorizontalScrollBar();
        if (h != null)
        {
            style(h, false, config);
        }
    }

    private static void style(JScrollBar bar, boolean vertical, FarmutilsConfig config)
    {
        int width = Math.max(6, Math.min(16, config.scrollbarWidth()));
        Theme theme = Theme.from(config);

        bar.setOpaque(false);
        bar.setUnitIncrement(16);

        bar.setPreferredSize(
                vertical
                        ? new Dimension(width, Integer.MAX_VALUE)
                        : new Dimension(Integer.MAX_VALUE, width)
        );

        bar.setUI(new RuneLiteScrollbarUI(width, vertical, theme, config.showScrollButtons()));
        bar.revalidate();
        bar.repaint();
    }

    /* ========================= THEME ========================= */

    private static final class Theme
    {
        final boolean solidWell;

        final Color wellFill;
        final Color wellOutline;

        final Color base;
        final Color hover;
        final Color pressed;

        final Color glyphBase;
        final Color glyphHover;
        final Color glyphPressed;

        private Theme(
                boolean solidWell,
                Color wellFill,
                Color wellOutline,
                Color base,
                Color hover,
                Color pressed,
                Color glyphBase,
                Color glyphHover,
                Color glyphPressed)
        {
            this.solidWell = solidWell;
            this.wellFill = wellFill;
            this.wellOutline = wellOutline;
            this.base = base;
            this.hover = hover;
            this.pressed = pressed;
            this.glyphBase = glyphBase;
            this.glyphHover = glyphHover;
            this.glyphPressed = glyphPressed;
        }

        static Theme from(FarmutilsConfig config)
        {
            boolean solid = config.scrollbarWellBackground();

            Color wellFill = ColorScheme.DARKER_GRAY_COLOR;   // MUST differ from panel

            Color wellOutline;
            if (config.scrollbarOutlineStyle() == FarmutilsConfig.ScrollbarOutlineStyle.ACCENT)
            {
                // Strong orange outline (visible). Not the brand orange.
                wellOutline = new Color(255, 175, 0);
            }
            else
            {
                // Dark outline should match the well, not the panel.
                wellOutline = wellFill;
            }


            if (config.scrollbarColor() == FarmutilsConfig.ScrollbarStyle.ACCENT)
            {
                Color hoverOrange = new Color(255, 175, 0);
                Color pressedOrange = new Color(220, 125, 0);

                return new Theme(
                        solid,
                        wellFill,
                        wellOutline,
                        ColorScheme.BRAND_ORANGE,
                        hoverOrange,
                        pressedOrange,
                        ColorScheme.DARKER_GRAY_COLOR,
                        ColorScheme.MEDIUM_GRAY_COLOR,
                        ColorScheme.MEDIUM_GRAY_COLOR
                );
            }

            // DARK
            return new Theme(
                    solid,
                    wellFill,
                    wellOutline,
                    ColorScheme.MEDIUM_GRAY_COLOR,   // base (now clearly visible vs wellFill)
                    ColorScheme.LIGHT_GRAY_COLOR,    // hover
                    ColorScheme.MEDIUM_GRAY_COLOR,    // pressed/drag
                    ColorScheme.DARKER_GRAY_COLOR,  // glyph base
                    ColorScheme.DARKER_GRAY_COLOR,  // glyph hover
                    ColorScheme.DARKER_GRAY_COLOR   // glyph pressed
            );
        }
    }

    /* ========================= UI ========================= */

    private static final class RuneLiteScrollbarUI extends BasicScrollBarUI
    {
        private final int width;
        private final boolean vertical;
        private final Theme theme;
        private final boolean showButtons;

        RuneLiteScrollbarUI(int width, boolean vertical, Theme theme, boolean showButtons)
        {
            this.width = width;
            this.vertical = vertical;
            this.theme = theme;
            this.showButtons = showButtons;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                if (theme.solidWell)
                {
                    g2.setColor(theme.wellFill);
                    g2.fillRect(r.x, r.y, r.width, r.height);
                }

                g2.setColor(theme.wellOutline);
                g2.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            }
            finally
            {
                g2.dispose();
            }
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r)
        {
            if (r.isEmpty() || !scrollbar.isEnabled())
            {
                return;
            }

            Color color =
                    isDragging ? theme.pressed :
                            isThumbRollover() ? theme.hover :
                                    theme.base;

            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setColor(color);
                g2.fillRect(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
            }
            finally
            {
                g2.dispose();
            }
        }

        @Override
        protected JButton createDecreaseButton(int o)
        {
            return vertical && showButtons
                    ? new ArrowButton(SwingConstants.NORTH, width, theme)
                    : zero();
        }

        @Override
        protected JButton createIncreaseButton(int o)
        {
            return vertical && showButtons
                    ? new ArrowButton(SwingConstants.SOUTH, width, theme)
                    : zero();
        }

        private JButton zero()
        {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setOpaque(false);
            b.setBorder(null);
            return b;
        }
    }

    private static final class ArrowButton extends JButton
    {
        private final int dir;
        private final int size;
        private final Theme theme;

        ArrowButton(int dir, int size, Theme theme)
        {
            this.dir = dir;
            this.size = size;
            this.theme = theme;
            setBorder(null);
            setOpaque(false);
            setFocusable(false);
            setRolloverEnabled(true);
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                boolean hover = getModel().isRollover();
                boolean press = getModel().isPressed();

                if (theme.solidWell)
                {
                    g2.setColor(theme.wellFill);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }

                g2.setColor(theme.wellOutline);
                g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

                Color bg = press ? theme.pressed : hover ? theme.hover : theme.base;
                g2.setColor(bg);
                g2.fillRect(1, 1, getWidth() - 2, getHeight() - 2);

                Color glyph = press ? theme.glyphPressed : hover ? theme.glyphHover : theme.glyphBase;
                g2.setColor(glyph);

                int m = Math.max(2, size / 4);
                Polygon p = new Polygon();

                if (dir == SwingConstants.NORTH)
                {
                    p.addPoint(size / 2, m);
                    p.addPoint(size - m, size - m);
                    p.addPoint(m, size - m);
                }
                else
                {
                    p.addPoint(m, m);
                    p.addPoint(size - m, m);
                    p.addPoint(size / 2, size - m);
                }

                g2.fillPolygon(p);
            }
            finally
            {
                g2.dispose();
            }
        }
    }
}
