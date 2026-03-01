package com.farmutils.overlay.footprint;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public class TileSetFootprint implements Footprint
{
	private final Set<WorldPoint> tiles;

	public TileSetFootprint(final Set<WorldPoint> tiles)
	{
		this.tiles = Collections.unmodifiableSet(new HashSet<>(tiles));
	}

	@Override
	public Collection<WorldPoint> getTiles()
	{
		return tiles;
	}
}
