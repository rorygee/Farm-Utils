package com.farmutils.model;

import com.farmutils.model.PatchQualifier;
import javax.annotation.Nullable;

/**
 * Canonical list of farming patches exposed by Farm Utils.
 *
 * Groups are intentionally user-facing and should remain stable.
 * Labels are intentionally short (location-first) for scannability.
 */
public enum PatchId
{
// --- Allotment / Flower / Herb patches ---

    // Falador (Two Allotment + Flower + Herb)
    ALLOTMENT_FALADOR_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "falador", "Falador", "Plot 1"
    ),
    ALLOTMENT_FALADOR_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "falador", "Falador", "Plot 2"
    ),
    FLOWER_FALADOR(
            "Flower", "Falador", null, null,
            "falador", "Falador", null
    ),
    HERB_FALADOR(
            "Herb", "Falador", null, null,
            "falador", "Falador", null
    ),

    // Port Phasmatys (Two Allotment + Flower + Herb)
    ALLOTMENT_PORT_PHASMATYS_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "port_phasmatys", "Port Phasmatys", "Plot 1"
    ),
    ALLOTMENT_PORT_PHASMATYS_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "port_phasmatys", "Port Phasmatys", "Plot 2"
    ),
    FLOWER_PORT_PHASMATYS(
            "Flower", "Port Phasmatys", null, null,
            "port_phasmatys", "Port Phasmatys", null
    ),
    HERB_PORT_PHASMATYS(
            "Herb", "Port Phasmatys", null, null,
            "port_phasmatys", "Port Phasmatys", null
    ),

    // Catherby (Two Allotment + Flower + Herb)
    ALLOTMENT_CATHERBY_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "catherby", "Catherby", "Plot 1"
    ),
    ALLOTMENT_CATHERBY_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "catherby", "Catherby", "Plot 2"
    ),
    FLOWER_CATHERBY(
            "Flower", "Catherby", null, null,
            "catherby", "Catherby", null
    ),
    HERB_CATHERBY(
            "Herb", "Catherby", null, null,
            "catherby", "Catherby", null
    ),

    // Ardougne (Two Allotment + Flower + Herb)
    ALLOTMENT_ARDOUGNE_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "ardougne", "Ardougne", "Plot 1"
    ),
    ALLOTMENT_ARDOUGNE_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "ardougne", "Ardougne", "Plot 2"
    ),
    FLOWER_ARDOUGNE(
            "Flower", "Ardougne", null, null,
            "ardougne", "Ardougne", null
    ),
    HERB_ARDOUGNE(
            "Herb", "Ardougne", null, null,
            "ardougne", "Ardougne", null
    ),

    // Hosidius (Two Allotment + Flower + Herb)
    ALLOTMENT_HOSIDIUS_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "hosidius", "Hosidius", "Plot 1"
    ),
    ALLOTMENT_HOSIDIUS_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "hosidius", "Hosidius", "Plot 2"
    ),
    FLOWER_HOSIDIUS(
            "Flower", "Hosidius", null, null,
            "hosidius", "Hosidius", null
    ),
    HERB_HOSIDIUS(
            "Herb", "Hosidius", null, null,
            "hosidius", "Hosidius", null
    ),

    // Harmony Island (single allotment + herb)
    ALLOTMENT_HARMONY_ISLAND(
            "Allotment", "Harmony Island", null, null,
            null, null, null
    ),
    HERB_HARMONY_ISLAND(
            "Herb", "Harmony Island", null, null,
            null, null, null
    ),

    // Farming Guild (Two Allotment + Flower, and a Herb patch also listed separately)
    ALLOTMENT_FARMING_GUILD_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "farming_guild", "Farming Guild", "Plot 1"
    ),
    ALLOTMENT_FARMING_GUILD_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "farming_guild", "Farming Guild", "Plot 2"
    ),
    FLOWER_FARMING_GUILD(
            "Flower", "Farming Guild", null, null,
            "farming_guild", "Farming Guild", null
    ),
    HERB_FARMING_GUILD(
            "Herb", "Farming Guild", null, null,
            "farming_guild", "Farming Guild", null
    ),

    // Prifddinas (Two Allotment + Flower)
    ALLOTMENT_PRIFDDINAS_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "prifddinas", "Prifddinas", "Plot 1"
    ),
    ALLOTMENT_PRIFDDINAS_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "prifddinas", "Prifddinas", "Plot 2"
    ),
    FLOWER_PRIFDDINAS(
            "Flower", "Prifddinas", null, null,
            "prifddinas", "Prifddinas", null
    ),

    // Troll Stronghold herb patch
    HERB_TROLL_STRONGHOLD(
            "Herb", "Troll Stronghold", null, null,
            "troll_stronghold", "Troll Stronghold", null
    ),

    // Weiss herb patch
    HERB_WEISS(
            "Herb", "Weiss", null, null,
            "weiss", "Weiss", null
    ),

    // Civitas illa Fortis (Two Allotment + Flower + Herb)
    ALLOTMENT_CIVITAS_ILLA_FORTIS_PLOT_1(
            "Allotment", "Plot 1", null, null,
            "civitas_illa_fortis", "Civitas illa Fortis", "Plot 1"
    ),
    ALLOTMENT_CIVITAS_ILLA_FORTIS_PLOT_2(
            "Allotment", "Plot 2", null, null,
            "civitas_illa_fortis", "Civitas illa Fortis", "Plot 2"
    ),
    FLOWER_CIVITAS_ILLA_FORTIS(
            "Flower", "Civitas illa Fortis", null, null,
            "civitas_illa_fortis", "Civitas illa Fortis", null
    ),
    HERB_CIVITAS_ILLA_FORTIS(
            "Herb", "Civitas illa Fortis", null, null,
            "civitas_illa_fortis", "Civitas illa Fortis", null
    ),

    // Kastori flower patch (listed as a Flower patch location)
    FLOWER_KASTORI(
            "Flower", "Kastori", null, null,
            "kastori", "Kastori", null
    ),


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
    SPECIAL_BELLADONNA_DRAYNOR("Belladonna", "Draynor Manor (Belladonna)"),
    SPECIAL_BELLADONNA_AUBURNVALE("Belladonna", "Auburnvale (Belladonna)"),
    SPECIAL_HESPORI("Hespori", "Farming Guild", PatchQualifier.BOSS, "Hespori"),
    SPECIAL_ANIMA("Anima", "Farming Guild", PatchQualifier.ACTIVITY, "Anima patch"),

    // --- Special tree patches ---
    // --- Hardwood ---
    SPECIAL_TREE_HARDWOOD_FOSSIL_ISLAND("Hardwood", "Fossil Island"),
    SPECIAL_TREE_HARDWOOD_LOCUS_OASIS("Hardwood", "Locus Oasis"),
    SPECIAL_TREE_HARDWOOD_ANGLERS_RETREAT("Hardwood", "Anglers' Retreat"),

    // --- Calquat ---
    SPECIAL_TREE_CALQUAT_TAI_BWO_WANNAI("Calquat", "Tai Bwo Wannai"),
    SPECIAL_TREE_CALQUAT_SUMMER_SHORE("Calquat", "Summer Shore"),
    SPECIAL_TREE_CALQUAT_KASTORI("Calquat", "Kastori"),

    // --- Special tree ---
    SPECIAL_TREE_CRYSTAL_PRIFDDINAS("Special tree", "Prifddinas (Crystal)"),
    SPECIAL_TREE_CELASTRUS_FARMING_GUILD("Special tree", "Farming Guild (Celastrus)"),
    SPECIAL_TREE_REDWOOD_FARMING_GUILD("Special tree", "Farming Guild (Redwood)"),


    // --- Cactus ---
    CACTUS_AL_KHARID("Cactus", "Al Kharid"),
    CACTUS_FARMING_GUILD("Cactus", "Farming Guild"),

    // --- Activity (minigame/raid/etc) ---
    SPECIAL_TITHE_FARM("Tithe Farm", "Tithe Farm", PatchQualifier.MINIGAME, "Tithe Farm"),
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

    /**
     * Optional stable key used to group multiple patch slots that share a location (e.g. allotments).
     *
     * Null means the entry is rendered as a normal single-row item.
     */
    @Nullable
    private final String locationKey;

    /**
     * Display name for the grouped location header (e.g. "Falador").
     */
    @Nullable
    private final String locationName;

    /**
     * Slot label rendered under a location header (e.g. "Plot 1", "Plot 2").
     */
    @Nullable
    private final String slotLabel;

    @Nullable
    private final PatchQualifier qualifier;
    @Nullable
    private final String qualifierDetail;


    PatchId(String group, String label)
    {
        this(group, label, null, null, null, null, null);
    }

    PatchId(String group, String label, PatchQualifier qualifier)
    {
        this(group, label, qualifier, null, null, null, null);
    }

    PatchId(String group, String label, PatchQualifier qualifier, String qualifierDetail)
    {
        this(group, label, qualifier, qualifierDetail, null, null, null);
    }

    PatchId(
            String group,
            String label,
            PatchQualifier qualifier,
            String qualifierDetail,
            String locationKey,
            String locationName,
            String slotLabel
    )
    {
        this.group = group;
        this.label = label;
        this.qualifier = qualifier;
        this.qualifierDetail = qualifierDetail;
        this.locationKey = locationKey;
        this.locationName = locationName;
        this.slotLabel = slotLabel;
    }


    public String getGroup()
    {
        return group;
    }

    public String getLabel()
    {
        return label;
    }

    @Nullable
    public String getLocationKey()
    {
        return locationKey;
    }

    @Nullable
    public String getLocationName()
    {
        return locationName;
    }

    @Nullable
    public String getSlotLabel()
    {
        return slotLabel;
    }

    @Nullable
    public PatchQualifier getQualifier() {
        return qualifier;
    }

    public String getQualifierDetail()
    {
        return qualifierDetail;
    }

    public String storageKey()
    {
        return name();
    }
}
