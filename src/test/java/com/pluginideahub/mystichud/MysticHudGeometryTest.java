package com.pluginideahub.mystichud;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.runelite.api.gameval.SpriteID;
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

	/**
	 * The round-minimap fix only works while the map draws through a sprite id the game never
	 * uses: the engine caches the shape it built per id, so a real sprite id could already hold
	 * a stale one. If a game update ever ships a sprite under this id, this fails instead of the
	 * circle quietly coming back.
	 */
	@Test
	public void privateDrawMaskIsNotAGameSprite() throws IllegalAccessException
	{
		int checked = 0;
		java.util.List<Class<?>> classes = new java.util.ArrayList<>();
		classes.add(SpriteID.class);
		classes.addAll(java.util.Arrays.asList(SpriteID.class.getDeclaredClasses()));
		for (Class<?> c : classes)
		{
			for (Field f : c.getDeclaredFields())
			{
				if (f.getType() == int.class && Modifier.isStatic(f.getModifiers()))
				{
					checked++;
					assertTrue(c.getSimpleName() + "." + f.getName() + " is a real sprite on the private "
						+ "draw mask id, pick another", f.getInt(null) != MysticHudPlugin.DRAW_MASK_SPRITE);
				}
			}
		}
		assertTrue("found no sprite ids to check against, so the test proved nothing", checked > 1000);
	}

	@Test
	public void privateDrawMaskGetsTheOverride()
	{
		boolean found = false;
		for (int s : MysticHudPlugin.MAP_MASK_SPRITES)
		{
			found |= s == MysticHudPlugin.DRAW_MASK_SPRITE;
		}
		assertTrue("the map draws through the private id, so it needs our mask too", found);
	}
}
