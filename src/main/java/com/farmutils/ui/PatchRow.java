package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.model.PatchId;
import com.farmutils.model.PatchRecord;
import com.farmutils.model.PatchState;
import com.farmutils.model.PatchView;
import com.farmutils.storage.PatchStore;
import com.farmutils.storage.UiStateStore;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ItemID;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.runelite.client.util.AsyncBufferedImage;

public class PatchRow extends JPanel
{
    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;

    public static final String PROP_PATCH_DRAG_HANDLE = "farmutils.patchDragHandle";

    private final UiStateStore uiStateStore;
    private final PatchView view;
    private JLabel titleLabel;
    private JLabel indicatorLabel;
    private JComponent iconComponent;
    private JComponent swatchComponent;

    public PatchRow(PatchId id, PatchStore store, UiStateStore uiStateStore, ItemManager itemManager, FarmutilsConfig config, Runnable onChange)
    {
        this(id, store, uiStateStore, itemManager, config, true, null, null, onChange);
    }

    public PatchRow(PatchId id, PatchStore store, UiStateStore uiStateStore, ItemManager itemManager, FarmutilsConfig config, boolean showIndicator, Runnable onChange)
    {
        this(id, store, uiStateStore, itemManager, config, showIndicator, null, null, onChange);
    }

    /**
     * @param titleSuffix Optional suffix appended to the primary title line (e.g. " · Catherby").
     * @param indicatorOverride Optional replacement for the secondary/indicator line.
     *                          If null, the indicator shows patch state (Unknown/Growing/etc.).
     */
    public PatchRow(PatchId id, PatchStore store, UiStateStore uiStateStore, ItemManager itemManager, FarmutilsConfig config, boolean showIndicator, String titleSuffix, String indicatorOverride, Runnable onChange)
    {
        this.uiStateStore = uiStateStore;
        this.view = store.view(id);

        PatchView view = this.view;

        float scale = config.textScale().multiplier();

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        int padY = Math.max(4, Math.round(PAD_Y * scale));
        int padX = Math.max(6, Math.round(PAD_X * scale));

        setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);
        leftCol.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel();
        this.titleLabel = title;
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
        this.indicatorLabel = indicator;
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

        // Icon is on the right side (left of swatch). Size it relative to the row's text stack
        // so it feels "row-attached" and roughly matches the swatch's visual height.
        int contentH = titleH + (showIndicator ? indH : 0);
        int iconSize = clamp(Math.round((contentH + padY) * 0.90f), 16, 32);

        // Small horizontal gap between icon and swatch.
        int gapAfterIcon = clamp(Math.round(3 * scale), 2, 6);

        setBorder(BorderFactory.createEmptyBorder(padY, padX, padY, padX));





        JLabel iconLabel = new JLabel();
        this.iconComponent = iconLabel;
        iconLabel.setOpaque(false);
        iconLabel.setAlignmentY(0.5f);

// Keep layout stable whether the sprite is available or not.
        Dimension iconDim = new Dimension(iconSize, iconSize);
        iconLabel.setPreferredSize(iconDim);
        iconLabel.setMinimumSize(iconDim);
        iconLabel.setMaximumSize(iconDim);

// Default placeholder (stable layout).
        iconLabel.setText("?");
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        iconLabel.setFont(UiFont.scaled(iconLabel.getFont(), scale * 0.9f, Font.PLAIN));

// Async item sprite pipeline (RuneLite): getImage returns an AsyncBufferedImage.
        AsyncBufferedImage asyncImg = (itemManager != null) ? itemManager.getImage(ItemID.WEEDS) : null;
        if (asyncImg != null)
        {
            // Keep the label repainting while the image loads.
            asyncImg.onLoaded(() ->
            {
                Image scaled = asyncImg.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH);
                SwingUtilities.invokeLater(() ->
                {
                    iconLabel.setText(null);
                    iconLabel.setIcon(new ImageIcon(scaled));
                    iconLabel.revalidate();
                    iconLabel.repaint();
                });
            });
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

// Highlight swatch: a row-attached vertical bar just left of the drag handle.
        int swatchWidth = clamp(Math.round(6 * scale), 4, 10);
        JPanel swatch = new JPanel();
        this.swatchComponent = swatch;
        swatch.setOpaque(true);
        swatch.setBorder(null);
        swatch.setPreferredSize(new Dimension(swatchWidth, 1));
        swatch.setMinimumSize(new Dimension(swatchWidth, 1));
        swatch.setMaximumSize(new Dimension(swatchWidth, Integer.MAX_VALUE));

        applySwatchColor(swatch, store.getHighlightSlot(id));

// Swatch cycles highlight slot on left click. Right-click should fall through to row menu.
        swatch.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger())
                {
                    store.cycleHighlightSlot(id);
                    onChange.run();
                    e.consume();
                }
            }
        });

        JPanel rightCol = new JPanel();
        rightCol.setOpaque(false);
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.X_AXIS));

        swatch.setAlignmentY(0.5f);
        dragHandle.setAlignmentY(0.5f);

        // Icon sits immediately left of the swatch to avoid left-column alignment drift across views.
        iconLabel.setAlignmentY(0.5f);
        swatch.setAlignmentY(0.5f);
        dragHandle.setAlignmentY(0.5f);

        rightCol.add(iconLabel);
        rightCol.add(Box.createRigidArea(new Dimension(gapAfterIcon, 1)));
        rightCol.add(swatch);
        rightCol.add(dragHandle);

        add(rightCol, BorderLayout.EAST);
        String tooltip = buildTooltip(view);
        setToolTipText(tooltip);
        title.setToolTipText(tooltip);
        indicator.setToolTipText(tooltip);

        // Apply state-text coloring for the modes that are meant to do so.
        applyIndicatorColorFromMode();

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

        menu.addSeparator();

        JMenuItem highlightNone = new JMenuItem("Highlight: None");
        highlightNone.addActionListener(e ->
        {
            store.setHighlightSlot(id, 0);
            onChange.run();
        });
        menu.add(highlightNone);

        for (int slot = 1; slot <= 4; slot++)
        {
            final int s = slot;
            JMenuItem item = new JMenuItem("Highlight: Slot " + slot);
            item.addActionListener(e ->
            {
                store.setHighlightSlot(id, s);
                onChange.run();
            });
            menu.add(item);
        }

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
            case DISEASED: return "Diseased";
            case EMPTY: return "Empty";
            case DEAD: return "Dead";
            default: return state.name();
        }
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private void applySwatchColor(JPanel swatch, int slot)
    {
        if (slot <= 0)
        {
            // Slot 0 = no highlight
            swatch.setBackground(getBackground());
            return;
        }

        // Simple muted palette (temporary, runtime only)
        switch (slot)
        {
            case 1:
                swatch.setBackground(new Color(120, 160, 255));
                break;
            case 2:
                swatch.setBackground(new Color(140, 200, 140));
                break;
            case 3:
                swatch.setBackground(new Color(220, 180, 120));
                break;
            case 4:
                swatch.setBackground(new Color(200, 120, 200));
                break;
            default:
                swatch.setBackground(getBackground());
        }
    }


    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        if (uiStateStore == null)
        {
            return;
        }

        UiStateStore.StateIndicatorMode mode = uiStateStore.getStateIndicatorMode();
        if (mode == UiStateStore.StateIndicatorMode.OFF)
        {
            return;
        }

        Color c = stateColor(view);
        if (c == null)
        {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try
        {
            g2.setColor(c);

            // Draw just above the bottom divider line so we don't get visually "merged" or overdrawn.
            int y = Math.max(0, getHeight() - 2);

            // Canonical margins: match the title text indent (grouped rows have additional indent).
            // This keeps grouped/non-grouped rows consistent relative to their visible content.
            int margin = computeContentMargin();
            int xL = margin;
            int xR = getWidth() - margin - 1;

            if (mode == UiStateStore.StateIndicatorMode.FULL_WIDTH || mode == UiStateStore.StateIndicatorMode.FULL_AND_TITLE)
            {
                g2.drawLine(xL, y, xR, y);
            }

            if (mode == UiStateStore.StateIndicatorMode.RIGHT_STRIP)
            {
                drawRightStripLine(g2, y);
            }

            if (mode == UiStateStore.StateIndicatorMode.FULL_AND_TITLE)
            {
                drawTitleLine(g2, y);
            }
        }
        finally
        {
            g2.dispose();
        }
    }

    private int computeContentMargin()
    {
        // Prefer the actual title label x-position (stable across views and reflects grouping indent).
        if (titleLabel != null)
        {
            Rectangle r = SwingUtilities.convertRectangle(titleLabel.getParent(), titleLabel.getBounds(), this);
            return Math.max(0, r.x);
        }

        Insets in = getInsets();
        return (in != null) ? in.left : 0;
    }

    private void drawRightStripLine(Graphics2D g2, int y)
    {
        // Right-strip mode: only under the icon region (explicitly *not* including the swatch).
        if (iconComponent == null)
        {
            return;
        }

        Rectangle iconR = SwingUtilities.convertRectangle(iconComponent.getParent(), iconComponent.getBounds(), this);
        int x1 = iconR.x;
        int x2 = iconR.x + iconR.width - 1;
        g2.drawLine(x1, y, x2, y);
    }

    private void drawTitleLine(Graphics2D g2, int y)
    {
        if (titleLabel == null)
        {
            return;
        }

        Rectangle r = SwingUtilities.convertRectangle(titleLabel.getParent(), titleLabel.getBounds(), this);
        int prefW = titleLabel.getPreferredSize() != null ? titleLabel.getPreferredSize().width : r.width;
        int w = Math.min(prefW, r.width);

        int x1 = r.x;
        int x2 = r.x + Math.max(0, w);
        g2.drawLine(x1, y, x2, y);
    }

    private static Color stateColor(PatchView view)
    {
        if (view == null || !view.getRecord().isPresent())
        {
            return null;
        }

        PatchState s = view.getRecord().get().getState();
        // Muted semantic colors; can be centralized later.
        switch (s)
        {
            case READY:
                return new Color(90, 170, 110);
            case GROWING:
                return new Color(110, 150, 210);
            case DISEASED:
                return new Color(190, 150, 70);
            case DEAD:
                return new Color(200, 90, 90);
            case EMPTY:
                return new Color(170, 170, 170);
            default:
                return null;
        }
    }

    private void applyIndicatorColorFromMode()
    {
        if (uiStateStore == null || indicatorLabel == null)
        {
            return;
        }

        UiStateStore.StateIndicatorMode mode = uiStateStore.getStateIndicatorMode();
        Color c = stateColor(view);

        if (c != null && (mode == UiStateStore.StateIndicatorMode.TITLE_ONLY || mode == UiStateStore.StateIndicatorMode.FULL_AND_TITLE))
        {
            indicatorLabel.setForeground(c);
        }
        else
        {
            indicatorLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }
    }

}