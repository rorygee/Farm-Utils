package com.farmutils.calc;

import java.util.Objects;

public final class CalcItemRef
{
    private final String name;
    private final Integer itemId;
    private final boolean tradeable;
    private final boolean hasGePrice;

    public CalcItemRef(final String name, final Integer itemId, final boolean tradeable, final boolean hasGePrice)
    {
        this.name = Objects.requireNonNull(name, "name");
        this.itemId = itemId;
        this.tradeable = tradeable;
        this.hasGePrice = hasGePrice;
    }

    public String getName()
    {
        return name;
    }

    public Integer getItemId()
    {
        return itemId;
    }

    public boolean isTradeable()
    {
        return tradeable;
    }

    public boolean hasGePrice()
    {
        return hasGePrice;
    }

    public boolean hasPriceableItemId()
    {
        return hasGePrice && itemId != null && itemId > 0;
    }
}
