package com.farmutils.overlay.footprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public class WorldAreaFootprint implements Footprint
{
	private final List<WorldPoint> tiles;

	public WorldAreaFootprint(final WorldPoint southWestInclusive, final WorldPoint northEastInclusive)
	{
		if (southWestInclusive.getPlane() != northEastInclusive.getPlane())
		{
			throw new IllegalArgumentException("WorldAreaFootprint requires corners on the same plane");
		}

		final int plane = southWestInclusive.getPlane();
		final int minX = Math.min(southWestInclusive.getX(), northEastInclusive.getX());
		final int maxX = Math.max(southWestInclusive.getX(), northEastInclusive.getX());
		final int minY = Math.min(southWestInclusive.getY(), northEastInclusive.getY());
		final int maxY = Math.max(southWestInclusive.getY(), northEastInclusive.getY());

		final List<WorldPoint> out = new ArrayList<>((maxX - minX + 1) * (maxY - minY + 1));
		for (int x = minX; x <= maxX; x++)
		{
			for (int y = minY; y <= maxY; y++)
			{
				out.add(new WorldPoint(x, y, plane));
			}
		}

		this.tiles = Collections.unmodifiableList(out);
	}

	public WorldAreaFootprint(final WorldArea area)
	{
		final List<WorldPoint> out = new ArrayList<>(area.getWidth() * area.getHeight());
		for (WorldPoint wp : area.toWorldPointList())
		{
			out.add(wp);
		}
		this.tiles = Collections.unmodifiableList(out);
	}

	@Override
	public Collection<WorldPoint> getTiles()
	{
		return tiles;
	}
}
