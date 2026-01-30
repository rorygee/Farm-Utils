package com.farmutils;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

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
}
