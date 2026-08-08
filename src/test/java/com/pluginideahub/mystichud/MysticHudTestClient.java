package com.pluginideahub.mystichud;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MysticHudTestClient
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MysticHudPlugin.class);
		RuneLite.main(args);
	}
}
