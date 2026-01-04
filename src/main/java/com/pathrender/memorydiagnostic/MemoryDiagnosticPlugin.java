package com.pathrender.memorydiagnostic;

import com.google.common.cache.Cache;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.events.ClientTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
	name = "Memory Diagnostic",
	description = "Estimates and displays relative memory usage of enabled plugins."
)
public class MemoryDiagnosticPlugin extends Plugin
{
	private static final int MAX_DEPTH = 2;
	private static final long OBJECT_UNITS = 16;
	private static final long ARRAY_BASE_UNITS = 16;
	private static final long ARRAY_ENTRY_UNITS = 8;
	private static final long COLLECTION_BASE_UNITS = 24;
	private static final long COLLECTION_ENTRY_UNITS = 8;
	private static final long MAP_BASE_UNITS = 32;
	private static final long MAP_ENTRY_UNITS = 16;
	private static final long CACHE_BASE_UNITS = 48;
	private static final long CACHE_ENTRY_UNITS = 24;
	private static final long STRING_BASE_UNITS = 16;
	private static final long STRING_CHAR_UNITS = 2;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private MemoryDiagnosticConfig config;

	@Inject
	private MemoryDiagnosticPanel panel;

	private final MemoryEstimator estimator = new MemoryEstimator();

	private volatile List<PluginUsage> usage = Collections.emptyList();
	private volatile long totalUnits = 0;
	private long nextUpdateMillis = 0;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		navigationButton = NavigationButton.builder()
			.tooltip("Memory Diagnostic")
			.icon(createIcon())
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navigationButton);
		updateUsage();
	}

	@Override
	protected void shutDown()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		panel.clear();
		usage = Collections.emptyList();
		totalUnits = 0;
		nextUpdateMillis = 0;
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		long now = System.currentTimeMillis();
		int intervalSeconds = Math.max(1, config.refreshIntervalSeconds());
		if (now < nextUpdateMillis)
		{
			return;
		}

		nextUpdateMillis = now + intervalSeconds * 1000L;
		updateUsage();
	}

	List<PluginUsage> getUsage()
	{
		return usage;
	}

	long getTotalUnits()
	{
		return totalUnits;
	}

	@Provides
	MemoryDiagnosticConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MemoryDiagnosticConfig.class);
	}

	private void updateUsage()
	{
		List<PluginUsage> updated = new ArrayList<>();
		long total = 0;

		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (!pluginManager.isPluginEnabled(plugin))
			{
				continue;
			}

			long estimate = estimator.estimate(plugin);
			String name = getPluginName(plugin);
			updated.add(new PluginUsage(name, estimate));
			total += estimate;
		}

		updated.sort(Comparator.comparingLong(PluginUsage::getUnits).reversed());
		usage = Collections.unmodifiableList(updated);
		totalUnits = total;
		panel.update(usage, totalUnits);
	}

	private static String getPluginName(Plugin plugin)
	{
		PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
		if (descriptor != null && !descriptor.name().isEmpty())
		{
			return descriptor.name();
		}
		return plugin.getClass().getSimpleName();
	}

	static final class PluginUsage
	{
		private final String name;
		private final long units;

		PluginUsage(String name, long units)
		{
			this.name = name;
			this.units = units;
		}

		String getName()
		{
			return name;
		}

		long getUnits()
		{
			return units;
		}
	}

	private static final class MemoryEstimator
	{
		private final Map<Class<?>, Field[]> fieldCache = new HashMap<>();

		long estimate(Plugin plugin)
		{
			String ownerPackage = plugin.getClass().getPackage().getName();
			IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
			return estimateObject(plugin, visited, 0, ownerPackage);
		}

		private long estimateObject(Object obj, IdentityHashMap<Object, Boolean> visited, int depth, String ownerPackage)
		{
			if (obj == null || visited.containsKey(obj))
			{
				return 0;
			}
			visited.put(obj, Boolean.TRUE);

			Class<?> type = obj.getClass();

			if (type.isArray())
			{
				int length = Array.getLength(obj);
				return ARRAY_BASE_UNITS + (long) length * ARRAY_ENTRY_UNITS;
			}

			if (obj instanceof Cache)
			{
				long size = ((Cache<?, ?>) obj).size();
				return CACHE_BASE_UNITS + size * CACHE_ENTRY_UNITS;
			}

			if (obj instanceof Map)
			{
				int size = ((Map<?, ?>) obj).size();
				return MAP_BASE_UNITS + (long) size * MAP_ENTRY_UNITS;
			}

			if (obj instanceof Collection)
			{
				int size = ((Collection<?>) obj).size();
				return COLLECTION_BASE_UNITS + (long) size * COLLECTION_ENTRY_UNITS;
			}

			if (obj instanceof CharSequence)
			{
				int length = ((CharSequence) obj).length();
				return STRING_BASE_UNITS + (long) length * STRING_CHAR_UNITS;
			}

			if (!isOwnedType(type, ownerPackage))
			{
				return 0;
			}

			long total = OBJECT_UNITS;

			if (depth >= MAX_DEPTH)
			{
				return total;
			}

			for (Field field : getAllFields(type))
			{
				try
				{
					total += estimateObject(field.get(obj), visited, depth + 1, ownerPackage);
				}
				catch (IllegalAccessException ex)
				{
					// Ignore inaccessible fields; estimator should stay best-effort.
				}
			}

			return total;
		}

		private Field[] getAllFields(Class<?> type)
		{
			return fieldCache.computeIfAbsent(type, cls ->
			{
				List<Field> fields = new ArrayList<>();
				for (Class<?> current = cls; current != null && current != Object.class; current = current.getSuperclass())
				{
					for (Field field : current.getDeclaredFields())
					{
						int modifiers = field.getModifiers();
						if (Modifier.isStatic(modifiers) || field.isSynthetic())
						{
							continue;
						}
						try
						{
							field.setAccessible(true);
						}
						catch (RuntimeException ex)
						{
							continue;
						}
						fields.add(field);
					}
				}
				return fields.toArray(new Field[0]);
			});
		}

		private static boolean isOwnedType(Class<?> type, String ownerPackage)
		{
			Package pkg = type.getPackage();
			if (pkg == null)
			{
				return false;
			}
			return pkg.getName().startsWith(ownerPackage);
		}
	}

	private static BufferedImage createIcon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(0, 0, 0, 0));
		graphics.fillRect(0, 0, 16, 16);
		graphics.setColor(new Color(53, 174, 255));
		graphics.fillRect(2, 9, 3, 5);
		graphics.fillRect(7, 6, 3, 8);
		graphics.fillRect(12, 3, 3, 11);
		graphics.setColor(new Color(22, 22, 22));
		graphics.setStroke(new BasicStroke(1.5f));
		graphics.drawRect(1, 1, 13, 13);
		graphics.dispose();
		return image;
	}
}
