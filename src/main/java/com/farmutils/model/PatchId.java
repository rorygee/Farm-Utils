package com.farmutils.model;

/**
 * Canonical list of farming patches exposed by Farm Utils.
 *
 * Groups are intentionally user-facing and should remain stable.
 * Labels are intentionally short (location-first) for scannability.
 */
public enum PatchId
{
    // --- Allotment / Flower / Herb patches (shared locations) ---
    ALLOTMENT_FALADOR("Allotment", "Falador"),
    FLOWER_FALADOR("Flower", "Falador"),
    HERB_FALADOR("Herb", "Falador"),

    ALLOTMENT_PORT_PHASMATYS("Allotment", "Port Phasmatys"),
    FLOWER_PORT_PHASMATYS("Flower", "Port Phasmatys"),
    HERB_PORT_PHASMATYS("Herb", "Port Phasmatys"),

    ALLOTMENT_CATHERBY("Allotment", "Catherby"),
    FLOWER_CATHERBY("Flower", "Catherby"),
    HERB_CATHERBY("Herb", "Catherby"),

    ALLOTMENT_ARDOUGNE("Allotment", "Ardougne"),
    FLOWER_ARDOUGNE("Flower", "Ardougne"),
    HERB_ARDOUGNE("Herb", "Ardougne"),

    ALLOTMENT_HOSIDIUS("Allotment", "Hosidius"),
    FLOWER_HOSIDIUS("Flower", "Hosidius"),
    HERB_HOSIDIUS("Herb", "Hosidius"),

    ALLOTMENT_FARMING_GUILD("Allotment", "Farming Guild"),
    FLOWER_FARMING_GUILD("Flower", "Farming Guild"),
    HERB_FARMING_GUILD("Herb", "Farming Guild"),

    ALLOTMENT_PRIFDDINAS("Allotment", "Prifddinas"),
    FLOWER_PRIFDDINAS("Flower", "Prifddinas"),

    ALLOTMENT_CIVITAS_ILLA_FORTIS("Allotment", "Civitas illa Fortis"),
    FLOWER_CIVITAS_ILLA_FORTIS("Flower", "Civitas illa Fortis"),
    HERB_CIVITAS_ILLA_FORTIS("Herb", "Civitas illa Fortis"),

    // Single-type patches that are still part of the core run loop
    ALLOTMENT_HARMONY_ISLAND("Allotment", "Harmony Island"),

    HERB_TROLL_STRONGHOLD("Herb", "Troll Stronghold"),
    HERB_HARMONY_ISLAND("Herb", "Harmony Island"),
    HERB_WEISS("Herb", "Weiss"),

    FLOWER_KASTORI("Flower", "Kastori"),

    // --- Hops ---
    HOPS_LUMBRIDGE("Hops", "Lumbridge"),
    HOPS_MCGUBORS_WOOD("Hops", "McGrubor's Wood"),
    HOPS_YANILLE("Hops", "Yanille"),
    HOPS_ENTRANA("Hops", "Entrana"),
    HOPS_ALDARIN("Hops", "Aldarin"),

    // --- Bush ---
    BUSH_CHAMPIONS_GUILD("Bush", "Champions' Guild"),
    BUSH_RIMMINGTON("Bush", "Rimmington"),
    BUSH_ARDOUGNE("Bush", "Ardougne"),
    BUSH_ETCETERIA("Bush", "Etceteria"),
    BUSH_FARMING_GUILD("Bush", "Farming Guild"),

    // --- Tree ---
    TREE_LUMBRIDGE("Tree", "Lumbridge"),
    TREE_VARROCK("Tree", "Varrock"),
    TREE_FALADOR("Tree", "Falador"),
    TREE_TAVERLEY("Tree", "Taverley"),
    TREE_GNOME_STRONGHOLD("Tree", "Gnome Stronghold"),
    TREE_FARMING_GUILD("Tree", "Farming Guild"),
    TREE_NEMUS_RETREAT("Tree", "Nemus Retreat"),

    // --- Fruit tree ---
    FRUIT_TREE_GNOME_STRONGHOLD("Fruit tree", "Gnome Stronghold"),
    FRUIT_TREE_CATHERBY("Fruit tree", "Catherby"),
    FRUIT_TREE_GNOME_VILLAGE("Fruit tree", "Gnome Village"),
    FRUIT_TREE_BRIMHAVEN("Fruit tree", "Brimhaven"),
    FRUIT_TREE_LLETYA("Fruit tree", "Lletya"),
    FRUIT_TREE_FARMING_GUILD("Fruit tree", "Farming Guild"),
    FRUIT_TREE_KASTORI("Fruit tree", "Kastori"),

    // --- Spirit tree ---
    SPIRIT_TREE_ETCETERIA("Spirit tree", "Etceteria"),
    SPIRIT_TREE_PORT_SARIM("Spirit tree", "Port Sarim"),
    SPIRIT_TREE_BRIMHAVEN("Spirit tree", "Brimhaven"),
    SPIRIT_TREE_HOSIDIUS("Spirit tree", "Hosidius"),
    SPIRIT_TREE_FARMING_GUILD("Spirit tree", "Farming Guild"),

    // --- Special patches (non-tree) ---
    SPECIAL_SEAWEED("Special", "Underwater (Seaweed)"),
    SPECIAL_CORAL_NURSERY("Special", "Coral Nurseries"),
    SPECIAL_GRAPES("Special", "Hosidius Vinery (Grapes)"),
    SPECIAL_MUSHROOM("Special", "Canifis (Mushroom)"),
    SPECIAL_BELLADONNA_DRAYNOR("Special", "Draynor Manor (Belladonna)"),
    SPECIAL_BELLADONNA_AUBURNVALE("Special", "Auburnvale (Belladonna)"),
    SPECIAL_HESPORI("Special", "Farming Guild (Hespori)"),
    SPECIAL_ANIMA("Special", "Farming Guild (Anima)"),

    // --- Special tree patches ---
    SPECIAL_TREE_HARDWOOD_FOSSIL_ISLAND("Special tree", "Hardwood (Fossil Island)"),
    SPECIAL_TREE_HARDWOOD_LOCUS_OASIS("Special tree", "Hardwood (Locus Oasis)"),
    SPECIAL_TREE_HARDWOOD_ANGLERS_RETREAT("Special tree", "Hardwood (Anglers' Retreat)"),
    SPECIAL_TREE_CALQUAT_TAI_BWO_WANNAI("Special tree", "Calquat (Tai Bwo Wannai)"),
    SPECIAL_TREE_CALQUAT_SUMMER_SHORE("Special tree", "Calquat (Summer Shore)"),
    SPECIAL_TREE_CALQUAT_KASTORI("Special tree", "Calquat (Kastori)"),
    SPECIAL_TREE_CRYSTAL_PRIFDDINAS("Special tree", "Crystal (Prifddinas)"),
    SPECIAL_TREE_CELASTRUS_FARMING_GUILD("Special tree", "Celastrus (Farming Guild)"),
    SPECIAL_TREE_REDWOOD_FARMING_GUILD("Special tree", "Redwood (Farming Guild)"),

    // --- Cactus ---
    CACTUS_AL_KHARID("Cactus", "Al Kharid"),
    CACTUS_FARMING_GUILD("Cactus", "Farming Guild"),

    // --- Activity (minigame/raid/etc) ---
    ACTIVITY_TITHE_PATCH("Activity", "Tithe Farm"),
    ACTIVITY_COX_HERB("Activity", "Chambers of Xeric"),
    ACTIVITY_COX_WEEDS("Activity", "Chambers of Xeric (Weeds)"),
    ACTIVITY_MISCELLANIA_HERB("Activity", "Miscellania (Herb)"),
    ACTIVITY_MISCELLANIA_FLAX("Activity", "Miscellania (Flax)"),
    ACTIVITY_BIG_CATS_GRASS("Activity", "Big Cats & WWF"),

    // --- Quest-specific patches ---
    QUEST_UNFERTHS_PATCH("Quest", "Burthorpe (Unferth's Patch)"),
    QUEST_MAGIC_BEANS("Quest", "Taverley (Magic Beans)"),
    QUEST_KELDA_HOPS("Quest", "Keldagrim (Kelda Hops)"),
    QUEST_ELDER_CADANTINE("Quest", "Lletya (Elder Cadantine)"),
    QUEST_ENRICHED_SNAPDRAGON("Quest", "White Knights' Castle (Enriched Snapdragon)");

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
