package com.pluginideahub.mystichud;

import java.awt.Rectangle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Covers the pure geometry the layout depends on. Anything touching Widget needs a
 * live client, so the value here is pinning the arithmetic that silently went wrong
 * during development: slot maths, the compass hit box, and the orb ordering.
 */
public class MysticHudGeometryTest
{
	@Test
	public void fourSlotsFitInsideTheMapWidth()
	{
		// the whole premise of the row: four blocks plus three gaps must not overflow
		for (int mapW = 120; mapW <= 320; mapW++)
		{
			int slot = (mapW - 3 * MysticHudPlugin.BLOCK_GAP) / 4;
			int used = slot * 4 + 3 * MysticHudPlugin.BLOCK_GAP;
			assertTrue("slots overflow at mapW=" + mapW, used <= mapW);
			assertTrue("slot too small at mapW=" + mapW, slot > 0);
		}
	}

	@Test
	public void mapStaysAtTheEnginesNativeSize()
	{
		// the engine's minimap draw is fixed at 152x152; the plugin must never ask for
		// anything else (see compact-orbs, which never resizes the draw widget at all)
		assertEquals(152, MysticHudPlugin.NATIVE_MAP);
	}

	@Test
	public void compassBoxSitsInsideTheMapsTopLeftCorner()
	{
		Rectangle map = new Rectangle(500, 100, 204, 132);
		Rectangle c = MysticHudPlugin.compassBounds(map);

		assertTrue("compass escapes the map", map.contains(c));
		assertEquals(map.x + MysticHudPlugin.COMPASS_INSET, c.x);
		assertEquals(map.y + MysticHudPlugin.COMPASS_INSET, c.y);
		assertEquals(MysticHudPlugin.COMPASS_SIZE, c.width);
	}

	@Test
	public void compassBoxTracksTheMapWhenItMoves()
	{
		Rectangle a = MysticHudPlugin.compassBounds(new Rectangle(0, 0, 204, 132));
		Rectangle b = MysticHudPlugin.compassBounds(new Rectangle(300, 40, 204, 132));

		assertEquals(300, b.x - a.x);
		assertEquals(40, b.y - a.y);
	}

	@Test
	public void everyOrbOrderContainsAllFourOrbsExactlyOnce()
	{
		for (MysticHudConfig.OrbOrder order : MysticHudConfig.OrbOrder.values())
		{
			int[] ids = orderToIds(order);
			assertEquals(4, ids.length);

			int mask = 0;
			for (int id : ids)
			{
				mask |= 1 << bit(id);
			}
			assertEquals("order " + order + " is missing or repeating an orb", 0b1111, mask);
		}
	}

	private static int bit(int orbChild)
	{
		switch (orbChild)
		{
			case MysticHudPlugin.HP: return 0;
			case MysticHudPlugin.PRAYER: return 1;
			case MysticHudPlugin.RUN: return 2;
			case MysticHudPlugin.SPEC: return 3;
			default: throw new AssertionError("unexpected orb child " + orbChild);
		}
	}

	/** Mirrors MysticHudPlugin#orbChildren without needing an injected config. */
	private static int[] orderToIds(MysticHudConfig.OrbOrder order)
	{
		switch (order)
		{
			case HP_PRAYER_RUN_SPEC:
				return new int[]{MysticHudPlugin.HP, MysticHudPlugin.PRAYER,
					MysticHudPlugin.RUN, MysticHudPlugin.SPEC};
			case HP_PRAYER_SPEC_RUN:
				return new int[]{MysticHudPlugin.HP, MysticHudPlugin.PRAYER,
					MysticHudPlugin.SPEC, MysticHudPlugin.RUN};
			case SPEC_PRAYER_RUN_HP:
			default:
				return new int[]{MysticHudPlugin.SPEC, MysticHudPlugin.PRAYER,
					MysticHudPlugin.RUN, MysticHudPlugin.HP};
		}
	}

	@Test
	public void placedOrbsAreNeverSweptAwayByTheHideAllPass()
	{
		// the sweep hides every root child not in PLACED; if an orb we position were
		// missing from that list it would be hidden and repositioned at the same time
		for (int id : new int[]{MysticHudPlugin.HP, MysticHudPlugin.PRAYER,
			MysticHudPlugin.RUN, MysticHudPlugin.SPEC,
			MysticHudPlugin.XP_ORB, MysticHudPlugin.WORLD_MAP_ORB})
		{
			boolean found = false;
			for (int p : MysticHudPlugin.PLACED)
			{
				found |= p == id;
			}
			assertTrue("placed orb " + id + " is not exempt from the sweep", found);
		}
	}

	@Test
	public void spheresListNeverHidesAnOrbContainerItself()
	{
		// hiding a container would take its icon and number with it
		int[] containers = {MysticHudPlugin.HP, MysticHudPlugin.PRAYER,
			MysticHudPlugin.RUN, MysticHudPlugin.SPEC};
		for (int sphere : MysticHudPlugin.SPHERES)
		{
			for (int c : containers)
			{
				assertTrue("sphere child " + sphere + " collides with container " + c,
					sphere != c);
			}
		}
	}

	/**
	 * The walk-click offset lived here and went unfound across three sessions. The map
	 * has THREE mask sprites: 1178 draws it, 2154 and 3513 are the click masks the layout
	 * script swaps in. Override only the draw mask and the map paints at your width while
	 * clicks keep resolving against the stock 152 shape, which reads on screen as the
	 * destination flag landing to one side of the cursor. Nothing about that is visible in
	 * the drawing, so it has to be pinned here.
	 */
	@Test
	public void everyMapMaskIsOverridden()
	{
		assertTrue("the draw mask alone is not enough, the click masks decide where a "
				+ "walk-click resolves", MysticHudPlugin.MAP_MASK_SPRITES.length >= 3);

		for (int required : new int[]{
			MysticHudPlugin.MASK_SPRITE,
			MysticHudPlugin.CLICK_MASK_SPRITE,
			MysticHudPlugin.BOND_CLICK_MASK_SPRITE})
		{
			boolean found = false;
			for (int s : MysticHudPlugin.MAP_MASK_SPRITES)
			{
				found |= s == required;
			}
			assertTrue("mask sprite " + required + " is not in MAP_MASK_SPRITES, so it "
				+ "keeps its stock shape and clicks fall out of step with the draw", found);
		}
	}

	/**
	 * The world map orb is PAINTED, not placed: group 160 has a fixed clip, so with the
	 * xp orb widening the block the stock widget was clipped or dragged into the middle
	 * of the map whatever its position was set to. Painting it means the rect depends on
	 * the map alone, so nothing about the block's width can move it.
	 */
	@Test
	public void worldMapOrbSitsInTheMapsBottomRightCorner()
	{
		Rectangle map = new Rectangle(560, 0, 204, 152);
		Rectangle orb = MysticHudPlugin.worldMapBounds(map);

		assertTrue("orb escapes the map", map.contains(orb));
		assertEquals(MysticHudPlugin.WORLD_ORB_SIZE, orb.width);
		assertEquals(MysticHudPlugin.WORLD_ORB_SIZE, orb.height);
		assertEquals("inset from the map's right edge",
			MysticHudPlugin.ORB_INSET_X, map.x + map.width - (orb.x + orb.width));
		assertEquals("inset from the map's bottom edge",
			MysticHudPlugin.ORB_INSET, map.y + map.height - (orb.y + orb.height));
	}

	@Test
	public void worldMapOrbFollowsOnlyTheMapRect()
	{
		// the same map rect must give the same orb rect however wide the block around it
		// is, which is the whole point of painting it rather than placing the widget
		Rectangle map = new Rectangle(561, 0, 204, 152);
		assertEquals(MysticHudPlugin.worldMapBounds(map),
			MysticHudPlugin.worldMapBounds(new Rectangle(561, 0, 204, 152)));

		// and it must track the map when the map itself moves
		Rectangle moved = MysticHudPlugin.worldMapBounds(new Rectangle(661, 0, 204, 152));
		assertEquals(MysticHudPlugin.worldMapBounds(map).x + 100, moved.x);
	}

	/**
	 * Only two children move between the resizable layouts. Everything inside the minimap
	 * block keeps its id, which is why classic gets the full feature set rather than a
	 * restyle. Values decoded from the gameval constants, not guessed: MAP_CONTAINER is
	 * 164:92 / 161:95 and SIDE_CONTAINER is 164:96 / 161:73.
	 */
	@Test
	public void theTwoLayoutsDifferByExactlyTwoChildIds()
	{
		int modern = MysticHudPlugin.TOPLEVEL_MODERN;
		int classic = MysticHudPlugin.TOPLEVEL_CLASSIC;

		assertEquals("resizable modern is the bottom-line toplevel", 164, modern);
		assertEquals("resizable classic is the old-school-box toplevel", 161, classic);

		assertEquals(92, MysticHudPlugin.minimapBlockChild(modern));
		assertEquals(95, MysticHudPlugin.minimapBlockChild(classic));
		assertEquals(96, MysticHudPlugin.invPanelChild(modern));
		assertEquals(73, MysticHudPlugin.invPanelChild(classic));
	}

	@Test
	public void bothResizableLayoutsAreAcceptedAndFixedIsNot()
	{
		assertTrue(MysticHudPlugin.isResizableToplevel(MysticHudPlugin.TOPLEVEL_MODERN));
		assertTrue(MysticHudPlugin.isResizableToplevel(MysticHudPlugin.TOPLEVEL_CLASSIC));
		// 548 is the fixed viewport: the plugin must stay inert there
		assertTrue("fixed layout must not be laid out in",
			!MysticHudPlugin.isResizableToplevel(548));
	}

	@Test
	public void maskSpritesAreDistinct()
	{
		// a duplicate would silently leave one of the three on its stock mask
		for (int i = 0; i < MysticHudPlugin.MAP_MASK_SPRITES.length; i++)
		{
			for (int j = i + 1; j < MysticHudPlugin.MAP_MASK_SPRITES.length; j++)
			{
				assertTrue("duplicate mask sprite " + MysticHudPlugin.MAP_MASK_SPRITES[i],
					MysticHudPlugin.MAP_MASK_SPRITES[i] != MysticHudPlugin.MAP_MASK_SPRITES[j]);
			}
		}
	}
}
