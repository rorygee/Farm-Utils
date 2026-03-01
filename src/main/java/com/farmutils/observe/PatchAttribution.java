package com.farmutils.observe;

import com.farmutils.model.PatchId;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/**
 * Small helper for attributing a shared varbit "slot" to a specific patch based on proximity
 * to explicit, hard-coded anchors.
 *
 * <p>This is intentionally deterministic: no scene scanning, no object/action heuristics.
 * It is only used as a fallback when region-id attribution is insufficient (e.g. standing near
 * a patch on a region boundary).</p>
 */
final class PatchAttribution
{
    private PatchAttribution() {}

    static PatchId byNearestAnchor(final WorldPoint here, final Map<PatchId, WorldPoint> patchToAnchor, final int maxDistanceTiles)
    {
        if (here == null || patchToAnchor == null || patchToAnchor.isEmpty())
        {
            return null;
        }

        PatchId bestPatch = null;
        int bestDist = Integer.MAX_VALUE;

        for (Map.Entry<PatchId, WorldPoint> e : patchToAnchor.entrySet())
        {
            final PatchId patch = e.getKey();
            final WorldPoint anchor = e.getValue();
            if (patch == null || anchor == null)
            {
                continue;
            }

            if (here.getPlane() != anchor.getPlane())
            {
                continue;
            }

            final int d = here.distanceTo2D(anchor);
            if (d <= maxDistanceTiles && d < bestDist)
            {
                bestDist = d;
                bestPatch = patch;
            }
        }

        return bestPatch;
    }
}
