package com.farmutils;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import com.farmutils.config.NavContent;
import com.farmutils.config.NavColumns;


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