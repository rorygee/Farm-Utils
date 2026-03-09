package com.farmutils.calc;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CalcCropDefinition
{
    private final String group;
    private final String cropName;
    private final CalcItemRef primaryPlantingItem;
    private final List<CalcItemStack> plantingInputs;
    private final List<CalcItemStack> protectionPayments;
    private final CalcPropagationChain propagationChain;
    private final List<CalcOutputDefinition> outputs;
    private final CalcXpProfile xpProfile;
    private final CalcYieldProfile yieldProfile;
    private final List<String> adjacentProtectionTargets;
    private final Set<CalcCropFlag> flags;
    private final CalcSourceConfidence confidence;
    private final String notes;
    private final CalcSimpleModel simpleModel;

    public CalcCropDefinition(
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
            final Set<CalcCropFlag> flags,
            final CalcSourceConfidence confidence,
            final String notes,
            final CalcSimpleModel simpleModel)
    {
        this.group = Objects.requireNonNull(group, "group");
        this.cropName = Objects.requireNonNull(cropName, "cropName");
        this.primaryPlantingItem = Objects.requireNonNull(primaryPlantingItem, "primaryPlantingItem");
        this.plantingInputs = Collections.unmodifiableList(Objects.requireNonNull(plantingInputs, "plantingInputs"));
        this.protectionPayments = Collections.unmodifiableList(Objects.requireNonNull(protectionPayments, "protectionPayments"));
        this.propagationChain = propagationChain;
        this.outputs = Collections.unmodifiableList(Objects.requireNonNull(outputs, "outputs"));
        this.xpProfile = xpProfile;
        this.yieldProfile = yieldProfile;
        this.adjacentProtectionTargets = Collections.unmodifiableList(Objects.requireNonNull(adjacentProtectionTargets, "adjacentProtectionTargets"));
        this.flags = Collections.unmodifiableSet(flags == null ? EnumSet.noneOf(CalcCropFlag.class) : EnumSet.copyOf(flags));
        this.confidence = Objects.requireNonNull(confidence, "confidence");
        this.notes = notes;
        this.simpleModel = simpleModel;
    }

    public String getGroup() { return group; }
    public String getCropName() { return cropName; }
    public CalcItemRef getPrimaryPlantingItem() { return primaryPlantingItem; }
    public List<CalcItemStack> getPlantingInputs() { return plantingInputs; }
    public List<CalcItemStack> getProtectionPayments() { return protectionPayments; }
    public CalcPropagationChain getPropagationChain() { return propagationChain; }
    public List<CalcOutputDefinition> getOutputs() { return outputs; }
    public CalcXpProfile getXpProfile() { return xpProfile; }
    public CalcYieldProfile getYieldProfile() { return yieldProfile; }
    public List<String> getAdjacentProtectionTargets() { return adjacentProtectionTargets; }
    public Set<CalcCropFlag> getFlags() { return flags; }
    public CalcSourceConfidence getConfidence() { return confidence; }
    public String getNotes() { return notes; }
    public CalcSimpleModel getSimpleModel() { return simpleModel; }

    public boolean supportsSimpleCalculation()
    {
        return simpleModel != null;
    }
}
