package com.farmutils.calc;

import com.farmutils.model.PatchId;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class CalcCatalogueTest
{
    @Test
    public void flowerCatalogueRetainsExtendedMetadata()
    {
        assertTrue(Arrays.asList(CalcCatalogue.cropNamesForGroup("Flower")).contains("White lily"));
        assertTrue(Arrays.asList(CalcCatalogue.cropNamesForGroup("Flower")).contains("Scarecrow"));
        assertTrue(Arrays.asList(CalcCatalogue.currentUiCropNamesForGroup("Flower")).contains("White lily"));

        final CalcCropDefinition whiteLily = CalcCatalogue.cropFor("Flower", "White lily");
        assertNotNull(whiteLily);
        assertTrue(whiteLily.getAdjacentProtectionTargets().contains("All neighbouring allotments"));
        assertEquals(Double.valueOf(42.0d), whiteLily.getXpProfile().getPlantingXp());
        assertEquals(Double.valueOf(250.0d), whiteLily.getXpProfile().getHarvestXpPerItem());
        assertEquals(CalcSourceConfidence.USER_CONFIRMED, whiteLily.getConfidence());
    }

    @Test
    public void harvestLivesRulesAreLockedIn()
    {
        final CalcHarvestLivesRules rules = CalcCatalogue.harvestLivesRules();
        assertEquals(3, rules.getBaseLives());
        assertEquals(Integer.valueOf(0), rules.getCompostLifeBonus().get(CalcCompostTier.NONE));
        assertEquals(Integer.valueOf(1), rules.getCompostLifeBonus().get(CalcCompostTier.COMPOST));
        assertEquals(Integer.valueOf(2), rules.getCompostLifeBonus().get(CalcCompostTier.SUPERCOMPOST));
        assertEquals(Integer.valueOf(3), rules.getCompostLifeBonus().get(CalcCompostTier.ULTRACOMPOST));
        assertEquals(0.10d, rules.getSecateursItemBonus(), 0.0001d);
        assertEquals(0.05d, rules.getFarmingCapeItemBonus(), 0.0001d);
        assertEquals(0.05d, rules.getAttasBonus(), 0.0001d);
        assertEquals(10, rules.getKandarinMediumDiaryBonus());
        assertEquals(17, rules.getKandarinHardDiaryBonus());
        assertEquals(25, rules.getKandarinEliteDiaryBonus());
        assertEquals(10, rules.getKourendKebosHardDiaryBonus());
    }

    @Test
    public void userConfirmedPaymentChainsArePresent()
    {
        final CalcCropDefinition snapeGrass = CalcCatalogue.cropFor("Allotment", "Snape grass");
        assertNotNull(snapeGrass);
        assertEquals("Jangerberries", snapeGrass.getProtectionPayments().get(0).getItem().getName());
        assertEquals(5, snapeGrass.getProtectionPayments().get(0).getQuantity());
        assertEquals(CalcSourceConfidence.USER_CONFIRMED, snapeGrass.getConfidence());

        final CalcCropDefinition pillar = CalcCatalogue.cropFor("Coral", "Pillar");
        assertNotNull(pillar);
        assertEquals("Elkhorn coral", pillar.getProtectionPayments().get(0).getItem().getName());
        assertEquals(5, pillar.getProtectionPayments().get(0).getQuantity());
    }

    @Test
    public void patchModifiersExposeDiaryScopesAndDiseaseFreePatches()
    {
        final CalcPatchModifierDefinition catherby = CalcCatalogue.patchModifierFor(PatchId.HERB_CATHERBY);
        assertNotNull(catherby);
        assertEquals(CalcDiaryScope.CATHERBY_HERB, catherby.getDiaryScope());
        assertFalse(catherby.isDiseaseFree());

        final CalcPatchModifierDefinition troll = CalcCatalogue.patchModifierFor(PatchId.HERB_TROLL_STRONGHOLD);
        assertNotNull(troll);
        assertTrue(troll.isDiseaseFree());
    }
    @Test
    public void baselineYieldAssumptionsExposeExpandedFamilyCoverage()
    {
        assertEquals(Integer.valueOf(4), CalcCatalogue.baselineYieldFor("Bush", "Jangerberry"));
        assertEquals(Integer.valueOf(6), CalcCatalogue.baselineYieldFor("Fruit tree", "Palm"));
        assertEquals(Integer.valueOf(3), CalcCatalogue.baselineYieldFor("Seaweed", "Giant seaweed"));
        assertEquals(Integer.valueOf(6), CalcCatalogue.baselineYieldFor("Mushroom", "Bittercap mushroom"));
        assertEquals(Integer.valueOf(16), CalcCatalogue.baselineYieldFor("Crystal tree", "Crystal tree"));
        assertEquals(Integer.valueOf(0), CalcCatalogue.baselineYieldFor("Redwood", "Redwood tree"));
    }


    @Test
    public void compostMetadataExposesSupportedGroupsAndTieredBaselines()
    {
        assertTrue(CalcCatalogue.supportsCompostForGroup("Herb"));
        assertTrue(CalcCatalogue.supportsCompostForGroup("Crystal tree"));
        assertTrue(CalcCatalogue.supportsCompostForGroup("Redwood"));
        assertTrue(CalcCatalogue.supportsCompostForGroup("Bush"));
        assertTrue(CalcCatalogue.supportsCompostForGroup("Fruit tree"));

        assertNull(CalcCatalogue.compostItemFor(CalcCompostTier.NONE));
        assertEquals("Ultracompost", CalcCatalogue.compostItemFor(CalcCompostTier.ULTRACOMPOST).getName());
        assertEquals(Integer.valueOf(8), CalcCatalogue.baselineYieldFor("Herb", "Ranarr", CalcCompostTier.ULTRACOMPOST));
        assertEquals(Integer.valueOf(4), CalcCatalogue.baselineYieldFor("Hops", "Wildblood", CalcCompostTier.ULTRACOMPOST));
        assertEquals(Integer.valueOf(6), CalcCatalogue.baselineYieldFor("Seaweed", "Giant seaweed", CalcCompostTier.ULTRACOMPOST));
        assertEquals(Integer.valueOf(28), CalcCatalogue.baselineYieldFor("Crystal tree", "Crystal tree", CalcCompostTier.ULTRACOMPOST));
    }


    @Test
    public void expandedCatalogueFamiliesExposeBaseXpProfiles()
    {
        assertEquals(Double.valueOf(19.0d), CalcCatalogue.cropFor("Seaweed", "Giant seaweed").getXpProfile().getPlantingXp());
        assertEquals(Double.valueOf(14130.0d), CalcCatalogue.cropFor("Celastrus", "Celastrus tree").getXpProfile().getCheckHealthXp());
        assertEquals(Double.valueOf(19301.8d), CalcCatalogue.cropFor("Spirit tree", "Spirit tree").getXpProfile().getCheckHealthXp());
        assertEquals(Double.valueOf(8.5d), CalcCatalogue.cropFor("Fruit tree", "Apple").getXpProfile().getHarvestXpPerItem());
        assertEquals(Double.valueOf(64.0d), CalcCatalogue.cropFor("Bush", "Redberry").getXpProfile().getCheckHealthXp());
    }


    @Test
    public void expectedYieldModelsCoverHarvestLivesAndSpecialCases()
    {
        final CalcExpectedYieldResult ranarr = CalcCatalogue.expectedYieldFor("Herb", "Ranarr", CalcCompostTier.ULTRACOMPOST, 99, 99, false, false, false, 0);
        assertTrue(ranarr.isResolved());
        assertEquals(CalcYieldConfidenceTag.EXACT, ranarr.getConfidenceTag());
        assertEquals(8.7771d, ranarr.getExpectedYield(), 0.0001d);

        final CalcExpectedYieldResult seaweed = CalcCatalogue.expectedYieldFor("Seaweed", "Giant seaweed", CalcCompostTier.ULTRACOMPOST, 99, 99, false, false, false, 0);
        assertTrue(seaweed.isResolved());
        assertEquals(CalcYieldConfidenceTag.EXACT, seaweed.getConfidenceTag());
        assertEquals(34.1333d, seaweed.getExpectedYield(), 0.0001d);

        final CalcExpectedYieldResult limpwurt = CalcCatalogue.expectedYieldFor("Flower", "Limpwurt", CalcCompostTier.NONE, 99, 104, false, false, true, 0);
        assertTrue(limpwurt.isResolved());
        assertEquals(CalcYieldConfidenceTag.SPECIAL_CASE, limpwurt.getConfidenceTag());
        assertEquals(7.9038d, limpwurt.getExpectedYield(), 0.0001d);

        final CalcExpectedYieldResult cactus = CalcCatalogue.expectedYieldFor("Cactus", "Cactus", CalcCompostTier.ULTRACOMPOST, 99, 99, false, false, false, 0);
        assertTrue(cactus.isResolved());
        assertEquals(CalcYieldConfidenceTag.SPECIAL_CASE, cactus.getConfidenceTag());
        assertEquals(10.0d, cactus.getExpectedYield(), 0.0001d);
    }

    @Test
    public void currentUiCropNamesHideExcludedBelladonnaEntry()
    {
        assertEquals(0, CalcCatalogue.currentUiCropNamesForGroup("Belladonna").length);
        final CalcCropDefinition belladonna = CalcCatalogue.cropFor("Belladonna", "Belladonna");
        assertNotNull(belladonna);
        assertEquals(CalcYieldConfidenceTag.EXCLUDED, belladonna.getYieldProfile().getConfidenceTag());
    }

}
