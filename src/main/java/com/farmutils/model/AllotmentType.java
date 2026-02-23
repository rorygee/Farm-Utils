package com.farmutils.model;

/** Standard allotment patch crops (classic farming areas + Harmony Island). */
public enum AllotmentType
{
    POTATO("Potato", 1942, 5),
    ONION("Onion", 1957, 5),
    CABBAGE("Cabbage", 1965, 5),
    TOMATO("Tomato", 1982, 5),
    SWEETCORN("Sweetcorn", 5986, 7),
    STRAWBERRY("Strawberry", 5504, 7),
    WATERMELON("Watermelon", 5982, 9),
    SNAPE_GRASS("Snape grass", 231, 8);

    private final String displayName;
    private final int itemId;
    private final int maxGrowthStage;

    AllotmentType(String displayName, int itemId, int maxGrowthStage)
    {
        this.displayName = displayName;
        this.itemId = itemId;
        this.maxGrowthStage = maxGrowthStage;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    /** OSRS item id suitable for row icons. */
    public int getItemId()
    {
        return itemId;
    }

    /**
     * Maximum growth stage for this crop, where stage == max indicates harvestable/ready.
     *
     * <p>This mirrors RuneLite Time Tracking {@code Produce.stages} for allotment crops.</p>
     */
    public int getMaxGrowthStage()
    {
        return maxGrowthStage;
    }
}
