package com.farmutils.ui;

import com.farmutils.model.PatchId;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class FarmPanel extends PluginPanel
{
    @Inject
    private UiStateStore uiStateStore;

    @Inject
    private PatchStore store;

    private final JPanel list = new JPanel();

    public FarmPanel()
    {
        super(false);

        setBorder(null);
        setLayout(new BorderLayout());

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        // Key: stop the list/viewport from painting a light background
        setOpaque(false);
        list.setOpaque(false);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(scroll, BorderLayout.CENTER);
    }

    public void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            list.removeAll();

            Map<String, List<PatchId>> grouped = Arrays.stream(PatchId.values())
                    .sorted(Comparator.comparing(PatchId::getGroup).thenComparing(PatchId::getLabel))
                    .collect(Collectors.groupingBy(
                            PatchId::getGroup,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            boolean firstGroup = true;

            for (Map.Entry<String, List<PatchId>> entry : grouped.entrySet())
            {
                String groupName = entry.getKey();
                boolean collapsed = uiStateStore.isGroupCollapsed(groupName);

                if (!firstGroup)
                {
                    list.add(fullWidth(Box.createVerticalStrut(8)));
                }
                firstGroup = false;

                list.add(fullWidth(createGroupHeader(groupName, collapsed)));
                list.add(fullWidth(Box.createVerticalStrut(4)));

                if (!collapsed)
                {
                    for (PatchId id : entry.getValue())
                    {
                        list.add(fullWidth(new PatchRow(id, store, this::rebuild)));
                    }
                }
            }

            list.add(fullWidth(Box.createVerticalStrut(6)));

            list.revalidate();
            list.repaint();
        });
    }

    private static JPanel fullWidth(java.awt.Component child)
    {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(child, BorderLayout.CENTER);
        wrap.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
        return wrap;
    }


    private Component createGroupHeader(String groupName, boolean collapsed)
    {
        String text = (collapsed ? "▸ " : "▾ ") + groupName;

        JLabel label = new JLabel(text);
        label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        label.setHorizontalAlignment(SwingConstants.LEFT);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        label.setOpaque(false);
        label.setForeground(label.getForeground().darker());

        label.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                uiStateStore.toggleGroupCollapsed(groupName);
                rebuild();
            }
        });

        return label;
    }

}
