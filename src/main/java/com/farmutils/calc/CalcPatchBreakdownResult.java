package com.farmutils.calc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CalcPatchBreakdownResult
{
    private final boolean complete;
    private final long costs;
    private final long revenue;
    private final double xp;
    private final List<CalcBreakdownRow> rows;

    public CalcPatchBreakdownResult(
            final boolean complete,
            final long costs,
            final long revenue,
            final double xp,
            final List<CalcBreakdownRow> rows)
    {
        this.complete = complete;
        this.costs = costs;
        this.revenue = revenue;
        this.xp = xp;
        this.rows = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(rows, "rows")));
    }

    public boolean isComplete()
    {
        return complete;
    }

    public long getCosts()
    {
        return costs;
    }

    public long getRevenue()
    {
        return revenue;
    }

    public double getXp()
    {
        return xp;
    }

    public List<CalcBreakdownRow> getRows()
    {
        return rows;
    }
}
