package com.farmutils.ui;

public final class UiRowMetrics
{
    private UiRowMetrics() {}

    public static int iconSize(float scale)
    {
        int v = Math.round(16 * scale);
        return Math.max(12, Math.min(24, v));
    }

    public static int iconGap(float scale)
    {
        int v = Math.round(4 * scale);
        return Math.max(3, v);
    }

    public static int iconColWidth(float scale)
    {
        return iconSize(scale) + iconGap(scale);
    }
}
