package com.farmutils.calc;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class CalcHarvestLivesRules
{
    private final int baseLives;
    private final Map<CalcCompostTier, Integer> compostLifeBonus;
    private final double secateursItemBonus;
    private final double farmingCapeItemBonus;
    private final double attasBonus;
    private final int kandarinMediumDiaryBonus;
    private final int kandarinHardDiaryBonus;
    private final int kandarinEliteDiaryBonus;
    private final int kourendKebosHardDiaryBonus;
    private final String chanceToSaveFormula;
    private final String expectedYieldFormula;

    public CalcHarvestLivesRules(
            final int baseLives,
            final Map<CalcCompostTier, Integer> compostLifeBonus,
            final double secateursItemBonus,
            final double farmingCapeItemBonus,
            final double attasBonus,
            final int kandarinMediumDiaryBonus,
            final int kandarinHardDiaryBonus,
            final int kandarinEliteDiaryBonus,
            final int kourendKebosHardDiaryBonus,
            final String chanceToSaveFormula,
            final String expectedYieldFormula)
    {
        this.baseLives = baseLives;
        this.compostLifeBonus = Collections.unmodifiableMap(new EnumMap<>(Objects.requireNonNull(compostLifeBonus, "compostLifeBonus")));
        this.secateursItemBonus = secateursItemBonus;
        this.farmingCapeItemBonus = farmingCapeItemBonus;
        this.attasBonus = attasBonus;
        this.kandarinMediumDiaryBonus = kandarinMediumDiaryBonus;
        this.kandarinHardDiaryBonus = kandarinHardDiaryBonus;
        this.kandarinEliteDiaryBonus = kandarinEliteDiaryBonus;
        this.kourendKebosHardDiaryBonus = kourendKebosHardDiaryBonus;
        this.chanceToSaveFormula = chanceToSaveFormula;
        this.expectedYieldFormula = expectedYieldFormula;
    }

    public int getBaseLives() { return baseLives; }
    public Map<CalcCompostTier, Integer> getCompostLifeBonus() { return compostLifeBonus; }
    public double getSecateursItemBonus() { return secateursItemBonus; }
    public double getFarmingCapeItemBonus() { return farmingCapeItemBonus; }
    public double getAttasBonus() { return attasBonus; }
    public int getKandarinMediumDiaryBonus() { return kandarinMediumDiaryBonus; }
    public int getKandarinHardDiaryBonus() { return kandarinHardDiaryBonus; }
    public int getKandarinEliteDiaryBonus() { return kandarinEliteDiaryBonus; }
    public int getKourendKebosHardDiaryBonus() { return kourendKebosHardDiaryBonus; }
    public String getChanceToSaveFormula() { return chanceToSaveFormula; }
    public String getExpectedYieldFormula() { return expectedYieldFormula; }
}
