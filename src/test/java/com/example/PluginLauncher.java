package com.example;

import com.KittenTracker.KittenTrackerPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PluginLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(KittenTrackerPlugin.class);
		RuneLite.main(args);
	}
}