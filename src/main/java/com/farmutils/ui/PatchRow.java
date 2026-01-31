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

    public PatchRow(PatchId id, PatchStore store, FarmutilsConfig config, Runnable onChange)
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

        JLabel title = new JLabel(id.getGroup() + " — " + id.getLabel());
        title.setOpaque(false);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setFont(UiFont.scaled(title.getFont(), scale, Font.PLAIN));

        JLabel indicator = new JLabel(indicatorText(view));
        indicator.setOpaque(false);
        indicator.setAlignmentX(Component.LEFT_ALIGNMENT);
        indicator.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        indicator.setFont(UiFont.scaled(indicator.getFont(), scale * 0.95f, Font.PLAIN));

        leftCol.add(title);
        leftCol.add(indicator);

        add(leftCol, BorderLayout.CENTER);

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
                if (e.isPopupTrigger())
                {
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                if (e.isPopupTrigger())
                {
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
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
