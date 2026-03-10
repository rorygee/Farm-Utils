package com.farmutils.calc;

import com.farmutils.model.AllotmentType;
import com.farmutils.model.AnimaType;
import com.farmutils.model.FlowerType;
import com.farmutils.model.HerbType;
import com.farmutils.model.PatchId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CalcCatalogue
{
    private static final Map<String, Map<String, CalcCropDefinition>> CROPS_BY_GROUP = new LinkedHashMap<>();
    private static final List<CalcModifierDefinition> MODIFIERS = new ArrayList<>();
    private static final Map<PatchId, CalcPatchModifierDefinition> PATCH_MODIFIERS = new LinkedHashMap<>();
    private static final CalcHarvestLivesRules HARVEST_LIVES_RULES;

    static
    {
        final Map<CalcCompostTier, Integer> compostBonuses = new EnumMap<>(CalcCompostTier.class);
        compostBonuses.put(CalcCompostTier.NONE, 0);
        compostBonuses.put(CalcCompostTier.COMPOST, 1);
        compostBonuses.put(CalcCompostTier.SUPERCOMPOST, 2);
        compostBonuses.put(CalcCompostTier.ULTRACOMPOST, 3);
        HARVEST_LIVES_RULES = new CalcHarvestLivesRules(
                3,
                compostBonuses,
                0.10d,
                0.05d,
                0.05d,
                10,
                17,
                25,
                10,
                "(1 + floor((ctsLow * (99 - level)) / 98 + (ctsHigh * (level - 1)) / 98 + 0.5)) / 256",
                "lives / (1 - chanceToSave)"
        );

        registerCrops();
        registerModifiers();
        registerPatchModifiers();
    }

    private CalcCatalogue()
    {
    }

    public static String[] cropNamesForGroup(final String group)
    {
        final Map<String, CalcCropDefinition> byName = CROPS_BY_GROUP.get(group);
        if (byName == null)
        {
            return new String[0];
        }
        return byName.keySet().toArray(new String[0]);
    }

    public static String[] currentUiCropNamesForGroup(final String group)
    {
        final Map<String, CalcCropDefinition> byName = CROPS_BY_GROUP.get(group);
        if (byName == null)
        {
            return new String[0];
        }

        final List<String> names = new ArrayList<>();
        for (final CalcCropDefinition crop : byName.values())
        {
            if (crop == null)
            {
                continue;
            }

            final CalcYieldProfile yieldProfile = crop.getYieldProfile();
            if (yieldProfile != null && yieldProfile.getConfidenceTag() == CalcYieldConfidenceTag.EXCLUDED)
            {
                continue;
            }
            names.add(crop.getCropName());
        }
        return names.toArray(new String[0]);
    }

    public static CalcCropDefinition cropFor(final String group, final String cropName)
    {
        final Map<String, CalcCropDefinition> byName = CROPS_BY_GROUP.get(group);
        return byName == null ? null : byName.get(cropName);
    }

    public static Integer baselineYieldFor(final String group, final String cropName)
    {
        return baselineYieldFor(group, cropName, CalcCompostTier.NONE);
    }

    public static Integer baselineYieldFor(final String group, final String cropName, final CalcCompostTier compostTier)
    {
        final CalcCropDefinition crop = cropFor(group, cropName);
        if (crop == null)
        {
            return null;
        }

        final CalcYieldProfile yieldProfile = crop.getYieldProfile();
        final CalcSimpleModel simpleModel = crop.getSimpleModel();
        final CalcCompostTier tier = compostTier == null ? CalcCompostTier.NONE : compostTier;

        if (yieldProfile == null || yieldProfile.getType() == null)
        {
            return simpleModel == null ? null : simpleModel.getFlatYield();
        }

        if (yieldProfile.getFlatYield() != null)
        {
            return yieldProfile.getFlatYield();
        }

        switch (yieldProfile.getType())
        {
            case HARVEST_LIVES:
                if ("Herb".equals(group) || "Allotment".equals(group) || "Hops".equals(group))
                {
                    final int base = simpleModel == null ? HARVEST_LIVES_RULES.getBaseLives() : simpleModel.getFlatYield();
                    return base + HARVEST_LIVES_RULES.getCompostLifeBonus().getOrDefault(tier, 0);
                }
                if ("Celastrus".equals(group) || "Seaweed".equals(group))
                {
                    return HARVEST_LIVES_RULES.getBaseLives() + HARVEST_LIVES_RULES.getCompostLifeBonus().getOrDefault(tier, 0);
                }
                return HARVEST_LIVES_RULES.getBaseLives();
            case REGROWING_PICK:
                if ("Fruit tree".equals(group) || "Calquat".equals(group))
                {
                    return 6;
                }
                if ("Bush".equals(group))
                {
                    return 4;
                }
                if ("Cactus".equals(group))
                {
                    return "Potato cactus".equals(cropName) ? 7 : 3;
                }
                if ("Grapes".equals(group))
                {
                    return 10;
                }
                return 1;
            case LIMPWURT_SCALING:
                return 2;
            case FIXED_BATCH:
                if ("Mushroom".equals(group))
                {
                    return 6;
                }
                if ("Belladonna".equals(group))
                {
                    return 3;
                }
                return 1;
            case FLAT:
                return 1;
            case CORAL_CHAIN:
                return 1;
            case CRYSTAL_TREE_COMPOST_BANDED:
                switch (tier)
                {
                    case COMPOST:
                        return 20;
                    case SUPERCOMPOST:
                        return 24;
                    case ULTRACOMPOST:
                        return 28;
                    case NONE:
                    default:
                        return 16;
                }
            case CHECK_HEALTH_ONLY:
            case CHECK_HEALTH_THEN_CHOP:
                return 0;
            default:
                return null;
        }
    }

    public static CalcExpectedYieldResult expectedYieldFor(
            final String group,
            final String cropName,
            final CalcCompostTier compostTier,
            final Integer visibleFarmingLevel,
            final Integer effectiveFarmingLevel,
            final boolean magicSecateurs,
            final boolean farmingCape,
            final boolean attas,
            final int diaryBonus)
    {
        final CalcCropDefinition crop = cropFor(group, cropName);
        if (crop == null)
        {
            return CalcExpectedYieldResult.unresolved(CalcYieldConfidenceTag.EXCLUDED, "Calc metadata is missing for the selected crop.");
        }
        return expectedYieldFor(crop, compostTier, visibleFarmingLevel, effectiveFarmingLevel, magicSecateurs, farmingCape, attas, diaryBonus);
    }

    public static CalcExpectedYieldResult expectedYieldFor(
            final CalcCropDefinition crop,
            final CalcCompostTier compostTier,
            final Integer visibleFarmingLevel,
            final Integer effectiveFarmingLevel,
            final boolean magicSecateurs,
            final boolean farmingCape,
            final boolean attas,
            final int diaryBonus)
    {
        if (crop == null)
        {
            return CalcExpectedYieldResult.unresolved(CalcYieldConfidenceTag.EXCLUDED, "Calc metadata is missing for the selected crop.");
        }

        final CalcYieldProfile yieldProfile = crop.getYieldProfile();
        final CalcYieldConfidenceTag confidenceTag = yieldProfile == null || yieldProfile.getConfidenceTag() == null
                ? CalcYieldConfidenceTag.EXACT
                : yieldProfile.getConfidenceTag();
        if (confidenceTag == CalcYieldConfidenceTag.EXCLUDED)
        {
            return CalcExpectedYieldResult.unresolved(confidenceTag, "This crop is intentionally excluded from the calc panel for now.");
        }

        final Integer fallbackYield = baselineYieldFor(crop.getGroup(), crop.getCropName(), compostTier);
        if (yieldProfile == null || yieldProfile.getType() == null)
        {
            return fallbackYield == null
                    ? CalcExpectedYieldResult.unresolved(confidenceTag, "Yield metadata is unresolved for the selected crop.")
                    : CalcExpectedYieldResult.resolved(fallbackYield.doubleValue(), confidenceTag, yieldModelNote(crop, fallbackYield.doubleValue(), null));
        }

        switch (yieldProfile.getType())
        {
            case HARVEST_LIVES:
                return harvestLivesExpectedYield(crop, yieldProfile, compostTier, effectiveFarmingLevel, magicSecateurs, farmingCape, attas, diaryBonus, confidenceTag);
            case LIMPWURT_SCALING:
                return limpwurtExpectedYield(crop, yieldProfile, effectiveFarmingLevel, attas, confidenceTag);
            case SPECIAL_CASE:
                if ("Cactus".equals(crop.getGroup()) && "Cactus".equals(crop.getCropName()))
                {
                    return cactusExpectedYield(crop, visibleFarmingLevel, effectiveFarmingLevel, confidenceTag);
                }
                break;
            default:
                break;
        }

        return fallbackYield == null
                ? CalcExpectedYieldResult.unresolved(confidenceTag, "Yield metadata is unresolved for the selected crop.")
                : CalcExpectedYieldResult.resolved(fallbackYield.doubleValue(), confidenceTag, yieldModelNote(crop, fallbackYield.doubleValue(), null));
    }

    private static CalcExpectedYieldResult harvestLivesExpectedYield(
            final CalcCropDefinition crop,
            final CalcYieldProfile yieldProfile,
            final CalcCompostTier compostTier,
            final Integer effectiveFarmingLevel,
            final boolean magicSecateurs,
            final boolean farmingCape,
            final boolean attas,
            final int diaryBonus,
            final CalcYieldConfidenceTag confidenceTag)
    {
        if (yieldProfile.getCtsLow() == null || yieldProfile.getCtsHigh() == null)
        {
            return CalcExpectedYieldResult.unresolved(confidenceTag, "CTS constants are unresolved for the selected crop.");
        }

        final Integer levelValue = effectiveFarmingLevel;
        if (levelValue == null || levelValue <= 0)
        {
            return CalcExpectedYieldResult.unresolved(confidenceTag, "Current Farming level is unavailable for harvest-lives modelling.");
        }

        final int level = clamp(levelValue, 1, 99);
        final int baseCts = interpolateCts(yieldProfile.getCtsLow(), yieldProfile.getCtsHigh(), level);
        int boostedCts = baseCts;

        double itemBonus = 0.0d;
        if (magicSecateurs && yieldProfile.supportsSecateurs())
        {
            itemBonus += HARVEST_LIVES_RULES.getSecateursItemBonus();
        }
        if (farmingCape && yieldProfile.supportsFarmingCape())
        {
            itemBonus += HARVEST_LIVES_RULES.getFarmingCapeItemBonus();
        }
        if (itemBonus > 0.0d)
        {
            boostedCts = (int) Math.floor(boostedCts * (1.0d + itemBonus));
        }
        if (diaryBonus > 0 && yieldProfile.supportsDiaryBonus())
        {
            boostedCts += diaryBonus;
        }
        if (attas && yieldProfile.supportsAttas())
        {
            boostedCts = (int) Math.floor(boostedCts * (1.0d + HARVEST_LIVES_RULES.getAttasBonus()));
        }

        final int lives = HARVEST_LIVES_RULES.getBaseLives() + HARVEST_LIVES_RULES.getCompostLifeBonus().getOrDefault(compostTier == null ? CalcCompostTier.NONE : compostTier, 0);
        final double chanceToSave = (1.0d + boostedCts) / 256.0d;
        final double expectedYield = lives / (1.0d - chanceToSave);
        return CalcExpectedYieldResult.resolved(expectedYield, confidenceTag, yieldModelNote(crop, expectedYield,
                "Yield: standard · L" + level + " · " + lives + " lives"));
    }

    private static CalcExpectedYieldResult limpwurtExpectedYield(
            final CalcCropDefinition crop,
            final CalcYieldProfile yieldProfile,
            final Integer effectiveFarmingLevel,
            final boolean attas,
            final CalcYieldConfidenceTag confidenceTag)
    {
        final Integer levelValue = effectiveFarmingLevel;
        if (levelValue == null || levelValue <= 0)
        {
            return CalcExpectedYieldResult.unresolved(confidenceTag, "Current Farming level is unavailable for limpwurt yield modelling.");
        }

        final int level = Math.max(1, levelValue);
        double totalBonus = 0.0d;
        for (int roll = 0; roll < level; roll++)
        {
            final double scaledRoll = attas && yieldProfile.supportsAttas()
                    ? Math.floor(roll * (1.0d + HARVEST_LIVES_RULES.getAttasBonus()))
                    : roll;
            totalBonus += Math.floor(scaledRoll / 10.0d);
        }

        final double expectedYield = 3.0d + (totalBonus / level);
        return CalcExpectedYieldResult.resolved(expectedYield, confidenceTag,
                yieldModelNote(crop, expectedYield, "Limpwurt · eff " + level + " · min 3 · no compost"));
    }

    private static CalcExpectedYieldResult cactusExpectedYield(
            final CalcCropDefinition crop,
            final Integer visibleFarmingLevel,
            final Integer effectiveFarmingLevel,
            final CalcYieldConfidenceTag confidenceTag)
    {
        final Integer levelValue = effectiveFarmingLevel != null && effectiveFarmingLevel > 0 ? effectiveFarmingLevel : visibleFarmingLevel;
        if (levelValue == null || levelValue <= 0)
        {
            return CalcExpectedYieldResult.unresolved(confidenceTag, "Current Farming level is unavailable for cactus yield modelling.");
        }

        final int level = clamp(levelValue, 55, 99);
        final double useChance = 0.75d + ((level - 55) * (0.30d - 0.75d) / 44.0d);
        final double expectedYield = 3.0d / useChance;
        return CalcExpectedYieldResult.resolved(expectedYield, confidenceTag,
                yieldModelNote(crop, expectedYield, "Cactus · L" + level + " · 3 lives · no compost"));
    }

    private static int interpolateCts(final int ctsLow, final int ctsHigh, final int level)
    {
        final double interpolated = ((ctsLow * (99 - level)) / 98.0d) + ((ctsHigh * (level - 1)) / 98.0d);
        return (int) Math.floor(interpolated + 0.5d);
    }

    private static int clamp(final int value, final int minimum, final int maximum)
    {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String yieldModelNote(final CalcCropDefinition crop, final double expectedYield, final String baseNote)
    {
        final List<String> notes = new ArrayList<>();
        if (baseNote != null && !baseNote.trim().isEmpty())
        {
            notes.add(baseNote.trim());
        }

        if (crop != null && crop.getYieldProfile() != null)
        {
            final CalcYieldConfidenceTag tag = crop.getYieldProfile().getConfidenceTag();
            if (tag == CalcYieldConfidenceTag.ESTIMATED)
            {
                notes.add("Est.");
            }
            else if (tag == CalcYieldConfidenceTag.DERIVED)
            {
                notes.add("Derived.");
            }
            else if (tag == CalcYieldConfidenceTag.SPECIAL_CASE)
            {
                notes.add("Special-case.");
            }
        }

        if (notes.isEmpty())
        {
            return null;
        }
        return String.join(" · ", notes);
    }

    public static CalcSimpleModel simpleModelFor(final String group, final String cropName)
    {
        final CalcCropDefinition crop = cropFor(group, cropName);
        return crop == null ? null : crop.getSimpleModel();
    }

    public static List<CalcModifierDefinition> modifiers()
    {
        return Collections.unmodifiableList(MODIFIERS);
    }

    public static CalcPatchModifierDefinition patchModifierFor(final PatchId patchId)
    {
        return PATCH_MODIFIERS.get(patchId);
    }

    public static CalcHarvestLivesRules harvestLivesRules()
    {
        return HARVEST_LIVES_RULES;
    }

    public static boolean supportsCompostForGroup(final String group)
    {
        if (group == null)
        {
            return false;
        }

        switch (group)
        {
            case "Herb":
            case "Allotment":
            case "Flower":
            case "Hops":
            case "Seaweed":
            case "Celastrus":
            case "Crystal tree":
            case "Belladonna":
            case "Cactus":
            case "Bush":
            case "Tree":
            case "Fruit tree":
            case "Calquat":
            case "Hardwood":
            case "Redwood":
            case "Spirit tree":
                return true;
            default:
                return false;
        }
    }

    public static CalcItemRef compostItemFor(final CalcCompostTier tier)
    {
        if (tier == null)
        {
            return null;
        }

        switch (tier)
        {
            case COMPOST:
                return tradeableItem("Compost");
            case SUPERCOMPOST:
                return tradeableItem("Supercompost");
            case ULTRACOMPOST:
                return tradeableItem("Ultracompost");
            case NONE:
            default:
                return null;
        }
    }

    private static void registerCrops()
    {
        final CalcItemRef compost = tradeableItem("Compost");
        final CalcItemRef giantSeaweed = tradeableItem("Giant seaweed");
        final CalcItemRef filledPlantPot = tradeableItem("Filled plant pot");

        // Herbs
        register(crop("Herb", "Guam", tradeableItem("Guam seed", 5291),
                List.of(stack(tradeableItem("Guam seed", 5291), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy guam leaf", HerbType.GUAM.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(11.0, 12.5, null),
                harvestLives(25, 80, true, true, true, true, "Classic herb harvest-lives crop."),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5291, HerbType.GUAM.getGrimyItemId(), 11.0, 12.5, 1, 5)));
        register(crop("Herb", "Marrentill", tradeableItem("Marrentill seed", 5292),
                List.of(stack(tradeableItem("Marrentill seed", 5292), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy marrentill", HerbType.MARRENTILL.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(13.5, 15.0, null), harvestLives(28, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5292, HerbType.MARRENTILL.getGrimyItemId(), 13.5, 15.0, 1, 5)));
        register(crop("Herb", "Tarromin", tradeableItem("Tarromin seed", 5293),
                List.of(stack(tradeableItem("Tarromin seed", 5293), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy tarromin", HerbType.TARROMIN.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(16.0, 18.0, null), harvestLives(31, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5293, HerbType.TARROMIN.getGrimyItemId(), 16.0, 18.0, 1, 5)));
        register(crop("Herb", "Harralander", tradeableItem("Harralander seed", 5294),
                List.of(stack(tradeableItem("Harralander seed", 5294), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy harralander", HerbType.HARRALANDER.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(21.5, 24.0, null), harvestLives(36, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5294, HerbType.HARRALANDER.getGrimyItemId(), 21.5, 24.0, 1, 5)));
        register(crop("Herb", "Ranarr", tradeableItem("Ranarr seed", 5295),
                List.of(stack(tradeableItem("Ranarr seed", 5295), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy ranarr weed", HerbType.RANARR.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(27.0, 30.5, null), harvestLives(39, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5295, HerbType.RANARR.getGrimyItemId(), 27.0, 30.5, 1, 5)));
        register(crop("Herb", "Toadflax", tradeableItem("Toadflax seed", 5296),
                List.of(stack(tradeableItem("Toadflax seed", 5296), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy toadflax", HerbType.TOADFLAX.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(34.0, 38.5, null), harvestLives(43, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5296, HerbType.TOADFLAX.getGrimyItemId(), 34.0, 38.5, 1, 5)));
        register(crop("Herb", "Irit", tradeableItem("Irit seed", 5297),
                List.of(stack(tradeableItem("Irit seed", 5297), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy irit leaf", HerbType.IRIT.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(43.0, 48.5, null), harvestLives(46, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5297, HerbType.IRIT.getGrimyItemId(), 43.0, 48.5, 1, 5)));
        register(crop("Herb", "Avantoe", tradeableItem("Avantoe seed", 5298),
                List.of(stack(tradeableItem("Avantoe seed", 5298), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy avantoe", HerbType.AVANTOE.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(54.5, 61.5, null), harvestLives(50, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5298, HerbType.AVANTOE.getGrimyItemId(), 54.5, 61.5, 1, 5)));
        register(crop("Herb", "Huasca", tradeableItem("Huasca seed"),
                List.of(stack(tradeableItem("Huasca seed"), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy huasca", HerbType.HUASCA.getGrimyItemId()), CalcOutputRole.PRIMARY, null, "Treated as herb-family harvest-lives crop for now.")),
                xp(null, null, null), harvestLives(59, 80, true, true, true, true, "Yield model aligns with herbs; provisional per-crop constants are estimated.", CalcYieldConfidenceTag.ESTIMATED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.USER_CONFIRMED, null,
                null));
        register(crop("Herb", "Kwuarm", tradeableItem("Kwuarm seed", 5299),
                List.of(stack(tradeableItem("Kwuarm seed", 5299), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy kwuarm", HerbType.KWUARM.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(69.0, 78.0, null), harvestLives(54, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5299, HerbType.KWUARM.getGrimyItemId(), 69.0, 78.0, 1, 5)));
        register(crop("Herb", "Snapdragon", tradeableItem("Snapdragon seed", 5300),
                List.of(stack(tradeableItem("Snapdragon seed", 5300), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy snapdragon", HerbType.SNAPDRAGON.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(87.5, 98.5, null), harvestLives(57, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5300, HerbType.SNAPDRAGON.getGrimyItemId(), 87.5, 98.5, 1, 5)));
        register(crop("Herb", "Cadantine", tradeableItem("Cadantine seed", 5301),
                List.of(stack(tradeableItem("Cadantine seed", 5301), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy cadantine", HerbType.CADANTINE.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(106.5, 120.0, null), harvestLives(60, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5301, HerbType.CADANTINE.getGrimyItemId(), 106.5, 120.0, 1, 5)));
        register(crop("Herb", "Lantadyme", tradeableItem("Lantadyme seed", 5302),
                List.of(stack(tradeableItem("Lantadyme seed", 5302), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy lantadyme", HerbType.LANTADYME.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(134.5, 151.5, null), harvestLives(64, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5302, HerbType.LANTADYME.getGrimyItemId(), 134.5, 151.5, 1, 5)));
        register(crop("Herb", "Dwarf weed", tradeableItem("Dwarf weed seed", 5303),
                List.of(stack(tradeableItem("Dwarf weed seed", 5303), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy dwarf weed", HerbType.DWARF_WEED.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(170.5, 192.0, null), harvestLives(67, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5303, HerbType.DWARF_WEED.getGrimyItemId(), 170.5, 192.0, 1, 5)));
        register(crop("Herb", "Torstol", tradeableItem("Torstol seed", 5304),
                List.of(stack(tradeableItem("Torstol seed", 5304), 1)), List.of(), null,
                List.of(output(tradeableItem("Grimy torstol", HerbType.TORSTOL.getGrimyItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(199.5, 224.5, null), harvestLives(71, 80, true, true, true, true, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5304, HerbType.TORSTOL.getGrimyItemId(), 199.5, 224.5, 1, 5)));

        // Allotments
        register(crop("Allotment", "Potato", tradeableItem("Potato seed", 5318),
                List.of(stack(tradeableItem("Potato seed", 5318), 3)), List.of(stack(compost, 2)), null,
                List.of(output(tradeableItem("Potato", AllotmentType.POTATO.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(8.0, 9.0, null), harvestLives(101, 180, true, false, true, false, null),
                List.of("Marigold"), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5318, AllotmentType.POTATO.getItemId(), 8.0, 9.0, 3, 10)));
        register(crop("Allotment", "Onion", tradeableItem("Onion seed", 5319),
                List.of(stack(tradeableItem("Onion seed", 5319), 3)), List.of(stack(tradeableItem("Sack of potatoes"), 1)), null,
                List.of(output(tradeableItem("Onion", AllotmentType.ONION.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(9.5, 10.5, null), harvestLives(103, 180, true, false, true, false, null),
                List.of("Marigold"), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5319, AllotmentType.ONION.getItemId(), 9.5, 10.5, 3, 10)));
        register(crop("Allotment", "Cabbage", tradeableItem("Cabbage seed", 5324),
                List.of(stack(tradeableItem("Cabbage seed", 5324), 3)), List.of(stack(tradeableItem("Sack of onions"), 1)), null,
                List.of(output(tradeableItem("Cabbage", AllotmentType.CABBAGE.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(10.0, 11.5, null), harvestLives(107, 180, true, false, true, false, null),
                List.of("Rosemary"), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5324, AllotmentType.CABBAGE.getItemId(), 10.0, 11.5, 3, 10)));
        register(crop("Allotment", "Tomato", tradeableItem("Tomato seed", 5322),
                List.of(stack(tradeableItem("Tomato seed", 5322), 3)), List.of(stack(tradeableItem("Sack of cabbages"), 2)), null,
                List.of(output(tradeableItem("Tomato", AllotmentType.TOMATO.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(12.5, 14.0, null), harvestLives(112, 180, true, false, true, false, null),
                List.of("Marigold"), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5322, AllotmentType.TOMATO.getItemId(), 12.5, 14.0, 3, 10)));
        register(crop("Allotment", "Sweetcorn", tradeableItem("Sweetcorn seed", 5320),
                List.of(stack(tradeableItem("Sweetcorn seed", 5320), 3)), List.of(stack(tradeableItem("Jute fibre"), 10)), null,
                List.of(output(tradeableItem("Sweetcorn", AllotmentType.SWEETCORN.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(17.0, 19.0, null), harvestLives(88, 180, true, false, true, false, null),
                List.of("Scarecrow"), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5320, AllotmentType.SWEETCORN.getItemId(), 17.0, 19.0, 3, 10)));
        register(crop("Allotment", "Strawberry", tradeableItem("Strawberry seed", 5323),
                List.of(stack(tradeableItem("Strawberry seed", 5323), 3)), List.of(stack(tradeableItem("Basket of apples"), 1)), null,
                List.of(output(tradeableItem("Strawberry", AllotmentType.STRAWBERRY.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(26.0, 29.0, null), harvestLives(103, 180, true, false, true, false, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5323, AllotmentType.STRAWBERRY.getItemId(), 26.0, 29.0, 3, 10)));
        register(crop("Allotment", "Watermelon", tradeableItem("Watermelon seed", 5321),
                List.of(stack(tradeableItem("Watermelon seed", 5321), 3)), List.of(stack(tradeableItem("Curry leaf"), 10)), null,
                List.of(output(tradeableItem("Watermelon", AllotmentType.WATERMELON.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(48.5, 54.5, null), harvestLives(126, 180, true, false, true, false, null),
                List.of("Nasturtium"), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5321, AllotmentType.WATERMELON.getItemId(), 48.5, 54.5, 3, 10)));
        register(crop("Allotment", "Snape grass", tradeableItem("Snape grass seed", 22879),
                List.of(stack(tradeableItem("Snape grass seed", 22879), 3)), List.of(stack(tradeableItem("Jangerberries"), 5)), null,
                List.of(output(tradeableItem("Snape grass", AllotmentType.SNAPE_GRASS.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(82.0, 82.0, null), harvestLives(148, 195, true, false, true, false, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.USER_CONFIRMED, null,
                simple(22879, AllotmentType.SNAPE_GRASS.getItemId(), 82.0, 82.0, 3, 10)));

        // Flowers
        register(crop("Flower", "Marigold", tradeableItem("Marigold seed", 5096),
                List.of(stack(tradeableItem("Marigold seed", 5096), 1)), List.of(), null,
                List.of(output(tradeableItem("Marigolds", FlowerType.MARIGOLD.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(8.5, 47.0, null), flat(1, null),
                List.of("Potato", "Onion", "Tomato"), EnumSet.of(CalcCropFlag.PROTECTS_NEIGHBOURS), CalcSourceConfidence.HIGH, null,
                simple(5096, FlowerType.MARIGOLD.getItemId(), 8.5, 47.0, 1, 1)));
        register(crop("Flower", "Rosemary", tradeableItem("Rosemary seed", 5097),
                List.of(stack(tradeableItem("Rosemary seed", 5097), 1)), List.of(), null,
                List.of(output(tradeableItem("Rosemary", FlowerType.ROSEMARY.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(12.0, 66.5, null), flat(1, null),
                List.of("Cabbage"), EnumSet.of(CalcCropFlag.PROTECTS_NEIGHBOURS), CalcSourceConfidence.HIGH, null,
                simple(5097, FlowerType.ROSEMARY.getItemId(), 12.0, 66.5, 1, 1)));
        register(crop("Flower", "Nasturtium", tradeableItem("Nasturtium seed", 5098),
                List.of(stack(tradeableItem("Nasturtium seed", 5098), 1)), List.of(), null,
                List.of(output(tradeableItem("Nasturtiums", FlowerType.NASTURTIUM.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(19.5, 111.0, null), flat(1, null),
                List.of("Watermelon", "Wildblood"), EnumSet.of(CalcCropFlag.PROTECTS_NEIGHBOURS), CalcSourceConfidence.HIGH, null,
                simple(5098, FlowerType.NASTURTIUM.getItemId(), 19.5, 111.0, 1, 1)));
        register(crop("Flower", "Woad", tradeableItem("Woad seed", 5099),
                List.of(stack(tradeableItem("Woad seed", 5099), 1)), List.of(), null,
                List.of(output(tradeableItem("Woad leaf", FlowerType.WOAD.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(20.5, 38.5, null), flat(1, null),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5099, FlowerType.WOAD.getItemId(), 20.5, 38.5, 1, 1)));
        register(crop("Flower", "Limpwurt", tradeableItem("Limpwurt seed", 5100),
                List.of(stack(tradeableItem("Limpwurt seed", 5100), 1)), List.of(), null,
                List.of(output(tradeableItem("Limpwurt root", FlowerType.LIMPWURT.getItemId()), CalcOutputRole.PRIMARY, null, null)),
                xp(21.5, 40.0, null),
                new CalcYieldProfile(CalcYieldModelType.LIMPWURT_SCALING, null, null, null, false, false, true, false, CalcYieldConfidenceTag.SPECIAL_CASE,
                        "Minimum yield three; uses the separate limpwurt random-bonus model. Compost does not affect yield."),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.USER_CONFIRMED, null,
                simple(5100, FlowerType.LIMPWURT.getItemId(), 21.5, 40.0, 1, 1)));
        register(crop("Flower", "White lily", tradeableItem("White lily seed"),
                List.of(stack(tradeableItem("White lily seed"), 1)), List.of(), null,
                List.of(output(tradeableItem("White lily", FlowerType.WHITE_LILY.getItemId()), CalcOutputRole.PRIMARY, null, "Harvests a single white lily.")),
                xp(42.0, 250.0, null), flat(1, "Protects all neighbouring allotments from disease."),
                List.of("All neighbouring allotments"), EnumSet.of(CalcCropFlag.PROTECTS_NEIGHBOURS), CalcSourceConfidence.USER_CONFIRMED, null,
                null));
        register(crop("Flower", "Scarecrow", tradeableItem("Scarecrow", FlowerType.SCARECROW.getItemId()),
                List.of(stack(tradeableItem("Scarecrow", FlowerType.SCARECROW.getItemId()), 1)), List.of(), null,
                List.of(output(tradeableItem("Scarecrow", FlowerType.SCARECROW.getItemId()), CalcOutputRole.PRIMARY, null, "Occupies the flower patch slot rather than behaving like a normal flower.")),
                xp(null, null, null), flat(null, "Protects sweetcorn from birds."),
                List.of("Sweetcorn"), EnumSet.of(CalcCropFlag.PROTECTS_NEIGHBOURS), CalcSourceConfidence.HIGH, null,
                null));

        // Hops
        register(crop("Hops", "Barley", tradeableItem("Barley seed", 5305),
                List.of(stack(tradeableItem("Barley seed", 5305), 4)), List.of(stack(compost, 3)), null,
                List.of(output(tradeableItem("Barley", net.runelite.api.gameval.ItemID.BARLEY), CalcOutputRole.PRIMARY, null, null)),
                xp(8.5, 9.5, null), harvestLives(101, 180, true, false, true, false, null, CalcYieldConfidenceTag.DERIVED),
                List.of("Jute"), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5305, net.runelite.api.gameval.ItemID.BARLEY, 8.5, 9.5, 4, 1)));
        register(crop("Hops", "Hammerstone", tradeableItem("Hammerstone seed", 5307),
                List.of(stack(tradeableItem("Hammerstone seed", 5307), 4)), List.of(stack(tradeableItem("Marigolds", FlowerType.MARIGOLD.getItemId()), 1)), null,
                List.of(output(tradeableItem("Hammerstone hops", net.runelite.api.gameval.ItemID.HAMMERSTONE_HOPS), CalcOutputRole.PRIMARY, null, null)),
                xp(9.0, 10.0, null), harvestLives(103, 180, true, false, true, false, null, CalcYieldConfidenceTag.DERIVED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5307, net.runelite.api.gameval.ItemID.HAMMERSTONE_HOPS, 9.0, 10.0, 4, 1)));
        register(crop("Hops", "Asgarnian", tradeableItem("Asgarnian seed", 5308),
                List.of(stack(tradeableItem("Asgarnian seed", 5308), 4)), List.of(stack(tradeableItem("Sack of onions"), 1)), null,
                List.of(output(tradeableItem("Asgarnian hops", net.runelite.api.gameval.ItemID.ASGARNIAN_HOPS), CalcOutputRole.PRIMARY, null, null)),
                xp(10.5, 12.0, null), harvestLives(107, 180, true, false, true, false, null, CalcYieldConfidenceTag.DERIVED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5308, net.runelite.api.gameval.ItemID.ASGARNIAN_HOPS, 10.5, 12.0, 4, 1)));
        register(crop("Hops", "Jute", tradeableItem("Jute seed", 5306),
                List.of(stack(tradeableItem("Jute seed", 5306), 4)), List.of(stack(tradeableItem("Barley malt"), 6)), null,
                List.of(output(tradeableItem("Jute fibre", net.runelite.api.gameval.ItemID.JUTE_FIBRE), CalcOutputRole.PRIMARY, null, null)),
                xp(13.0, 14.5, null), harvestLives(113, 180, true, false, true, false, null, CalcYieldConfidenceTag.DERIVED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5306, net.runelite.api.gameval.ItemID.JUTE_FIBRE, 13.0, 14.5, 4, 1)));
        register(crop("Hops", "Yanillian", tradeableItem("Yanillian seed", 5309),
                List.of(stack(tradeableItem("Yanillian seed", 5309), 4)), List.of(stack(tradeableItem("Basket of tomatoes"), 1)), null,
                List.of(output(tradeableItem("Yanillian hops", net.runelite.api.gameval.ItemID.YANILLIAN_HOPS), CalcOutputRole.PRIMARY, null, null)),
                xp(14.5, 16.0, null), harvestLives(115, 180, true, false, true, false, null, CalcYieldConfidenceTag.DERIVED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5309, net.runelite.api.gameval.ItemID.YANILLIAN_HOPS, 14.5, 16.0, 4, 1)));
        register(crop("Hops", "Krandorian", tradeableItem("Krandorian seed", 5310),
                List.of(stack(tradeableItem("Krandorian seed", 5310), 4)), List.of(stack(tradeableItem("Sack of cabbages"), 3)), null,
                List.of(output(tradeableItem("Krandorian hops", net.runelite.api.gameval.ItemID.KRANDORIAN_HOPS), CalcOutputRole.PRIMARY, null, null)),
                xp(17.5, 19.5, null), harvestLives(120, 180, true, false, true, false, null, CalcYieldConfidenceTag.DERIVED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5310, net.runelite.api.gameval.ItemID.KRANDORIAN_HOPS, 17.5, 19.5, 4, 1)));
        register(crop("Hops", "Wildblood", tradeableItem("Wildblood seed", 5311),
                List.of(stack(tradeableItem("Wildblood seed", 5311), 4)), List.of(stack(tradeableItem("Nasturtiums", FlowerType.NASTURTIUM.getItemId()), 1)), null,
                List.of(output(tradeableItem("Wildblood hops", net.runelite.api.gameval.ItemID.WILDBLOOD_HOPS), CalcOutputRole.PRIMARY, null, null)),
                xp(23.0, 26.0, null), harvestLives(126, 180, true, false, true, false, null, CalcYieldConfidenceTag.DERIVED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null,
                simple(5311, net.runelite.api.gameval.ItemID.WILDBLOOD_HOPS, 23.0, 26.0, 4, 1)));

        // Bushes
        register(crop("Bush", "Redberry", tradeableItem("Redberry seed"),
                List.of(stack(tradeableItem("Redberry seed"), 1)), List.of(stack(tradeableItem("Sack of cabbages"), 4)), null,
                List.of(output(tradeableItem("Redberries"), CalcOutputRole.PRIMARY, null, null)),
                xp(11.5, 4.0, 64.0), regrow("Bush yield regrows over time after harvesting."),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK), CalcSourceConfidence.HIGH, null, null));
        register(crop("Bush", "Cadavaberry", tradeableItem("Cadavaberry seed"),
                List.of(stack(tradeableItem("Cadavaberry seed"), 1)), List.of(stack(tradeableItem("Basket of tomatoes"), 3)), null,
                List.of(output(tradeableItem("Cadava berries"), CalcOutputRole.PRIMARY, null, null)),
                xp(18.0, 7.0, 102.5), regrow(null),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK), CalcSourceConfidence.HIGH, null, null));
        register(crop("Bush", "Dwellberry", tradeableItem("Dwellberry seed"),
                List.of(stack(tradeableItem("Dwellberry seed"), 1)), List.of(stack(tradeableItem("Basket of strawberries"), 3)), null,
                List.of(output(tradeableItem("Dwellberries"), CalcOutputRole.PRIMARY, null, null)),
                xp(31.5, 12.0, 177.5), regrow(null),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK), CalcSourceConfidence.HIGH, null, null));
        register(crop("Bush", "Jangerberry", tradeableItem("Jangerberry seed"),
                List.of(stack(tradeableItem("Jangerberry seed"), 1)), List.of(stack(tradeableItem("Watermelon"), 6)), null,
                List.of(output(tradeableItem("Jangerberries"), CalcOutputRole.PRIMARY, null, null)),
                xp(50.0, 19.0, 285.0), regrow(null),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK), CalcSourceConfidence.HIGH, null, null));
        register(crop("Bush", "Whiteberry", tradeableItem("Whiteberry seed"),
                List.of(stack(tradeableItem("Whiteberry seed"), 1)), List.of(stack(tradeableItem("Bittercap mushroom"), 8)), null,
                List.of(output(tradeableItem("White berries"), CalcOutputRole.PRIMARY, null, null)),
                xp(78.0, 29.0, 437.5), regrow(null),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK), CalcSourceConfidence.HIGH, null, null));
        register(crop("Bush", "Poison ivy", tradeableItem("Poison ivy seed"),
                List.of(stack(tradeableItem("Poison ivy seed"), 1)), List.of(), null,
                List.of(output(tradeableItem("Poison ivy berries"), CalcOutputRole.PRIMARY, null, "Poison ivy bushes do not become diseased.")),
                xp(120.0, 45.0, 675.0), regrow(null),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK, CalcCropFlag.CANNOT_BE_PROTECTED), CalcSourceConfidence.HIGH, null, null));

        // Trees
        register(treeCrop("Tree", "Oak", "Acorn", "Oak sapling", "Oak tree", List.of(stack(tradeableItem("Basket of tomatoes"), 1)),
                List.of(output(tradeableItem("Oak logs"), CalcOutputRole.PRIMARY, null, null), output(tradeableItem("Oak roots"), CalcOutputRole.SECONDARY, null, null), output(tradeableItem("Oak leaves"), CalcOutputRole.BYPRODUCT, "Pruning only", null)),
                xp(14.0, null, 467.3), CalcSourceConfidence.HIGH));
        register(treeCrop("Tree", "Willow", "Willow seed", "Willow sapling", "Willow tree", List.of(stack(tradeableItem("Basket of apples"), 1)),
                List.of(output(tradeableItem("Willow logs"), CalcOutputRole.PRIMARY, null, null), output(tradeableItem("Willow roots"), CalcOutputRole.SECONDARY, null, null), output(tradeableItem("Willow leaves"), CalcOutputRole.BYPRODUCT, "Pruning only", null)),
                xp(25.0, null, 1456.5), CalcSourceConfidence.HIGH));
        register(treeCrop("Tree", "Maple", "Maple seed", "Maple sapling", "Maple tree", List.of(stack(tradeableItem("Basket of oranges"), 1)),
                List.of(output(tradeableItem("Maple logs"), CalcOutputRole.PRIMARY, null, null), output(tradeableItem("Maple roots"), CalcOutputRole.SECONDARY, null, null), output(tradeableItem("Maple leaves"), CalcOutputRole.BYPRODUCT, "Pruning only", null)),
                xp(45.0, null, 3403.4), CalcSourceConfidence.HIGH));
        register(treeCrop("Tree", "Yew", "Yew seed", "Yew sapling", "Yew tree", List.of(stack(tradeableItem("Cactus spine"), 10)),
                List.of(output(tradeableItem("Yew logs"), CalcOutputRole.PRIMARY, null, null), output(tradeableItem("Yew roots"), CalcOutputRole.SECONDARY, null, null), output(tradeableItem("Yew leaves"), CalcOutputRole.BYPRODUCT, "Pruning only", null)),
                xp(81.0, null, 7069.9), CalcSourceConfidence.HIGH));
        register(treeCrop("Tree", "Magic", "Magic seed", "Magic sapling", "Magic tree", List.of(stack(tradeableItem("Coconut"), 25)),
                List.of(output(tradeableItem("Magic logs"), CalcOutputRole.PRIMARY, null, null), output(tradeableItem("Magic roots"), CalcOutputRole.SECONDARY, null, null), output(tradeableItem("Magic leaves"), CalcOutputRole.BYPRODUCT, "Pruning only", null)),
                xp(145.5, null, 13768.3), CalcSourceConfidence.HIGH));

        // Fruit trees
        register(treeCrop("Fruit tree", "Apple", "Apple tree seed", "Apple sapling", "Apple tree", List.of(stack(tradeableItem("Sweetcorn"), 9)),
                List.of(output(tradeableItem("Cooking apple"), CalcOutputRole.PRIMARY, null, null)), xp(22.0, 8.5, 1199.5), CalcSourceConfidence.HIGH));
        register(treeCrop("Fruit tree", "Banana", "Banana tree seed", "Banana sapling", "Banana tree", List.of(stack(tradeableItem("Basket of apples"), 4)),
                List.of(output(tradeableItem("Banana"), CalcOutputRole.PRIMARY, null, null)), xp(28.0, 10.5, 1750.5), CalcSourceConfidence.HIGH));
        register(treeCrop("Fruit tree", "Orange", "Orange tree seed", "Orange sapling", "Orange tree", List.of(stack(tradeableItem("Basket of strawberries"), 3)),
                List.of(output(tradeableItem("Orange"), CalcOutputRole.PRIMARY, null, null)), xp(35.5, 13.5, 2470.2), CalcSourceConfidence.HIGH));
        register(treeCrop("Fruit tree", "Curry", "Curry tree seed", "Curry sapling", "Curry tree", List.of(stack(tradeableItem("Basket of bananas"), 5)),
                List.of(output(tradeableItem("Curry leaf"), CalcOutputRole.PRIMARY, null, null)), xp(40.0, 15.0, 2906.9), CalcSourceConfidence.HIGH));
        register(treeCrop("Fruit tree", "Pineapple", "Pineapple seed", "Pineapple sapling", "Pineapple plant", List.of(stack(tradeableItem("Watermelon"), 10)),
                List.of(output(tradeableItem("Pineapple"), CalcOutputRole.PRIMARY, null, null)), xp(57.0, 21.5, 4605.7), CalcSourceConfidence.HIGH));
        register(treeCrop("Fruit tree", "Papaya", "Papaya tree seed", "Papaya sapling", "Papaya tree", List.of(stack(tradeableItem("Pineapple"), 10)),
                List.of(output(tradeableItem("Papaya fruit"), CalcOutputRole.PRIMARY, null, null)), xp(72.0, 27.0, 6146.4), CalcSourceConfidence.HIGH));
        register(treeCrop("Fruit tree", "Palm", "Palm tree seed", "Palm sapling", "Palm tree", List.of(stack(tradeableItem("Papaya fruit"), 15)),
                List.of(output(tradeableItem("Coconut"), CalcOutputRole.PRIMARY, null, null)), xp(110.5, 41.5, 10150.1), CalcSourceConfidence.HIGH));
        register(treeCrop("Fruit tree", "Dragonfruit", "Dragonfruit tree seed", "Dragonfruit sapling", "Dragonfruit tree", List.of(stack(tradeableItem("Coconut"), 15)),
                List.of(output(tradeableItem("Dragonfruit"), CalcOutputRole.PRIMARY, null, null)), xp(140.0, (70.0d / 6.0d), 17335.0), CalcSourceConfidence.USER_CONFIRMED));

        // Special trees and one-offs
        register(treeCrop("Hardwood", "Teak", "Teak seed", "Teak sapling", "Teak tree", List.of(stack(tradeableItem("Limpwurt root", FlowerType.LIMPWURT.getItemId()), 15)),
                List.of(output(tradeableItem("Teak logs"), CalcOutputRole.PRIMARY, null, null)), xp(35.0, null, 7290.0), CalcSourceConfidence.HIGH));
        register(treeCrop("Hardwood", "Mahogany", "Mahogany seed", "Mahogany sapling", "Mahogany tree", List.of(stack(tradeableItem("Yanillian hops", net.runelite.api.gameval.ItemID.YANILLIAN_HOPS), 25)),
                List.of(output(tradeableItem("Mahogany logs"), CalcOutputRole.PRIMARY, null, null)), xp(63.0, null, 15720.0), CalcSourceConfidence.HIGH));
        register(treeCrop("Calquat", "Calquat tree", "Calquat tree seed", "Calquat sapling", "Calquat tree", List.of(stack(tradeableItem("Poison ivy berries"), 8)),
                List.of(output(tradeableItem("Calquat fruit"), CalcOutputRole.PRIMARY, null, null)), xp(129.5, 48.5, 12096.0), CalcSourceConfidence.HIGH));
        register(treeCrop("Spirit tree", "Spirit tree", "Spirit seed", "Spirit sapling", "Spirit tree", List.of(
                stack(tradeableItem("Monkey nuts"), 5),
                stack(tradeableItem("Monkey bar"), 1),
                stack(tradeableItem("Ground tooth"), 1)
        ), List.of(), xp(199.5, null, 19301.8), CalcSourceConfidence.HIGH, CalcYieldModelType.CHECK_HEALTH_ONLY,
                "Utility tree. No harvested produce item."));
        register(treeCrop("Celastrus", "Celastrus tree", "Celastrus seed", "Celastrus sapling", "Celastrus tree", List.of(stack(tradeableItem("Potato cactus"), 8)),
                List.of(output(tradeableItem("Celastrus bark"), CalcOutputRole.PRIMARY, null, "Uses the harvest-lives yield system.")), xp(204.0, 23.5, 14130.0), CalcSourceConfidence.USER_CONFIRMED,
                harvestLives(68, 76, true, false, true, false, "Minimum 3 bark; harvest-lives constants are estimated from public yield reports.", CalcYieldConfidenceTag.ESTIMATED)));
        register(treeCrop("Redwood", "Redwood tree", "Redwood tree seed", "Redwood sapling", "Redwood tree", List.of(stack(tradeableItem("Dragonfruit"), 6)),
                List.of(output(tradeableItem("Redwood logs"), CalcOutputRole.PRIMARY, null, null)), xp(230.0, null, 22450.0), CalcSourceConfidence.USER_CONFIRMED,
                CalcYieldModelType.CHECK_HEALTH_THEN_CHOP, "Leaves from pruning intentionally omitted from standard run output modelling."));
        register(crop("Crystal tree", "Crystal tree", nonTradeableItem("Crystal sapling"),
                List.of(stack(nonTradeableItem("Crystal sapling"), 1)), List.of(),
                new CalcPropagationChain(nonTradeableItem("Crystal acorn"), tradeableItem("Filled plant pot"), nonTradeableItem("Crystal sapling"), "Users may start prep from acorn or sapling; both are non-tradeable."),
                List.of(output(nonTradeableItem("Crystal shards"), CalcOutputRole.PRIMARY, null, "Compost-banded shard output. Non-tradeable.")),
                xp(0.0, null, 13540.0),
                new CalcYieldProfile(CalcYieldModelType.CRYSTAL_TREE_COMPOST_BANDED, null, null, null, false, false, false, false, CalcYieldConfidenceTag.EXACT,
                        "Assumed shard bands: none 16-20, compost 20-24, supercompost 24-28, ultracompost 28-32."),
                List.of(), EnumSet.of(CalcCropFlag.HAS_PROPAGATION_CHAIN, CalcCropFlag.CANNOT_BE_PROTECTED, CalcCropFlag.NON_TRADEABLE_OUTPUT),
                CalcSourceConfidence.USER_CONFIRMED, "Cannot be protected; acorn, sapling, and output are non-tradeable.", null));

        // Other special patches
        register(crop("Seaweed", "Giant seaweed", tradeableItem("Seaweed spore"),
                List.of(stack(tradeableItem("Seaweed spore"), 1)), List.of(stack(tradeableItem("Numulite"), 200)), null,
                List.of(output(giantSeaweed, CalcOutputRole.PRIMARY, null, null)),
                xp(19.0, 21.0, null), harvestLives(150, 210, false, false, true, false, "Magic secateurs and farming cape do not affect yield."),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null, null));
        register(crop("Grapes", "Grapes", tradeableItem("Grape seed"),
                List.of(stack(tradeableItem("Grape seed"), 1)), List.of(), null,
                List.of(output(tradeableItem("Grapes"), CalcOutputRole.PRIMARY, null, null), output(tradeableItem("Zamorak's grapes"), CalcOutputRole.ALTERNATE, "With Bologa's blessings", null)),
                xp(0.0, 6.0, null), regrow("Alternate output mode should be handled explicitly in later logic."),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null, null));
        register(crop("Mushroom", "Bittercap mushroom", tradeableItem("Mushroom spore"),
                List.of(stack(tradeableItem("Mushroom spore"), 1)), List.of(), null,
                List.of(output(tradeableItem("Bittercap mushroom"), CalcOutputRole.PRIMARY, null, "Typically harvested in a small fixed batch.")),
                xp(61.5, 58.0, null), fixedBatch("Accessible sources indicate six mushrooms from the patch."),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null, null));
        register(crop("Cactus", "Cactus", tradeableItem("Cactus seed"),
                List.of(stack(tradeableItem("Cactus seed"), 1)), List.of(stack(tradeableItem("Cadava berries"), 6)), null,
                List.of(output(tradeableItem("Cactus spine"), CalcOutputRole.PRIMARY, null, null)),
                xp(66.5, 25.0, 374.0), new CalcYieldProfile(CalcYieldModelType.SPECIAL_CASE, null, null, null, false, false, false, false, CalcYieldConfidenceTag.SPECIAL_CASE, "Uses the cactus-specific linear life-use model. Compost, cape, and secateurs do not affect yield."),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK), CalcSourceConfidence.HIGH, null, null));
        register(crop("Cactus", "Potato cactus", tradeableItem("Potato cactus seed"),
                List.of(stack(tradeableItem("Potato cactus seed"), 1)), List.of(stack(tradeableItem("Watermelon"), 10)), null,
                List.of(output(tradeableItem("Potato cactus"), CalcOutputRole.PRIMARY, null, "Replenishes one fruit every five minutes; seven growths to refill.")),
                xp(null, null, null), regrow("Potato cactus replenishes every five minutes until fully stocked."),
                List.of(), EnumSet.of(CalcCropFlag.REPEAT_PICK), CalcSourceConfidence.USER_CONFIRMED, null, null));
        register(crop("Belladonna", "Belladonna", tradeableItem("Belladonna seed"),
                List.of(stack(tradeableItem("Belladonna seed"), 1)), List.of(), null,
                List.of(output(tradeableItem("Cave nightshade"), CalcOutputRole.PRIMARY, null, "Typically harvested as a small fixed batch.")),
                xp(91.0, (512.0d / 3.0d), null), fixedBatch("Harvests cave nightshade; handling details left to later logic.", CalcYieldConfidenceTag.EXCLUDED),
                List.of(), EnumSet.noneOf(CalcCropFlag.class), CalcSourceConfidence.HIGH, null, null));

        register(crop("Coral", "Elkhorn", tradeableItem("Elkhorn frag", null, true, true),
                List.of(stack(tradeableItem("Elkhorn frag", null, true, true), 1)), List.of(stack(giantSeaweed, 5)), null,
                List.of(output(tradeableItem("Elkhorn coral", null, true, true), CalcOutputRole.PRIMARY, null, null)),
                xp(20.5, 24.0, null), new CalcYieldProfile(CalcYieldModelType.CORAL_CHAIN, 1, null, null, false, false, false, false, CalcYieldConfidenceTag.EXACT, "Coral nursery crop chain."),
                List.of(), EnumSet.of(CalcCropFlag.SPECIAL_PROTECTION_PAYMENT), CalcSourceConfidence.USER_CONFIRMED, null, null));
        register(crop("Coral", "Pillar", tradeableItem("Pillar frag", null, true, true),
                List.of(stack(tradeableItem("Pillar frag", null, true, true), 1)), List.of(stack(tradeableItem("Elkhorn coral", null, true, true), 5)), null,
                List.of(output(tradeableItem("Pillar coral", null, true, true), CalcOutputRole.PRIMARY, null, null)),
                xp(52.5, 60.0, null), new CalcYieldProfile(CalcYieldModelType.CORAL_CHAIN, 1, null, null, false, false, false, false, CalcYieldConfidenceTag.EXACT, "Coral nursery crop chain."),
                List.of(), EnumSet.of(CalcCropFlag.SPECIAL_PROTECTION_PAYMENT), CalcSourceConfidence.USER_CONFIRMED, null, null));
        register(crop("Coral", "Umbral", tradeableItem("Umbral frag", null, true, true),
                List.of(stack(tradeableItem("Umbral frag", null, true, true), 1)), List.of(stack(tradeableItem("Pillar coral", null, true, true), 5)), null,
                List.of(output(tradeableItem("Umbral coral", null, true, true), CalcOutputRole.PRIMARY, null, null)),
                xp(136.0, 159.0, null), new CalcYieldProfile(CalcYieldModelType.CORAL_CHAIN, 1, null, null, false, false, false, false, CalcYieldConfidenceTag.EXACT, "Coral nursery crop chain."),
                List.of(), EnumSet.of(CalcCropFlag.SPECIAL_PROTECTION_PAYMENT), CalcSourceConfidence.USER_CONFIRMED, null, null));
    }

    private static void registerModifiers()
    {
        final List<String> harvestLivesGroups = List.of("Herb", "Allotment", "Hops", "Celastrus", "Seaweed");
        MODIFIERS.add(new CalcModifierDefinition("compost", "Compost", CalcModifierType.TREATMENT,
                List.of("Herb", "Allotment", "Hops", "Celastrus", "Seaweed", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Coral"),
                EnumSet.of(CalcModifierTarget.HARVEST_LIVES, CalcModifierTarget.SURVIVAL), true,
                null, null, null, null, null, null,
                "Adds one harvest life on harvest-lives crops and reduces disease risk more generally."));
        MODIFIERS.add(new CalcModifierDefinition("supercompost", "Supercompost", CalcModifierType.TREATMENT,
                List.of("Herb", "Allotment", "Hops", "Celastrus", "Seaweed", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Coral"),
                EnumSet.of(CalcModifierTarget.HARVEST_LIVES, CalcModifierTarget.SURVIVAL), true,
                null, null, null, null, null, null,
                "Adds two harvest lives on harvest-lives crops and reduces disease risk more generally."));
        MODIFIERS.add(new CalcModifierDefinition("ultracompost", "Ultracompost", CalcModifierType.TREATMENT,
                List.of("Herb", "Allotment", "Hops", "Celastrus", "Seaweed", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Crystal tree", "Coral"),
                EnumSet.of(CalcModifierTarget.HARVEST_LIVES, CalcModifierTarget.SURVIVAL), true,
                null, null, null, null, null, null,
                "Adds three harvest lives on harvest-lives crops and reduces disease risk more generally."));
        MODIFIERS.add(new CalcModifierDefinition("bottomless_bucket", "Bottomless compost bucket", CalcModifierType.ITEM,
                List.of("Herb", "Allotment", "Hops", "Celastrus", "Seaweed", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Crystal tree", "Coral"),
                EnumSet.of(CalcModifierTarget.COMPOST_COST), true,
                null, null, null, null, null, null,
                "Economic modifier only for now; intended to halve effective compost consumption in future cost logic."));
        MODIFIERS.add(new CalcModifierDefinition("magic_secateurs", "Magic secateurs", CalcModifierType.ITEM,
                harvestLivesGroups, EnumSet.of(CalcModifierTarget.YIELD, CalcModifierTarget.HARVEST_LIVES), false,
                HARVEST_LIVES_RULES.getSecateursItemBonus(), null, null, null, null, null,
                "Item bonus in the harvest-lives formula. Also relevant to limpwurt; giant seaweed is explicitly excluded in notes."));
        MODIFIERS.add(new CalcModifierDefinition("farming_cape", "Farming cape", CalcModifierType.ITEM,
                List.of("Herb"), EnumSet.of(CalcModifierTarget.YIELD, CalcModifierTarget.HARVEST_LIVES), false,
                HARVEST_LIVES_RULES.getFarmingCapeItemBonus(), null, null, null, null, null,
                "Herb patches only. Stacks with magic secateurs in the harvest-lives model."));
        MODIFIERS.add(new CalcModifierDefinition("max_cape", "Max cape", CalcModifierType.ITEM,
                List.of("Herb"), EnumSet.of(CalcModifierTarget.YIELD, CalcModifierTarget.HARVEST_LIVES), false,
                HARVEST_LIVES_RULES.getFarmingCapeItemBonus(), null, null, null, null, null,
                "Herb patches only. Mirrors the farming cape harvest-lives bonus."));
        MODIFIERS.add(new CalcModifierDefinition("amulet_of_bounty", "Amulet of bounty", CalcModifierType.JEWELRY,
                List.of("Allotment"), EnumSet.of(CalcModifierTarget.SEED_COST), false,
                null, null, null, 0.25d, 10, null,
                "Planting-time seed save chance for allotments. Affects cost, not yield."));
        MODIFIERS.add(new CalcModifierDefinition("attas", "Attas plant", CalcModifierType.ANIMA,
                harvestLivesGroups, EnumSet.of(CalcModifierTarget.YIELD, CalcModifierTarget.HARVEST_LIVES), false,
                null, null, HARVEST_LIVES_RULES.getAttasBonus(), null, null, null,
                "Final multiplicative layer in the harvest-lives model."));
        MODIFIERS.add(new CalcModifierDefinition("iasor", "Iasor plant", CalcModifierType.ANIMA,
                List.of("Herb", "Allotment", "Hops", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Celastrus", "Seaweed", "Grapes", "Mushroom", "Calquat", "Hardwood", "Coral"),
                EnumSet.of(CalcModifierTarget.SURVIVAL), true,
                null, null, null, null, null, null,
                "Disease-risk modifier retained as metadata only pending a dedicated formula pass."));
        MODIFIERS.add(new CalcModifierDefinition("kronos", "Kronos plant", CalcModifierType.ANIMA,
                List.of("Herb", "Allotment", "Hops", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Celastrus", "Seaweed", "Grapes", "Mushroom", "Calquat", "Hardwood", "Coral"),
                EnumSet.of(CalcModifierTarget.GROWTH_TIME), true,
                null, null, null, null, null, null,
                "Growth-stage skipping retained as metadata only pending a later timing pass."));
        MODIFIERS.add(new CalcModifierDefinition("farmers_outfit", "Farmer's outfit", CalcModifierType.OUTFIT,
                List.of("Herb", "Allotment", "Hops", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Celastrus", "Seaweed", "Grapes", "Mushroom", "Calquat", "Hardwood", "Coral", "Crystal tree"),
                EnumSet.of(CalcModifierTarget.XP), false,
                null, null, null, null, null, 0.025d,
                "XP-only modifier; must remain separate from yield and cost logic."));
        MODIFIERS.add(new CalcModifierDefinition("fertile_soil", "Fertile Soil", CalcModifierType.SPELL,
                List.of("Herb", "Allotment", "Hops", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Celastrus", "Seaweed", "Grapes", "Mushroom", "Calquat", "Hardwood", "Coral"),
                EnumSet.of(CalcModifierTarget.PATCH_STATE), true,
                null, null, null, null, null, null,
                "Alternate treatment source; affects input mode and rune cost rather than the yield formula itself."));
        MODIFIERS.add(new CalcModifierDefinition("resurrect_crops", "Resurrect Crops", CalcModifierType.SPELL,
                List.of("Herb", "Allotment", "Hops", "Tree", "Fruit tree", "Bush", "Flower", "Cactus", "Belladonna", "Celastrus", "Seaweed", "Grapes", "Mushroom", "Calquat", "Hardwood", "Coral"),
                EnumSet.of(CalcModifierTarget.SURVIVAL), true,
                null, null, null, null, null, null,
                "Active recovery mechanic. Stored as metadata only for now."));
        MODIFIERS.add(new CalcModifierDefinition("kandarin_diary", "Kandarin Diary", CalcModifierType.DIARY,
                List.of("Herb"), EnumSet.of(CalcModifierTarget.YIELD, CalcModifierTarget.HARVEST_LIVES), false,
                null, HARVEST_LIVES_RULES.getKandarinMediumDiaryBonus(), null, null, null, null,
                "Patch-specific diary bonus at Catherby herb patch: medium +10, hard +17, elite +25."));
        MODIFIERS.add(new CalcModifierDefinition("kourend_kebos_diary", "Kourend & Kebos Diary", CalcModifierType.DIARY,
                List.of("Herb"), EnumSet.of(CalcModifierTarget.YIELD, CalcModifierTarget.HARVEST_LIVES), false,
                null, HARVEST_LIVES_RULES.getKourendKebosHardDiaryBonus(), null, null, null, null,
                "Patch-specific diary bonus at Hosidius and Farming Guild herb patches."));
        MODIFIERS.add(new CalcModifierDefinition("attas_seed_item", AnimaType.ATTAS.getDisplayName() + " seed", CalcModifierType.ITEM,
                List.of("Anima"), EnumSet.of(CalcModifierTarget.PATCH_STATE), true,
                null, null, null, null, null, null,
                "Seed item backing the Attas anima effect."));
    }

    private static void registerPatchModifiers()
    {
        registerPatchModifier(new CalcPatchModifierDefinition(
                "catherby_herb_diary",
                List.of(PatchId.HERB_CATHERBY),
                CalcDiaryScope.CATHERBY_HERB,
                false,
                "Catherby herb patch carries Kandarin diary harvest-lives bonuses."
        ));
        registerPatchModifier(new CalcPatchModifierDefinition(
                "hosidius_herb_diary",
                List.of(PatchId.HERB_HOSIDIUS),
                CalcDiaryScope.HOSIDIUS_HERB,
                false,
                "Hosidius herb patch carries the Kourend & Kebos diary harvest-lives bonus. Disease-free handling intentionally left explicit for later verification."
        ));
        registerPatchModifier(new CalcPatchModifierDefinition(
                "farming_guild_herb_diary",
                List.of(PatchId.HERB_FARMING_GUILD),
                CalcDiaryScope.FARMING_GUILD_HERB,
                false,
                "Farming Guild herb patch carries the Kourend & Kebos diary harvest-lives bonus."
        ));
        registerPatchModifier(new CalcPatchModifierDefinition(
                "troll_stronghold_herb_disease_free",
                List.of(PatchId.HERB_TROLL_STRONGHOLD),
                CalcDiaryScope.NONE,
                true,
                "Disease-free herb patch state."
        ));
        registerPatchModifier(new CalcPatchModifierDefinition(
                "weiss_herb_disease_free",
                List.of(PatchId.HERB_WEISS),
                CalcDiaryScope.NONE,
                true,
                "Disease-free herb patch state."
        ));
    }

    private static CalcCropDefinition treeCrop(
            final String group,
            final String cropName,
            final String seedName,
            final String saplingName,
            final String plantedName,
            final List<CalcItemStack> protectionPayments,
            final List<CalcOutputDefinition> outputs,
            final CalcXpProfile xpProfile,
            final CalcSourceConfidence confidence)
    {
        final CalcYieldModelType yieldModelType;
        if ("Fruit tree".equals(group) || "Calquat".equals(group))
        {
            yieldModelType = CalcYieldModelType.REGROWING_PICK;
        }
        else if ("Spirit tree".equals(group))
        {
            yieldModelType = CalcYieldModelType.CHECK_HEALTH_ONLY;
        }
        else
        {
            yieldModelType = CalcYieldModelType.CHECK_HEALTH_THEN_CHOP;
        }
        return treeCrop(group, cropName, tradeableItem(seedName), tradeableItem(saplingName), tradeableItem(plantedName), protectionPayments,
                outputs, xpProfile, confidence, yieldModelType, "Stores sapling planting plus upstream propagation inputs.");
    }

    private static CalcCropDefinition treeCrop(
            final String group,
            final String cropName,
            final String seedName,
            final String saplingName,
            final String plantedName,
            final List<CalcItemStack> protectionPayments,
            final List<CalcOutputDefinition> outputs,
            final CalcXpProfile xpProfile,
            final CalcSourceConfidence confidence,
            final CalcYieldModelType yieldModelType,
            final String notes)
    {
        return treeCrop(group, cropName, tradeableItem(seedName), tradeableItem(saplingName), tradeableItem(plantedName), protectionPayments,
                outputs, xpProfile, confidence, yieldModelType, notes);
    }
    private static CalcCropDefinition treeCrop(
            final String group,
            final String cropName,
            final String seedName,
            final String saplingName,
            final String plantedName,
            final List<CalcItemStack> protectionPayments,
            final List<CalcOutputDefinition> outputs,
            final CalcXpProfile xpProfile,
            final CalcSourceConfidence confidence,
            final CalcYieldProfile yieldProfile)
    {
        return treeCrop(group, cropName, tradeableItem(seedName), tradeableItem(saplingName), tradeableItem(plantedName), protectionPayments,
                outputs, xpProfile, confidence, yieldProfile);
    }


    private static CalcCropDefinition treeCrop(
            final String group,
            final String cropName,
            final CalcItemRef seedItem,
            final CalcItemRef saplingItem,
            final CalcItemRef plantedItem,
            final List<CalcItemStack> protectionPayments,
            final List<CalcOutputDefinition> outputs,
            final CalcXpProfile xpProfile,
            final CalcSourceConfidence confidence,
            final CalcYieldModelType yieldModelType,
            final String notes)
    {
        return crop(group, cropName, saplingItem,
                List.of(stack(saplingItem, 1)), protectionPayments,
                new CalcPropagationChain(seedItem, tradeableItem("Filled plant pot"), saplingItem, "Users may start prep from either the seed chain or the sapling itself."),
                outputs,
                xpProfile,
                new CalcYieldProfile(yieldModelType, null, null, null, false, false, false, false, CalcYieldConfidenceTag.EXACT, notes),
                List.of(), EnumSet.of(CalcCropFlag.HAS_PROPAGATION_CHAIN), confidence, notes, null);
    }
    private static CalcCropDefinition treeCrop(
            final String group,
            final String cropName,
            final CalcItemRef seedItem,
            final CalcItemRef saplingItem,
            final CalcItemRef plantedItem,
            final List<CalcItemStack> protectionPayments,
            final List<CalcOutputDefinition> outputs,
            final CalcXpProfile xpProfile,
            final CalcSourceConfidence confidence,
            final CalcYieldProfile yieldProfile)
    {
        final String notes = yieldProfile == null ? null : yieldProfile.getNotes();
        return crop(group, cropName, saplingItem,
                List.of(stack(saplingItem, 1)), protectionPayments,
                new CalcPropagationChain(seedItem, tradeableItem("Filled plant pot"), saplingItem, "Users may start prep from either the seed chain or the sapling itself."),
                outputs,
                xpProfile,
                yieldProfile,
                List.of(), EnumSet.of(CalcCropFlag.HAS_PROPAGATION_CHAIN), confidence, notes, null);
    }


    private static CalcCropDefinition crop(
            final String group,
            final String cropName,
            final CalcItemRef primaryPlantingItem,
            final List<CalcItemStack> plantingInputs,
            final List<CalcItemStack> protectionPayments,
            final CalcPropagationChain propagationChain,
            final List<CalcOutputDefinition> outputs,
            final CalcXpProfile xpProfile,
            final CalcYieldProfile yieldProfile,
            final List<String> adjacentProtectionTargets,
            final EnumSet<CalcCropFlag> flags,
            final CalcSourceConfidence confidence,
            final String notes,
            final CalcSimpleModel simpleModel)
    {
        return new CalcCropDefinition(group, cropName, primaryPlantingItem, plantingInputs, protectionPayments, propagationChain,
                outputs, xpProfile, yieldProfile, adjacentProtectionTargets, flags, confidence, notes, simpleModel);
    }

    private static CalcSimpleModel simple(
            final int seedItemId,
            final int outputItemId,
            final double plantingXp,
            final double harvestXp,
            final int seedCount,
            final int flatYield)
    {
        return new CalcSimpleModel(seedItemId, outputItemId, plantingXp, harvestXp, seedCount, flatYield);
    }

    private static CalcXpProfile xp(final Double plantingXp, final Double harvestXpPerItem, final Double checkHealthXp)
    {
        return new CalcXpProfile(plantingXp, harvestXpPerItem, checkHealthXp);
    }

    private static CalcYieldProfile flat(final Integer flatYield, final String notes)
    {
        return flat(flatYield, notes, CalcYieldConfidenceTag.EXACT);
    }

    private static CalcYieldProfile flat(final Integer flatYield, final String notes, final CalcYieldConfidenceTag confidenceTag)
    {
        return new CalcYieldProfile(CalcYieldModelType.FLAT, flatYield, null, null, false, false, false, false, confidenceTag, notes);
    }

    private static CalcYieldProfile harvestLives(
            final Integer ctsLow,
            final Integer ctsHigh,
            final boolean secateurs,
            final boolean farmingCape,
            final boolean attas,
            final boolean diary,
            final String notes)
    {
        return harvestLives(ctsLow, ctsHigh, secateurs, farmingCape, attas, diary, notes, CalcYieldConfidenceTag.EXACT);
    }

    private static CalcYieldProfile harvestLives(
            final Integer ctsLow,
            final Integer ctsHigh,
            final boolean secateurs,
            final boolean farmingCape,
            final boolean attas,
            final boolean diary,
            final String notes,
            final CalcYieldConfidenceTag confidenceTag)
    {
        return new CalcYieldProfile(CalcYieldModelType.HARVEST_LIVES, null, ctsLow, ctsHigh, secateurs, farmingCape, attas, diary, confidenceTag, notes);
    }

    private static CalcYieldProfile regrow(final String notes)
    {
        return regrow(notes, CalcYieldConfidenceTag.EXACT);
    }

    private static CalcYieldProfile regrow(final String notes, final CalcYieldConfidenceTag confidenceTag)
    {
        return new CalcYieldProfile(CalcYieldModelType.REGROWING_PICK, null, null, null, false, false, false, false, confidenceTag, notes);
    }

    private static CalcYieldProfile fixedBatch(final String notes)
    {
        return fixedBatch(notes, CalcYieldConfidenceTag.EXACT);
    }

    private static CalcYieldProfile fixedBatch(final String notes, final CalcYieldConfidenceTag confidenceTag)
    {
        return new CalcYieldProfile(CalcYieldModelType.FIXED_BATCH, null, null, null, false, false, false, false, confidenceTag, notes);
    }

    private static CalcItemRef tradeableItem(final String name)
    {
        return new CalcItemRef(name, null, true, true);
    }

    private static CalcItemRef tradeableItem(final String name, final int itemId)
    {
        return new CalcItemRef(name, itemId, true, true);
    }

    private static CalcItemRef tradeableItem(final String name, final Integer itemId, final boolean tradeable, final boolean hasGePrice)
    {
        return new CalcItemRef(name, itemId, tradeable, hasGePrice);
    }

    private static CalcItemRef nonTradeableItem(final String name)
    {
        return new CalcItemRef(name, null, false, false);
    }

    private static CalcItemStack stack(final CalcItemRef item, final int quantity)
    {
        return new CalcItemStack(item, quantity);
    }

    private static CalcOutputDefinition output(final CalcItemRef item, final CalcOutputRole role, final String condition, final String notes)
    {
        return new CalcOutputDefinition(item, role, condition, notes);
    }

    private static void register(final CalcCropDefinition definition)
    {
        CROPS_BY_GROUP.computeIfAbsent(definition.getGroup(), ignored -> new LinkedHashMap<>())
                .put(definition.getCropName(), definition);
    }

    private static void registerPatchModifier(final CalcPatchModifierDefinition definition)
    {
        for (final PatchId patchId : definition.getPatchIds())
        {
            PATCH_MODIFIERS.put(patchId, definition);
        }
    }
}
