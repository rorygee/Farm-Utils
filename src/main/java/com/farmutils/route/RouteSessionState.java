package com.farmutils.route;

/**
 * Runtime-only state for a route session.
 *
 * A session is purely about tracking/progress UI (no automation).
 */
public enum RouteSessionState
{
    RUNNING,
    PAUSED
}
