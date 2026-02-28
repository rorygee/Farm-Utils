package com.farmutils.route;

import com.farmutils.model.PatchId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-only representation of a route.
 */
public final class Route
{
	private final RouteId id;
	private String name;
	private final ArrayList<PatchId> patchIds;

	Route(final RouteId id, final String name, final List<PatchId> patchIds)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.name = requireNonBlank(name, "name");
		this.patchIds = new ArrayList<>(Objects.requireNonNull(patchIds, "patchIds"));
	}

	public RouteId getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	void setName(final String name)
	{
		this.name = requireNonBlank(name, "name");
	}

	/**
	 * Ordered, de-duplicated list of patches in this route.
	 */
	public List<PatchId> getPatchIds()
	{
		return Collections.unmodifiableList(patchIds);
	}

	boolean addPatch(final PatchId patchId)
	{
		Objects.requireNonNull(patchId, "patchId");
		if (patchIds.contains(patchId))
		{
			return false;
		}
		return patchIds.add(patchId);
	}

	boolean removePatch(final PatchId patchId)
	{
		Objects.requireNonNull(patchId, "patchId");
		return patchIds.remove(patchId);
	}

	void movePatch(final int fromIndex, final int toIndex)
	{
		if (fromIndex < 0 || fromIndex >= patchIds.size())
		{
			throw new IndexOutOfBoundsException("fromIndex=" + fromIndex);
		}
		if (toIndex < 0 || toIndex >= patchIds.size())
		{
			throw new IndexOutOfBoundsException("toIndex=" + toIndex);
		}
		if (fromIndex == toIndex)
		{
			return;
		}
		final PatchId moved = patchIds.remove(fromIndex);
		patchIds.add(toIndex, moved);
	}

	static String requireNonBlank(final String s, final String field)
	{
		Objects.requireNonNull(s, field);
		final String trimmed = s.trim();
		if (trimmed.isEmpty())
		{
			throw new IllegalArgumentException(field + " cannot be blank");
		}
		return trimmed;
	}

	@Override
	public String toString()
	{
		return "Route{" +
			"id=" + id +
			", name='" + name + '\'' +
			", patchIds=" + patchIds +
			'}';
	}
}
