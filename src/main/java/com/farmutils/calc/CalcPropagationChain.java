package com.farmutils.calc;

import java.util.Objects;

public final class CalcPropagationChain
{
    private final CalcItemRef seedItem;
    private final CalcItemRef filledPlantPotItem;
    private final CalcItemRef saplingItem;
    private final String notes;

    public CalcPropagationChain(
            final CalcItemRef seedItem,
            final CalcItemRef filledPlantPotItem,
            final CalcItemRef saplingItem,
            final String notes)
    {
        this.seedItem = Objects.requireNonNull(seedItem, "seedItem");
        this.filledPlantPotItem = Objects.requireNonNull(filledPlantPotItem, "filledPlantPotItem");
        this.saplingItem = Objects.requireNonNull(saplingItem, "saplingItem");
        this.notes = notes;
    }

    public CalcItemRef getSeedItem()
    {
        return seedItem;
    }

    public CalcItemRef getFilledPlantPotItem()
    {
        return filledPlantPotItem;
    }

    public CalcItemRef getSaplingItem()
    {
        return saplingItem;
    }

    public String getNotes()
    {
        return notes;
    }
}
