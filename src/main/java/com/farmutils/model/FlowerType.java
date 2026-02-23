package com.farmutils.model;

/** Standard flower patch crops. */
public enum FlowerType
{
    MARIGOLD("Marigold", 6010),
    ROSEMARY("Rosemary", 6014),
    NASTURTIUM("Nasturtium", 6012),
    WOAD("Woad", 1793),
    LIMPWURT("Limpwurt", 225),
    WHITE_LILY("White lily", 22932),

    // Not a flower crop, but it occupies the flower patch slot.
    SCARECROW("Scarecrow", 6059);

    private final String displayName;
    private final int itemId;

    FlowerType(String displayName, int itemId)
    {
        this.displayName = displayName;
        this.itemId = itemId;
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
}
