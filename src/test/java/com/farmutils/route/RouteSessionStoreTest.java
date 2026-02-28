package com.farmutils.route;

import org.junit.Assert;
import org.junit.Test;

public class RouteSessionStoreTest
{
	@Test
	public void startPauseResumeStop()
	{
		RouteSessionStore store = new RouteSessionStore();
		RouteId r1 = RouteId.random();
		RouteId r2 = RouteId.random();

		Assert.assertFalse(store.getActiveRouteId().isPresent());

		store.start(r1);
		Assert.assertEquals(r1, store.getActiveRouteId().get());
		Assert.assertEquals(RouteSessionState.RUNNING, store.getState(r1).get());

		store.pauseActive();
		Assert.assertEquals(RouteSessionState.PAUSED, store.getState(r1).get());

		store.start(r1); // resume
		Assert.assertEquals(RouteSessionState.RUNNING, store.getState(r1).get());

		store.start(r2); // switch route (implicit stop of r1)
		Assert.assertEquals(r2, store.getActiveRouteId().get());
		Assert.assertFalse(store.getState(r1).isPresent());
		Assert.assertEquals(RouteSessionState.RUNNING, store.getState(r2).get());

		store.stopIfActive(r1);
		Assert.assertTrue(store.getActiveRouteId().isPresent());
		Assert.assertEquals(r2, store.getActiveRouteId().get());

		store.stopIfActive(r2);
		Assert.assertFalse(store.getActiveRouteId().isPresent());

		store.start(r1);
		store.stopActive();
		Assert.assertFalse(store.getActiveRouteId().isPresent());
	}
}
