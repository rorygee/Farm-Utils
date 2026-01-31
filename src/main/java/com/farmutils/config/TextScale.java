package com.farmutils.config;

public enum TextScale
{
    SMALL(0.90f, "Small"),
    NORMAL(1.00f, "Normal"),
    LARGE(1.15f, "Large"),
    XL(1.30f, "Extra large");

    private final float multiplier;
    private final String label;

    TextScale(float multiplier, String label)
    {
        this.multiplier = multiplier;
        this.label = label;
    }

    public float multiplier()
    {
        return multiplier;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
