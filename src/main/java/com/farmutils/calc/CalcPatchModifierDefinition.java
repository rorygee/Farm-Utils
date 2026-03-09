package com.farmutils.calc;

import com.farmutils.model.PatchId;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CalcPatchModifierDefinition
{
    private final String id;
    private final List<PatchId> patchIds;
    private final CalcDiaryScope diaryScope;
    private final boolean diseaseFree;
    private final String notes;

    public CalcPatchModifierDefinition(
            final String id,
            final List<PatchId> patchIds,
            final CalcDiaryScope diaryScope,
            final boolean diseaseFree,
            final String notes)
    {
        this.id = Objects.requireNonNull(id, "id");
        this.patchIds = Collections.unmodifiableList(Objects.requireNonNull(patchIds, "patchIds"));
        this.diaryScope = Objects.requireNonNull(diaryScope, "diaryScope");
        this.diseaseFree = diseaseFree;
        this.notes = notes;
    }

    public String getId() { return id; }
    public List<PatchId> getPatchIds() { return patchIds; }
    public CalcDiaryScope getDiaryScope() { return diaryScope; }
    public boolean isDiseaseFree() { return diseaseFree; }
    public String getNotes() { return notes; }
}
