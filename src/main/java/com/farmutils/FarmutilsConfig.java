package com.farmutils;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import com.farmutils.config.NavContent;
import com.farmutils.config.NavColumns;

import java.awt.Color;



@ConfigGroup("farmutils")
public interface FarmutilsConfig extends Config
{


    @ConfigSection(
            name = "Content",
            description = "What appears in the patch list",
            position = 80
    )
    String contentSection = "content";

    @ConfigItem(
            keyName = "staleDays",
            name = "Stale after (days)",
            description = "Show a patch as stale if it hasn't been updated in this many days."
    )
    default int staleDays()
    {
        return 7;
    }

    @ConfigItem(
        keyName = "hideQuestPatches",
        name = "Hide quest patches",
        description = "Hide patches that are only relevant during quests.",
        position = 1,
        section = contentSection
)
        default boolean hideQuestPatches()
{
    return false;
    }

    @ConfigSection(
            name = "Appearance",
            description = "Display settings",
            position = 90
    )
    String appearanceSection = "appearance";

    @ConfigSection(
            name = "Colours",
            description = "State, caret, and progress colours",
            position = 91
    )
    String coloursSection = "colours";

    @ConfigItem(
            keyName = "navContent",
            name = "Navigation content",
            description = "Choose how navigation buttons are rendered.",
            position = 50
    )
    default NavContent navContent()
    {
        return NavContent.TEXT_ONLY;
    }

    @ConfigItem(
            keyName = "navColumns",
            name = "Navigation columns",
            description = "How many items per row in the navigation.",
            position = 51
    )
    default NavColumns navColumns()
    {
        return NavColumns.FOUR_PER_ROW;
    }

@ConfigItem(
            keyName = "textScale",
            name = "Text scale",
            description = "Scales text in the Farm Utils panel",
            position = 1,
            section = appearanceSection
    )
    default com.farmutils.config.TextScale textScale()
    {
        return com.farmutils.config.TextScale.NORMAL;
    }

    @ConfigItem(
            keyName = "emphasizeHeaders",
            name = "Emphasize group headers",
            description = "Slightly larger/bolder group headers",
            position = 2,
            section = appearanceSection
    )
    default boolean emphasizeHeaders()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showPatchCategoryPrefix",
            name = "Show patch category prefix",
            description = "Prefix each patch name with its category (e.g. \"Herb -\", \"Tree -\").",
            position = 3,
            section = appearanceSection
    )
    default boolean showPatchCategoryPrefix()
    {
        return false;
    }

    @ConfigItem(
            keyName = "largerHeadings",
            name = "Larger headings",
            description = "Slightly increase font size for group headings and subheadings.",
            position = 4,
            section = appearanceSection
    )
    default boolean largerHeadings()
    {
        return false;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(
            keyName = "secondaryTextIndentPx",
            name = "Secondary text indent (px)",
            description = "Indent the secondary (2nd/3rd) line relative to the patch name.",
            position = 6,
            section = appearanceSection
    )
    default int secondaryTextIndentPx()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "conservativeTimeEstimates",
            name = "Conservative time estimates",
            description = "When showing estimated completion times, prefer the latest (worst-case) bound.",
            position = 7,
            section = appearanceSection
    )
    default boolean conservativeTimeEstimates()
    {
        return true;
    }

    @ConfigItem(
            keyName = "indentSingleLocationRows",
            name = "Indent single patch rows",
            description = "Indent patch rows to match rows shown under location headings, even when only one patch is visible.",
            position = 8,
            section = appearanceSection
    )
    default boolean indentSingleLocationRows()
    {
        return false;
    }

enum SelectionOutlineColor
    {
        WHITE,
        ACCENT_ORANGE
    }

    @ConfigItem(
            keyName = "selectionOutlineColor",
            name = "Selection outline colour",
            description = "Outline colour used to indicate selected patches in the patch list.",
            position = 5,
            section = appearanceSection
    )
    default SelectionOutlineColor selectionOutlineColor()
    {
        return SelectionOutlineColor.WHITE;
    }

    // --- State colours (used for state text + indicator line) ---

    @ConfigItem(
            keyName = "stateColorUnknown",
            name = "State: Unknown",
            description = "Colour used for Unknown state text/indicators.",
            position = 1,
            section = coloursSection
    )
    default Color stateColorUnknown()
    {
        return new Color(150, 150, 150);
    }

    @ConfigItem(
            keyName = "stateColorEmpty",
            name = "State: Empty",
            description = "Colour used for Empty state text/indicators.",
            position = 2,
            section = coloursSection
    )
    default Color stateColorEmpty()
    {
        return new Color(170, 170, 170);
    }

    @ConfigItem(
            keyName = "stateColorGrowing",
            name = "State: Growing",
            description = "Colour used for Growing state text/indicators.",
            position = 3,
            section = coloursSection
    )
    default Color stateColorGrowing()
    {
        return new Color(110, 150, 210);
    }

    @ConfigItem(
            keyName = "stateColorReady",
            name = "State: Ready",
            description = "Colour used for Ready state text/indicators.",
            position = 4,
            section = coloursSection
    )
    default Color stateColorReady()
    {
        return new Color(90, 170, 110);
    }

    @ConfigItem(
            keyName = "stateColorDiseased",
            name = "State: Diseased",
            description = "Colour used for Diseased state text/indicators.",
            position = 5,
            section = coloursSection
    )
    default Color stateColorDiseased()
    {
        return new Color(190, 150, 70);
    }

    @ConfigItem(
            keyName = "stateColorDead",
            name = "State: Dead",
            description = "Colour used for Dead state text/indicators.",
            position = 6,
            section = coloursSection
    )
    default Color stateColorDead()
    {
        return new Color(200, 90, 90);
    }

    // --- Caret colouring ---

    enum ExpandedCaretMode
    {
        ACCENT,
        CUSTOM
    }

    enum CollapsedCaretMode
    {
        GREY("Grey"),
        STATE_OVERVIEW("State (Muted)"),
        STATE("State"),
        CUSTOM("Custom");

        private final String label;

        CollapsedCaretMode(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    @ConfigItem(
            keyName = "expandedCaretMode",
            name = "Expanded caret",
            description = "Colour mode for the expanded group caret.",
            position = 10,
            section = coloursSection
    )
    default ExpandedCaretMode expandedCaretMode()
    {
        return ExpandedCaretMode.ACCENT;
    }

    @ConfigItem(
            keyName = "expandedCaretCustomColor",
            name = "Expanded caret custom colour",
            description = "Custom colour used when Expanded caret mode is CUSTOM.",
            position = 11,
            section = coloursSection
    )
    default Color expandedCaretCustomColor()
    {
        return new Color(255, 152, 31);
    }

    @ConfigItem(
            keyName = "collapsedCaretMode",
            name = "Collapsed caret",
            description = "Colour mode for the collapsed group caret.",
            position = 12,
            section = coloursSection
    )
    default CollapsedCaretMode collapsedCaretMode()
    {
        return CollapsedCaretMode.GREY;
    }

    @ConfigItem(
            keyName = "collapsedCaretCustomColor",
            name = "Collapsed caret custom colour",
            description = "Custom colour used when Collapsed caret mode is CUSTOM.",
            position = 13,
            section = coloursSection
    )
    default Color collapsedCaretCustomColor()
    {
        return new Color(130, 130, 130);
    }

    @ConfigItem(
            keyName = "globalScroll",
            name = "Scroll anywhere (Patches)",
            description = "When enabled, scrolling over the header area (tabs/filter/toolbar) scrolls the patch list.",
            position = 4,
            section = appearanceSection
    )
    default boolean globalScroll()
    {
        return false;
    }

    enum ScrollbarVisibility
    {
        NO,
        SOMETIMES,
        ALWAYS
    }

    @ConfigItem(
            keyName = "scrollbarVisibility",
            name = "Scrollbar",
            description = "Scrollbar visibility for the patch list.",
            position = 10,
            section = appearanceSection
    )
    default ScrollbarVisibility scrollbarVisibility()
    {
        return ScrollbarVisibility.SOMETIMES;
    }

    @ConfigItem(
            keyName = "scrollbarWidth",
            name = "Scrollbar width",
            description = "Scrollbar width in pixels. Valid range is 6–16.",
            position = 11,
            section = appearanceSection
    )
    default int scrollbarWidth()
    {
        return 8;
    }

    /**
     * Scrollbar visual style.
     *
     * DARK: calm dark baseline; hover/press lighten.
     * ACCENT: orange thumb with distinct hover/press states; neutral well.
     */
    enum ScrollbarStyle
    {
        DARK,
        ACCENT
    }

    enum ScrollbarOutlineStyle
    {
        DARK,
        ACCENT
    }

    @ConfigItem(
            keyName = "scrollbarOutlineStyle",
            name = "Scrollbar outline style",
            description = "Outline colour of the scrollbar strip (track + buttons).",
            position = 14, // place near other scrollbar options
            section = appearanceSection
    )
    default ScrollbarOutlineStyle scrollbarOutlineStyle()
    {
        return ScrollbarOutlineStyle.DARK;
    }


    @ConfigItem(
            keyName = "scrollbarColor",
            name = "Scrollbar style",
            description = "Visual style for the scrollbar and scroll buttons.",
            position = 12,
            section = appearanceSection
    )
    default ScrollbarStyle scrollbarColor()
    {
        return ScrollbarStyle.DARK;
    }

    @ConfigItem(
            keyName = "showScrollButtons",
            name = "Show scroll buttons",
            description = "Show up/down scroll buttons on the scrollbar.",
            position = 13,
            section = appearanceSection
    )
    default boolean showScrollButtons()
    {
        return false;
    }

    @ConfigItem(
            keyName = "toolbarSolidBackground",
            name = "Toolbar solid background",
            description = "Use a solid background behind the toolbar area on the Patches tab.",
            position = 15,
            section = appearanceSection
    )
    default boolean toolbarSolidBackground()
    {
        return true;
    }

    @ConfigItem(
            keyName = "scrollbarWellBackground",
            name = "Scrollbar well background",
            description = "Paint a solid background behind the scrollbar (track + buttons).",
            position = 16, // pick an appropriate slot in your appearance section
            section = appearanceSection
    )
    default boolean scrollbarWellBackground()
    {
        return false;
    }

}