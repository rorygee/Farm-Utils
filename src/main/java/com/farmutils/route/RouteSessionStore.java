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

    /**
     * Advance the active session cursor by one, bounded to the provided route size.
     *
     * <p>Returns true if the cursor changed.</p>
     */
    public synchronized boolean advanceCursor(final int routeSize)
    {
        if (active == null)
        {
            return false;
        }
        if (routeSize <= 0)
        {
            return false;
        }

        final int current = active.getCursorIndex();
        final int maxIndex = routeSize - 1;
        final int clamped = Math.max(0, Math.min(maxIndex, current));
        final int next = clamped + 1;
        if (next > maxIndex)
        {
            return false;
        }

        active = active.withCursor(next, Instant.now());
        return true;
    }

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
