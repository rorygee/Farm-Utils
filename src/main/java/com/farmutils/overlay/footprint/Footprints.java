package com.farmutils.overlay.footprint;

import java.util.Collections;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

public final class Footprints
{
	private static final Footprint EMPTY = new TileSetFootprint(Collections.emptySet());

	private Footprints()
	{
	}

	public static Footprint empty()
	{
		return EMPTY;
	}

	public static Footprint tile(final WorldPoint tile)
	{
		return new TileSetFootprint(Collections.singleton(tile));
	}

	public static Footprint tiles(final Set<WorldPoint> tiles)
	{
		if (tiles == null || tiles.isEmpty())
		{
			return EMPTY;
		}
		return new TileSetFootprint(tiles);
	}

	public static Footprint worldArea(final WorldPoint southWestInclusive, final WorldPoint northEastInclusive)
	{
		return new WorldAreaFootprint(southWestInclusive, northEastInclusive);
	}
}
