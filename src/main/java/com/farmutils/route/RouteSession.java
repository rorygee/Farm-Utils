package com.farmutils.route;

import java.time.Instant;

/**
 * Runtime-only session for a route.
 *
 * For now this only captures state and a simple cursor (index into the route's patch list).
 * Later tasks can advance the cursor based on observed in-game actions.
 */
public final class RouteSession
{
    private final RouteId routeId;
    private final RouteSessionState state;
    private final int cursorIndex;
    private final Instant startedAt;
    private final Instant updatedAt;

    public RouteSession(RouteId routeId, RouteSessionState state, int cursorIndex, Instant startedAt, Instant updatedAt)
    {
        this.routeId = routeId;
        this.state = state;
        this.cursorIndex = cursorIndex;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
    }

    public RouteId getRouteId()
    {
        return routeId;
    }

    public RouteSessionState getState()
    {
        return state;
    }

    public int getCursorIndex()
    {
        return cursorIndex;
    }

    public Instant getStartedAt()
    {
        return startedAt;
    }

    public Instant getUpdatedAt()
    {
        return updatedAt;
    }

    public RouteSession withState(RouteSessionState next, Instant now)
    {
        return new RouteSession(routeId, next, cursorIndex, startedAt, now);
    }

    public RouteSession withCursor(int nextIndex, Instant now)
    {
        return new RouteSession(routeId, state, nextIndex, startedAt, now);
    }
}
