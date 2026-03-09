package com.farmutils.calc;

public final class CalcXpProfile
{
    private final Double plantingXp;
    private final Double harvestXpPerItem;
    private final Double checkHealthXp;

    public CalcXpProfile(final Double plantingXp, final Double harvestXpPerItem, final Double checkHealthXp)
    {
        this.plantingXp = plantingXp;
        this.harvestXpPerItem = harvestXpPerItem;
        this.checkHealthXp = checkHealthXp;
    }

    public Double getPlantingXp()
    {
        return plantingXp;
    }

    public Double getHarvestXpPerItem()
    {
        return harvestXpPerItem;
    }

    public Double getCheckHealthXp()
    {
        return checkHealthXp;
    }
}
