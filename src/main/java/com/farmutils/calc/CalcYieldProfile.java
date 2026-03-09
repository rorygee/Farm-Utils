package com.farmutils.calc;

public final class CalcYieldProfile
{
    private final CalcYieldModelType type;
    private final Integer flatYield;
    private final Integer ctsLow;
    private final Integer ctsHigh;
    private final boolean supportsSecateurs;
    private final boolean supportsFarmingCape;
    private final boolean supportsAttas;
    private final boolean supportsDiaryBonus;
    private final CalcYieldConfidenceTag confidenceTag;
    private final String notes;

    public CalcYieldProfile(
            final CalcYieldModelType type,
            final Integer flatYield,
            final Integer ctsLow,
            final Integer ctsHigh,
            final boolean supportsSecateurs,
            final boolean supportsFarmingCape,
            final boolean supportsAttas,
            final boolean supportsDiaryBonus,
            final CalcYieldConfidenceTag confidenceTag,
            final String notes)
    {
        this.type = type;
        this.flatYield = flatYield;
        this.ctsLow = ctsLow;
        this.ctsHigh = ctsHigh;
        this.supportsSecateurs = supportsSecateurs;
        this.supportsFarmingCape = supportsFarmingCape;
        this.supportsAttas = supportsAttas;
        this.supportsDiaryBonus = supportsDiaryBonus;
        this.confidenceTag = confidenceTag;
        this.notes = notes;
    }

    public CalcYieldModelType getType()
    {
        return type;
    }

    public Integer getFlatYield()
    {
        return flatYield;
    }

    public Integer getCtsLow()
    {
        return ctsLow;
    }

    public Integer getCtsHigh()
    {
        return ctsHigh;
    }

    public boolean supportsSecateurs()
    {
        return supportsSecateurs;
    }

    public boolean supportsFarmingCape()
    {
        return supportsFarmingCape;
    }

    public boolean supportsAttas()
    {
        return supportsAttas;
    }

    public boolean supportsDiaryBonus()
    {
        return supportsDiaryBonus;
    }

    public CalcYieldConfidenceTag getConfidenceTag()
    {
        return confidenceTag;
    }

    public String getNotes()
    {
        return notes;
    }
}
