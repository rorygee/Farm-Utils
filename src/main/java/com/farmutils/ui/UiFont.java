package com.farmutils.ui;

import java.awt.Font;

public final class UiFont
{
    private UiFont() {}

    public static Font scaled(Font base, float scale, int style)
    {
        if (base == null)
        {
            base = new Font("SansSerif", Font.PLAIN, 12);
        }

        float size = base.getSize2D() * scale;
        return base.deriveFont(style, size);
    }
}
