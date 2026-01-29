package com.farmutils.ui;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchView;
import com.farmutils.storage.PatchStore;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

public class PatchRow extends JPanel
{
    private static final long STALE_MILLIS = Duration.ofDays(7).toMillis();

    public PatchRow(PatchId id, PatchStore store, Runnable onChange)
    {
        setLayout(new BorderLayout());

        JLabel left = new JLabel(id.getGroup() + " — " + id.getLabel());
        PatchView view = store.view(id);
        Optional<PatchRecord> record = view.getRecord();
        JLabel right = new JLabel(formatState(view));

        String tooltip = buildTooltip(view);
        setToolTipText(tooltip);
        right.setToolTipText(tooltip);
        left.setToolTipText(tooltip);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);

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

    private static String buildTooltip(PatchView view)
    {
        if (!view.getRecord().isPresent())
        {
            return "Unknown: no record for this patch yet.";
        }

        PatchState state = view.getRecord().get().getState();
        String stateLine;

        switch (state)
        {
            case EMPTY:
                stateLine = "Empty: you checked this patch and recorded nothing planted.";
                break;
            case GROWING:
                stateLine = "Growing: you recorded this patch as in progress.";
                break;
            case READY:
                stateLine = "Ready: you recorded this patch as harvestable.";
                break;
            case DEAD:
                stateLine = "Dead: you recorded this patch as dead.";
                break;
            default:
                stateLine = state.name();
        }

        // Source is calm + factual; doesn’t shout.
        return stateLine + " Source: " + prettySource(view.getSource()) + ".";
    }
    private static String prettySource(com.farmutils.model.PatchSource source)
    {
        switch (source)
        {
            case MANUAL: return "Manual";
            case INFERRED: return "Inferred";
            case UNKNOWN: return "Unknown";
            default: return source.name();
        }
    }


    private static String formatState(PatchView view)
    {
        Optional<PatchRecord> record = view.getRecord();
        if (!record.isPresent())
        {
            return "Unknown";
        }

        String base = pretty(record.get().getState());
        return base + (view.isStale() ? " (Stale)" : "");
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
