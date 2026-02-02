package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;

/**
 * Render-only row used to group multiple patch slots that share a location.
 *
 * This component is inert: no actions, no drag, no indicator line.
 */
public final class LocationHeaderRow extends JPanel
{
    private static final int PAD_X = 8;
    private static final int PAD_Y = 4;

    public LocationHeaderRow(String locationName, FarmutilsConfig config)
    {
        float scale = config.textScale().multiplier();

        setLayout(new BorderLayout());
        setOpaque(false);

        int padY = Math.max(3, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));
        setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(locationName);
        label.setOpaque(false);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(UiFont.scaled(label.getFont(), scale * 0.92f, Font.PLAIN));
        add(label, BorderLayout.CENTER);
    }
}
