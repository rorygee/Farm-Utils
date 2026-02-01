package com.farmutils;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("farmutils")
public interface FarmutilsConfig extends Config
{
    @ConfigItem(
            keyName = "staleDays",
            name = "Stale after (days)",
            description = "Show a patch as stale if it hasn't been updated in this many days."
    )

    default int staleDays()
    {
        return 7;
    }

    @ConfigSection(
            name = "Appearance",
            description = "Display settings",
            position = 90
    )
    String appearanceSection = "appearance";

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



}
