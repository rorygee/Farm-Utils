package com.farmutils.ui;

import com.farmutils.model.PatchId;
import com.farmutils.storage.PatchStore;
import java.awt.BorderLayout;
import java.util.Arrays;
import javax.inject.Inject;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import javax.inject.Singleton;

@Singleton
public class FarmPanel extends PluginPanel
{
    @Inject
    private PatchStore store;

    private final JPanel list = new JPanel();

    public FarmPanel()
    {
        super(false);
        setLayout(new BorderLayout());
        list.setLayout(new javax.swing.BoxLayout(list, javax.swing.BoxLayout.Y_AXIS));
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public void rebuild()
    {
        SwingUtilities.invokeLater(() ->
        {
            list.removeAll();
            Arrays.stream(PatchId.values()).forEach(id ->
            {
                list.add(new PatchRow(id, store, this::rebuild));
            });
            list.revalidate();
            list.repaint();
        });
    }
}
