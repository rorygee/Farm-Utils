package com.farmutils.route;

import com.farmutils.model.PatchId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime-only in-memory store for routes.
 * <p>
 * Headless: no Swing types, no persistence.
 */
public final class RouteStore
{
	private final Map<RouteId, Route> routes = new HashMap<>();
	/**
	 * Stable runtime ordering for route headings.
	 * <p>
	 * Creation order is the default; callers may reorder via {@link #moveRoute(int, int)}.
	 */
	private final List<RouteId> routeOrder = new ArrayList<>();

	public Route create(final String name)
	{
		final RouteId id = RouteId.random();
		final Route route = new Route(id, name, Collections.emptyList());
		routes.put(id, route);
		routeOrder.add(id);
		return snapshot(route);
	}

	public Optional<Route> get(final RouteId routeId)
	{
		Objects.requireNonNull(routeId, "routeId");
		final Route route = routes.get(routeId);
		return route == null ? Optional.empty() : Optional.of(snapshot(route));
	}

	public List<Route> list()
	{
		final ArrayList<Route> out = new ArrayList<>(routes.size());
		for (final RouteId id : routeOrder)
		{
			final Route route = routes.get(id);
			if (route != null)
			{
				out.add(snapshot(route));
			}
		}
		return Collections.unmodifiableList(out);
	}

	public void rename(final RouteId routeId, final String newName)
	{
		final Route route = requireRoute(routeId);
		route.setName(Route.requireNonBlank(newName, "newName"));
	}

	public void delete(final RouteId routeId)
	{
		Objects.requireNonNull(routeId, "routeId");
		routes.remove(routeId);
		routeOrder.remove(routeId);
	}

	public int size()
	{
		return routes.size();
	}

	public int indexOf(final RouteId routeId)
	{
		Objects.requireNonNull(routeId, "routeId");
		return routeOrder.indexOf(routeId);
	}

	/**
	 * Reorder route headings within the current runtime.
	 */
	public void moveRoute(final int fromIndex, final int toIndex)
	{
		final int size = routeOrder.size();
		if (fromIndex < 0 || fromIndex >= size)
		{
			throw new IndexOutOfBoundsException("fromIndex=" + fromIndex + ", size=" + size);
		}
		if (toIndex < 0 || toIndex >= size)
		{
			throw new IndexOutOfBoundsException("toIndex=" + toIndex + ", size=" + size);
		}
		if (fromIndex == toIndex)
		{
			return;
		}
		final RouteId id = routeOrder.remove(fromIndex);
		routeOrder.add(toIndex, id);
	}

	public void moveRoute(final RouteId routeId, final int toIndex)
	{
		Objects.requireNonNull(routeId, "routeId");
		final int fromIndex = routeOrder.indexOf(routeId);
		if (fromIndex < 0)
		{
			throw new IllegalArgumentException("Unknown routeId: " + routeId);
		}
		moveRoute(fromIndex, toIndex);
	}

	/**
	 * Adds a patch to the end of the route. Returns true if it was added.
	 */
	public boolean addPatch(final RouteId routeId, final PatchId patchId)
	{
		final Route route = requireRoute(routeId);
		return route.addPatch(patchId);
	}

	public boolean removePatch(final RouteId routeId, final PatchId patchId)
	{
		final Route route = requireRoute(routeId);
		return route.removePatch(patchId);
	}

	public void movePatch(final RouteId routeId, final int fromIndex, final int toIndex)
	{
		final Route route = requireRoute(routeId);
		route.movePatch(fromIndex, toIndex);
	}

	/**
	 * Convenience: move a specific patch to a new index.
	 */
	public void movePatch(final RouteId routeId, final PatchId patchId, final int toIndex)
	{
		final Route route = requireRoute(routeId);
		Objects.requireNonNull(patchId, "patchId");
		final int fromIndex = route.getPatchIds().indexOf(patchId);
		if (fromIndex < 0)
		{
			throw new IllegalArgumentException("Patch not in route: " + patchId);
		}
		route.movePatch(fromIndex, toIndex);
	}

	private Route requireRoute(final RouteId routeId)
	{
		Objects.requireNonNull(routeId, "routeId");
		final Route route = routes.get(routeId);
		if (route == null)
		{
			throw new IllegalArgumentException("Unknown routeId: " + routeId);
		}
		return route;
	}

	private static Route snapshot(final Route route)
	{
		return new Route(route.getId(), route.getName(), route.getPatchIds());
	}
}
