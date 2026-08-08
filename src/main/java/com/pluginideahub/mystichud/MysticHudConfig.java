package com.pluginideahub.mystichud;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(MysticHudConfig.GROUP)
public interface MysticHudConfig extends Config
{
	String GROUP = "mystichud";

	@ConfigSection(
		name = "Orb block layout",
		description = "Size and placement of the icon and the value inside each orb block",
		position = 5,
		closedByDefault = true
	)
	String ORB_LAYOUT = "orbLayout";

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
		position = 10
	)
	default boolean mapOutline()
	{
		return true;
	}

	@Range(min = 24, max = 70)
	@ConfigItem(
		keyName = "orbRowHeight",
		name = "Row height",
		description = "Height of the four orb blocks",
		section = ORB_LAYOUT,
		position = 1
	)
	default int orbRowHeight()
	{
		return 40;
	}

	// the four stock icons are authored at different sizes (15x14, 20x20, 15x18, 16x16),
	// so by default this is a CAP rather than a target: anything already inside it is
	// drawn 1:1 and stays pixel-exact, and only the oversized ones shrink. whatever the
	// value, the draw clamps it to the block so a big icon can never outgrow a short row.
	@Range(min = 6, max = 48)
	@ConfigItem(
		keyName = "orbIconSize",
		name = "Icon size",
		description = "Largest an orb icon is drawn. 20 is the biggest native size, so at 20 nothing is resized at all. Going above it needs 'Scale icons up', and needs the stacked value position to have anywhere to grow.",
		section = ORB_LAYOUT,
		position = 2
	)
	default int orbIconSize()
	{
		return 20;
	}

	@ConfigItem(
		keyName = "orbIconUpscale",
		name = "Scale icons up",
		description = "Also grow icons that are smaller than the icon size, for tall rows. Whole multiples of the native size (2x, 3x) stay sharpest.",
		section = ORB_LAYOUT,
		position = 3
	)
	default boolean orbIconUpscale()
	{
		return false;
	}

	@Range(min = 0, max = 24)
	@ConfigItem(
		keyName = "orbIconPadX",
		name = "Icon left padding",
		description = "Gap between the block's left edge and the icon",
		section = ORB_LAYOUT,
		position = 4
	)
	default int orbIconPadX()
	{
		return 3;
	}

	@Range(min = -20, max = 20)
	@ConfigItem(
		keyName = "orbIconNudgeY",
		name = "Icon nudge Y",
		description = "Shift the icon up or down from vertical centre",
		section = ORB_LAYOUT,
		position = 5
	)
	default int orbIconNudgeY()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "orbTextAlign",
		name = "Value position",
		description = "Where the number sits. Stacked puts it under the icon and spends the row's height on the icon. Over icon reserves it no space at all, which is the only way to fill the whole block with the icon.",
		section = ORB_LAYOUT,
		position = 6
	)
	default TextAlign orbTextAlign()
	{
		return TextAlign.AFTER_ICON;
	}

	@Range(min = 0, max = 24)
	@ConfigItem(
		keyName = "orbTextGap",
		name = "Icon to value gap",
		description = "Space between the icon and the number",
		section = ORB_LAYOUT,
		position = 7
	)
	default int orbTextGap()
	{
		return 3;
	}

	@Range(min = -20, max = 20)
	@ConfigItem(
		keyName = "orbTextNudgeY",
		name = "Value nudge Y",
		description = "Shift the number up or down from vertical centre",
		section = ORB_LAYOUT,
		position = 8
	)
	default int orbTextNudgeY()
	{
		return 0;
	}

	@Range(min = 8, max = 32)
	@ConfigItem(
		keyName = "orbFontSize",
		name = "Value font size",
		description = "Size of the number. 16 is the font's native size and the crispest; other sizes are interpolated.",
		section = ORB_LAYOUT,
		position = 9
	)
	default int orbFontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "orbDebug",
		name = "Debug readout",
		description = "Paint the real numbers the layout is working from over the minimap, for when a setting does not appear to do anything",
		section = ORB_LAYOUT,
		position = 10
	)
	default boolean orbDebug()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showExtraOrbs",
		name = "XP orb",
		description = "Show the xp counter orb beside the minimap",
		position = 7
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
		position = 12
	)
	default boolean showCompass()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWorldMap",
		name = "World map orb",
		description = "Show the world map orb in the minimap's bottom right corner",
		position = 11
	)
	default boolean showWorldMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "attachInventory",
		name = "Attach to inventory",
		description = "Glue the block flush onto the inventory panel so they read as one piece; snaps to the top right corner while the inventory is closed. Disables alt-drag.",
		position = 8
	)
	default boolean attachInventory()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideWhenClosed",
		name = "Hide when inventory closed",
		description = "Hide the minimap block entirely while no side panel is open",
		position = 9
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

	enum TextAlign
	{
		AFTER_ICON,
		RIGHT_EDGE,
		CENTRED,
		STACKED,
		OVER_ICON
	}
}
