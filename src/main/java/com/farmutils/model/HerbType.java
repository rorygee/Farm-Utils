package com.farmutils.model;

/** Standard herb types for herb patches that use the shared transform slot table. */
public enum HerbType
{
    GUAM("Guam", 249),
    MARRENTILL("Marrentill", 251),
    TARROMIN("Tarromin", 253),
    HARRALANDER("Harralander", 255),
    RANARR("Ranarr", 257),
    TOADFLAX("Toadflax", 2998),
    IRIT("Irit", 259),
    AVANTOE("Avantoe", 261),
    HUASCA("Huasca", 30097),
    KWUARM("Kwuarm", 263),
    SNAPDRAGON("Snapdragon", 3000),
    CADANTINE("Cadantine", 265),
    LANTADYME("Lantadyme", 2481),
    DWARF_WEED("Dwarf weed", 267),
    TORSTOL("Torstol", 269);

    private final String displayName;
    private final int cleanItemId;

    HerbType(String displayName, int cleanItemId)
    {
        this.displayName = displayName;
        this.cleanItemId = cleanItemId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    /** OSRS item id for the clean herb, suitable for row icons. */
    public int getCleanItemId()
    {
        return cleanItemId;
    }
}
