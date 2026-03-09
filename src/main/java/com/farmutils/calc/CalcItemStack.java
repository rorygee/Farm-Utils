package com.farmutils.calc;

import java.util.Objects;

public final class CalcItemStack
{
    private final CalcItemRef item;
    private final int quantity;

    public CalcItemStack(final CalcItemRef item, final int quantity)
    {
        this.item = Objects.requireNonNull(item, "item");
        this.quantity = quantity;
    }

    public CalcItemRef getItem()
    {
        return item;
    }

    public int getQuantity()
    {
        return quantity;
    }
}
