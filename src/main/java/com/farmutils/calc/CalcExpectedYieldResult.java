package com.farmutils.calc;

public final class CalcExpectedYieldResult
{
    private final Double expectedYield;
    private final CalcYieldConfidenceTag confidenceTag;
    private final String note;

    public CalcExpectedYieldResult(final Double expectedYield, final CalcYieldConfidenceTag confidenceTag, final String note)
    {
        this.expectedYield = expectedYield;
        this.confidenceTag = confidenceTag;
        this.note = note;
    }

    public static CalcExpectedYieldResult unresolved(final CalcYieldConfidenceTag confidenceTag, final String note)
    {
        return new CalcExpectedYieldResult(null, confidenceTag, note);
    }

    public static CalcExpectedYieldResult resolved(final double expectedYield, final CalcYieldConfidenceTag confidenceTag, final String note)
    {
        return new CalcExpectedYieldResult(expectedYield, confidenceTag, note);
    }

    public Double getExpectedYield()
    {
        return expectedYield;
    }

    public CalcYieldConfidenceTag getConfidenceTag()
    {
        return confidenceTag;
    }

    public String getNote()
    {
        return note;
    }

    public boolean isResolved()
    {
        return expectedYield != null;
    }
}
