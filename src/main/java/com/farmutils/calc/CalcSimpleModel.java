package com.farmutils.calc;

public final class CalcSimpleModel
{
    private final int seedItemId;
    private final int outputItemId;
    private final double plantingXp;
    private final double harvestXp;
    private final int seedCount;
    private final int flatYield;

    public CalcSimpleModel(
            final int seedItemId,
            final int outputItemId,
            final double plantingXp,
            final double harvestXp,
            final int seedCount,
            final int flatYield)
    {
        this.seedItemId = seedItemId;
        this.outputItemId = outputItemId;
        this.plantingXp = plantingXp;
        this.harvestXp = harvestXp;
        this.seedCount = seedCount;
        this.flatYield = flatYield;
    }

    public int getSeedItemId()
    {
        return seedItemId;
    }

    public int getOutputItemId()
    {
        return outputItemId;
    }

    public double getPlantingXp()
    {
        return plantingXp;
    }

    public double getHarvestXp()
    {
        return harvestXp;
    }

    public int getSeedCount()
    {
        return seedCount;
    }

    public int getFlatYield()
    {
        return flatYield;
    }
}
