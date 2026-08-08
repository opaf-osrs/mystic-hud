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
}
