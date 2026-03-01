package com.farmutils.overlay;

import com.farmutils.model.PatchId;
import com.farmutils.overlay.footprint.Footprint;
import com.farmutils.overlay.footprint.PatchFootprintRegistry;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import com.farmutils.ui.UiColors;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
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
 * <p>Task 1 baseline: no scene scanning and no rendering.</p>
 * <p>Task 2 will introduce explicit footprints and tile rendering.</p>
 */
@Singleton
public class PatchHighlightOverlay extends Overlay
{
	private final Client client;
	private final PatchStore patchStore;
	private final UiStateStore uiStateStore;
	private final BasicStroke stroke = new BasicStroke(2f);

	@Inject
	public PatchHighlightOverlay(final Client client, final PatchStore patchStore, final UiStateStore uiStateStore)
	{
		this.client = client;
		this.patchStore = patchStore;
		this.uiStateStore = uiStateStore;

		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
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
}
