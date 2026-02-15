package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchView;
import com.farmutils.storage.PatchStore;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class PatchRow extends JPanel
{
    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;

    public static final String PROP_PATCH_DRAG_HANDLE = "farmutils.patchDragHandle";

    public PatchRow(PatchId id, PatchStore store, FarmutilsConfig config, Runnable onChange)
    {
        this(id, store, config, true, null, null, onChange);
    }

    public PatchRow(PatchId id, PatchStore store, FarmutilsConfig config, boolean showIndicator, Runnable onChange)
    {
        this(id, store, config, showIndicator, null, null, onChange);
    }

    /**
     * @param titleSuffix Optional suffix appended to the primary title line (e.g. " · Catherby").
     * @param indicatorOverride Optional replacement for the secondary/indicator line.
     *                          If null, the indicator shows patch state (Unknown/Growing/etc.).
     */
    public PatchRow(PatchId id, PatchStore store, FarmutilsConfig config, boolean showIndicator, String titleSuffix, String indicatorOverride, Runnable onChange)
    {
        PatchView view = store.view(id);

        float scale = config.textScale().multiplier();

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        int padY = Math.max(4, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));
        setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));

        setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);
        leftCol.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel();
        title.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setFont(UiFont.scaled(title.getFont(), scale, Font.PLAIN));

        String titleText;
        if (config.showPatchCategoryPrefix())
        {
            titleText = id.getGroup() + " - " + id.getLabel();
        }
        else
        {
            titleText = id.getLabel();
        }

        if (titleSuffix != null && !titleSuffix.isBlank())
        {
            titleText = titleText + titleSuffix;
        }
        title.setText(titleText);

        String secondaryText = (indicatorOverride != null) ? indicatorOverride : indicatorText(view);
        JLabel indicator = new JLabel(secondaryText);
        indicator.setOpaque(false);
        indicator.setAlignmentX(Component.LEFT_ALIGNMENT);
        indicator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        indicator.setFont(UiFont.scaled(indicator.getFont(), scale * 0.95f, Font.PLAIN));

        // Prevent long titles from inflating preferred width (causes RuneLite viewport inset/border flips).
// We deliberately allow clipping for now; full text remains available via tooltip.
        Dimension titlePref = title.getPreferredSize();
        int titleH = titlePref.height;

        Dimension indPref = indicator.getPreferredSize();
        int indH = indPref.height;

        Dimension pref = getPreferredSize();
        setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
        indicator.setMinimumSize(new Dimension(0, indH));


        title.setPreferredSize(new Dimension(0, titleH));
        title.setMinimumSize(new Dimension(0, titleH));
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleH));


        leftCol.add(title);
        if (showIndicator)
        {
            leftCol.add(indicator);
        }

        add(leftCol, BorderLayout.CENTER);

        // Drag handle (hidden unless reorder mode is enabled by caller).
                JLabel dragHandle = new JLabel("⋮⋮");
        dragHandle.setOpaque(false);
        dragHandle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        dragHandle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        dragHandle.setVisible(false);
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        putClientProperty(PROP_PATCH_DRAG_HANDLE, dragHandle);
        add(dragHandle, BorderLayout.EAST);

        String tooltip = buildTooltip(view);
        setToolTipText(tooltip);
        title.setToolTipText(tooltip);
        indicator.setToolTipText(tooltip);

        JPopupMenu menu = new JPopupMenu();

        for (PatchState state : PatchState.values())
        {
            JMenuItem item = new JMenuItem("Set: " + pretty(state));
            item.addActionListener(e ->
            {
                store.save(id, state);
                onChange.run();
            });
            menu.add(item);
        }

        menu.addSeparator();

        JMenuItem clear = new JMenuItem("Clear (Unknown)");
        clear.addActionListener(e ->
        {
            store.clear(id);
            onChange.run();
        });
        menu.add(clear);

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (e.isConsumed())
                {
                    return;
                }
                if (e.isPopupTrigger())
                {
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isConsumed())
                {
                    return;
                }
                if (e.isPopupTrigger())
                {
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    public void setReorderHandleVisible(boolean visible)
    {
        Object h = getClientProperty(PROP_PATCH_DRAG_HANDLE);
        if (h instanceof JComponent)
            {
                        ((JComponent) h).setVisible(visible);
        }
    }

    private static String indicatorText(PatchView view)
    {
        if (!view.getRecord().isPresent())
        {
            return "Unknown";
        }

        String base = pretty(view.getRecord().get().getState());
        return view.isStale() ? base + " · Stale" : base;
    }

    private static String buildTooltip(PatchView view)
    {
        if (!view.getRecord().isPresent())
        {
            return "Unknown: no record for this patch yet. Updated: Never.";
        }

        PatchRecord record = view.getRecord().get();
        String updated = "Updated: " + timeAgo(record.getUpdatedAtMillis()) + ".";
        String stale = view.isStale() ? " (Stale)" : "";

        return pretty(record.getState()) + stale + ". " + updated + " Source: " + view.getSource().name() + ".";
    }

    private static String timeAgo(long updatedAtMillis)
    {
        Instant then = Instant.ofEpochMilli(updatedAtMillis);
        Instant now = Instant.now();

        long minutes = ChronoUnit.MINUTES.between(then, now);
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";

        long hours = ChronoUnit.HOURS.between(then, now);
        if (hours < 24) return hours + "h ago";

        long days = ChronoUnit.DAYS.between(then, now);
        return days + "d ago";
    }

    private static String pretty(PatchState state)
    {
        switch (state)
        {
            case GROWING: return "Growing";
            case READY: return "Ready";
            case EMPTY: return "Empty";
            case DEAD: return "Dead";
            default: return state.name();
        }
    }
}
