package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Small, boring UI primitives to keep spacing/dividers consistent.
 */
public final class UiTokens
{
    private UiTokens() {}

    public static final String PROP_INDENT_PX = "farmutils.indentPx";

    public static int scaledPx(float scale, int basePx, int minPx)
    {
        return Math.max(minPx, Math.round(basePx * scale));
    }

    public static Border padding(float scale, int top, int left, int bottom, int right,
                                 int minTop, int minLeft, int minBottom, int minRight)
    {
        int t = scaledPx(scale, top, minTop);
        int l = scaledPx(scale, left, minLeft);
        int b = scaledPx(scale, bottom, minBottom);
        int r = scaledPx(scale, right, minRight);
        return BorderFactory.createEmptyBorder(t, l, b, r);
    }

    public static JComponent divider()
    {
        return divider(ColorScheme.DARKER_GRAY_COLOR);
    }

    public static JComponent divider(Color color)
    {
        JPanel d = new JPanel();
        d.setOpaque(true);
        d.setBackground(color);
        d.setMinimumSize(new Dimension(0, 1));
        d.setPreferredSize(new Dimension(0, 1));
        d.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        d.setAlignmentX(Component.LEFT_ALIGNMENT);
        return d;
    }

    /**
     * Wrap a component with a left indent while preserving the child layout contract.
     */
    public static JComponent withLeftIndent(Component child, int indentPx)
    {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(child, BorderLayout.CENTER);
        if (indentPx > 0)
        {
            wrap.setBorder(BorderFactory.createEmptyBorder(0, indentPx, 0, 0));
            wrap.putClientProperty(PROP_INDENT_PX, indentPx);
        }
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
        return wrap;
    }

    public static int textFieldHeight(JTextField field)
    {
        if (field == null)
        {
            return 0;
        }

        Insets insets = field.getInsets();
        FontMetrics fm = field.getFontMetrics(field.getFont());
        int textH = (fm != null) ? fm.getHeight() : field.getFont().getSize();
        // +2 for caret/anti-alias rounding. Keep minimum for safety.
        return Math.max(18, textH + insets.top + insets.bottom + 2);
    }
}
