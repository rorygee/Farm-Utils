package com.farmutils.route;

import java.util.Objects;
import java.util.UUID;

/**
 * Stable identifier for a Route within the current client runtime.
 * <p>
 * Runtime-only: no persistence semantics are implied.
 */
public final class RouteId
{
	private final UUID uuid;

	private RouteId(final UUID uuid)
	{
		this.uuid = Objects.requireNonNull(uuid, "uuid");
	}

	public static RouteId random()
	{
		return new RouteId(UUID.randomUUID());
	}

	public UUID getUuid()
	{
		return uuid;
	}

	@Override
	public boolean equals(final Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof RouteId))
		{
			return false;
		}
		final RouteId routeId = (RouteId) o;
		return uuid.equals(routeId.uuid);
	}

	@Override
	public int hashCode()
	{
		return uuid.hashCode();
	}

	@Override
	public String toString()
	{
		return uuid.toString();
	}
}
