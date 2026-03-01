package com.farmutils.overlay.footprint;

import java.util.Collection;
import net.runelite.api.coords.WorldPoint;

/**
 * Explicit set of world tiles to highlight for a patch.
 */
public interface Footprint
{
	Collection<WorldPoint> getTiles();
}
