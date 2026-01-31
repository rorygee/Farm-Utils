package com.farmutils.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class FarmStubPanel extends JPanel
{
    public FarmStubPanel(String title, String body)
    {
        super(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel header = new JLabel(title);
        header.setForeground(Color.WHITE);

        // derive from current UI font, just bold
        header.setFont(header.getFont().deriveFont(Font.BOLD));


        JLabel text = new JLabel("<html>" + body + "</html>");
        text.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        // derive, keep plain
        text.setFont(text.getFont().deriveFont(Font.PLAIN));


        add(header, BorderLayout.NORTH);
        add(text, BorderLayout.CENTER);
    }
}
