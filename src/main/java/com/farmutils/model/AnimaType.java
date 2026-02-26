package com.farmutils.model;

/** Farming Guild anima patch seeds. */
public enum AnimaType
{
    ATTAS("Attas", net.runelite.api.gameval.ItemID.ANIMA_ATTAS),
    IASOR("Iasor", net.runelite.api.gameval.ItemID.ANIMA_IASOR),
    KRONOS("Kronos", net.runelite.api.gameval.ItemID.ANIMA_KRONOS);

    private final String displayName;
    private final int itemId;

    AnimaType(String displayName, int itemId)
    {
        this.displayName = displayName;
        this.itemId = itemId;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public int getItemId()
    {
        return itemId;
    }
}
