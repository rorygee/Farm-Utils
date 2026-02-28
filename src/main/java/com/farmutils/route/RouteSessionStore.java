package com.farmutils.route;

import java.time.Instant;
import java.util.Optional;

/**
 * Runtime-only session state for routes.
 *
 * Contract (v0): at most ONE active session at a time.
 * Starting a new route implicitly stops the prior active route.
 */
public final class RouteSessionStore
{
    private RouteSession active;

    public synchronized Optional<RouteId> getActiveRouteId()
    {
        return active == null ? Optional.empty() : Optional.of(active.getRouteId());
    }

    public synchronized Optional<RouteSession> getActiveSession()
    {
        return Optional.ofNullable(active);
    }

    public synchronized Optional<RouteSessionState> getState(RouteId routeId)
    {
        if (active == null || routeId == null)
        {
            return Optional.empty();
        }
        if (!active.getRouteId().equals(routeId))
        {
            return Optional.empty();
        }
        return Optional.of(active.getState());
    }

    public synchronized void start(RouteId routeId)
    {
        if (routeId == null)
        {
            return;
        }

        Instant now = Instant.now();

        // Starting always makes this the active route; previous one is implicitly stopped.
        if (active == null || !active.getRouteId().equals(routeId))
        {
            active = new RouteSession(routeId, RouteSessionState.RUNNING, 0, now, now);
            return;
        }

        // Same route: resume if paused.
        if (active.getState() == RouteSessionState.PAUSED)
        {
            active = active.withState(RouteSessionState.RUNNING, now);
        }
    }

    public synchronized void pauseActive()
    {
        if (active == null)
        {
            return;
        }
        if (active.getState() == RouteSessionState.RUNNING)
        {
            active = active.withState(RouteSessionState.PAUSED, Instant.now());
        }
    }

    public synchronized void stopActive()
    {
        active = null;
    }

    public synchronized void stopIfActive(RouteId routeId)
    {
        if (active == null || routeId == null)
        {
            return;
        }
        if (active.getRouteId().equals(routeId))
        {
            active = null;
        }
    }
}
