package com.farmutils.ui;

import com.farmutils.FarmutilsConfig;
import com.farmutils.calc.CalcBreakdownCategory;
import com.farmutils.calc.CalcBreakdownRow;
import com.farmutils.calc.CalcBreakdownStat;
import com.farmutils.calc.CalcCatalogue;
import com.farmutils.calc.CalcCompostTier;
import com.farmutils.calc.CalcCropDefinition;
import com.farmutils.calc.CalcExpectedYieldResult;
import com.farmutils.calc.CalcItemRef;
import com.farmutils.calc.CalcItemStack;
import com.farmutils.calc.CalcOutputDefinition;
import com.farmutils.calc.CalcOutputRole;
import com.farmutils.calc.CalcPatchBreakdownResult;
import com.farmutils.calc.CalcRouteBreakdownResult;
import com.farmutils.model.PatchId;
import com.farmutils.route.Route;
import com.farmutils.route.RouteId;
import com.farmutils.route.RouteStore;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.http.api.item.ItemPrice;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


/**
 * Route-planning calculator shell.
 *
 * Stage 2 scope only:
 * - route selection from an in-panel filter
 * - placeholder summary lines
 * - full-width editing container
 * - grouped route list
 * - patch type and patch row selection model
 * - explicit unresolved / inherited / override display states
 */
public class CalcPanel extends JPanel
{
    private static final String FILTER_PLACEHOLDER = "Filter routes…";
    private static final String PROP_FILTER_BASE_FONT = "farmutils.calcFilterBaseFont";

    private final FarmutilsConfig config;
    private final RouteStore routeStore;
    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final Client client;

    private final JPanel chrome = new JPanel();
    private final JPanel filterRow = new JPanel(new BorderLayout());
    private final JTextField filterField = new JTextField();
    private final JPanel toolbarRow = new JPanel(new BorderLayout());
    private final JButton clearRouteButton = new JButton("Clear route");

    private final DefaultListModel<RouteListItem> filterResultsModel = new DefaultListModel<>();
    private final JList<RouteListItem> filterResultsList = new JList<>(filterResultsModel);
    private final JScrollPane filterResultsScroll = new JScrollPane(
            filterResultsList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    );
    private final JPopupMenu filterPopup = new JPopupMenu();

    private final JPanel content = new JPanel();
    private final JPanel summaryPanel = new JPanel();
    private final JLabel profitValue = new JLabel("0 gp");
    private final JLabel costsValue = new JLabel("0 gp");
    private final JLabel revenueValue = new JLabel("0 gp");
    private final JLabel xpValue = new JLabel("0 xp");
    private final JLabel statusValue = new JLabel("Incomplete");
    private final Map<CalcBreakdownStat, JPanel> summaryBreakdownPanels = new EnumMap<>(CalcBreakdownStat.class);
    private final Map<CalcBreakdownStat, JLabel> summaryToggleLabels = new EnumMap<>(CalcBreakdownStat.class);
    private final Set<CalcBreakdownStat> expandedBreakdownStats = EnumSet.noneOf(CalcBreakdownStat.class);

    private static final String UNKNOWN_CROP_LABEL = "Unknown";
    private static final String INHERITED_MODIFIER_LABEL = "Inherited";
    private static final String MODIFIER_ENABLED_LABEL = "Enabled";
    private static final String MODIFIER_DISABLED_LABEL = "Disabled";
    private static final String MODIFIER_BOTTOMLESS_BUCKET = "bottomless_bucket";
    private static final String MODIFIER_PROTECTION_PAYMENT = "protection_payment";
    private static final String MODIFIER_MAGIC_SECATEURS = "magic_secateurs";
    private static final String MODIFIER_FARMING_CAPE = "farming_cape";
    private static final String MODIFIER_ATTAS = "attas";
    private static final String MODIFIER_AMULET_OF_BOUNTY = "amulet_of_bounty";
    private static final String FARMING_LEVEL_AUTO_LABEL_PREFIX = "Auto (";

    private final JPanel editingPanel = new JPanel();
    private final JLabel editingTitleLabel = new JLabel("Editing: Select a route");
    private final JTextArea editingHintLabel = buildHintTextArea("Choose a route from the filter above.");
    private final JTextArea editingStateHintLabel = buildHintTextArea("");
    private final Component editingAfterTitleSpacer = Box.createVerticalStrut(10);
    private final Component editingAfterHintSpacer = Box.createVerticalStrut(6);
    private final Component editingAfterCropSpacer = Box.createVerticalStrut(6);
    private final Component editingAfterCompostSpacer = Box.createVerticalStrut(6);
    private final Component editingAfterModifiersSpacer = Box.createVerticalStrut(6);
    private final Component editingAfterActionsSpacer = Box.createVerticalStrut(4);
    private final JLabel editingCropLabel = new JLabel("Crop:");
    private final JLabel editingCompostLabel = new JLabel("Compost:");
    private final JPanel editingControlsRow = new JPanel(new BorderLayout(8, 0));
    private final JPanel editingCompostRow = new JPanel(new BorderLayout(8, 0));
    private final JPanel editingModifierRows = new JPanel();
    private final JComboBox<String> editingCropDropdown = new JComboBox<>();
    private final JComboBox<String> editingCompostDropdown = new JComboBox<>();
    private final JPanel editingActionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JButton clearOverrideButton = new JButton("Clear override");

    private final TrackViewportWidthPanel list = new TrackViewportWidthPanel();
    private final JScrollPane scrollPane;

    private RouteId selectedRouteId;
    private Route selectedRoute;
    private String selectedGroup;
    private PatchId selectedPatchId;
    private boolean suppressFilterEvents;
    private boolean suppressCropDropdownEvents;
    private boolean suppressCompostDropdownEvents;
    private boolean suppressFarmingLevelDropdownEvents;
    private final Map<RouteId, RouteCalcState> routeCalcStates = new LinkedHashMap<>();
    private final Map<RouteId, Set<String>> routeCollapsedGroups = new LinkedHashMap<>();
    private final Map<Integer, Integer> itemPriceCache = new HashMap<>();
    private final Set<Integer> pendingPriceLoads = new HashSet<>();
    private final Map<String, Integer> itemIdSearchCache = new HashMap<>();
    private final Set<String> pendingItemSearches = new HashSet<>();
    private CalcRouteBreakdownResult latestRouteBreakdown = CalcRouteBreakdownResult.incomplete();

    public CalcPanel(final FarmutilsConfig config, final RouteStore routeStore, final ItemManager itemManager, final ClientThread clientThread, final Client client)
    {
        super(new BorderLayout());
        this.config = Objects.requireNonNull(config, "config");
        this.routeStore = Objects.requireNonNull(routeStore, "routeStore");
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.client = client;

        setOpaque(true);
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(null);

        chrome.setLayout(new BoxLayout(chrome, BoxLayout.Y_AXIS));
        chrome.setOpaque(true);
        chrome.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buildFilterRow();
        buildToolbarRow();
        chrome.add(filterRow);
        chrome.add(toolbarRow);
        chrome.add(UiTokens.divider());
        add(chrome, BorderLayout.NORTH);

        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        buildSummaryPanel();
        buildEditingPanel();

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(summaryPanel);
        content.add(Box.createVerticalStrut(6));
        content.add(editingPanel);
        content.add(Box.createVerticalStrut(6));
        content.add(list);

        final TrackViewportWidthPanel container = new TrackViewportWidthPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(content, BorderLayout.NORTH);

        scrollPane = new JScrollPane(
                container,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setBorder(null);
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        UiScrollbars.apply(scrollPane, config);
        add(scrollPane, BorderLayout.CENTER);

        wireFilter();
        refreshUiFromConfig();
        rebuildForRoute(null);
    }

    public void refreshFromStore()
    {
        if (selectedRouteId == null)
        {
            rebuildForRoute(null);
            return;
        }

        final Optional<Route> routeOpt = routeStore.get(selectedRouteId);
        if (!routeOpt.isPresent())
        {
            rebuildForRoute(null);
            return;
        }

        rebuildForRoute(selectedRouteId);
    }

    public void refreshUiFromConfig()
    {
        applyFilterSizing();
        styleComboBox(editingCropDropdown, textScale());
        refreshEditingPanel();
        renderBreakdownSections();
        UiScrollbars.apply(scrollPane, config);
        UiScrollbars.apply(filterResultsScroll, config);
        revalidate();
        repaint();
    }

    @Override
    public void addNotify()
    {
        super.addNotify();
        refreshFromStore();
    }

    private void buildFilterRow()
    {
        filterRow.setOpaque(true);
        filterRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        filterRow.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        filterField.setOpaque(false);
        filterField.setMargin(new Insets(0, 0, 0, 0));
        filterField.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 8));
        filterField.setCaretColor(ColorScheme.TEXT_COLOR);
        filterField.setToolTipText("Search and select a route from the Routes panel.");
        showFilterPlaceholder();
        filterField.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusGained(final FocusEvent e)
            {
                if (isFilterPlaceholderActive())
                {
                    suppressFilterEvents = true;
                    try
                    {
                        filterField.setText("");
                        filterField.setForeground(ColorScheme.TEXT_COLOR);
                    }
                    finally
                    {
                        suppressFilterEvents = false;
                    }
                }
            }

            @Override
            public void focusLost(final FocusEvent e)
            {
                SwingUtilities.invokeLater(() ->
                {
                    if (!filterField.isFocusOwner())
                    {
                        if (filterField.getText().trim().isEmpty())
                        {
                            showFilterPlaceholder();
                        }
                        hideFilterPopup();
                    }
                });
            }
        });

        filterRow.add(filterField, BorderLayout.CENTER);

        filterPopup.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));
        filterPopup.setFocusable(false);
        filterPopup.setOpaque(true);
        filterPopup.setBackground(ColorScheme.DARK_GRAY_COLOR);

        filterResultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        filterResultsList.setCellRenderer(new RouteListCellRenderer());
        filterResultsList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterResultsList.setForeground(ColorScheme.TEXT_COLOR);
        filterResultsList.setSelectionBackground(ColorScheme.DARKER_GRAY_COLOR);
        filterResultsList.setSelectionForeground(ColorScheme.TEXT_COLOR);
        filterResultsList.setFixedCellHeight(-1);
        filterResultsList.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(final MouseEvent e)
            {
                if (!SwingUtilities.isLeftMouseButton(e))
                {
                    return;
                }
                final RouteListItem item = filterResultsList.getSelectedValue();
                if (item != null)
                {
                    applyRouteSelection(item.routeId);
                }
            }
        });

        filterResultsScroll.setBorder(BorderFactory.createEmptyBorder());
        filterResultsScroll.setViewportBorder(BorderFactory.createEmptyBorder());
        filterResultsScroll.setPreferredSize(new Dimension(320, 180));
        filterResultsScroll.setOpaque(true);
        filterResultsScroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterResultsScroll.getViewport().setOpaque(true);
        filterResultsScroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        filterResultsScroll.getVerticalScrollBar().setUnitIncrement(16);
        UiScrollbars.apply(filterResultsScroll, config);
        filterPopup.add(filterResultsScroll);
    }

    private void buildToolbarRow()
    {
        toolbarRow.setOpaque(true);
        toolbarRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        toolbarRow.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);

        clearRouteButton.setFocusable(false);
        clearRouteButton.setEnabled(false);
        clearRouteButton.addActionListener(e -> clearRouteSelection());
        buttons.add(clearRouteButton);

        final JLabel hint = new JLabel("Selected route only");
        hint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        hint.setBorder(new EmptyBorder(0, 8, 0, 0));

        toolbarRow.add(buttons, BorderLayout.WEST);
        toolbarRow.add(hint, BorderLayout.CENTER);
    }

    private void buildSummaryPanel()
    {
        summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
        summaryPanel.setOpaque(false);
        summaryPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        summaryPanel.removeAll();
        summaryBreakdownPanels.clear();
        summaryToggleLabels.clear();

        summaryPanel.add(summaryValueLine("Profit:", profitValue));
        summaryPanel.add(buildSummarySection(CalcBreakdownStat.COSTS, "Costs:", costsValue));
        summaryPanel.add(buildSummarySection(CalcBreakdownStat.REVENUE, "Revenue:", revenueValue));
        summaryPanel.add(UiTokens.divider(ColorScheme.DARKER_GRAY_COLOR));
        summaryPanel.add(buildSummarySection(CalcBreakdownStat.XP, "XP:", xpValue));
        summaryPanel.add(summaryValueLine("Route status:", statusValue));
        renderBreakdownSections();
    }

    private JPanel buildSummarySection(final CalcBreakdownStat stat, final String labelText, final JLabel valueLabel)
    {
        final JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);

        final JLabel toggleLabel = new JLabel("▸");
        toggleLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        toggleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final JLabel label = new JLabel(labelText);
        label.setForeground(ColorScheme.TEXT_COLOR);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        valueLabel.setForeground(ColorScheme.TEXT_COLOR);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        left.add(toggleLabel);
        left.add(label);
        row.add(left, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);

        final JPanel breakdownPanel = new JPanel();
        breakdownPanel.setLayout(new BoxLayout(breakdownPanel, BoxLayout.Y_AXIS));
        breakdownPanel.setOpaque(false);
        breakdownPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        breakdownPanel.setBorder(BorderFactory.createEmptyBorder(0, 16, 4, 0));
        breakdownPanel.setVisible(expandedBreakdownStats.contains(stat));

        installToggleOnly(row, () -> toggleBreakdownStat(stat));
        installToggleOnly(left, () -> toggleBreakdownStat(stat));
        installToggleOnly(toggleLabel, () -> toggleBreakdownStat(stat));
        installToggleOnly(label, () -> toggleBreakdownStat(stat));
        installToggleOnly(valueLabel, () -> toggleBreakdownStat(stat));

        summaryToggleLabels.put(stat, toggleLabel);
        summaryBreakdownPanels.put(stat, breakdownPanel);

        section.add(row);
        section.add(breakdownPanel);
        return section;
    }

    private JPanel summaryValueLine(final String labelText, final JLabel valueLabel)
    {
        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        final JLabel label = new JLabel(labelText);
        label.setForeground(ColorScheme.TEXT_COLOR);
        valueLabel.setForeground(ColorScheme.TEXT_COLOR);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(label, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
        return row;
    }

    private JTextArea buildHintTextArea(final String text)
    {
        final JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private void buildEditingPanel()
    {
        editingPanel.setLayout(new BoxLayout(editingPanel, BoxLayout.Y_AXIS));
        editingPanel.setOpaque(true);
        editingPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        editingPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR),
                BorderFactory.createEmptyBorder(10, 8, 8, 8)
        ));
        editingPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        editingPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        editingTitleLabel.setForeground(Color.WHITE);
        editingHintLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        editingHintLabel.setFont(UiFont.scaled(editingHintLabel.getFont(), textScale(), Font.PLAIN));
        editingCropLabel.setForeground(ColorScheme.TEXT_COLOR);
        editingCompostLabel.setForeground(ColorScheme.TEXT_COLOR);
        editingStateHintLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        editingStateHintLabel.setFont(UiFont.scaled(editingStateHintLabel.getFont(), textScale(), Font.PLAIN));

        editingCropDropdown.setFocusable(false);
        editingCropDropdown.setEnabled(false);
        editingCropDropdown.addActionListener(e ->
        {
            if (!suppressCropDropdownEvents)
            {
                applyEditingCropSelection();
            }
        });
        styleComboBox(editingCropDropdown, textScale());

        editingCompostDropdown.setFocusable(false);
        editingCompostDropdown.setEnabled(false);
        editingCompostDropdown.addActionListener(e ->
        {
            if (!suppressCompostDropdownEvents)
            {
                applyEditingCompostSelection();
            }
        });
        styleComboBox(editingCompostDropdown, textScale());

        clearOverrideButton.setFocusable(false);
        clearOverrideButton.setVisible(false);
        clearOverrideButton.setEnabled(false);
        clearOverrideButton.addActionListener(e -> clearSelectedPatchOverride());

        editingActionsRow.setOpaque(false);
        editingActionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        editingActionsRow.add(clearOverrideButton);
        editingActionsRow.setVisible(false);

        editingControlsRow.setOpaque(false);
        editingControlsRow.add(editingCropLabel, BorderLayout.WEST);
        editingControlsRow.add(editingCropDropdown, BorderLayout.CENTER);
        editingControlsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        editingControlsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        editingCompostRow.setOpaque(false);
        editingCompostRow.add(editingCompostLabel, BorderLayout.WEST);
        editingCompostRow.add(editingCompostDropdown, BorderLayout.CENTER);
        editingCompostRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        editingCompostRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        editingCompostRow.setVisible(false);

        editingModifierRows.setLayout(new BoxLayout(editingModifierRows, BoxLayout.Y_AXIS));
        editingModifierRows.setOpaque(false);
        editingModifierRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        editingModifierRows.setVisible(false);

        editingPanel.add(editingTitleLabel);
        editingPanel.add(editingAfterTitleSpacer);
        editingPanel.add(editingHintLabel);
        editingPanel.add(editingAfterHintSpacer);
        editingPanel.add(editingControlsRow);
        editingPanel.add(editingAfterCropSpacer);
        editingPanel.add(editingCompostRow);
        editingPanel.add(editingAfterCompostSpacer);
        editingPanel.add(editingModifierRows);
        editingPanel.add(editingAfterModifiersSpacer);
        editingPanel.add(editingActionsRow);
        editingPanel.add(editingAfterActionsSpacer);
        editingPanel.add(editingStateHintLabel);
    }

    private void wireFilter()
    {
        filterField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(final DocumentEvent e)
            {
                onFilterTextChanged();
            }

            @Override
            public void removeUpdate(final DocumentEvent e)
            {
                onFilterTextChanged();
            }

            @Override
            public void changedUpdate(final DocumentEvent e)
            {
                onFilterTextChanged();
            }
        });

        filterField.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(final KeyEvent e)
            {
                if (!filterPopup.isVisible())
                {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    {
                        selectFirstMatchingRoute();
                        e.consume();
                    }
                    return;
                }

                switch (e.getKeyCode())
                {
                    case KeyEvent.VK_DOWN:
                        movePopupSelection(1);
                        e.consume();
                        break;
                    case KeyEvent.VK_UP:
                        movePopupSelection(-1);
                        e.consume();
                        break;
                    case KeyEvent.VK_ENTER:
                        final RouteListItem item = filterResultsList.getSelectedValue();
                        if (item != null)
                        {
                            applyRouteSelection(item.routeId);
                        }
                        else
                        {
                            selectFirstMatchingRoute();
                        }
                        e.consume();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        hideFilterPopup();
                        e.consume();
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private String normalizedFilterQuery()
    {
        final String text = filterField.getText();
        if (text == null)
        {
            return "";
        }

        final String trimmed = text.trim();
        if (trimmed.isEmpty() || isFilterPlaceholderActive())
        {
            return "";
        }
        return trimmed;
    }

    private boolean isFilterPlaceholderActive()
    {
        return FILTER_PLACEHOLDER.equals(filterField.getText())
                && ColorScheme.MEDIUM_GRAY_COLOR.equals(filterField.getForeground());
    }

    private void showFilterPlaceholder()
    {
        filterField.setText(FILTER_PLACEHOLDER);
        filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        filterField.setCaretPosition(0);
    }

    private void onFilterTextChanged()
    {
        if (suppressFilterEvents)
        {
            return;
        }

        final String query = normalizedFilterQuery();
        if (query.isEmpty())
        {
            hideFilterPopup();
            return;
        }

        final List<RouteListItem> matches = computeFilterResults(query);
        filterResultsModel.clear();
        for (final RouteListItem item : matches)
        {
            filterResultsModel.addElement(item);
        }

        if (matches.isEmpty())
        {
            hideFilterPopup();
            return;
        }

        filterResultsList.setSelectedIndex(0);
        resizeFilterPopup();
        filterPopup.show(filterField, 0, filterField.getHeight());
        filterPopup.repaint();
    }

    private List<RouteListItem> computeFilterResults(final String query)
    {
        final String q = query.toLowerCase(Locale.ROOT);
        final List<RouteListItem> matches = new ArrayList<>();

        for (final Route route : routeStore.list())
        {
            if (route.getName().toLowerCase(Locale.ROOT).contains(q))
            {
                matches.add(new RouteListItem(route.getId(), route.getName(), route.getPatchIds().size()));
            }
        }
        return matches;
    }

    private void resizeFilterPopup()
    {
        final int width = Math.max(1, filterField.getWidth());
        final int rows = Math.min(8, Math.max(1, filterResultsModel.getSize()));
        final int rowHeight = Math.max(24, filterResultsList.getFontMetrics(filterResultsList.getFont()).getHeight() + 8);
        filterResultsScroll.setPreferredSize(new Dimension(width, rows * rowHeight + 4));
        filterPopup.setPopupSize(filterResultsScroll.getPreferredSize());
    }

    private void movePopupSelection(final int delta)
    {
        final int size = filterResultsModel.size();
        if (size <= 0)
        {
            return;
        }
        int index = filterResultsList.getSelectedIndex();
        if (index < 0)
        {
            index = 0;
        }
        index = Math.max(0, Math.min(size - 1, index + delta));
        filterResultsList.setSelectedIndex(index);
        filterResultsList.ensureIndexIsVisible(index);
    }

    private void hideFilterPopup()
    {
        filterPopup.setVisible(false);
    }

    private void selectFirstMatchingRoute()
    {
        final List<RouteListItem> matches = computeFilterResults(normalizedFilterQuery());
        if (!matches.isEmpty())
        {
            applyRouteSelection(matches.get(0).routeId);
        }
    }

    private void applyRouteSelection(final RouteId routeId)
    {
        hideFilterPopup();
        rebuildForRoute(routeId);
        if (selectedRoute != null)
        {
            suppressFilterEvents = true;
            try
            {
                filterField.setText(selectedRoute.getName());
                filterField.setForeground(ColorScheme.TEXT_COLOR);
                filterField.setCaretPosition(filterField.getText().length());
            }
            finally
            {
                suppressFilterEvents = false;
            }
        }
    }

    private void clearRouteSelection()
    {
        selectedRouteId = null;
        selectedRoute = null;
        selectedGroup = null;
        selectedPatchId = null;
        suppressFilterEvents = true;
        try
        {
            showFilterPlaceholder();
        }
        finally
        {
            suppressFilterEvents = false;
        }
        hideFilterPopup();
        rebuildForRoute(null);
    }

    private void rebuildForRoute(final RouteId routeId)
    {
        selectedRouteId = routeId;
        selectedRoute = routeId == null ? null : routeStore.get(routeId).orElse(null);
        if (selectedRoute == null)
        {
            selectedRouteId = null;
        }

        final Map<String, List<PatchId>> grouped = selectedRoute == null
                ? new LinkedHashMap<>()
                : groupRoutePatches(selectedRoute);

        if (grouped.isEmpty())
        {
            selectedGroup = null;
            selectedPatchId = null;
        }
        else if (selectedPatchId != null && grouped.containsKey(selectedPatchId.getGroup())
                && grouped.get(selectedPatchId.getGroup()).contains(selectedPatchId))
        {
            selectedGroup = selectedPatchId.getGroup();
        }
        else if (selectedGroup != null && grouped.containsKey(selectedGroup))
        {
            selectedPatchId = null;
        }
        else
        {
            selectedGroup = grouped.keySet().iterator().next();
            selectedPatchId = null;
        }

        clearRouteButton.setEnabled(selectedRoute != null);
        refreshCalculatedView(grouped);
    }

    private void refreshCalculatedView()
    {
        refreshCalculatedView(selectedRoute == null ? new LinkedHashMap<>() : groupRoutePatches(selectedRoute));
    }

    private void refreshCalculatedView(final Map<String, List<PatchId>> grouped)
    {
        primeCurrentRoutePriceData();
        renderTopSummary();
        refreshEditingPanel();
        rebuildGroupedList(grouped);
        revalidate();
        repaint();
    }

    private void primeCurrentRoutePriceData()
    {
        if (selectedRoute == null || itemManager == null || clientThread == null)
        {
            return;
        }

        final List<CalcItemRef> itemsToPrime = collectCurrentRoutePriceableItems();
        if (itemsToPrime.isEmpty())
        {
            return;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        clientThread.invokeLater(() ->
        {
            try
            {
                for (final CalcItemRef item : itemsToPrime)
                {
                    primeItemCachesOnClientThread(item);
                }
            }
            finally
            {
                latch.countDown();
            }
            return false;
        });

        try
        {
            latch.await(2, TimeUnit.SECONDS);
        }
        catch (final InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    private List<CalcItemRef> collectCurrentRoutePriceableItems()
    {
        final List<CalcItemRef> items = new ArrayList<>();
        if (selectedRoute == null)
        {
            return items;
        }

        final Set<String> seenKeys = new LinkedHashSet<>();
        for (final PatchId patchId : selectedRoute.getPatchIds())
        {
            if (patchId == null || !isSupportedCropGroup(patchId.getGroup()))
            {
                continue;
            }

            final String cropName = resolvedCropNameForPatch(patchId);
            if (cropName == null || cropName.trim().isEmpty())
            {
                continue;
            }

            final CalcCropDefinition crop = CalcCatalogue.cropFor(patchId.getGroup(), cropName);
            if (crop == null)
            {
                continue;
            }
            final CalcCompostTier compostTier = selectedCompostTierForPatch(patchId);
            if (compostTier != null && compostTier != CalcCompostTier.NONE)
            {
                final CalcItemRef compostItem = CalcCatalogue.compostItemFor(compostTier);
                if (compostItem != null)
                {
                    addItemToPrime(items, seenKeys, compostItem);
                }
            }

            for (final CalcItemStack input : crop.getPlantingInputs())
            {
                addItemToPrime(items, seenKeys, input.getItem());
            }
            if (selectedModifierEnabledForPatch(patchId, MODIFIER_PROTECTION_PAYMENT))
            {
                for (final CalcItemStack payment : crop.getProtectionPayments())
                {
                    addItemToPrime(items, seenKeys, payment.getItem());
                }
            }
            for (final CalcOutputDefinition output : crop.getOutputs())
            {
                if (output.getRole() == CalcOutputRole.PRIMARY && output.getCondition() == null)
                {
                    addItemToPrime(items, seenKeys, output.getItem());
                }
            }
        }

        return items;
    }

    private void addItemToPrime(final List<CalcItemRef> items, final Set<String> seenKeys, final CalcItemRef item)
    {
        if (item == null || !item.hasGePrice())
        {
            return;
        }

        final String key = item.hasPriceableItemId()
                ? "id:" + item.getItemId()
                : "name:" + normalizeItemLookupName(item.getName());
        if (!seenKeys.add(key))
        {
            return;
        }

        items.add(item);
    }

    private void primeItemCachesOnClientThread(final CalcItemRef item)
    {
        if (item == null || !item.hasGePrice())
        {
            return;
        }

        Integer itemId = item.getItemId();
        if (itemId == null || itemId <= 0)
        {
            final String key = normalizeItemLookupName(item.getName());
            final Integer cachedId = itemIdSearchCache.get(key);
            if (cachedId != null)
            {
                itemId = cachedId;
            }
            else
            {
                int resolvedItemId = -1;
                try
                {
                    final List<ItemPrice> results = itemManager.search(item.getName().trim());
                    resolvedItemId = chooseSearchedItemId(item.getName().trim(), results);
                }
                catch (final RuntimeException ex)
                {
                    resolvedItemId = -1;
                }
                itemIdSearchCache.put(key, resolvedItemId);
                itemId = resolvedItemId;
            }
        }

        if (itemId == null || itemId <= 0 || itemPriceCache.containsKey(itemId))
        {
            return;
        }

        int resolvedPrice = -1;
        try
        {
            final int price = itemManager.getItemPrice(itemId);
            resolvedPrice = price > 0 ? price : -1;
        }
        catch (final RuntimeException ex)
        {
            resolvedPrice = -1;
        }
        itemPriceCache.put(itemId, resolvedPrice);
    }

    private void renderTopSummary()
    {
        latestRouteBreakdown = calculateRouteBreakdown();
        profitValue.setText(formatGp(latestRouteBreakdown.getRevenue() - latestRouteBreakdown.getCosts()));
        costsValue.setText(formatGp(latestRouteBreakdown.getCosts()));
        revenueValue.setText(formatGp(latestRouteBreakdown.getRevenue()));
        xpValue.setText(formatXp(latestRouteBreakdown.getXp()));
        statusValue.setText(latestRouteBreakdown.isComplete() ? "Complete" : "Incomplete");
        statusValue.setForeground(latestRouteBreakdown.isComplete() ? ColorScheme.TEXT_COLOR : ColorScheme.BRAND_ORANGE);
        profitValue.setForeground((latestRouteBreakdown.getRevenue() - latestRouteBreakdown.getCosts()) < 0
                ? ColorScheme.BRAND_ORANGE
                : ColorScheme.TEXT_COLOR);
        renderBreakdownSections();
    }

    private void toggleBreakdownStat(final CalcBreakdownStat stat)
    {
        if (stat == null || stat == CalcBreakdownStat.PROFIT)
        {
            return;
        }

        if (expandedBreakdownStats.contains(stat))
        {
            expandedBreakdownStats.remove(stat);
        }
        else
        {
            expandedBreakdownStats.add(stat);
        }

        renderBreakdownSections();
        revalidate();
        repaint();
    }

    private void renderBreakdownSections()
    {
        for (final CalcBreakdownStat stat : CalcBreakdownStat.values())
        {
            final JPanel breakdownPanel = summaryBreakdownPanels.get(stat);
            final JLabel toggleLabel = summaryToggleLabels.get(stat);
            if (breakdownPanel == null || toggleLabel == null)
            {
                continue;
            }

            final boolean expanded = expandedBreakdownStats.contains(stat);
            toggleLabel.setText(expanded ? "▾" : "▸");
            toggleLabel.setFont(UiFont.scaled(toggleLabel.getFont(), textScale(), Font.PLAIN));

            breakdownPanel.removeAll();
            if (expanded)
            {
                final List<CalcBreakdownRow> rows = breakdownRowsForStat(stat);
                if (rows.isEmpty())
                {
                    breakdownPanel.add(buildEmptyBreakdownState());
                }
                else
                {
                    for (int i = 0; i < rows.size(); i++)
                    {
                        breakdownPanel.add(buildBreakdownRowPanel(rows.get(i)));
                        if (i < rows.size() - 1)
                        {
                            breakdownPanel.add(UiTokens.divider(ColorScheme.DARKER_GRAY_COLOR));
                        }
                    }
                }
            }

            breakdownPanel.setVisible(expanded);
        }
    }

    private List<CalcBreakdownRow> breakdownRowsForStat(final CalcBreakdownStat stat)
    {
        final List<CalcBreakdownRow> rows = new ArrayList<>();
        if (latestRouteBreakdown == null)
        {
            return rows;
        }

        for (final CalcBreakdownRow row : latestRouteBreakdown.getRows())
        {
            if (row.getStat() == stat)
            {
                rows.add(row);
            }
        }
        return rows;
    }

    private JComponent buildEmptyBreakdownState()
    {
        final JTextArea area = buildHintTextArea("No breakdown rows are available for the current route state.");
        area.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        area.setFont(UiFont.scaled(area.getFont(), textScale(), Font.PLAIN));
        area.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return area;
    }

    private JComponent buildBreakdownRowPanel(final CalcBreakdownRow row)
    {
        final JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        final JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.setAlignmentX(Component.LEFT_ALIGNMENT);

        final JTextArea primary = buildHintTextArea(breakdownPrimaryText(row));
        primary.setForeground(ColorScheme.TEXT_COLOR);
        primary.setFont(UiFont.scaled(primary.getFont(), textScale(), Font.PLAIN));
        left.add(primary);

        final String secondaryText = breakdownSecondaryText(row);
        if (!secondaryText.isEmpty())
        {
            final JTextArea secondary = buildHintTextArea(secondaryText);
            secondary.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            secondary.setFont(UiFont.scaled(secondary.getFont(), textScale(), Font.PLAIN));
            left.add(secondary);
        }

        if (row.getNote() != null && !row.getNote().trim().isEmpty())
        {
            final JTextArea note = buildHintTextArea(row.getNote().trim());
            note.setForeground(row.getCategory() == CalcBreakdownCategory.UNRESOLVED ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR);
            note.setFont(UiFont.scaled(note.getFont(), textScale(), Font.PLAIN));
            note.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
            left.add(note);
        }

        final JLabel value = new JLabel(breakdownValueText(row));
        value.setForeground(breakdownValueColor(row));
        value.setHorizontalAlignment(SwingConstants.RIGHT);
        value.setVerticalAlignment(SwingConstants.TOP);
        value.setFont(UiFont.scaled(value.getFont(), textScale(), Font.PLAIN));
        value.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));

        panel.add(left, BorderLayout.CENTER);
        panel.add(value, BorderLayout.EAST);
        return panel;
    }

    private String breakdownPrimaryText(final CalcBreakdownRow row)
    {
        switch (row.getCategory())
        {
            case PLANTING_XP:
                return "Planting — " + row.getName();
            case CHECK_HEALTH_XP:
                return "Check health — " + row.getName();
            case HARVEST_XP:
                return "Harvesting — " + row.getName();
            default:
                return row.getName();
        }
    }

    private String breakdownSecondaryText(final CalcBreakdownRow row)
    {
        final List<String> parts = new ArrayList<>();
        parts.add(friendlyBreakdownCategory(row.getCategory()));

        if (row.getPatchId() != null)
        {
            parts.add(displayPatch(row.getPatchId()));
        }
        else if (row.getGroup() != null && !row.getGroup().trim().isEmpty())
        {
            parts.add(row.getGroup());
        }

        if (row.getQuantity() != null)
        {
            parts.add("Qty " + formatQuantity(row.getQuantity()));
        }

        return String.join(" · ", parts);
    }

    private String breakdownValueText(final CalcBreakdownRow row)
    {
        if (row.getGpAmount() != null)
        {
            if (row.getStat() == CalcBreakdownStat.COSTS)
            {
                return formatSignedGp(-row.getGpAmount());
            }
            return formatSignedGp(row.getGpAmount());
        }

        if (row.getXpAmount() != null)
        {
            return formatSignedXp(row.getXpAmount());
        }

        return "—";
    }

    private Color breakdownValueColor(final CalcBreakdownRow row)
    {
        if (row.getCategory() == CalcBreakdownCategory.UNRESOLVED)
        {
            return ColorScheme.BRAND_ORANGE;
        }
        if (row.getCategory() == CalcBreakdownCategory.OMITTED)
        {
            return ColorScheme.MEDIUM_GRAY_COLOR;
        }
        if (row.getStat() == CalcBreakdownStat.COSTS && row.getGpAmount() != null)
        {
            return ColorScheme.BRAND_ORANGE;
        }
        return ColorScheme.TEXT_COLOR;
    }

    private String friendlyBreakdownCategory(final CalcBreakdownCategory category)
    {
        switch (category)
        {
            case PLANTING_INPUT:
                return "Planting input";
            case TREATMENT:
                return "Treatment";
            case PROTECTION_PAYMENT:
                return "Protection payment";
            case HARVEST_OUTPUT:
                return "Harvest output";
            case PLANTING_XP:
                return "Planting XP";
            case HARVEST_XP:
                return "Harvest XP";
            case CHECK_HEALTH_XP:
                return "Check health XP";
            case DERIVED_SUMMARY:
                return "Derived summary";
            case UNRESOLVED:
                return "Unresolved";
            case OMITTED:
            default:
                return "Omitted";
        }
    }

    private void setEditingSectionVisible(final JComponent component, final Component spacer, final boolean visible)
    {
        component.setVisible(visible);
        if (spacer != null)
        {
            spacer.setVisible(visible);
        }
    }

    private void setEditingHintVisible(final boolean visible)
    {
        editingHintLabel.setVisible(visible);
        editingAfterTitleSpacer.setVisible(true);
        editingAfterHintSpacer.setVisible(visible);
    }

    private void refreshEditingPanel()
    {
        styleComboBox(editingCropDropdown, textScale());
        styleComboBox(editingCompostDropdown, textScale());
        editingTitleLabel.setFont(UiFont.scaled(editingTitleLabel.getFont(), textScale(), Font.BOLD));
        editingHintLabel.setFont(UiFont.scaled(editingHintLabel.getFont(), textScale(), Font.PLAIN));
        editingCropLabel.setFont(UiFont.scaled(editingCropLabel.getFont(), textScale(), Font.PLAIN));
        editingCompostLabel.setFont(UiFont.scaled(editingCompostLabel.getFont(), textScale(), Font.PLAIN));

        if (selectedRoute == null)
        {
            editingTitleLabel.setText("Select a route");
            editingHintLabel.setText("Use the filter above to select a route from the Routes panel.");
            editingStateHintLabel.setText("");
            setEditingHintVisible(true);
            setEditingSectionVisible(editingControlsRow, editingAfterCropSpacer, false);
            setEditingSectionVisible(editingCompostRow, editingAfterCompostSpacer, false);
            setEditingSectionVisible(editingModifierRows, editingAfterModifiersSpacer, false);
            setEditingSectionVisible(editingActionsRow, editingAfterActionsSpacer, false);
            setEditingCropOptions(null, null, false);
            setEditingCompostOptions(null, null, false, false);
            refreshEditingModifierRows(null, null, null, false, false);
            clearOverrideButton.setVisible(false);
            clearOverrideButton.setEnabled(false);
            return;
        }

        if (selectedPatchId != null)
        {
            final String group = selectedPatchId.getGroup();
            final CropDisplayState displayState = cropDisplayStateForPatch(selectedPatchId);
            editingTitleLabel.setText(editingRowTitle(selectedPatchId));

            if (!isSupportedCropGroup(group))
            {
                editingHintLabel.setText("");
                editingStateHintLabel.setText(group + " is shown in the route but is not yet included in calculations.");
                setEditingHintVisible(false);
                setEditingSectionVisible(editingControlsRow, editingAfterCropSpacer, true);
                setEditingSectionVisible(editingCompostRow, editingAfterCompostSpacer, true);
                refreshEditingModifierRows(group, selectedPatchId, null, false, true);
                setEditingSectionVisible(editingModifierRows, editingAfterModifiersSpacer, editingModifierRows.isVisible());
                clearOverrideButton.setVisible(false);
                clearOverrideButton.setEnabled(false);
                setEditingSectionVisible(editingActionsRow, editingAfterActionsSpacer, false);
                setEditingCropOptions(group, null, false);
                setEditingCompostOptions(group, null, false, true);
                return;
            }

            editingHintLabel.setText("");
            editingStateHintLabel.setText(editingPatchHint(displayState));
            setEditingHintVisible(false);
            setEditingSectionVisible(editingControlsRow, editingAfterCropSpacer, true);
            setEditingSectionVisible(editingCompostRow, editingAfterCompostSpacer, true);
            setEditingCropOptions(group, resolvedCropNameForPatch(selectedPatchId), true);
            setEditingCompostOptions(group, selectedCompostTierForPatch(selectedPatchId), true, true);
            refreshEditingModifierRows(group, selectedPatchId, resolvedCropForPatch(selectedPatchId), true, true);
            setEditingSectionVisible(editingModifierRows, editingAfterModifiersSpacer, editingModifierRows.isVisible());
            clearOverrideButton.setVisible(true);
            clearOverrideButton.setEnabled(hasExplicitOverride(selectedPatchId));
            setEditingSectionVisible(editingActionsRow, editingAfterActionsSpacer, true);
            return;
        }

        if (selectedGroup == null || selectedGroup.trim().isEmpty())
        {
            editingTitleLabel.setText("No patch types in route");
            editingHintLabel.setText("This route has no calc-supported patch groups yet.");
            editingStateHintLabel.setText("");
            setEditingHintVisible(true);
            setEditingSectionVisible(editingControlsRow, editingAfterCropSpacer, false);
            setEditingSectionVisible(editingCompostRow, editingAfterCompostSpacer, false);
            setEditingSectionVisible(editingModifierRows, editingAfterModifiersSpacer, false);
            clearOverrideButton.setVisible(false);
            clearOverrideButton.setEnabled(false);
            setEditingSectionVisible(editingActionsRow, editingAfterActionsSpacer, false);
            setEditingCropOptions(null, null, false);
            setEditingCompostOptions(null, null, false, false);
            refreshEditingModifierRows(null, null, null, false, false);
            return;
        }

        final CropDisplayState displayState = cropDisplayStateForGroup(selectedGroup);
        editingTitleLabel.setText(selectedGroup + " defaults");

        if (!isSupportedCropGroup(selectedGroup))
        {
            editingHintLabel.setText("");
            editingStateHintLabel.setText(selectedGroup + " is shown in the route but is not yet included in calculations.");
            setEditingHintVisible(false);
            setEditingSectionVisible(editingControlsRow, editingAfterCropSpacer, true);
            setEditingSectionVisible(editingCompostRow, editingAfterCompostSpacer, false);
            setEditingCropOptions(selectedGroup, null, false);
            setEditingCompostOptions(selectedGroup, null, false, false);
            refreshEditingModifierRows(selectedGroup, null, null, false, false);
            setEditingSectionVisible(editingModifierRows, editingAfterModifiersSpacer, editingModifierRows.isVisible());
            clearOverrideButton.setVisible(false);
            clearOverrideButton.setEnabled(false);
            setEditingSectionVisible(editingActionsRow, editingAfterActionsSpacer, false);
            return;
        }

        editingHintLabel.setText("");
        editingStateHintLabel.setText(editingGroupHint(displayState));
        setEditingHintVisible(false);
        setEditingSectionVisible(editingControlsRow, editingAfterCropSpacer, true);
        setEditingSectionVisible(editingCompostRow, editingAfterCompostSpacer, true);
        setEditingCropOptions(selectedGroup, selectedDefaultCropName(selectedGroup), true);
        setEditingCompostOptions(selectedGroup, selectedDefaultCompostTier(selectedGroup), true, false);
        refreshEditingModifierRows(selectedGroup, null, resolvedDefaultCropForGroup(selectedGroup), true, false);
        setEditingSectionVisible(editingModifierRows, editingAfterModifiersSpacer, editingModifierRows.isVisible());
        clearOverrideButton.setVisible(false);
        clearOverrideButton.setEnabled(false);
        setEditingSectionVisible(editingActionsRow, editingAfterActionsSpacer, false);
    }


    private void refreshEditingModifierRows(
            final String group,
            final PatchId patchId,
            final CalcCropDefinition crop,
            final boolean enabled,
            final boolean patchScoped)
    {
        editingModifierRows.removeAll();
        editingModifierRows.setVisible(false);

        if (group == null || group.trim().isEmpty())
        {
            return;
        }

        int count = 0;
        count += addEditingFarmingLevelRow(enabled);
        count += addEditingModifierRowIfApplicable(group, patchId, crop, enabled, patchScoped, MODIFIER_BOTTOMLESS_BUCKET, "Bottomless bucket");
        count += addEditingModifierRowIfApplicable(group, patchId, crop, enabled, patchScoped, MODIFIER_PROTECTION_PAYMENT, "Protection payment");
        count += addEditingModifierRowIfApplicable(group, patchId, crop, enabled, patchScoped, MODIFIER_MAGIC_SECATEURS, "Magic secateurs");
        count += addEditingModifierRowIfApplicable(group, patchId, crop, enabled, patchScoped, MODIFIER_FARMING_CAPE, "Farming/max cape");
        count += addEditingModifierRowIfApplicable(group, patchId, crop, enabled, patchScoped, MODIFIER_ATTAS, "Attas");
        count += addEditingModifierRowIfApplicable(group, patchId, crop, enabled, patchScoped, MODIFIER_AMULET_OF_BOUNTY, "Amulet of bounty");

        editingModifierRows.setVisible(count > 0);
        editingModifierRows.revalidate();
        editingModifierRows.repaint();
    }

    private int addEditingFarmingLevelRow(final boolean enabled)
    {
        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        final JLabel label = new JLabel("Farming level:");
        label.setForeground(ColorScheme.TEXT_COLOR);
        label.setFont(UiFont.scaled(label.getFont(), textScale(), Font.PLAIN));

        final JTextField field = new JTextField();
        field.setEnabled(enabled);
        field.setFocusable(true);
        field.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        field.setBackground(ColorScheme.DARK_GRAY_COLOR);
        field.setForeground(ColorScheme.TEXT_COLOR);
        field.setCaretColor(ColorScheme.TEXT_COLOR);
        field.setFont(UiFont.scaled(field.getFont(), textScale(), Font.PLAIN));
        field.setToolTipText("Blank = " + farmingLevelAutoLabel() + ".");
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));

        if (field.getDocument() instanceof AbstractDocument)
        {
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new FarmingLevelDocumentFilter());
        }

        final Integer override = selectedFarmingLevelOverride();
        field.setText(override == null ? "" : String.valueOf(override));

        field.addActionListener(e -> applyEditingFarmingLevelSelection(field.getText(), field));
        field.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(final FocusEvent e)
            {
                applyEditingFarmingLevelSelection(field.getText(), field);
            }
        });

        final JPanel fieldContainer = new JPanel(new BorderLayout());
        fieldContainer.setOpaque(false);
        fieldContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, dropdownValueInset()));
        fieldContainer.add(field, BorderLayout.CENTER);

        row.add(label, BorderLayout.WEST);
        row.add(fieldContainer, BorderLayout.CENTER);
        editingModifierRows.add(row);
        editingModifierRows.add(Box.createVerticalStrut(6));
        return 1;
    }

    private int addEditingModifierRowIfApplicable(
            final String group,
            final PatchId patchId,
            final CalcCropDefinition crop,
            final boolean enabled,
            final boolean patchScoped,
            final String modifierId,
            final String labelText)
    {
        if (!modifierAppliesToContext(group, crop, modifierId))
        {
            return 0;
        }

        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        final JLabel label = new JLabel(labelText + ":");
        label.setForeground(ColorScheme.TEXT_COLOR);
        label.setFont(UiFont.scaled(label.getFont(), textScale(), Font.PLAIN));

        final JComboBox<String> combo = new JComboBox<>();
        final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        if (patchScoped)
        {
            model.addElement(INHERITED_MODIFIER_LABEL);
        }
        model.addElement(MODIFIER_DISABLED_LABEL);
        model.addElement(MODIFIER_ENABLED_LABEL);
        combo.setModel(model);
        styleComboBox(combo, textScale());
        combo.setFocusable(false);
        combo.setEnabled(enabled);

        final Boolean explicitValue = patchScoped
                ? selectedOverrideModifierEnabled(patchId, modifierId)
                : selectedExplicitDefaultModifierEnabled(group, modifierId);
        if (patchScoped && explicitValue == null)
        {
            combo.setSelectedItem(INHERITED_MODIFIER_LABEL);
        }
        else
        {
            combo.setSelectedItem(Boolean.TRUE.equals(selectedModifierValueForContext(group, patchId, modifierId))
                    ? MODIFIER_ENABLED_LABEL
                    : MODIFIER_DISABLED_LABEL);
        }

        combo.addActionListener(e -> applyEditingModifierSelection(modifierId, patchScoped, (String) combo.getSelectedItem()));

        row.add(label, BorderLayout.WEST);
        row.add(combo, BorderLayout.CENTER);
        editingModifierRows.add(row);
        editingModifierRows.add(Box.createVerticalStrut(6));
        return 1;
    }

    private boolean modifierAppliesToContext(final String group, final CalcCropDefinition crop, final String modifierId)
    {
        if (group == null || modifierId == null)
        {
            return false;
        }

        if (MODIFIER_BOTTOMLESS_BUCKET.equals(modifierId))
        {
            return CalcCatalogue.supportsCompostForGroup(group);
        }
        if (MODIFIER_PROTECTION_PAYMENT.equals(modifierId))
        {
            return crop != null && !crop.getProtectionPayments().isEmpty();
        }
        if (MODIFIER_AMULET_OF_BOUNTY.equals(modifierId))
        {
            return "Allotment".equals(group);
        }
        if (MODIFIER_MAGIC_SECATEURS.equals(modifierId))
        {
            return "Herb".equals(group)
                    || "Allotment".equals(group)
                    || "Hops".equals(group)
                    || "Celastrus".equals(group)
                    || (crop != null && crop.getYieldProfile() != null && crop.getYieldProfile().supportsSecateurs());
        }
        if (MODIFIER_FARMING_CAPE.equals(modifierId))
        {
            return "Herb".equals(group)
                    || (crop != null && crop.getYieldProfile() != null && crop.getYieldProfile().supportsFarmingCape());
        }
        if (MODIFIER_ATTAS.equals(modifierId))
        {
            return "Herb".equals(group)
                    || "Allotment".equals(group)
                    || "Hops".equals(group)
                    || "Celastrus".equals(group)
                    || "Seaweed".equals(group)
                    || (crop != null && crop.getYieldProfile() != null && crop.getYieldProfile().supportsAttas());
        }
        return false;
    }

    private void applyEditingModifierSelection(final String modifierId, final boolean patchScoped, final String selectionLabel)
    {
        if (selectedRouteId == null || modifierId == null)
        {
            return;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        final Boolean selection = modifierSelectionFromLabel(selectionLabel);
        boolean changed = false;

        if (patchScoped && selectedPatchId != null)
        {
            final Boolean currentOverride = selectedOverrideModifierEnabled(selectedPatchId, modifierId);
            if (selection == null)
            {
                changed = currentOverride != null;
                removePatchModifierOverride(state, selectedPatchId, modifierId);
            }
            else if (Objects.equals(selection, selectedDefaultModifierEnabled(selectedPatchId.getGroup(), modifierId)))
            {
                changed = currentOverride != null;
                removePatchModifierOverride(state, selectedPatchId, modifierId);
            }
            else if (!Objects.equals(selection, currentOverride))
            {
                state.overrideModifierEnabledByPatch.computeIfAbsent(selectedPatchId, ignored -> new LinkedHashMap<>()).put(modifierId, selection);
                changed = true;
            }
        }
        else if (!patchScoped && selectedGroup != null && !selectedGroup.trim().isEmpty())
        {
            final boolean implicitDefault = implicitModifierDefaultEnabled(modifierId);
            final boolean normalizedSelection = selection == null ? implicitDefault : selection;
            final Boolean currentExplicit = selectedExplicitDefaultModifierEnabled(selectedGroup, modifierId);
            if (normalizedSelection == implicitDefault)
            {
                changed = currentExplicit != null;
                removeGroupModifierDefault(state, selectedGroup, modifierId);
            }
            else if (!Objects.equals(currentExplicit, normalizedSelection))
            {
                state.defaultModifierEnabledByGroup.computeIfAbsent(selectedGroup, ignored -> new LinkedHashMap<>()).put(modifierId, normalizedSelection);
                changed = true;
            }
        }

        if (!changed)
        {
            return;
        }

        refreshCalculatedView();
    }

    private void applyEditingFarmingLevelSelection(final String selection, final JTextField field)
    {
        if (selectedRouteId == null)
        {
            return;
        }

        final Integer parsed = parseFarmingLevelSelection(selection);
        final RouteCalcState state = routeCalcState(selectedRouteId);
        final Integer current = selectedFarmingLevelOverride();
        if (parsed == null)
        {
            if (field != null)
            {
                field.setText("");
            }
            if (current == null)
            {
                return;
            }
            state.farmingLevelOverride = null;
            refreshCalculatedView();
            return;
        }

        if (field != null)
        {
            field.setText(String.valueOf(parsed));
        }

        if (Objects.equals(current, parsed))
        {
            return;
        }

        state.farmingLevelOverride = parsed;
        refreshCalculatedView();
    }

    private Integer parseFarmingLevelSelection(final String selection)
    {
        if (selection == null)
        {
            return null;
        }

        final String trimmed = selection.trim();
        if (trimmed.isEmpty() || trimmed.startsWith(FARMING_LEVEL_AUTO_LABEL_PREFIX))
        {
            return null;
        }

        try
        {
            return Math.max(1, Math.min(126, Integer.parseInt(trimmed)));
        }
        catch (final NumberFormatException ex)
        {
            return selectedFarmingLevelOverride();
        }
    }

    private String farmingLevelAutoLabel()
    {
        final Integer liveLevel = currentLiveFarmingLevel();
        final int fallback = liveLevel != null && liveLevel > 0 ? liveLevel : 1;
        return FARMING_LEVEL_AUTO_LABEL_PREFIX + fallback + ")";
    }

    private Integer selectedFarmingLevelOverride()
    {
        if (selectedRouteId == null)
        {
            return null;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        return state == null ? null : state.farmingLevelOverride;
    }

    private Boolean modifierSelectionFromLabel(final String value)
    {
        if (value == null)
        {
            return null;
        }

        final String trimmed = value.trim();
        if (trimmed.isEmpty() || INHERITED_MODIFIER_LABEL.equals(trimmed))
        {
            return null;
        }
        if (MODIFIER_ENABLED_LABEL.equals(trimmed))
        {
            return Boolean.TRUE;
        }
        if (MODIFIER_DISABLED_LABEL.equals(trimmed))
        {
            return Boolean.FALSE;
        }
        return null;
    }

    private CalcCropDefinition resolvedCropForPatch(final PatchId patchId)
    {
        if (patchId == null)
        {
            return null;
        }
        final String cropName = resolvedCropNameForPatch(patchId);
        return cropName == null ? null : CalcCatalogue.cropFor(patchId.getGroup(), cropName);
    }

    private CalcCropDefinition resolvedDefaultCropForGroup(final String group)
    {
        if (group == null || group.trim().isEmpty())
        {
            return null;
        }
        final String cropName = selectedDefaultCropName(group);
        return cropName == null ? null : CalcCatalogue.cropFor(group, cropName);
    }

    private String editingGroupHint(final CropDisplayState displayState)
    {
        switch (displayState.kind)
        {
            case OVERRIDE:
                return "Group defaults should not render as overrides.";
            case INHERITED:
                return "This patch type has a selected default crop.";
            case UNRESOLVED:
            default:
                return "No crop has been selected for this patch type yet.";
        }
    }

    private String editingPatchHint(final CropDisplayState displayState)
    {
        switch (displayState.kind)
        {
            case OVERRIDE:
                return "This patch has its own explicit override.";
            case INHERITED:
                return "This patch currently inherits the patch-type default.";
            case UNRESOLVED:
            default:
                return "No crop is resolved here yet. This row would inherit once a default exists.";
        }
    }

    private void rebuildGroupedList(final Map<String, List<PatchId>> grouped)
    {
        list.removeAll();
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (selectedRoute == null)
        {
            return;
        }

        if (grouped.isEmpty())
        {
            list.add(buildEmptyState("Route is empty", "Add patches to this route in the Routes panel, then return here."));
            return;
        }

        for (final Map.Entry<String, List<PatchId>> entry : grouped.entrySet())
        {
            final String group = entry.getKey();
            final List<PatchId> patchIds = entry.getValue();
            final boolean collapsed = isGroupCollapsed(group);
            list.add(buildGroupHeader(group, patchIds.size(), collapsed));

            if (!collapsed)
            {
                for (final PatchId patchId : patchIds)
                {
                    list.add(buildPatchRow(patchId));
                }
            }
        }
    }

    private JComponent buildGroupHeader(final String group, final int count, final boolean collapsed)
    {
        final boolean selected = selectedPatchId == null && Objects.equals(group, selectedGroup);
        final CropDisplayState displayState = cropDisplayStateForGroup(group);

        final JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(true);
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(selected
                ? BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 1),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8))
                : BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.DARKER_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);

        final JLabel triLabel = new JLabel(collapsed ? "▸" : "▾");
        triLabel.setOpaque(false);
        triLabel.setForeground(ColorScheme.BRAND_ORANGE);
        triLabel.setFont(UiFont.scaled(triLabel.getFont(), textScale(), Font.PLAIN));
        final int caretSize = Math.max(12, Math.round(16 * textScale()));
        final Dimension triDim = new Dimension(caretSize, caretSize);
        triLabel.setPreferredSize(triDim);
        triLabel.setMinimumSize(triDim);
        triLabel.setMaximumSize(triDim);
        triLabel.setHorizontalAlignment(SwingConstants.CENTER);
        triLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final JLabel groupLabel = new JLabel(group);
        groupLabel.setForeground(Color.WHITE);
        groupLabel.setFont(UiFont.scaled(groupLabel.getFont(), textScale(), Font.BOLD));

        final JLabel countLabel = new JLabel(" (" + count + ")");
        countLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        countLabel.setFont(UiFont.scaled(countLabel.getFont(), textScale(), Font.PLAIN));

        left.add(triLabel);
        left.add(Box.createHorizontalStrut(4));
        left.add(groupLabel);
        left.add(countLabel);

        final JPanel right = buildStateChipPanel(displayState);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        installClickSelection(header, () -> selectGroup(group));
        installToggleOnly(triLabel, () -> toggleGroupCollapsed(group));
        return header;
    }

    private JComponent buildEmptyState(final String title, final String body)
    {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        final JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(Color.WHITE);
        final JLabel bodyLabel = new JLabel("<html>" + escapeHtml(body) + "</html>");
        bodyLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(bodyLabel);
        return panel;
    }

    private JPanel buildStateChipPanel(final CropDisplayState displayState)
    {
        final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Math.max(4, Math.round(6 * textScale())), 0));
        right.setOpaque(false);

        if (displayState.kind == CropDisplayKind.OVERRIDE)
        {
            final JLabel dotLabel = new JLabel("•");
            dotLabel.setForeground(ColorScheme.BRAND_ORANGE);
            dotLabel.setFont(UiFont.scaled(dotLabel.getFont(), textScale(), Font.BOLD));
            right.add(dotLabel);
        }

        final JLabel valueLabel = new JLabel(stateChipText(displayState));
        valueLabel.setForeground(stateChipColor(displayState));
        valueLabel.setFont(UiFont.scaled(valueLabel.getFont(), textScale(), Font.PLAIN));
        right.add(valueLabel);

        final Dimension pref = right.getPreferredSize();
        right.setMinimumSize(new Dimension(Math.max(108, pref.width), pref.height));
        right.setPreferredSize(new Dimension(Math.max(108, pref.width), pref.height));
        return right;
    }

    private String stateChipText(final CropDisplayState displayState)
    {
        switch (displayState.kind)
        {
            case UNRESOLVED:
                return "[Set]";
            case UNSUPPORTED:
                return "[Unsupported]";
            case INHERITED:
            case OVERRIDE:
            default:
                return "[" + displayState.valueText() + "]";
        }
    }

    private Color stateChipColor(final CropDisplayState displayState)
    {
        switch (displayState.kind)
        {
            case UNRESOLVED:
                return ColorScheme.BRAND_ORANGE;
            case UNSUPPORTED:
                return ColorScheme.MEDIUM_GRAY_COLOR;
            case INHERITED:
                return ColorScheme.MEDIUM_GRAY_COLOR;
            case OVERRIDE:
            default:
                return ColorScheme.TEXT_COLOR;
        }
    }

    private JComponent buildPatchRow(final PatchId patchId)
    {
        final boolean selected = selectedPatchId == patchId;
        final CropDisplayState displayState = cropDisplayStateForPatch(patchId);

        final JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(true);
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(selected
                ? BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE, 1),
                        BorderFactory.createEmptyBorder(6, 16, 6, 8))
                : BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARKER_GRAY_COLOR),
                        BorderFactory.createEmptyBorder(6, 16, 6, 8)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final JLabel label = new JLabel(displayPatch(patchId));
        label.setForeground(ColorScheme.TEXT_COLOR);
        label.setFont(UiFont.scaled(label.getFont(), textScale(), Font.PLAIN));
        row.add(label, BorderLayout.WEST);

        row.add(buildStateChipPanel(displayState), BorderLayout.EAST);
        installClickSelection(row, () -> selectPatch(patchId));
        return row;
    }

    private void installClickSelection(final Component component, final Runnable action)
    {
        final MouseAdapter listener = new MouseAdapter()
        {
            @Override
            public void mousePressed(final MouseEvent e)
            {
                if (!SwingUtilities.isLeftMouseButton(e))
                {
                    return;
                }
                action.run();
            }
        };

        installClickSelectionRecursive(component, listener);
    }

    private void installClickSelectionRecursive(final Component component, final MouseAdapter listener)
    {
        component.addMouseListener(listener);
        if (component instanceof JComponent)
        {
            ((JComponent) component).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        if (component instanceof Container)
        {
            for (final Component child : ((Container) component).getComponents())
            {
                installClickSelectionRecursive(child, listener);
            }
        }
    }

    private void selectGroup(final String group)
    {
        if (group == null || selectedRoute == null)
        {
            return;
        }
        selectedGroup = group;
        selectedPatchId = null;
        refreshEditingPanel();
        rebuildGroupedList(groupRoutePatches(selectedRoute));
        revalidate();
        repaint();
    }

    private void selectPatch(final PatchId patchId)
    {
        if (patchId == null || selectedRoute == null)
        {
            return;
        }
        selectedPatchId = patchId;
        selectedGroup = patchId.getGroup();
        refreshEditingPanel();
        rebuildGroupedList(groupRoutePatches(selectedRoute));
        revalidate();
        repaint();
    }

    private CropDisplayState cropDisplayStateForGroup(final String group)
    {
        if (!isSupportedCropGroup(group))
        {
            return CropDisplayState.unsupported();
        }

        final String defaultCropName = selectedDefaultCropName(group);
        if (defaultCropName == null || defaultCropName.trim().isEmpty())
        {
            return CropDisplayState.unresolved();
        }
        return CropDisplayState.inherited(defaultCropName);
    }

    private CropDisplayState cropDisplayStateForPatch(final PatchId patchId)
    {
        if (patchId == null || !isSupportedCropGroup(patchId.getGroup()))
        {
            return CropDisplayState.unsupported();
        }

        final String overrideCropName = selectedOverrideCropName(patchId);
        if (overrideCropName != null && !overrideCropName.trim().isEmpty())
        {
            return CropDisplayState.override(overrideCropName);
        }

        final String defaultCropName = selectedDefaultCropName(patchId.getGroup());
        if (defaultCropName != null && !defaultCropName.trim().isEmpty())
        {
            return CropDisplayState.inherited(defaultCropName);
        }

        return CropDisplayState.unresolved();
    }

    private String selectedDefaultCropName(final String group)
    {
        final RouteCalcState state = selectedRouteCalcState();
        if (state == null || group == null)
        {
            return null;
        }
        return state.defaultCropNamesByGroup.get(group);
    }

    private String selectedOverrideCropName(final PatchId patchId)
    {
        final RouteCalcState state = selectedRouteCalcState();
        if (state == null || patchId == null)
        {
            return null;
        }
        return state.overrideCropNamesByPatch.get(patchId);
    }

    private String resolvedCropNameForPatch(final PatchId patchId)
    {
        if (patchId == null)
        {
            return null;
        }

        final String overrideCropName = selectedOverrideCropName(patchId);
        if (overrideCropName != null && !overrideCropName.trim().isEmpty())
        {
            return overrideCropName;
        }

        return selectedDefaultCropName(patchId.getGroup());
    }

    private CalcCompostTier selectedDefaultCompostTier(final String group)
    {
        if (selectedRouteId == null || group == null || group.trim().isEmpty())
        {
            return CalcCompostTier.NONE;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        if (state == null)
        {
            return CalcCompostTier.NONE;
        }

        final CalcCompostTier tier = state.defaultCompostTierByGroup.get(group);
        return tier == null ? CalcCompostTier.NONE : tier;
    }

    private CalcCompostTier selectedOverrideCompostTier(final PatchId patchId)
    {
        if (selectedRouteId == null || patchId == null)
        {
            return null;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        return state == null ? null : state.overrideCompostTierByPatch.get(patchId);
    }

    private CalcCompostTier selectedCompostTierForPatch(final PatchId patchId)
    {
        if (patchId == null)
        {
            return CalcCompostTier.NONE;
        }

        final CalcCompostTier overrideTier = selectedOverrideCompostTier(patchId);
        return overrideTier != null ? overrideTier : selectedDefaultCompostTier(patchId.getGroup());
    }

    private void applyEditingCropSelection()
    {
        if (selectedRouteId == null)
        {
            return;
        }

        final String selection = normaliseCropSelection((String) editingCropDropdown.getSelectedItem());
        final RouteCalcState state = routeCalcState(selectedRouteId);
        boolean changed = false;

        if (selectedPatchId != null)
        {
            final String inheritedCropName = selectedDefaultCropName(selectedPatchId.getGroup());
            final String currentOverride = state.overrideCropNamesByPatch.get(selectedPatchId);
            if (selection == null || Objects.equals(selection, inheritedCropName))
            {
                changed = currentOverride != null;
                state.overrideCropNamesByPatch.remove(selectedPatchId);
            }
            else if (!Objects.equals(selection, currentOverride))
            {
                state.overrideCropNamesByPatch.put(selectedPatchId, selection);
                changed = true;
            }
        }
        else if (selectedGroup != null && !selectedGroup.trim().isEmpty())
        {
            final String currentDefault = state.defaultCropNamesByGroup.get(selectedGroup);
            if (selection == null)
            {
                changed = currentDefault != null;
                state.defaultCropNamesByGroup.remove(selectedGroup);
            }
            else if (!Objects.equals(selection, currentDefault))
            {
                state.defaultCropNamesByGroup.put(selectedGroup, selection);
                changed = true;
            }
        }

        if (!changed)
        {
            return;
        }

        refreshCalculatedView();
    }

    private void applyEditingCompostSelection()
    {
        if (selectedRouteId == null)
        {
            return;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        final CalcCompostTier selection = compostTierFromSelection((String) editingCompostDropdown.getSelectedItem());
        boolean changed = false;

        if (selectedPatchId != null)
        {
            final CalcCompostTier currentOverride = state.overrideCompostTierByPatch.get(selectedPatchId);
            if (selection == null)
            {
                changed = currentOverride != null;
                state.overrideCompostTierByPatch.remove(selectedPatchId);
            }
            else if (Objects.equals(selection, selectedDefaultCompostTier(selectedPatchId.getGroup())))
            {
                changed = currentOverride != null;
                state.overrideCompostTierByPatch.remove(selectedPatchId);
            }
            else if (!Objects.equals(selection, currentOverride))
            {
                state.overrideCompostTierByPatch.put(selectedPatchId, selection);
                changed = true;
            }
        }
        else if (selectedGroup != null && !selectedGroup.trim().isEmpty())
        {
            final CalcCompostTier normalizedSelection = selection == null ? CalcCompostTier.NONE : selection;
            final CalcCompostTier currentDefault = state.defaultCompostTierByGroup.get(selectedGroup);
            if (!Objects.equals(normalizedSelection, currentDefault))
            {
                state.defaultCompostTierByGroup.put(selectedGroup, normalizedSelection);
                changed = true;
            }
        }

        if (!changed)
        {
            return;
        }

        refreshCalculatedView();
    }

    private void clearSelectedPatchOverride()
    {
        if (selectedRouteId == null || selectedPatchId == null)
        {
            return;
        }

        final RouteCalcState state = selectedRouteCalcState();
        if (state != null)
        {
            state.overrideCropNamesByPatch.remove(selectedPatchId);
            state.overrideCompostTierByPatch.remove(selectedPatchId);
            state.overrideModifierEnabledByPatch.remove(selectedPatchId);
        }

        refreshCalculatedView();
    }

    private boolean hasExplicitOverride(final PatchId patchId)
    {
        if (patchId == null || selectedRouteId == null)
        {
            return false;
        }

        final String overrideCropName = selectedOverrideCropName(patchId);
        if (overrideCropName != null && !overrideCropName.trim().isEmpty())
        {
            return true;
        }
        if (selectedOverrideCompostTier(patchId) != null)
        {
            return true;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        final Map<String, Boolean> modifierOverrides = state == null ? null : state.overrideModifierEnabledByPatch.get(patchId);
        return modifierOverrides != null && !modifierOverrides.isEmpty();
    }

    private void setEditingCropOptions(final String group, final String selectedCropName, final boolean enabled)
    {
        final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();

        if (group == null || !isSupportedCropGroup(group))
        {
            model.addElement(group == null ? UNKNOWN_CROP_LABEL : "Not yet supported");
        }
        else
        {
            model.addElement(UNKNOWN_CROP_LABEL);
            for (final String cropName : CalcCatalogue.currentUiCropNamesForGroup(group))
            {
                model.addElement(cropName);
            }
            if (selectedCropName != null && !selectedCropName.trim().isEmpty() && !modelContains(model, selectedCropName))
            {
                model.addElement(selectedCropName);
            }
        }

        suppressCropDropdownEvents = true;
        try
        {
            editingCropDropdown.setModel(model);
            if (group != null && isSupportedCropGroup(group))
            {
                editingCropDropdown.setSelectedItem(selectedCropName == null || selectedCropName.trim().isEmpty()
                        ? UNKNOWN_CROP_LABEL
                        : selectedCropName);
            }
            else
            {
                editingCropDropdown.setSelectedIndex(0);
            }
            editingCropDropdown.setEnabled(enabled && group != null && isSupportedCropGroup(group));
        }
        finally
        {
            suppressCropDropdownEvents = false;
        }
    }

    private void setEditingCompostOptions(final String group, final CalcCompostTier selectedTier, final boolean enabled, final boolean patchScoped)
    {
        final DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        final boolean supported = group != null && CalcCatalogue.supportsCompostForGroup(group);

        if (!supported)
        {
            model.addElement("Not applicable");
        }
        else
        {
            if (patchScoped)
            {
                model.addElement(INHERITED_MODIFIER_LABEL);
            }
            model.addElement(compostSelectionLabel(CalcCompostTier.NONE));
            model.addElement(compostSelectionLabel(CalcCompostTier.COMPOST));
            model.addElement(compostSelectionLabel(CalcCompostTier.SUPERCOMPOST));
            model.addElement(compostSelectionLabel(CalcCompostTier.ULTRACOMPOST));
        }

        suppressCompostDropdownEvents = true;
        try
        {
            editingCompostDropdown.setModel(model);
            editingCompostRow.setVisible(supported);
            if (!supported)
            {
                editingCompostDropdown.setSelectedIndex(0);
                editingCompostDropdown.setEnabled(false);
                return;
            }

            if (patchScoped && selectedOverrideCompostTier(selectedPatchId) == null)
            {
                editingCompostDropdown.setSelectedItem(INHERITED_MODIFIER_LABEL);
            }
            else
            {
                editingCompostDropdown.setSelectedItem(compostSelectionLabel(selectedTier == null ? CalcCompostTier.NONE : selectedTier));
            }
            editingCompostDropdown.setEnabled(enabled);
        }
        finally
        {
            suppressCompostDropdownEvents = false;
        }
    }

    private CalcCompostTier compostTierFromSelection(final String value)
    {
        if (value == null)
        {
            return null;
        }

        final String trimmed = value.trim();
        if (trimmed.isEmpty() || INHERITED_MODIFIER_LABEL.equals(trimmed))
        {
            return null;
        }

        switch (trimmed)
        {
            case "None":
                return CalcCompostTier.NONE;
            case "Compost":
                return CalcCompostTier.COMPOST;
            case "Supercompost":
                return CalcCompostTier.SUPERCOMPOST;
            case "Ultracompost":
                return CalcCompostTier.ULTRACOMPOST;
            default:
                return null;
        }
    }

    private String compostSelectionLabel(final CalcCompostTier tier)
    {
        if (tier == null)
        {
            return INHERITED_MODIFIER_LABEL;
        }

        switch (tier)
        {
            case COMPOST:
                return "Compost";
            case SUPERCOMPOST:
                return "Supercompost";
            case ULTRACOMPOST:
                return "Ultracompost";
            case NONE:
            default:
                return "None";
        }
    }

    private String normaliseCropSelection(final String value)
    {
        if (value == null)
        {
            return null;
        }

        final String trimmed = value.trim();
        if (trimmed.isEmpty() || UNKNOWN_CROP_LABEL.equals(trimmed))
        {
            return null;
        }
        return trimmed;
    }

    private boolean isSupportedCropGroup(final String group)
    {
        return CalcCatalogue.currentUiCropNamesForGroup(group).length > 0;
    }

    private boolean modelContains(final DefaultComboBoxModel<String> model, final String value)
    {
        for (int i = 0; i < model.getSize(); i++)
        {
            if (Objects.equals(model.getElementAt(i), value))
            {
                return true;
            }
        }
        return false;
    }

    private Boolean selectedExplicitDefaultModifierEnabled(final String group, final String modifierId)
    {
        if (selectedRouteId == null || group == null || modifierId == null)
        {
            return null;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        if (state == null)
        {
            return null;
        }

        final Map<String, Boolean> values = state.defaultModifierEnabledByGroup.get(group);
        return values == null ? null : values.get(modifierId);
    }

    private boolean selectedDefaultModifierEnabled(final String group, final String modifierId)
    {
        final Boolean explicit = selectedExplicitDefaultModifierEnabled(group, modifierId);
        return explicit == null ? implicitModifierDefaultEnabled(modifierId) : explicit;
    }

    private Boolean selectedOverrideModifierEnabled(final PatchId patchId, final String modifierId)
    {
        if (selectedRouteId == null || patchId == null || modifierId == null)
        {
            return null;
        }

        final RouteCalcState state = routeCalcState(selectedRouteId);
        if (state == null)
        {
            return null;
        }

        final Map<String, Boolean> values = state.overrideModifierEnabledByPatch.get(patchId);
        return values == null ? null : values.get(modifierId);
    }

    private Boolean selectedModifierValueForContext(final String group, final PatchId patchId, final String modifierId)
    {
        if (patchId != null)
        {
            final Boolean override = selectedOverrideModifierEnabled(patchId, modifierId);
            if (override != null)
            {
                return override;
            }
            return selectedDefaultModifierEnabled(patchId.getGroup(), modifierId);
        }
        if (group == null)
        {
            return implicitModifierDefaultEnabled(modifierId);
        }
        return selectedDefaultModifierEnabled(group, modifierId);
    }

    private boolean selectedModifierEnabledForPatch(final PatchId patchId, final String modifierId)
    {
        return Boolean.TRUE.equals(selectedModifierValueForContext(patchId == null ? null : patchId.getGroup(), patchId, modifierId));
    }

    private boolean implicitModifierDefaultEnabled(final String modifierId)
    {
        return false;
    }

    private void removePatchModifierOverride(final RouteCalcState state, final PatchId patchId, final String modifierId)
    {
        if (state == null || patchId == null || modifierId == null)
        {
            return;
        }

        final Map<String, Boolean> values = state.overrideModifierEnabledByPatch.get(patchId);
        if (values == null)
        {
            return;
        }
        values.remove(modifierId);
        if (values.isEmpty())
        {
            state.overrideModifierEnabledByPatch.remove(patchId);
        }
    }

    private void removeGroupModifierDefault(final RouteCalcState state, final String group, final String modifierId)
    {
        if (state == null || group == null || modifierId == null)
        {
            return;
        }

        final Map<String, Boolean> values = state.defaultModifierEnabledByGroup.get(group);
        if (values == null)
        {
            return;
        }
        values.remove(modifierId);
        if (values.isEmpty())
        {
            state.defaultModifierEnabledByGroup.remove(group);
        }
    }

    private int dropdownValueInset()
    {
        final int comboHeight = editingCropDropdown == null ? 0 : editingCropDropdown.getPreferredSize().height;
        return Math.max(18, comboHeight - 2);
    }

    private void styleComboBox(final JComboBox<?> combo, final float scale)
    {
        if (combo == null)
        {
            return;
        }

        combo.setFont(UiFont.scaled(combo.getFont(), scale, Font.PLAIN));
        combo.setBackground(ColorScheme.DARK_GRAY_COLOR);
        combo.setForeground(ColorScheme.TEXT_COLOR);
        combo.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));
        combo.setMinimumSize(new Dimension(0, combo.getPreferredSize().height));
        combo.setPreferredSize(new Dimension(0, combo.getPreferredSize().height));
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, combo.getPreferredSize().height));
    }

    private CalcRouteBreakdownResult calculateRouteBreakdown()
    {
        if (selectedRoute == null || selectedRoute.getPatchIds().isEmpty())
        {
            return CalcRouteBreakdownResult.incomplete();
        }

        final FarmingLevelState farmingLevelState = currentFarmingLevelState();
        final List<CalcBreakdownRow> rows = new ArrayList<>();
        boolean complete = true;
        long costs = 0L;
        long revenue = 0L;
        double xp = 0.0d;

        for (final PatchId patchId : selectedRoute.getPatchIds())
        {
            final CalcPatchBreakdownResult calculation = calculatePatchBreakdown(patchId, farmingLevelState);
            if (!calculation.isComplete())
            {
                complete = false;
            }

            costs += calculation.getCosts();
            revenue += calculation.getRevenue();
            xp += calculation.getXp();
            rows.addAll(calculation.getRows());
        }

        rows.add(new CalcBreakdownRow(
                CalcBreakdownStat.PROFIT,
                null,
                null,
                null,
                "Revenue minus costs",
                CalcBreakdownCategory.DERIVED_SUMMARY,
                null,
                revenue - costs,
                null,
                complete ? "Revenue - costs." : "Revenue - costs (partial)."
        ));

        if (!complete)
        {
            rows.add(new CalcBreakdownRow(
                    CalcBreakdownStat.PROFIT,
                    null,
                    null,
                    null,
                    "Unresolved patch values",
                    CalcBreakdownCategory.UNRESOLVED,
                    null,
                    null,
                    null,
                    "Some patch values are unresolved."
            ));
        }

        return new CalcRouteBreakdownResult(complete, costs, revenue, xp, rows);
    }

    private CalcPatchBreakdownResult calculatePatchBreakdown(final PatchId patchId, final FarmingLevelState farmingLevelState)
    {
        final List<CalcBreakdownRow> rows = new ArrayList<>();
        if (patchId == null)
        {
            addMissingPatchRows(rows, null, null, null, "Patch unavailable.");
            return new CalcPatchBreakdownResult(false, 0L, 0L, 0.0d, rows);
        }

        if (!isSupportedCropGroup(patchId.getGroup()))
        {
            addMissingPatchRows(rows, patchId, patchId.getGroup(), null, patchId.getGroup() + " not in calc yet.");
            return new CalcPatchBreakdownResult(false, 0L, 0L, 0.0d, rows);
        }

        final String cropName = resolvedCropNameForPatch(patchId);
        if (cropName == null || cropName.trim().isEmpty())
        {
            addMissingPatchRows(rows, patchId, patchId.getGroup(), null, "Crop unset.");
            return new CalcPatchBreakdownResult(false, 0L, 0L, 0.0d, rows);
        }

        final CalcCropDefinition crop = CalcCatalogue.cropFor(patchId.getGroup(), cropName);
        if (crop == null)
        {
            addMissingPatchRows(rows, patchId, patchId.getGroup(), cropName, "Crop metadata missing.");
            return new CalcPatchBreakdownResult(false, 0L, 0L, 0.0d, rows);
        }

        final CalcCompostTier compostTier = selectedCompostTierForPatch(patchId);
        final boolean magicSecateursEnabled = magicSecateursEnabled(patchId);
        final boolean farmingCapeEnabled = farmingCapeEnabled(patchId);
        final boolean attasEnabled = attasEnabled(patchId);
        final boolean bottomlessBucketEnabled = bottomlessBucketEnabled(patchId);
        final boolean protectionPaymentEnabled = protectionPaymentsEnabled(patchId, crop);
        final boolean amuletOfBountyEnabled = amuletOfBountyEnabled(patchId, crop);
        final CalcExpectedYieldResult yieldResult = CalcCatalogue.expectedYieldFor(
                crop,
                compostTier,
                farmingLevelState == null ? null : farmingLevelState.visibleFarmingLevel,
                farmingLevelState == null ? null : farmingLevelState.effectiveFarmingLevel,
                magicSecateursEnabled,
                farmingCapeEnabled,
                attasEnabled,
                diaryBonusForPatch(patchId));
        if (yieldResult == null || !yieldResult.isResolved())
        {
            addMissingPatchRows(rows, patchId, patchId.getGroup(), cropName, yieldResult == null ? "Yield unresolved." : yieldResult.getNote());
            return new CalcPatchBreakdownResult(false, 0L, 0L, 0.0d, rows);
        }
        final double expectedYield = yieldResult.getExpectedYield();

        long costs = 0L;
        long revenue = 0L;
        boolean complete = true;

        if (compostTier != null && compostTier != CalcCompostTier.NONE)
        {
            final CalcItemRef compostItem = CalcCatalogue.compostItemFor(compostTier);
            if (compostItem != null)
            {
                final double compostQuantity = bottomlessBucketEnabled ? 0.5d : 1.0d;
                final String compostNote = buildNotes(
                        compostSelectionNote(patchId, compostTier),
                        bottomlessBucketEnabled ? "Bottomless: 0.5 charge." : null);
                final int compostPrice = itemPrice(compostItem);
                if (compostPrice < 0)
                {
                    if (compostItem.hasGePrice())
                    {
                        complete = false;
                        rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, compostItem.getName(), CalcBreakdownCategory.UNRESOLVED, compostQuantity, null, null, buildNotes(compostNote, "GE price unresolved.")));
                    }
                    else
                    {
                        rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, compostItem.getName(), CalcBreakdownCategory.OMITTED, compostQuantity, null, null, buildNotes(compostNote, "No GE price.")));
                    }
                }
                else
                {
                    final long compostTotalPrice = Math.round(compostPrice * compostQuantity);
                    costs += compostTotalPrice;
                    rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, compostItem.getName(), CalcBreakdownCategory.TREATMENT, compostQuantity, compostTotalPrice, null, compostNote));
                }
            }
        }

        for (final CalcItemStack input : crop.getPlantingInputs())
        {
            final boolean bountyAdjusted = amuletOfBountyEnabled && sameCalcItem(input.getItem(), crop.getPrimaryPlantingItem());
            final double effectiveQuantity = bountyAdjusted
                    ? Math.max(0.0d, input.getQuantity() - 0.25d)
                    : input.getQuantity();
            final String inputNote = bountyAdjusted
                    ? "Bounty: -0.25 seed/patch."
                    : null;
            final int price = itemPrice(input.getItem());
            final long totalPrice = price < 0 ? 0L : Math.round(price * effectiveQuantity);
            if (price < 0)
            {
                if (input.getItem().hasGePrice())
                {
                    complete = false;
                    rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, input.getItem().getName(), CalcBreakdownCategory.UNRESOLVED, effectiveQuantity, null, null, buildNotes(inputNote, "GE price unresolved.")));
                }
                else
                {
                    rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, input.getItem().getName(), CalcBreakdownCategory.OMITTED, effectiveQuantity, null, null, buildNotes(inputNote, "No GE price.")));
                }
                continue;
            }

            costs += totalPrice;
            rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, input.getItem().getName(), CalcBreakdownCategory.PLANTING_INPUT, effectiveQuantity, totalPrice, null, inputNote));
        }

        for (final CalcItemStack payment : crop.getProtectionPayments())
        {
            if (!protectionPaymentEnabled)
            {
                rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, payment.getItem().getName(), CalcBreakdownCategory.OMITTED, (double) payment.getQuantity(), null, null, "Protection disabled."));
                continue;
            }

            final int price = itemPrice(payment.getItem());
            final long totalPrice = price < 0 ? 0L : (long) price * payment.getQuantity();
            if (price < 0)
            {
                if (payment.getItem().hasGePrice())
                {
                    rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, payment.getItem().getName(), CalcBreakdownCategory.OMITTED, (double) payment.getQuantity(), null, null, "Protection price unresolved."));
                }
                else
                {
                    rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, payment.getItem().getName(), CalcBreakdownCategory.OMITTED, (double) payment.getQuantity(), null, null, "Protection price unavailable."));
                }
                continue;
            }

            costs += totalPrice;
            rows.add(breakdownRow(CalcBreakdownStat.COSTS, patchId, cropName, payment.getItem().getName(), CalcBreakdownCategory.PROTECTION_PAYMENT, (double) payment.getQuantity(), totalPrice, null, null));
        }

        for (final CalcOutputDefinition output : crop.getOutputs())
        {
            final String outputNote = buildNotes(yieldResult.getNote(), activeYieldModifierNote(patchId, crop, magicSecateursEnabled, farmingCapeEnabled, attasEnabled), output.getNotes());
            if (output.getRole() != CalcOutputRole.PRIMARY)
            {
                rows.add(breakdownRow(CalcBreakdownStat.REVENUE, patchId, cropName, output.getItem().getName(), CalcBreakdownCategory.OMITTED, expectedYield, null, null, buildNotes(outputNote, "Excluded: " + output.getRole().name().toLowerCase(Locale.ROOT) + ".")));
                continue;
            }
            if (output.getCondition() != null)
            {
                rows.add(breakdownRow(CalcBreakdownStat.REVENUE, patchId, cropName, output.getItem().getName(), CalcBreakdownCategory.OMITTED, expectedYield, null, null, buildNotes(outputNote, "Conditional: " + output.getCondition())));
                continue;
            }

            final int price = itemPrice(output.getItem());
            if (price < 0)
            {
                if (output.getItem().hasGePrice())
                {
                    complete = false;
                    rows.add(breakdownRow(CalcBreakdownStat.REVENUE, patchId, cropName, output.getItem().getName(), CalcBreakdownCategory.UNRESOLVED, expectedYield, null, null, buildNotes(outputNote, "GE price unresolved.")));
                }
                else
                {
                    rows.add(breakdownRow(CalcBreakdownStat.REVENUE, patchId, cropName, output.getItem().getName(), CalcBreakdownCategory.OMITTED, expectedYield, null, null, buildNotes(outputNote, "No GE price.")));
                }
                continue;
            }

            final long totalPrice = Math.round((double) price * expectedYield);
            revenue += totalPrice;
            rows.add(breakdownRow(CalcBreakdownStat.REVENUE, patchId, cropName, output.getItem().getName(), CalcBreakdownCategory.HARVEST_OUTPUT, expectedYield, totalPrice, null, outputNote));
        }

        double xp = 0.0d;
        boolean hasXpContribution = false;
        if (crop.getXpProfile() != null)
        {
            if (crop.getXpProfile().getPlantingXp() != null)
            {
                xp += crop.getXpProfile().getPlantingXp();
                hasXpContribution = true;
                rows.add(breakdownRow(CalcBreakdownStat.XP, patchId, cropName, cropName, CalcBreakdownCategory.PLANTING_XP, null, null, crop.getXpProfile().getPlantingXp(), null));
            }
            if (crop.getXpProfile().getCheckHealthXp() != null)
            {
                xp += crop.getXpProfile().getCheckHealthXp();
                hasXpContribution = true;
                rows.add(breakdownRow(CalcBreakdownStat.XP, patchId, cropName, cropName, CalcBreakdownCategory.CHECK_HEALTH_XP, null, null, crop.getXpProfile().getCheckHealthXp(), null));
            }
            if (crop.getXpProfile().getHarvestXpPerItem() != null)
            {
                final double harvestXp = crop.getXpProfile().getHarvestXpPerItem() * expectedYield;
                xp += harvestXp;
                hasXpContribution = true;
                rows.add(breakdownRow(CalcBreakdownStat.XP, patchId, cropName, cropName, CalcBreakdownCategory.HARVEST_XP, expectedYield, null, harvestXp, activeYieldModifierNote(patchId, crop, magicSecateursEnabled, farmingCapeEnabled, attasEnabled)));
            }
        }

        final boolean hasKnownValue = costs > 0L || revenue > 0L || hasXpContribution || Math.abs(expectedYield) < 0.0001d;
        if (!hasKnownValue)
        {
            complete = false;
            addMissingPatchRows(rows, patchId, patchId.getGroup(), cropName, "No valued output resolved yet.");
        }

        return new CalcPatchBreakdownResult(complete, costs, revenue, xp, rows);
    }

    private CalcBreakdownRow breakdownRow(
            final CalcBreakdownStat stat,
            final PatchId patchId,
            final String cropName,
            final String name,
            final CalcBreakdownCategory category,
            final Double quantity,
            final Long gpAmount,
            final Double xpAmount,
            final String note)
    {
        return new CalcBreakdownRow(stat, patchId, patchId == null ? null : patchId.getGroup(), cropName, name, category, quantity, gpAmount, xpAmount, note);
    }

    private void addMissingPatchRows(
            final List<CalcBreakdownRow> rows,
            final PatchId patchId,
            final String group,
            final String cropName,
            final String reason)
    {
        final String patchName = patchId == null ? "Patch" : displayPatch(patchId);
        rows.add(new CalcBreakdownRow(CalcBreakdownStat.COSTS, patchId, group, cropName, patchName, CalcBreakdownCategory.UNRESOLVED, null, null, null, reason));
        rows.add(new CalcBreakdownRow(CalcBreakdownStat.REVENUE, patchId, group, cropName, patchName, CalcBreakdownCategory.UNRESOLVED, null, null, null, reason));
        rows.add(new CalcBreakdownRow(CalcBreakdownStat.XP, patchId, group, cropName, patchName, CalcBreakdownCategory.UNRESOLVED, null, null, null, reason));
    }

    private String compostSelectionNote(final PatchId patchId, final CalcCompostTier compostTier)
    {
        if (patchId == null || compostTier == null)
        {
            return null;
        }

        if (selectedOverrideCompostTier(patchId) != null)
        {
            return "Patch treatment: " + compostSelectionLabel(compostTier) + ".";
        }

        if (selectedRouteId != null)
        {
            final RouteCalcState state = routeCalcState(selectedRouteId);
            if (state != null && state.defaultCompostTierByGroup.containsKey(patchId.getGroup()))
            {
                return "Inherited treatment: " + compostSelectionLabel(compostTier) + ".";
            }
        }

        return null;
    }

    private String harvestModelNote(final CalcCropDefinition crop)
    {
        if (crop == null || crop.getYieldProfile() == null)
        {
            return null;
        }

        return buildNotes(crop.getYieldProfile().getNotes(), crop.getNotes());
    }

    private String buildNotes(final String... parts)
    {
        if (parts == null || parts.length == 0)
        {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        for (final String part : parts)
        {
            if (part == null)
            {
                continue;
            }

            final String trimmed = part.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }

            if (sb.length() > 0)
            {
                sb.append(" · ");
            }
            sb.append(trimmed);
        }

        return sb.length() == 0 ? null : sb.toString();
    }

    private boolean sameCalcItem(final CalcItemRef left, final CalcItemRef right)
    {
        if (left == null || right == null)
        {
            return false;
        }
        if (left.getItemId() != null && right.getItemId() != null)
        {
            return Objects.equals(left.getItemId(), right.getItemId());
        }
        return Objects.equals(left.getName(), right.getName());
    }

    public void refreshForLogin()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refreshForLogin);
            return;
        }

        refreshCalculatedView();
    }

    private String activeYieldModifierNote(
            final PatchId patchId,
            final CalcCropDefinition crop,
            final boolean magicSecateursEnabled,
            final boolean farmingCapeEnabled,
            final boolean attasEnabled)
    {
        final List<String> parts = new ArrayList<>();
        if (magicSecateursEnabled && crop != null && crop.getYieldProfile() != null && crop.getYieldProfile().supportsSecateurs())
        {
            parts.add("Secateurs");
        }
        if (farmingCapeEnabled && crop != null && crop.getYieldProfile() != null && crop.getYieldProfile().supportsFarmingCape())
        {
            parts.add("Cape");
        }
        if (attasEnabled && crop != null && crop.getYieldProfile() != null && crop.getYieldProfile().supportsAttas())
        {
            parts.add("Attas");
        }
        final int diaryBonus = diaryBonusForPatch(patchId);
        if (diaryBonus > 0)
        {
            parts.add("Diary +" + diaryBonus);
        }
        return parts.isEmpty() ? null : "Mods: " + String.join(", ", parts);
    }

    private boolean magicSecateursEnabled(final PatchId patchId)
    {
        return selectedModifierEnabledForPatch(patchId, MODIFIER_MAGIC_SECATEURS);
    }

    private boolean farmingCapeEnabled(final PatchId patchId)
    {
        return selectedModifierEnabledForPatch(patchId, MODIFIER_FARMING_CAPE);
    }

    private boolean attasEnabled(final PatchId patchId)
    {
        return selectedModifierEnabledForPatch(patchId, MODIFIER_ATTAS);
    }

    private boolean bottomlessBucketEnabled(final PatchId patchId)
    {
        return selectedModifierEnabledForPatch(patchId, MODIFIER_BOTTOMLESS_BUCKET);
    }

    private boolean amuletOfBountyEnabled(final PatchId patchId, final CalcCropDefinition crop)
    {
        return crop != null
                && "Allotment".equals(crop.getGroup())
                && selectedModifierEnabledForPatch(patchId, MODIFIER_AMULET_OF_BOUNTY);
    }

    private boolean protectionPaymentsEnabled(final PatchId patchId, final CalcCropDefinition crop)
    {
        return crop != null
                && !crop.getProtectionPayments().isEmpty()
                && selectedModifierEnabledForPatch(patchId, MODIFIER_PROTECTION_PAYMENT);
    }

    private int diaryBonusForPatch(final PatchId patchId)
    {
        return 0;
    }

    private FarmingLevelState currentFarmingLevelState()
    {
        final Integer override = selectedFarmingLevelOverride();
        if (override != null && override > 0)
        {
            return new FarmingLevelState(override, override);
        }

        final Integer liveVisible = currentLiveFarmingLevel();
        if (liveVisible != null && liveVisible > 0)
        {
            return new FarmingLevelState(liveVisible, liveVisible);
        }

        return new FarmingLevelState(1, 1);
    }

    private Integer currentLiveFarmingLevel()
    {
        if (client == null || clientThread == null)
        {
            return null;
        }

        final int[] visibleLevel = {-1};
        final int[] effectiveLevel = {-1};
        final CountDownLatch latch = new CountDownLatch(1);
        clientThread.invokeLater(() ->
        {
            try
            {
                visibleLevel[0] = client.getRealSkillLevel(Skill.FARMING);
                effectiveLevel[0] = client.getBoostedSkillLevel(Skill.FARMING);
            }
            catch (final RuntimeException ignored)
            {
                visibleLevel[0] = -1;
                effectiveLevel[0] = -1;
            }
            finally
            {
                latch.countDown();
            }
            return false;
        });

        try
        {
            latch.await(500, TimeUnit.MILLISECONDS);
        }
        catch (final InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            return null;
        }

        final int effective = effectiveLevel[0] > 0 ? effectiveLevel[0] : -1;
        if (effective > 0)
        {
            return effective;
        }

        final int visible = visibleLevel[0] > 0 ? visibleLevel[0] : -1;
        return visible > 0 ? visible : null;
    }

    private int itemPrice(final int itemId)
    {
        if (itemId <= 0)
        {
            return -1;
        }

        final Integer cachedPrice = itemPriceCache.get(itemId);
        if (cachedPrice != null)
        {
            return cachedPrice;
        }

        requestItemPrice(itemId);
        return -1;
    }

    private int itemPrice(final CalcItemRef item)
    {
        if (item == null || !item.hasGePrice())
        {
            return -1;
        }

        final Integer itemId = item.getItemId();
        if (itemId != null && itemId > 0)
        {
            return itemPrice(itemId);
        }

        final int resolvedItemId = resolveItemId(item.getName());
        if (resolvedItemId <= 0)
        {
            return -1;
        }

        return itemPrice(resolvedItemId);
    }

    private int resolveItemId(final String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return -1;
        }

        final String key = normalizeItemLookupName(itemName);
        final Integer cached = itemIdSearchCache.get(key);
        if (cached != null)
        {
            return cached;
        }

        requestItemSearch(itemName);
        return -1;
    }


    private void requestItemSearch(final String itemName)
    {
        if (itemManager == null || clientThread == null || itemName == null || itemName.trim().isEmpty())
        {
            return;
        }

        final String key = normalizeItemLookupName(itemName);
        if (!pendingItemSearches.add(key))
        {
            return;
        }

        final String lookupName = itemName.trim();
        clientThread.invokeLater(() ->
        {
            int resolvedItemId = -1;
            try
            {
                final List<ItemPrice> results = itemManager.search(lookupName);
                resolvedItemId = chooseSearchedItemId(lookupName, results);
            }
            catch (Exception ignored)
            {
                resolvedItemId = -1;
            }

            final int finalResolvedItemId = resolvedItemId;
            SwingUtilities.invokeLater(() ->
            {
                pendingItemSearches.remove(key);
                itemIdSearchCache.put(key, finalResolvedItemId);
            });
        });
    }

    private int chooseSearchedItemId(final String itemName, final List<ItemPrice> results)
    {
        if (results == null || results.isEmpty())
        {
            return -1;
        }

        final String wanted = normalizeItemLookupName(itemName);
        for (final ItemPrice itemPrice : results)
        {
            if (itemPrice != null && wanted.equals(normalizeItemLookupName(itemPrice.getName())))
            {
                return itemPrice.getId();
            }
        }

        for (final ItemPrice itemPrice : results)
        {
            if (itemPrice != null && normalizeItemLookupName(itemPrice.getName()).startsWith(wanted))
            {
                return itemPrice.getId();
            }
        }

        final ItemPrice first = results.get(0);
        return first == null ? -1 : first.getId();
    }

    private String normalizeItemLookupName(final String itemName)
    {
        return itemName
                .replaceAll("\\s*\\(\\d+\\)$", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private void requestItemPrice(final int itemId)
    {
        if (itemManager == null || clientThread == null || itemId <= 0)
        {
            return;
        }

        if (!pendingPriceLoads.add(itemId))
        {
            return;
        }

        clientThread.invokeLater(() ->
        {
            int resolvedPrice = -1;
            try
            {
                final int price = itemManager.getItemPrice(itemId);
                resolvedPrice = price > 0 ? price : -1;
            }
            catch (final RuntimeException ex)
            {
                resolvedPrice = -1;
            }

            final int finalResolvedPrice = resolvedPrice;
            SwingUtilities.invokeLater(() ->
            {
                pendingPriceLoads.remove(itemId);
                itemPriceCache.put(itemId, finalResolvedPrice);
            });
            return false;
        });
    }

    private String formatGp(final long value)
    {
        return String.format(Locale.US, "%,d gp", value);
    }

    private String formatSignedGp(final long value)
    {
        if (value == 0L)
        {
            return formatGp(0L);
        }
        return (value > 0L ? "+" : "-") + formatGp(Math.abs(value));
    }

    private String formatXp(final double value)
    {
        final double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.0001d)
        {
            return String.format(Locale.US, "%,d xp", (long) rounded);
        }
        return String.format(Locale.US, "%,.1f xp", value);
    }

    private String formatSignedXp(final double value)
    {
        if (Math.abs(value) < 0.0001d)
        {
            return formatXp(0.0d);
        }
        return (value > 0.0d ? "+" : "-") + formatXp(Math.abs(value));
    }

    private String formatQuantity(final double value)
    {
        final double rounded = Math.rint(value);
        if (Math.abs(value - rounded) < 0.0001d)
        {
            return String.format(Locale.US, "%,d", (long) rounded);
        }
        return String.format(Locale.US, "%,.1f", value);
    }

    private RouteCalcState selectedRouteCalcState()
    {
        return selectedRouteId == null ? null : routeCalcStates.get(selectedRouteId);
    }

    private RouteCalcState routeCalcState(final RouteId routeId)
    {
        return routeCalcStates.computeIfAbsent(routeId, ignored -> new RouteCalcState());
    }

    private Map<String, List<PatchId>> groupRoutePatches(final Route route)
    {
        final LinkedHashMap<String, List<PatchId>> grouped = new LinkedHashMap<>();
        for (final PatchId patchId : route.getPatchIds())
        {
            grouped.computeIfAbsent(patchId.getGroup(), ignored -> new ArrayList<>()).add(patchId);
        }
        return grouped;
    }

    private String displayPatch(final PatchId patchId)
    {
        final String locationName = patchId.getLocationName();
        final String slotLabel = patchId.getSlotLabel();
        if (locationName != null && !locationName.trim().isEmpty())
        {
            if (slotLabel != null && !slotLabel.trim().isEmpty())
            {
                return locationName + " — " + slotLabel;
            }
            return locationName;
        }
        return patchId.getLabel();
    }

    private String editingRowTitle(final PatchId patchId)
    {
        final String locationName = patchId.getLocationName();
        final String slotLabel = patchId.getSlotLabel();
        final String base = (locationName != null && !locationName.trim().isEmpty())
                ? locationName
                : patchId.getLabel();

        if (slotLabel != null && !slotLabel.trim().isEmpty())
        {
            return base + " — " + patchId.getGroup() + " — " + slotLabel;
        }
        return base + " — " + patchId.getGroup();
    }

    private Set<String> collapsedGroupsForSelectedRoute()
    {
        if (selectedRouteId == null)
        {
            return new HashSet<>();
        }
        return routeCollapsedGroups.computeIfAbsent(selectedRouteId, ignored -> new HashSet<>());
    }

    private boolean isGroupCollapsed(final String group)
    {
        return selectedRouteId != null && collapsedGroupsForSelectedRoute().contains(group);
    }

    private void toggleGroupCollapsed(final String group)
    {
        if (selectedRouteId == null || group == null || group.trim().isEmpty())
        {
            return;
        }

        final Set<String> collapsed = collapsedGroupsForSelectedRoute();
        if (collapsed.contains(group))
        {
            collapsed.remove(group);
        }
        else
        {
            collapsed.add(group);
            if (selectedPatchId != null && Objects.equals(selectedPatchId.getGroup(), group))
            {
                selectedPatchId = null;
                selectedGroup = group;
            }
        }

        final Map<String, List<PatchId>> grouped = selectedRoute == null
                ? new LinkedHashMap<>()
                : groupRoutePatches(selectedRoute);
        rebuildGroupedList(grouped);
        revalidate();
        repaint();
    }

    private void installToggleOnly(final Component component, final Runnable action)
    {
        component.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(final MouseEvent e)
            {
                if (!SwingUtilities.isLeftMouseButton(e))
                {
                    return;
                }
                e.consume();
                action.run();
            }
        });
    }

    private void applyFilterSizing()
    {
        final float scale = textScale();

        Font base = (Font) filterField.getClientProperty(PROP_FILTER_BASE_FONT);
        if (base == null)
        {
            base = filterField.getFont();
            filterField.putClientProperty(PROP_FILTER_BASE_FONT, base);
        }
        filterField.setFont(UiFont.scaled(base, scale, Font.PLAIN));
        filterResultsList.setFont(UiFont.scaled(base, scale, Font.PLAIN));

        final int fieldHeight = Math.round(26 * scale);
        filterField.setPreferredSize(new Dimension(0, fieldHeight));
        filterField.setMinimumSize(new Dimension(0, fieldHeight));
        filterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, fieldHeight));

        final int padY = Math.max(4, Math.round(6 * scale));
        final int padLeftX = Math.max(2, Math.round(2 * scale));
        final int padRightX = Math.max(6, Math.round(8 * scale));
        Icon searchIcon = null;
        if (config.showFilterSearchIcon())
        {
            final int maxPx = Math.max(12, fieldHeight - Math.round(10 * scale));
            final int px = (maxPx >= 64) ? 64 : (maxPx >= 28) ? 28 : 16;
            final BufferedImage img = loadToolbarImage("search", px);
            if (img != null)
            {
                searchIcon = new ForegroundTintIcon(img);
            }
        }
        final int iconGap = Math.max(6, Math.round(6 * scale));
        filterField.setBorder(new LeftIconBorder(padY, padLeftX, padY, padRightX, searchIcon, iconGap));

        if (isFilterPlaceholderActive())
        {
            filterField.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        }
        else
        {
            filterField.setForeground(ColorScheme.TEXT_COLOR);
        }
    }

    private float textScale()
    {
        return config.textScale().multiplier();
    }

    private static String escapeHtml(final String text)
    {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private BufferedImage loadToolbarImage(final String key, final int px)
    {
        final String path = "/toolbar/" + px + "/" + key + ".png";
        final java.net.URL url = CalcPanel.class.getResource(path);
        if (url == null)
        {
            return null;
        }

        try
        {
            return ImageIO.read(url);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private static final class LeftIconBorder extends AbstractBorder
    {
        private final Insets insets;
        private final Icon icon;
        private final int iconX;

        private LeftIconBorder(int top, int left, int bottom, int right, Icon icon, int gap)
        {
            this.icon = icon;
            final int extra = icon == null ? 0 : (icon.getIconWidth() + gap);
            this.insets = new Insets(top, left + extra, bottom, right);
            this.iconX = left;
        }

        @Override
        public Insets getBorderInsets(Component c)
        {
            return new Insets(insets.top, insets.left, insets.bottom, insets.right);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets out)
        {
            out.top = insets.top;
            out.left = insets.left;
            out.bottom = insets.bottom;
            out.right = insets.right;
            return out;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
        {
            if (icon == null)
            {
                return;
            }
            final int iy = y + (height - icon.getIconHeight()) / 2;
            icon.paintIcon(c, g, x + iconX, iy);
        }
    }

    private static final class ForegroundTintIcon implements Icon
    {
        private final BufferedImage base;
        private final int w;
        private final int h;
        private final Map<Integer, BufferedImage> cache = new HashMap<>();

        private ForegroundTintIcon(final BufferedImage base)
        {
            this.base = base;
            this.w = base.getWidth();
            this.h = base.getHeight();
        }

        @Override
        public int getIconWidth()
        {
            return w;
        }

        @Override
        public int getIconHeight()
        {
            return h;
        }

        private BufferedImage tinted(final Color color)
        {
            final int rgb = color.getRGB() & 0x00FFFFFF;
            return cache.computeIfAbsent(rgb, ignored ->
            {
                final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                for (int yy = 0; yy < h; yy++)
                {
                    for (int xx = 0; xx < w; xx++)
                    {
                        final int argb = base.getRGB(xx, yy);
                        final int a = (argb >>> 24) & 0xFF;
                        if (a == 0)
                        {
                            out.setRGB(xx, yy, 0);
                        }
                        else
                        {
                            out.setRGB(xx, yy, (a << 24) | rgb);
                        }
                    }
                }
                return out;
            });
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            final Color fg = c instanceof JTextField && ((JTextField) c).getText().trim().isEmpty()
                    ? ColorScheme.MEDIUM_GRAY_COLOR
                    : c.getForeground();
            g.drawImage(tinted(fg), x, y, null);
        }
    }

    private static final class PlaceholderTextField extends JTextField
    {
        private final String placeholder;

        private PlaceholderTextField(final String placeholder)
        {
            this.placeholder = placeholder;
        }

        @Override
        protected void paintComponent(final Graphics g)
        {
            super.paintComponent(g);
            if (!getText().isEmpty())
            {
                return;
            }

            final Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
                g2.setFont(getFont());
                final Insets ins = getInsets();
                final FontMetrics fm = g2.getFontMetrics();
                final int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(placeholder, ins.left, y);
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    private static final class TrackViewportWidthPanel extends JPanel implements Scrollable
    {
        private TrackViewportWidthPanel()
        {
            super();
        }

        private TrackViewportWidthPanel(final LayoutManager layout)
        {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(final Rectangle visibleRect, final int orientation, final int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(final Rectangle visibleRect, final int orientation, final int direction)
        {
            return Math.max(48, visibleRect.height - 48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }

    private static final class FarmingLevelDocumentFilter extends DocumentFilter
    {
        @Override
        public void insertString(final FilterBypass fb, final int offset, final String string, final AttributeSet attr) throws BadLocationException
        {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void replace(final FilterBypass fb, final int offset, final int length, final String text, final AttributeSet attrs) throws BadLocationException
        {
            final String replacement = text == null ? "" : text;
            final String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            final String candidate = current.substring(0, offset) + replacement + current.substring(offset + length);
            if (candidate.isEmpty() || candidate.matches("\\d{1,3}"))
            {
                fb.replace(offset, length, replacement, attrs);
            }
        }
    }

    private static final class FarmingLevelState
    {
        private final Integer visibleFarmingLevel;
        private final Integer effectiveFarmingLevel;

        private FarmingLevelState(final Integer visibleFarmingLevel, final Integer effectiveFarmingLevel)
        {
            this.visibleFarmingLevel = visibleFarmingLevel;
            this.effectiveFarmingLevel = effectiveFarmingLevel;
        }

        private static FarmingLevelState unavailable()
        {
            return new FarmingLevelState(null, null);
        }
    }

    private static final class RouteCalcState
    {
        private final Map<String, String> defaultCropNamesByGroup = new LinkedHashMap<>();
        private final Map<PatchId, String> overrideCropNamesByPatch = new LinkedHashMap<>();
        private final Map<String, CalcCompostTier> defaultCompostTierByGroup = new LinkedHashMap<>();
        private final Map<PatchId, CalcCompostTier> overrideCompostTierByPatch = new LinkedHashMap<>();
        private final Map<String, Map<String, Boolean>> defaultModifierEnabledByGroup = new LinkedHashMap<>();
        private final Map<PatchId, Map<String, Boolean>> overrideModifierEnabledByPatch = new LinkedHashMap<>();
        private Integer farmingLevelOverride;
    }

    private enum CropDisplayKind
    {
        UNRESOLVED,
        INHERITED,
        OVERRIDE,
        UNSUPPORTED
    }

    private static final class CropDisplayState
    {
        private final CropDisplayKind kind;
        private final String cropName;

        private CropDisplayState(final CropDisplayKind kind, final String cropName)
        {
            this.kind = kind;
            this.cropName = cropName;
        }

        private static CropDisplayState unresolved()
        {
            return new CropDisplayState(CropDisplayKind.UNRESOLVED, null);
        }

        private static CropDisplayState inherited(final String cropName)
        {
            return new CropDisplayState(CropDisplayKind.INHERITED, cropName);
        }

        private static CropDisplayState override(final String cropName)
        {
            return new CropDisplayState(CropDisplayKind.OVERRIDE, cropName);
        }

        private static CropDisplayState unsupported()
        {
            return new CropDisplayState(CropDisplayKind.UNSUPPORTED, null);
        }

        private String valueText()
        {
            switch (kind)
            {
                case UNRESOLVED:
                    return "[Set]";
                case UNSUPPORTED:
                    return "Unsupported";
                case INHERITED:
                case OVERRIDE:
                default:
                    return cropName;
            }
        }

        private Color valueColor()
        {
            switch (kind)
            {
                case UNRESOLVED:
                    return ColorScheme.BRAND_ORANGE;
                case UNSUPPORTED:
                    return ColorScheme.MEDIUM_GRAY_COLOR;
                case INHERITED:
                case OVERRIDE:
                default:
                    return ColorScheme.TEXT_COLOR;
            }
        }
    }

    private static final class RouteListItem
    {
        private final RouteId routeId;
        private final String name;
        private final int patchCount;

        private RouteListItem(final RouteId routeId, final String name, final int patchCount)
        {
            this.routeId = routeId;
            this.name = name;
            this.patchCount = patchCount;
        }
    }

    private static final class RouteListCellRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(
                final JList<?> list,
                final Object value,
                final int index,
                final boolean isSelected,
                final boolean cellHasFocus)
        {
            final JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof RouteListItem)
            {
                final RouteListItem item = (RouteListItem) value;
                label.setText(item.name + "  (" + item.patchCount + ")");
            }

            if (isSelected)
            {
                label.setBackground(ColorScheme.BRAND_ORANGE.darker());
                label.setForeground(ColorScheme.TEXT_COLOR);
            }
            else
            {
                label.setBackground(ColorScheme.DARK_GRAY_COLOR);
                label.setForeground(ColorScheme.TEXT_COLOR);
            }

            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            return label;
        }
    }

}
