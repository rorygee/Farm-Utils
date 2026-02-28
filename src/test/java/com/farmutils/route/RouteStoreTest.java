package com.farmutils.route;

import com.farmutils.model.PatchId;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RouteStoreTest
{
	@Test
	public void createRenameDeleteAndPatchOps()
	{
		final RouteStore store = new RouteStore();

		final Route r1 = store.create("My route");
		Assert.assertEquals("My route", r1.getName());
		Assert.assertTrue(store.get(r1.getId()).isPresent());
		Assert.assertEquals(1, store.list().size());

		store.rename(r1.getId(), "Renamed");
		Assert.assertEquals("Renamed", store.get(r1.getId()).get().getName());

		// Patch ops
		final PatchId p1 = PatchId.HERB_FALADOR;
		final PatchId p2 = PatchId.HERB_CATHERBY;
		Assert.assertTrue(store.addPatch(r1.getId(), p1));
		Assert.assertFalse(store.addPatch(r1.getId(), p1)); // de-dupe
		Assert.assertTrue(store.addPatch(r1.getId(), p2));
		List<PatchId> patches = store.get(r1.getId()).get().getPatchIds();
		Assert.assertEquals(2, patches.size());
		Assert.assertEquals(p1, patches.get(0));
		Assert.assertEquals(p2, patches.get(1));

		store.movePatch(r1.getId(), 1, 0);
		patches = store.get(r1.getId()).get().getPatchIds();
		Assert.assertEquals(p2, patches.get(0));
		Assert.assertEquals(p1, patches.get(1));

		Assert.assertTrue(store.removePatch(r1.getId(), p1));
		Assert.assertFalse(store.removePatch(r1.getId(), p1));
		Assert.assertEquals(1, store.get(r1.getId()).get().getPatchIds().size());

		store.delete(r1.getId());
		Assert.assertFalse(store.get(r1.getId()).isPresent());
		Assert.assertEquals(0, store.list().size());
	}

	@Test
	public void routeReorderPreservesSnapshots()
	{
		final RouteStore store = new RouteStore();
		final Route a = store.create("A");
		final Route b = store.create("B");
		final Route c = store.create("C");

		Assert.assertEquals(Arrays.asList("A", "B", "C"), store.list().stream().map(Route::getName).collect(Collectors.toList()));

		// Move C to the top.
		store.moveRoute(2, 0);
		Assert.assertEquals(Arrays.asList("C", "A", "B"), store.list().stream().map(Route::getName).collect(Collectors.toList()));

		// Move A to the end by id.
		store.moveRoute(a.getId(), 2);
		Assert.assertEquals(Arrays.asList("C", "B", "A"), store.list().stream().map(Route::getName).collect(Collectors.toList()));
	}
}
