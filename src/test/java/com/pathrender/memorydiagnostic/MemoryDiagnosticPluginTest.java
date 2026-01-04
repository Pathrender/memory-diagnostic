package com.pathrender.memorydiagnostic;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MemoryDiagnosticPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MemoryDiagnosticPlugin.class);
		RuneLite.main(args);
	}
}
