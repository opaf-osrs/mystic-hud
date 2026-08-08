package com.pluginideahub.mystichud;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(MysticHudConfig.GROUP)
public interface MysticHudConfig extends Config
{
	String GROUP = "mystichud";

	@ConfigItem(
		keyName = "squareMinimap",
		name = "Square minimap",
		description = "Remove the circular mask so the minimap renders as a rectangle",
		position = 1
	)
	default boolean squareMinimap()
	{
		return true;
	}


	@ConfigItem(
		keyName = "orbOrder",
		name = "Orb order",
		description = "Left to right order of the orb row",
		position = 3
	)
	default OrbOrder orbOrder()
	{
		return OrbOrder.SPEC_PRAYER_RUN_HP;
	}

	@ConfigItem(
		keyName = "drawBlocks",
		name = "Square orb blocks",
		description = "Draw a colour-filled block behind each orb instead of the stock capsule",
		position = 4
	)
	default boolean drawBlocks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mapOutline",
		name = "Map outline",
		description = "Draw a UI frame around the rectangular minimap",
		position = 9
	)
	default boolean mapOutline()
	{
		return true;
	}

	@Range(min = 24, max = 70)
	@ConfigItem(
		keyName = "orbRowHeight",
		name = "Orb row height",
		description = "Height of the four orb blocks",
		position = 5
	)
	default int orbRowHeight()
	{
		return 40;
	}

	@ConfigItem(
		keyName = "showExtraOrbs",
		name = "XP orb",
		description = "Show the xp counter orb beside the minimap",
		position = 6
	)
	default boolean showExtraOrbs()
	{
		return false;
	}

	@Range(min = -40, max = 40)
	@ConfigItem(
		keyName = "frameNudgeX",
		name = "Frame nudge X",
		description = "Shift the frame and row sideways until they sit exactly on the drawn map",
		position = 3
	)
	default int frameNudgeX()
	{
		return 0;
	}

	@Range(min = -40, max = 40)
	@ConfigItem(
		keyName = "frameNudgeY",
		name = "Frame nudge Y",
		description = "Shift the frame and row up or down until they sit exactly on the drawn map",
		position = 4
	)
	default int frameNudgeY()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showCompass",
		name = "Compass",
		description = "Show a small round compass over the minimap's top left corner",
		position = 11
	)
	default boolean showCompass()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWorldMap",
		name = "World map orb",
		description = "Show the world map orb in the minimap's bottom right corner",
		position = 10
	)
	default boolean showWorldMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "attachInventory",
		name = "Attach to inventory",
		description = "Glue the block flush onto the inventory panel so they read as one piece; snaps to the top right corner while the inventory is closed. Disables alt-drag.",
		position = 7
	)
	default boolean attachInventory()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideWhenClosed",
		name = "Hide when inventory closed",
		description = "Hide the minimap block entirely while no side panel is open",
		position = 8
	)
	default boolean hideWhenClosed()
	{
		return false;
	}

	@ConfigItem(
		keyName = "layoutVersion",
		name = "",
		description = "",
		hidden = true
	)
	default int layoutVersion()
	{
		return 0;
	}

	@Range(min = -400, max = 200)
	@ConfigItem(
		keyName = "posX",
		name = "Position X",
		description = "Move the block left or right. 0 lines it up with the inventory.",
		position = 20
	)
	default int posX()
	{
		return 0;
	}

	@Range(min = -600, max = 600)
	@ConfigItem(
		keyName = "posY",
		name = "Position Y",
		description = "Move the block up or down. 0 sits it just above the inventory.",
		position = 21
	)
	default int posY()
	{
		return 0;
	}

	enum OrbOrder
	{
		SPEC_PRAYER_RUN_HP,
		HP_PRAYER_RUN_SPEC,
		HP_PRAYER_SPEC_RUN
	}
}
