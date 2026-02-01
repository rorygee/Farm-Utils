package com.farmutils.ui;

import com.farmutils.model.PatchId;
import com.farmutils.storage.UiStateStore;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PatchOrderingResolver
{
    /**
     * Applies user group ordering (if any) to the canonical grouped map.
     * Does not mutate the input map.
     */
    public static List<Map.Entry<String, List<PatchId>>> resolveGroupOrder(
            Map<String, List<PatchId>> canonicalGrouped,
            UiStateStore uiState
    )
    {
        if (canonicalGrouped == null || canonicalGrouped.isEmpty() || uiState == null)
        {
            return canonicalGrouped == null
                    ? new ArrayList<>()
                    : new ArrayList<>(canonicalGrouped.entrySet());
        }

        List<String> preferred = uiState.getGroupOrder();
        if (preferred == null || preferred.isEmpty())
        {
            return new ArrayList<>(canonicalGrouped.entrySet());
        }

        // Track which canonical groups are still unplaced, preserving canonical order.
        Set<String> remaining = new LinkedHashSet<>(canonicalGrouped.keySet());
        List<Map.Entry<String, List<PatchId>>> result = new ArrayList<>();

        // 1) User-defined order (only groups that still exist)
        for (String groupName : preferred)
        {
            if (groupName == null) continue;
            if (!remaining.remove(groupName)) continue;

            result.add(new AbstractMap.SimpleImmutableEntry<>(groupName, canonicalGrouped.get(groupName)));
        }

        // 2) Append anything not in user list, in canonical order
        for (String groupName : canonicalGrouped.keySet())
        {
            if (!remaining.remove(groupName)) continue;

            result.add(new AbstractMap.SimpleImmutableEntry<>(groupName, canonicalGrouped.get(groupName)));
        }

        return result;
    }

    /**
     * Applies user entry ordering (if any) to a group's canonical PatchId list.
     * Does not mutate the input list.
     */
    public static List<PatchId> resolveEntryOrder(
            String groupName,
            List<PatchId> canonicalIds,
            UiStateStore uiState
    )
    {
        if (canonicalIds == null || canonicalIds.isEmpty() || uiState == null || groupName == null)
        {
            return canonicalIds == null ? new ArrayList<>() : canonicalIds;
        }

        Map<String, List<PatchId>> all = uiState.getEntryOrder();
        if (all == null)
        {
            return canonicalIds;
        }

        List<PatchId> preferred = all.get(groupName);
        if (preferred == null || preferred.isEmpty())
        {
            return canonicalIds;
        }

        Set<PatchId> remaining = new LinkedHashSet<>(canonicalIds);
        List<PatchId> result = new ArrayList<>(canonicalIds.size());

        // 1) User-defined ids (only those still present)
        for (PatchId id : preferred)
        {
            if (id == null) continue;
            if (remaining.remove(id))
            {
                result.add(id);
            }
        }

        // 2) Append anything missing/new in canonical order
        for (PatchId id : canonicalIds)
        {
            if (remaining.remove(id))
            {
                result.add(id);
            }
        }

        return result;
    }

    private PatchOrderingResolver() {}
}
