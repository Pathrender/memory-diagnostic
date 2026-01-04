package com.pathrender.memorydiagnostic;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("memorydiagnostic")
public interface MemoryDiagnosticConfig extends Config
{
	@Range(min = 1, max = 300)
	@ConfigItem(
		keyName = "refreshIntervalSeconds",
		name = "Refresh interval (seconds)",
		description = "How often to recalculate memory estimates."
	)
	default int refreshIntervalSeconds()
	{
		return 10;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "maxPlugins",
		name = "Max plugins to show",
		description = "Maximum number of plugins to list in the overlay."
	)
	default int maxPlugins()
	{
		return 10;
	}
}
