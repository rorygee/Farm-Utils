package com.farmutils.ui;

import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
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
        JLabel right = new JLabel(formatState(store.load(id)));

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

    private static String formatState(Optional<PatchRecord> record)
    {
        if (!record.isPresent())
        {
            return "Unknown";
        }

        PatchRecord r = record.get();
        long age = System.currentTimeMillis() - r.getUpdatedAtMillis();
        boolean stale = age > STALE_MILLIS;

        return pretty(r.getState()) + (stale ? " (Stale)" : "");
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
