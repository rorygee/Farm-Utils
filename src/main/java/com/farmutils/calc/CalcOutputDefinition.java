package com.farmutils.calc;

import java.util.Objects;

public final class CalcOutputDefinition
{
    private final CalcItemRef item;
    private final CalcOutputRole role;
    private final String condition;
    private final String notes;

    public CalcOutputDefinition(final CalcItemRef item, final CalcOutputRole role, final String condition, final String notes)
    {
        this.item = Objects.requireNonNull(item, "item");
        this.role = Objects.requireNonNull(role, "role");
        this.condition = condition;
        this.notes = notes;
    }

    public CalcItemRef getItem()
    {
        return item;
    }

    public CalcOutputRole getRole()
    {
        return role;
    }

    public String getCondition()
    {
        return condition;
    }

    public String getNotes()
    {
        return notes;
    }
}
