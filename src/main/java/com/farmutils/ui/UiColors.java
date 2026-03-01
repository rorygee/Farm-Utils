package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchView;
import java.awt.Color;

/**
 * Small UI colour helpers.
 *
 * <p>Keep rendering logic dumb: PatchRow asks for colours/progress and paints.
 * Patch/crop specifics live in inference/duration model seams.</p>
 */
public final class UiColors
{
    private UiColors() {}

	/**
	 * Slot palette used for both UI swatches and the in-world highlight overlay.
	 *
	 * <p>Slots are 1-based. Returns null for slot 0 / unassigned.</p>
	 */
	public static Color highlightSlotColorOrNull(final int slot)
	{
		return highlightSlotColorOrNull(slot, 255);
	}

	public static Color highlightSlotColorOrNull(final int slot, final int alpha)
	{
		if (slot <= 0)
		{
			return null;
		}

		final Color base;
		switch (slot)
		{
			case 1:
				base = new Color(120, 160, 255);
				break;
			case 2:
				base = new Color(140, 200, 140);
				break;
			case 3:
				base = new Color(220, 180, 120);
				break;
			case 4:
				base = new Color(200, 120, 200);
				break;
			default:
				base = new Color(140, 140, 140);
				break;
		}

		return new Color(base.getRed(), base.getGreen(), base.getBlue(), clamp(alpha, 0, 255));
	}

    public static Color stateColorOrNull(PatchView view, FarmutilsConfig config)
    {
        if (view == null || view.getRecord() == null || !view.getRecord().isPresent())
        {
            return null;
        }
        PatchState s = view.getRecord().get().getState();
        return stateColorOrNull(s, config);
    }

    /**
     * Returns a configured colour for a concrete state.
     * Returns null for unknown/untracked states (callers may fall back to neutral UI colours).
     */
    public static Color stateColorOrNull(PatchState s, FarmutilsConfig config)
    {
        if (s == null || config == null)
        {
            return null;
        }

        switch (s)
        {
            case READY:
                return config.stateColorReady();
            case GROWING:
                return config.stateColorGrowing();
            case DISEASED:
                return config.stateColorDiseased();
            case DEAD:
                return config.stateColorDead();
            case EMPTY:
                return config.stateColorEmpty();
            default:
                return null;
        }
    }

    public static Color unknownStateColor(FarmutilsConfig config)
    {
        return (config != null) ? config.stateColorUnknown() : null;
    }

    /**
     * Progress remainder colour: a darker, background-aware tint derived from the state colour.
     */
    public static Color remainderColor(Color stateColor, Color rowBackground)
    {
        if (stateColor == null)
        {
            return null;
        }

        // Blend towards the state colour (low weight), then darken slightly.
        Color bg = (rowBackground != null) ? rowBackground : new Color(30, 30, 30);
        Color blended = blend(bg, stateColor, 0.28f);
        Color darkened = scaleRgb(blended, 0.88f);
        return withAlpha(darkened, 200);
    }

    /**
     * Muted hint colour for collapsed caret state-overview mode.
     */
    public static Color mutedHint(Color base, Color background)
    {
        if (base == null)
        {
            return null;
        }
        Color bg = (background != null) ? background : new Color(30, 30, 30);
        Color blended = blend(bg, base, 0.35f);
        Color darkened = scaleRgb(blended, 0.92f);
        return withAlpha(darkened, 210);
    }

    private static Color withAlpha(Color c, int alpha)
    {
        if (c == null)
        {
            return null;
        }
        int a = clamp(alpha, 0, 255);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static Color blend(Color a, Color b, float weightB)
    {
        float w = clamp(weightB, 0f, 1f);
        int r = Math.round(a.getRed() * (1f - w) + b.getRed() * w);
        int g = Math.round(a.getGreen() * (1f - w) + b.getGreen() * w);
        int bl = Math.round(a.getBlue() * (1f - w) + b.getBlue() * w);
        return new Color(clamp(r, 0, 255), clamp(g, 0, 255), clamp(bl, 0, 255));
    }

    private static Color scaleRgb(Color c, float factor)
    {
        float f = clamp(factor, 0f, 2f);
        int r = Math.round(c.getRed() * f);
        int g = Math.round(c.getGreen() * f);
        int b = Math.round(c.getBlue() * f);
        return new Color(clamp(r, 0, 255), clamp(g, 0, 255), clamp(b, 0, 255));
    }

    private static int clamp(int v, int lo, int hi)
    {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(float v, float lo, float hi)
    {
        return Math.max(lo, Math.min(hi, v));
    }
}
