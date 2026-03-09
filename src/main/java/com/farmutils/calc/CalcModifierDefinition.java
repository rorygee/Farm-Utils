package com.farmutils.calc;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CalcModifierDefinition
{
    private final String id;
    private final String name;
    private final CalcModifierType type;
    private final List<String> appliesToGroups;
    private final Set<CalcModifierTarget> targets;
    private final boolean metadataOnly;
    private final Double itemBonus;
    private final Integer diaryBonus;
    private final Double attasBonus;
    private final Double seedSaveChance;
    private final Integer charges;
    private final Double xpBonus;
    private final String notes;

    public CalcModifierDefinition(
            final String id,
            final String name,
            final CalcModifierType type,
            final List<String> appliesToGroups,
            final Set<CalcModifierTarget> targets,
            final boolean metadataOnly,
            final Double itemBonus,
            final Integer diaryBonus,
            final Double attasBonus,
            final Double seedSaveChance,
            final Integer charges,
            final Double xpBonus,
            final String notes)
    {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.appliesToGroups = Collections.unmodifiableList(Objects.requireNonNull(appliesToGroups, "appliesToGroups"));
        this.targets = Collections.unmodifiableSet(targets == null ? EnumSet.noneOf(CalcModifierTarget.class) : EnumSet.copyOf(targets));
        this.metadataOnly = metadataOnly;
        this.itemBonus = itemBonus;
        this.diaryBonus = diaryBonus;
        this.attasBonus = attasBonus;
        this.seedSaveChance = seedSaveChance;
        this.charges = charges;
        this.xpBonus = xpBonus;
        this.notes = notes;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public CalcModifierType getType() { return type; }
    public List<String> getAppliesToGroups() { return appliesToGroups; }
    public Set<CalcModifierTarget> getTargets() { return targets; }
    public boolean isMetadataOnly() { return metadataOnly; }
    public Double getItemBonus() { return itemBonus; }
    public Integer getDiaryBonus() { return diaryBonus; }
    public Double getAttasBonus() { return attasBonus; }
    public Double getSeedSaveChance() { return seedSaveChance; }
    public Integer getCharges() { return charges; }
    public Double getXpBonus() { return xpBonus; }
    public String getNotes() { return notes; }
}
