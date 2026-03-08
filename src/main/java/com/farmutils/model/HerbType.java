package com.farmutils.model;

/** Standard herb types for herb patches that use the shared transform slot table. */
public enum HerbType
{
    GUAM("Guam", 249, 199),
    MARRENTILL("Marrentill", 251, 201),
    TARROMIN("Tarromin", 253, 203),
    HARRALANDER("Harralander", 255, 205),
    RANARR("Ranarr", 257, 207),
    TOADFLAX("Toadflax", 2998, 3049),
    IRIT("Irit", 259, 209),
    AVANTOE("Avantoe", 261, 211),
    HUASCA("Huasca", 30097, 30094),
    KWUARM("Kwuarm", 263, 213),
    SNAPDRAGON("Snapdragon", 3000, 3051),
    CADANTINE("Cadantine", 265, 215),
    LANTADYME("Lantadyme", 2481, 2485),
    DWARF_WEED("Dwarf weed", 267, 217),
    TORSTOL("Torstol", 269, 219);

    private final String displayName;
    private final int cleanItemId;
    private final int grimyItemId;

    HerbType(String displayName, int cleanItemId, int grimyItemId)
    {
        this.displayName = displayName;
        this.cleanItemId = cleanItemId;
        this.grimyItemId = grimyItemId;
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

    /** OSRS item id for the harvested grimy herb, suitable for calc revenue. */
    public int getGrimyItemId()
    {
        return grimyItemId;
    }
}
