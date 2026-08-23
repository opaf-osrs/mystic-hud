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

	// keyName deliberately NOT "nativeMapWidth": that key already has a stored false in
	// the profile from trying the toggle, and a stored value beats a changed default, so
	// the fix stayed switched off even after it became the default. a fresh key has
	// nothing stored against it and actually takes the default.
	@ConfigItem(
		keyName = "trueMapWidth",
		name = "True map width",
		description = "The engine only ever paints the minimap 152 wide, so a frame drawn at the inventory's 204 leaves black on the east side and puts the map's real centre 26px left of the frame's. That gap is what sends minimap walk-clicks sideways. On: the map is its true width, centred in the frame, so clicks land where you point and the black strip goes.",
		position = 2
	)
	default boolean nativeMapWidth()
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

	@Range(min = 132, max = 152)
	@ConfigItem(
		keyName = "mapHeight",
		name = "Map height",
		description = "Height of the minimap. The engine always draws 152 tall anchored to the bottom, so anything under 152 spills that much live map out above the frame instead of cropping.",
		section = ORB_LAYOUT,
		position = 0
	)
	default int mapHeight()
	{
		return 152;
	}

	@Range(min = 24, max = 120)
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

	// literal, not a cap: whatever is set here is the size drawn. nothing clamps it to
	// the block, so it will happily overflow or collide if that is what is asked for.
	@Range(max = 64)
	@ConfigItem(
		keyName = "orbIconSize",
		name = "Icon size",
		description = "Exact size the icon is drawn at, in pixels. 0 leaves it at its native size, which is the only setting that keeps it pixel-perfect. Nothing clamps this, so it can overflow the block.",
		section = ORB_LAYOUT,
		position = 2
	)
	default int orbIconSize()
	{
		return 0;
	}

	@Range(min = -32, max = 48)
	@ConfigItem(
		keyName = "orbIconPadX",
		name = "Icon X",
		description = "Icon distance from the block's left edge. Negative pushes it off the left.",
		section = ORB_LAYOUT,
		position = 4
	)
	default int orbIconPadX()
	{
		return 3;
	}

	@Range(min = -48, max = 48)
	@ConfigItem(
		keyName = "orbIconNudgeY",
		name = "Icon Y",
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

	@Range(min = -48, max = 48)
	@ConfigItem(
		keyName = "orbTextGap",
		name = "Icon to value gap",
		description = "Space between the icon and the number. Negative overlaps them.",
		section = ORB_LAYOUT,
		position = 7
	)
	default int orbTextGap()
	{
		return 3;
	}

	@Range(min = -48, max = 48)
	@ConfigItem(
		keyName = "orbTextNudgeX",
		name = "Value X",
		description = "Shift the number left or right from wherever the value position put it",
		section = ORB_LAYOUT,
		position = 8
	)
	default int orbTextNudgeX()
	{
		return 0;
	}

	// the four icons share a canvas size but not how much of it their art fills, so an
	// identical gap in pixels does not read as an identical gap. this rides on top of
	// Value X for the prayer orb alone.
	@Range(min = -48, max = 48)
	@ConfigItem(
		keyName = "orbPrayerValueX",
		name = "Prayer value X",
		description = "Extra sideways shift for the prayer number only, on top of Value X. Its icon's art sits nearer the edge of its canvas than the others, so the same gap looks tighter.",
		section = ORB_LAYOUT,
		position = 9
	)
	default int orbPrayerValueX()
	{
		return 1;
	}

	@Range(min = -48, max = 48)
	@ConfigItem(
		keyName = "orbTextNudgeY",
		name = "Value Y",
		description = "Shift the number up or down from vertical centre",
		section = ORB_LAYOUT,
		position = 10
	)
	default int orbTextNudgeY()
	{
		return 0;
	}

	@Range(min = 6, max = 40)
	@ConfigItem(
		keyName = "orbFontSize",
		name = "Value font size",
		description = "Size of the number. 16 is the font's native size and the crispest; other sizes are interpolated.",
		section = ORB_LAYOUT,
		position = 11
	)
	default int orbFontSize()
	{
		return 16;
	}

	@ConfigItem(
		keyName = "specArmedFill",
		name = "Spec armed fill",
		description = "How the special attack block changes while a special is queued. Brighter keeps the orb's own blue; Game colour is the pale grey-blue stock RuneLite uses.",
		section = ORB_LAYOUT,
		position = 12
	)
	default SpecArmedFill specArmedFill()
	{
		return SpecArmedFill.BRIGHTER;
	}

	@ConfigItem(
		keyName = "specArmedBorder",
		name = "Spec armed border",
		description = "Also light up the border of the special attack block while a special is queued. Combines with the fill setting.",
		section = ORB_LAYOUT,
		position = 13
	)
	default boolean specArmedBorder()
	{
		return false;
	}

	@ConfigItem(
		keyName = "orbDebug",
		name = "Debug readout",
		description = "Paint the real numbers the layout is working from over the minimap, for when a setting does not appear to do anything",
		section = ORB_LAYOUT,
		position = 14
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

	enum SpecArmedFill
	{
		NONE,
		BRIGHTER,
		GAME_COLOUR
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
