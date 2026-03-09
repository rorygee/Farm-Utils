package com.farmutils.calc;

import com.farmutils.model.PatchId;

import java.util.Objects;

public final class CalcBreakdownRow
{
    private final CalcBreakdownStat stat;
    private final PatchId patchId;
    private final String group;
    private final String cropName;
    private final String name;
    private final CalcBreakdownCategory category;
    private final Double quantity;
    private final Long gpAmount;
    private final Double xpAmount;
    private final String note;

    public CalcBreakdownRow(
            final CalcBreakdownStat stat,
            final PatchId patchId,
            final String group,
            final String cropName,
            final String name,
            final CalcBreakdownCategory category,
            final Double quantity,
            final Long gpAmount,
            final Double xpAmount,
            final String note)
    {
        this.stat = Objects.requireNonNull(stat, "stat");
        this.patchId = patchId;
        this.group = group;
        this.cropName = cropName;
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.quantity = quantity;
        this.gpAmount = gpAmount;
        this.xpAmount = xpAmount;
        this.note = note;
    }

    public CalcBreakdownStat getStat()
    {
        return stat;
    }

    public PatchId getPatchId()
    {
        return patchId;
    }

    public String getGroup()
    {
        return group;
    }

    public String getCropName()
    {
        return cropName;
    }

    public String getName()
    {
        return name;
    }

    public CalcBreakdownCategory getCategory()
    {
        return category;
    }

    public Double getQuantity()
    {
        return quantity;
    }

    public Long getGpAmount()
    {
        return gpAmount;
    }

    public Double getXpAmount()
    {
        return xpAmount;
    }

    public String getNote()
    {
        return note;
    }
}
