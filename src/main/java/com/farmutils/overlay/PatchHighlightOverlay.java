package com.farmutils.overlay;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
import com.farmutils.overlay.footprint.Footprint;
import com.farmutils.overlay.footprint.PatchFootprintRegistry;
import com.farmutils.route.Route;
import com.farmutils.route.RouteSession;
import com.farmutils.route.RouteSessionStore;
import com.farmutils.route.RouteStore;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import com.farmutils.ui.UiColors;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.List;
import java.util.Optional;
import net.runelite.api.GameState;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/**
 * In-world patch highlighting overlay.
 *
 * <p>Explicit footprint rendering only (no scene scanning).</p>
 * <p>When a route run is active, swatch/slot highlights are suppressed in favour of route highlighting.</p>
 */
@Singleton
public class PatchHighlightOverlay extends Overlay
{
	private final Client client;
	private final PatchStore patchStore;
	private final UiStateStore uiStateStore;
	private final FarmutilsConfig config;
	private final BasicStroke stroke = new BasicStroke(2f);

	private volatile RouteStore routeStore;
	private volatile RouteSessionStore routeSessionStore;

	@Inject
	public PatchHighlightOverlay(final Client client, final PatchStore patchStore, final UiStateStore uiStateStore, final FarmutilsConfig config)
	{
		this.client = client;
		this.patchStore = patchStore;
		this.uiStateStore = uiStateStore;
		this.config = config;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/**
	 * Runtime-only wiring: lets the overlay read the active route run state.
	 *
	 * <p>Routes are intentionally runtime-only in v0, so this is set by the plugin on startup.</p>
	 */
	public void setRouteContext(final RouteStore routeStore, final RouteSessionStore routeSessionStore)
	{
		this.routeStore = routeStore;
		this.routeSessionStore = routeSessionStore;
	}

	@Override
	public Dimension render(final Graphics2D graphics)
	{
		if (uiStateStore != null && !uiStateStore.isShowHighlightsOverlay())
		{
			return null;
		}

		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		// Route run mode: suppress swatch/slot rendering while a route session is active.
		if (routeSessionStore != null)
		{
			Optional<RouteSession> sessionOpt = routeSessionStore.getActiveSession();
			if (sessionOpt.isPresent())
			{
				renderRouteRun(graphics, sessionOpt.get());
				return null;
			}
		}

		final int plane = client.getPlane();
		for (PatchId patchId : PatchId.values())
		{
			final int slot = patchStore.getHighlightSlot(patchId);
			if (slot <= 0)
			{
				continue;
			}

			final Footprint footprint = PatchFootprintRegistry.get(patchId);
			if (footprint == null)
			{
				continue;
			}

			final Color outline = UiColors.highlightSlotColorOrNull(slot, 220);
			final Color fill = UiColors.highlightSlotColorOrNull(slot, 55);
			if (outline == null || fill == null)
			{
				continue;
			}

			for (WorldPoint wp : footprint.getTiles())
			{
				if (wp == null || wp.getPlane() != plane)
				{
					continue;
				}

				final LocalPoint lp = LocalPoint.fromWorld(client, wp);
				if (lp == null)
				{
					continue;
				}

				final Polygon poly = Perspective.getCanvasTilePoly(client, lp);
				if (poly == null)
				{
					continue;
				}

				OverlayUtil.renderPolygon(graphics, poly, outline, fill, stroke);
			}
		}

		return null;
	}

	private void renderRouteRun(final Graphics2D graphics, final RouteSession session)
	{
		if (session == null || routeStore == null)
		{
			return;
		}

		final Route route = routeStore.get(session.getRouteId()).orElse(null);
		if (route == null)
		{
			return;
		}

		final List<PatchId> patchIds = route.getPatchIds();
		if (patchIds.isEmpty())
		{
			return;
		}

		final int plane = client.getPlane();
		final int cursor = clamp(session.getCursorIndex(), 0, patchIds.size() - 1);
		final PatchId current = patchIds.get(cursor);

		// Non-current patches (muted)
		for (PatchId patchId : patchIds)
		{
			if (patchId == null || patchId.equals(current))
			{
				continue;
			}
			final Color outline = withAlpha(config.routeOverlayOtherPatchesColor(), 160);
			final Color fill = withAlpha(config.routeOverlayOtherPatchesColor(), 30);
			renderPatchFootprint(graphics, patchId, plane, outline, fill);
		}

		// Current patch (status-coloured)
		final Color base = currentStatusColor(current);
		final Color outline = withAlpha(base, 220);
		final Color fill = withAlpha(base, 55);
		renderPatchFootprint(graphics, current, plane, outline, fill);
	}

	private Color currentStatusColor(final PatchId patchId)
	{
		if (patchId == null || patchStore == null || config == null)
		{
			return new Color(230, 205, 90);
		}

		final Optional<PatchRecord> rec = patchStore.view(patchId).getRecord();
		if (!rec.isPresent())
		{
			return config.routeOverlayCurrentNeutralColor();
		}

		final PatchState state = rec.get().getState();
		if (state == null)
		{
			return config.routeOverlayCurrentNeutralColor();
		}

		switch (state)
		{
			case READY:
				return config.routeOverlayCurrentReadyColor();
			case DISEASED:
				return config.routeOverlayCurrentDiseasedColor();
			case DEAD:
				return config.routeOverlayCurrentDeadColor();
			case EMPTY:
			case GROWING:
			default:
				return config.routeOverlayCurrentNeutralColor();
		}
	}

	private void renderPatchFootprint(final Graphics2D graphics, final PatchId patchId, final int plane, final Color outline, final Color fill)
	{
		if (patchId == null)
		{
			return;
		}

		final Footprint footprint = PatchFootprintRegistry.get(patchId);
		if (footprint == null)
		{
			return;
		}

		if (outline == null || fill == null)
		{
			return;
		}

		for (WorldPoint wp : footprint.getTiles())
		{
			if (wp == null || wp.getPlane() != plane)
			{
				continue;
			}

			final LocalPoint lp = LocalPoint.fromWorld(client, wp);
			if (lp == null)
			{
				continue;
			}

			final Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly == null)
			{
				continue;
			}

			OverlayUtil.renderPolygon(graphics, poly, outline, fill, stroke);
		}
	}

	private static Color withAlpha(final Color base, final int alpha)
	{
		if (base == null)
		{
			return null;
		}
		final int a = clamp(alpha, 0, 255);
		return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
	}

	private static int clamp(final int v, final int lo, final int hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}
}
