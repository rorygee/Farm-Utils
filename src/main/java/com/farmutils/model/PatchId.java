package com.farmutils.model;

public enum PatchId
{
    HERB_ARDOUGNE("Herb", "Ardougne"),
    HERB_CATHERBY("Herb", "Catherby"),
    HERB_FALADOR("Herb", "Falador"),
    HERB_HOSIDIUS("Herb", "Hosidius"),
    HERB_TROLLHEIM("Herb", "Trollheim"),

    ALLOTMENT_CATHERBY("Allotment", "Catherby"),
    FLOWER_FALADOR("Flower", "Falador"),

    TREE_VARROCK("Tree", "Varrock"),
    TREE_TAVERLEY("Tree", "Taverley"),

    FRUIT_CATHERBY("Fruit tree", "Catherby");

    private final String group;
    private final String label;

    PatchId(String group, String label)
    {
        this.group = group;
        this.label = label;
    }

    public String getGroup()
    {
        return group;
    }

    public String getLabel()
    {
        return label;
    }

    public String storageKey()
    {
        return name();
    }
}
