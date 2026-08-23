package com.pluginideahub.mystichud;

import com.google.inject.Provides;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Rebuilds the minimap/orb area as a rectangular block sitting directly above the
 * inventory in resizable modern mode.
 *
 * All widget ids below were read from the interface cache export:
 * group 164 is the resizable-modern toplevel, group 160 is the orbs interface.
 * The circular minimap shape is nothing but mask sprite 1178 sitting on the
 * contentType-1338 map widget, so clearing that sprite id yields a square map.
 */
@Slf4j
@PluginDescriptor(
	name = "Mystic HUD",
	description = "Minimap and orbs as a rectangular block above the inventory",
	tags = {"minimap", "orbs", "layout", "hud", "mystic"}
)
public class MysticHudPlugin extends Plugin
{
	// toplevel, resizable modern (group 164)
	static final int TOPLEVEL = 164;
	static final int MINIMAP_BLOCK = 92;   // 211x207 container holding minimap + orbs
	static final int MINIMAP_INNER = 22;   // inner container the map widgets live in
	static final int MINIMAP_MAP = 30;     // contentType 1338, the actual map draw target
	static final int MINIMAP_COMPASS = 29; // contentType 1339, round compass + its mask
	static final int MINIMAP_RING = 32;    // sprite 1177 circular surround
	static final int ORB_HOST = 33;        // 207x197 container group 160 loads into
	static final int INV_PANEL = 96;       // 204x275 inventory panel

	// orbs (group 160): container / capsule-backing child per orb
	static final int ORBS = 160;
	static final int HP = 7, HP_BACK = 8;
	static final int PRAYER = 18, PRAYER_BACK = 19;
	static final int RUN = 26, RUN_BACK = 27;
	static final int SPEC = 34, SPEC_BACK = 35;
	// 160:6 carries sprite 1196, the XP disc. 160:48 (sprites 4547 + 3018) is the
	// activity adviser and 160:43 (1609 + 1668) is the store orb; both fall to the sweep
	static final int XP_ORB = 6;
	static final int WORLD_MAP_ORB = 49;
	// the glossy sphere art inside each orb (fill sprite + status overlay containers);
	// 160:16 stays visible, the hp icon turned out to live under it
	static final int[] SPHERES = {11, 12, 14, 22, 23, 30, 31, 38, 39, 41};
	// group-160 root children we lay out ourselves; everything else at root level is
	// hidden outright, which catches stray orbs whatever their child id turns out to be
	static final int[] PLACED = {HP, PRAYER, RUN, SPEC, XP_ORB, WORLD_MAP_ORB};
	// the value text and icon children of each orb container
	static final int[] ORB_TEXT = {10, 21, 29, 37};
	static final int[] ORB_ICONS = {17, 25, 33, 42};

	// set when any widget value actually changed this frame, so the expensive subtree
	// revalidate only runs when something moved
	private boolean dirty;
	// frames still to force a revalidate for. the client rebuilds the orb children just
	// after login with our values already in place, so change detection sees "nothing
	// moved" and never revalidates, leaving icons and numbers unrendered until something
	// else nudges the layout
	private int settle;
	// current orb row height; compresses when the window is short
	private int rowH = ROW_H;
	// current map width; insets when engulfed by the inventory panel
	private int mapW = NATIVE_MAP;
	// last screen position actually applied to the map container, so a move can be told
	// apart from a steady-state frame. contentType 1338's own click-to-walk math reads
	// off a cache keyed by this widget, the same one overrideMask() resets for the DRAWN
	// circle; that reset only fired on a mask size change, never on a plain reposition
	// (attach toggling, drag, window resize, inventory open/close shifting the anchor),
	// so the walk destination kept mapping against wherever the map cached last, one full
	// generation of moves behind the visible block. this is the recurring "flag lands
	// off to the side" bug: fixed by resetting the same cache on every actual move, not
	// only when the mask itself changes.
	private int lastMapDx = Integer.MIN_VALUE;
	private int lastMapY = Integer.MIN_VALUE;

	int rowH()
	{
		return rowH;
	}

	// live orb states, read off VarbitChanged rather than polled in the overlay's render.
	// written on the client thread, read on the draw thread, hence volatile.
	private volatile boolean specArmed;
	private volatile boolean running;
	private volatile boolean staminaActive;
	private volatile boolean quickPrayersOn;
	private volatile HealthState healthState = HealthState.NORMAL;

	enum HealthState
	{
		NORMAL,
		POISONED,
		VENOMED,
		DISEASED
	}

	boolean specArmed()
	{
		return specArmed;
	}

	boolean running()
	{
		return running;
	}

	boolean staminaActive()
	{
		return staminaActive;
	}

	boolean quickPrayersOn()
	{
		return quickPrayersOn;
	}

	HealthState healthState()
	{
		return healthState;
	}

	/**
	 * Re-reads every orb state. Cheap enough to do wholesale on any varbit or varp
	 * change rather than working out which one moved, and that way a state can never be
	 * missed because its var was not in a list.
	 */
	private void readOrbStates()
	{
		specArmed = client.getVarpValue(VarPlayerID.SA_ATTACK) != 0;
		running = client.getVarpValue(VarPlayerID.OPTION_RUN) != 0;
		staminaActive = client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0;
		quickPrayersOn = client.getVarbitValue(VarbitID.QUICKPRAYER_ACTIVE) != 0;

		// venom lives inside the poison varp as a magnitude, it has no var of its own.
		// order and thresholds taken from RuneLite's own StatusBarsOverlay.
		int poison = client.getVarpValue(VarPlayerID.POISON);
		if (poison >= VENOM_THRESHOLD)
		{
			healthState = HealthState.VENOMED;
		}
		else if (poison > 0)
		{
			healthState = HealthState.POISONED;
		}
		else if (client.getVarpValue(VarPlayerID.DISEASE) > 0)
		{
			healthState = HealthState.DISEASED;
		}
		else
		{
			healthState = HealthState.NORMAL;
		}
	}

	// poison varp counts up; at or past this it is venom instead
	private static final int VENOM_THRESHOLD = 1000000;

	// geometry, all relative to the 211-wide minimap block
	// extra block width so the xp orb beside the map is not clipped off the edge
	static final int SIDE_PAD = 72;
	static final int NATIVE_MAP = 152; // the engine's fixed minimap draw HEIGHT
	static final int INV_W = 204;      // inventory panel width; the map matches it
	// the map widget sits at (0,0) in its container: the ENGINE anchors the minimap
	// draw at the container origin (proven by the map bleeding exactly 7px left and 8px
	// up of the frame with the stock offsets), so widget offsets must be zero for the
	// draw, the widget and the frame to coincide
	static final int MAP_TOP = 0;
	static final int ROW_GAP = 6; // a bar of frame art sits between map and orb row
	static final int ROW_H = 40;
	static final int BLOCK_GAP = 6; // wide enough for a vertical bar between blocks
	static final int ORB_W = 57;           // stock orb container size
	static final int ORB_H = 34;
	// painted compass, offset from the map's top-left corner. inset 8 so its outer ring
	// (3px beyond the disc) clears the 4px frame band with a 1px breath instead of
	// sitting on it
	static final int COMPASS_INSET = 11; // ring starts at 8: exactly clear of the 8px silver corner
	static final int COMPASS_SIZE = 26;
	// world map orb inset, chosen by rendering the variants and looking: 5 puts the
	// orb's outer edge 5px off its corner, the same as the compass ring on the opposite
	// corner, clear of the 4px frame band
	// vertical inset to the bottom divider; the horizontal inset adds the 6px side bar
	// so the visible gap reads the same on both axes
	static final int ORB_INSET = 6;  // lower, snug to the bottom divider
	static final int ORB_INSET_X = 12;
	// height of the whole side stack in resizable modern: 204x275 panel plus two 36px
	// tab rows. used as a STABLE anchor so the block does not jump when a tab opens
	static final int INV_STACK_H = 275 + 2 * 36;
	// bump when the meaning of the saved drag offsets changes
	static final int LAYOUT_VERSION = 3;
	// bumped EVERY build; painted on screen so a stale client is instantly obvious
	static final String BUILD_TAG = "b51";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MysticHudConfig config;

	@Inject
	private MysticHudFrameOverlay frameOverlay;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private ConfigManager configManager;

	// live drag state; offsets persist through the hidden posX/posY config keys.
	// written on the AWT thread, read on the client thread
	private volatile Rectangle mapBounds;
	private volatile int offX;
	private volatile int offY;
	private boolean dragging;
	private int lastLoggedOffY = Integer.MIN_VALUE;
	private int grabX, grabY, dragBaseX, dragBaseY;

	private final MouseAdapter mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			Rectangle b = mapBounds;
			// the painted compass has no widget, so its click is handled here: face
			// north and consume so the map underneath cannot turn it into a walk
			if (b != null && config.showCompass()
				&& e.getButton() == MouseEvent.BUTTON1 && !e.isShiftDown())
			{
				Rectangle cb = compassBounds(b);
				cb.grow(3, 3); // forgiving edge
				boolean hit = hits(cb, e);
				log.debug("MHUD compass click win=({},{}) cb={} hit={}",
					e.getX(), e.getY(), cb, hit);
				if (hit)
				{
					clientThread.invokeLater(() -> client.setCameraYawTarget(0));
					e.consume();
					return e;
				}
			}
			// shift, not alt: Windows and several window managers swallow alt-drag.
			// free mode only: attached is glued and ignores position entirely
			Rectangle zone = blockZone();
			if (e.isShiftDown() && !config.attachInventory() && zone != null && hits(zone, e))
			{
				dragging = true;
				grabX = e.getX();
				grabY = e.getY();
				dragBaseX = offX;
				dragBaseY = offY;
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mouseDragged(MouseEvent e)
		{
			if (dragging)
			{
				offX = dragBaseX + e.getX() - grabX;
				offY = dragBaseY + e.getY() - grabY;
				log.debug("MHUD drag off=({},{})", offX, offY);
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mouseReleased(MouseEvent e)
		{
			if (dragging)
			{
				dragging = false;
				configManager.setConfiguration(MysticHudConfig.GROUP, "posX", offX);
				configManager.setConfiguration(MysticHudConfig.GROUP, "posY", offY);
				e.consume();
			}
			return e;
		}
	};

	/**
	 * Scrolling over the map zooms it, the way the vanilla minimap does. The engine's own
	 * wheel handling is bound to the stock minimap position, so once the block moves it
	 * no longer lines up; this drives our own zoom instead and consumes the event so the
	 * scene behind cannot zoom the camera at the same time.
	 */
	/**
	 * Whether mouse coordinates are canvas-space or stretched-display-space depends on
	 * the user's stretched mode, which can change between sessions (both cases are in
	 * the logs). So a hit accepts EITHER interpretation of the point.
	 */
	private boolean hits(Rectangle r, java.awt.event.MouseEvent e)
	{
		if (r.contains(e.getPoint()))
		{
			return true;
		}
		if (client.isStretchedEnabled())
		{
			java.awt.Dimension real = client.getRealDimensions();
			java.awt.Dimension stretched = client.getStretchedDimensions();
			if (stretched.width > 0 && stretched.height > 0)
			{
				return r.contains(
					e.getX() * real.width / stretched.width,
					e.getY() * real.height / stretched.height);
			}
		}
		return false;
	}

	/** The scroll/drag zone: the whole block plus a forgiving margin. */
	private Rectangle blockZone()
	{
		Rectangle b = mapBounds;
		if (b == null)
		{
			return null;
		}
		return new Rectangle(b.x - 8, b.y - 8,
			b.width + 16, b.height + ROW_GAP + rowH + 24);
	}

	// no wheel listener: zoom belongs entirely to the client's native minimap zoom,
	// which follows the real minimap widget and keeps clicks and drawing in agreement

	// widget -> original geometry so shutDown puts everything back
	private final Map<Widget, int[]> saved = new HashMap<>();
	private final Map<Widget, Boolean> savedHidden = new HashMap<>();

	// the game's own minimap zoom, captured so shutDown can hand it back

	// the transparent minimap mask override; its hole IS the whole rectangle
	// sprite 1178, RESIZE_MAP_MASK: the engine reads this to decide how much minimap to draw
	static final int MASK_SPRITE = 1178;
	private net.runelite.api.SpritePixels mask;
	private net.runelite.api.SpritePixels previousMask;
	private int maskW, maskH, maskVisH;
	private boolean maskSaved;

	@Provides
	MysticHudConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MysticHudConfig.class);
	}

	@Override
	protected void startUp()
	{
		// the free-mode anchor changed meaning, so any position saved against the old one
		// is nonsense. drop it once rather than leave the block parked somewhere absurd.
		if (config.layoutVersion() < LAYOUT_VERSION)
		{
			configManager.setConfiguration(MysticHudConfig.GROUP, "posX", 0);
			configManager.setConfiguration(MysticHudConfig.GROUP, "posY", 0);
			configManager.setConfiguration(MysticHudConfig.GROUP, "layoutVersion", LAYOUT_VERSION);
		}
		offX = config.posX();
		offY = config.posY();
		// high priority: at the default the client's own handlers see the event first and
		// our scroll zoom and drag never fire
		mouseManager.registerMouseListener(1, mouse);
		overlayManager.add(frameOverlay);
		settle = 60;
		clientThread.invoke(this::apply);
	}

	@Override
	protected void shutDown()
	{
		mouseManager.unregisterMouseListener(mouse);
		overlayManager.remove(frameOverlay);
		mapBounds = null;
		clientThread.invoke(this::restoreAll);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		// widgets SURVIVE region loads (LOADING), so the saved baselines must too;
		// clearing there once orphaned our edits and let modified values compound.
		// only a login-screen rebuild actually discards the widgets.
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			saved.clear();
			savedHidden.clear();
			mapBounds = null;
			maskSaved = false;
			mask = null;
			lastMapDx = Integer.MIN_VALUE;
			lastMapY = Integer.MIN_VALUE;
		}
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			settle = 60; // ~a second of forced revalidates while the interfaces build
			// VarbitChanged only tells us about CHANGES, so without this the orbs read
			// as walking, unarmed and healthy until the player happens to toggle
			// something. the vars are already set by the time we are logged in.
			readOrbStates();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		// fires for varps as well as varbits, so this one subscription covers all of it
		readOrbStates();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		// the drag itself writes posX/posY; rebuilding on those would reset the widget
		// sprite cache on every mouse release and flicker the map
		if (MysticHudConfig.GROUP.equals(e.getGroup())
			&& !"posX".equals(e.getKey()) && !"posY".equals(e.getKey()))
		{
			clientThread.invoke(() ->
			{
				restoreAll();
				settle = 10;
				apply();
			});
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender e)
	{
		// layout clientscripts rerun on resize and tab switches and stomp our values.
		// BeforeRender fires after scripts and before draw, so setting here always wins
		// and never visibly fights them.
		apply();
	}

	private Widget top(int child)
	{
		return client.getWidget(TOPLEVEL << 16 | child);
	}

	private Widget orb(int child)
	{
		return client.getWidget(ORBS << 16 | child);
	}

	boolean active()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !client.isResized())
		{
			return false;
		}
		Widget block = top(MINIMAP_BLOCK);
		return block != null && !block.isHidden();
	}

	private int cfgMapW()
	{
		return INV_W; // always inventory width
	}

	private int cfgMapH()
	{
		// the engine ALWAYS draws 152 tall (native), anchored to the container bottom, so
		// below 152 the difference spills out as live map above the frame rather than
		// cropping. exposed anyway: the spill is a look to judge, not a crash, and 152
		// is still the only height where draw, container and frame coincide exactly.
		return config.mapHeight();
	}

	int[] orbChildren()
	{
		switch (config.orbOrder())
		{
			case HP_PRAYER_RUN_SPEC:
				return new int[]{HP, PRAYER, RUN, SPEC};
			case HP_PRAYER_SPEC_RUN:
				return new int[]{HP, PRAYER, SPEC, RUN};
			case SPEC_PRAYER_RUN_HP:
			default:
				return new int[]{SPEC, PRAYER, RUN, HP};
		}
	}

	/** Block width shared by the four orb slots, inside the 6px steel border. */
	int slotWidth()
	{
		return (mapW - 12 - 3 * BLOCK_GAP) / 4;
	}

	/** Left inset of the block strip, centring the leftover so both margins match. */
	int rowInsetX()
	{
		return (mapW - (4 * slotWidth() + 3 * BLOCK_GAP)) / 2;
	}

	/** Where the map actually is on canvas, or null while the layout is not applied. */
	Rectangle layoutBounds()
	{
		return mapBounds;
	}

	/** Screen rect of the painted compass, given the map's bounds. */
	static Rectangle compassBounds(Rectangle map)
	{
		return new Rectangle(map.x + COMPASS_INSET, map.y + COMPASS_INSET,
			COMPASS_SIZE, COMPASS_SIZE);
	}

	private void apply()
	{
		if (!active())
		{
			// stop the overlays painting and stop the compass/drag hit tests firing
			// against a stale rect while we are in fixed or classic layout
			mapBounds = null;
			return;
		}

		Widget block = top(MINIMAP_BLOCK);
		Widget parent = block.getParent();
		if (parent == null)
		{
			return;
		}

		dirty = settle > 0;
		if (settle > 0)
		{
			settle--;
		}

		// READ THE SLIDERS EVERY FRAME. these were only loaded once in startUp, so moving
		// Position X/Y changed the config and nothing else, which is why the block would
		// not move in any mode. while dragging, the live drag value wins.
		if (!dragging)
		{
			offX = config.posX();
			offY = config.posY();
		}

		// ZOOM IS NEVER TOUCHED, not even the enable flag: enabling it is the only
		// click-related change in the build where the walk offset returned

		Widget inv = top(INV_PANEL);
		boolean invOpen = inv != null && !inv.isHidden() && inv.getHeight() > 0;
		Widget inner = top(MINIMAP_INNER);
		Widget host = top(ORB_HOST);

		if (config.hideWhenClosed())
		{
			hide(inner, !invOpen);
			hide(host, !invOpen);
			if (!invOpen)
			{
				mapBounds = null;
				return;
			}
		}
		else
		{
			hide(inner, false);
			hide(host, false);
		}

		// THE MAP IS ITS NATIVE SIZE, ALWAYS. compact-orbs, the shipping plugin that does
		// this exact job, never resizes the minimap draw widget, only its containers.
		// every draw glitch we had came from fighting that.
		rowH = config.orbRowHeight();
		int blockH = MAP_TOP + cfgMapH() + ROW_GAP + rowH;
		int avail = parent.getHeight();
		if (blockH > avail)
		{
			rowH = Math.max(24, rowH - (blockH - avail));
			blockH = MAP_TOP + cfgMapH() + ROW_GAP + rowH;
		}
		if (blockH > avail)
		{
			mapBounds = null; // overlay must not paint while the layout is not applied
			return; // window too small, leave stock layout alone
		}

		// anchor: attached glues onto the inventory panel; free mode anchors to where the
		// panel ALWAYS sits (not whether it is open, which jumps ~275px). Position X/Y
		// shift from there in every mode.
		boolean attached = config.attachInventory();
		int anchorTop = invOpen ? inv.getRelativeY() : parent.getHeight() - 2 * 36;
		int bottom;
		if (attached)
		{
			// glued: position sliders and drag do NOT apply in attach mode
			bottom = invOpen ? anchorTop + 2 : blockH;
		}
		else
		{
			// free mode: default parks the block at the TOP; drag/sliders move it down
			bottom = blockH + offY;
		}
		// attach ignores horizontal offset too, or a leftover free-mode drag shifts the
		// glued block off the inventory's edges
		int dx = attached ? 0 : Math.max(-(parent.getWidth() - 211), Math.min(200, offX));
		int targetY = Math.max(0, Math.min(avail - blockH, bottom - blockH));
		if (offY != lastLoggedOffY)
		{
			log.debug("MHUD move offY={} bottom={} blockH={} targetY={} attached={}",
				offY, bottom, blockH, targetY, attached);
			lastLoggedOffY = offY;
		}
		layout(targetY, dx, blockH);
	}

	private void layout(int targetY, int dx, int blockH)
	{
		// the block is EXACTLY the map width: the engine sizes the minimap draw from
		// this container (right-anchored), proven by the bleed being precisely
		// blockW - mapW on the left with right/bottom flush. any extra width becomes
		// visible map outside the frame.
		int blockW = INV_W + (config.showExtraOrbs() ? SIDE_PAD : 0);
		Widget block = top(MINIMAP_BLOCK);
		Widget parent = block == null ? null : block.getParent();
		if (block == null || parent == null)
		{
			mapBounds = null;
			return;
		}
		dirty |= set(block, null, null, blockW, parent.getHeight());

		// THE CONTAINER IS THE MAP. the engine draws into MAP_MINIMAP_GRAPHIC9, a child
		// that sizes itself to this container, NOT into the mask widget we position. so
		// the container must be EXACTLY the map rect: absolute width (its stock mode
		// tracks the block, which is wider), height exactly the map height.
		Widget inner = top(MINIMAP_INNER);
		if (inner != null)
		{
			inner.setWidthMode(net.runelite.api.widgets.WidgetSizeMode.ABSOLUTE);
			dirty |= set(inner, dx + (blockW - INV_W), targetY, INV_W, cfgMapH());
			// the stock UI guards the minimap with invisible MAP_NOCLICK shims so clicks
			// cannot fall through to the full-bleed 3D scene; our relocated block has no
			// shim under it, so the containers themselves must stop the fall-through
			inner.setNoClickThrough(true);
		}

		// the map moved: drop the widget sprite cache so contentType 1338 recomputes its
		// draw AND its click-to-world mapping against where the widget actually is now,
		// instead of leaving walk-clicks resolving against the last cached position
		if (dx != lastMapDx || targetY != lastMapY)
		{
			lastMapDx = dx;
			lastMapY = targetY;
			client.getWidgetSpriteCache().reset();
		}
		Widget host = top(ORB_HOST);
		if (host != null)
		{
			dirty |= set(host, dx, targetY, blockW, blockH + 4);
			host.setNoClickThrough(true);
		}

		Widget map = top(MINIMAP_MAP);
		if (map == null)
		{
			mapBounds = null;
			return;
		}
		// the mask widget just needs to cover the container so the transparent mask
		// override kills the circle everywhere
		dirty |= set(map, 0, 0, cfgMapW(), cfgMapH());
		if (config.squareMinimap())
		{
			overrideMask(cfgMapW(), cfgMapH());
		}
		else
		{
			clearMaskOverride();
		}

		dirty |= hide(top(MINIMAP_RING), true); // no circular surround around a rectangle

		// the real compass widget draws BEFORE the map and cannot sit on it; the frame
		// overlay paints a small rotating compass instead
		dirty |= hide(top(MINIMAP_COMPASS), true);

		// flat blocks: ALL orb art goes, including icons and numbers; the overlay paints
		// its own on top of the blocks. the widgets stay for clicks and menus only.
		boolean flat = config.drawBlocks();
		dirty |= hide(orb(HP_BACK), flat);
		dirty |= hide(orb(PRAYER_BACK), flat);
		dirty |= hide(orb(RUN_BACK), flat);
		dirty |= hide(orb(SPEC_BACK), flat);
		for (int c : SPHERES)
		{
			dirty |= hide(orb(c), flat);
		}
		for (int c : ORB_ICONS)
		{
			dirty |= hide(orb(c), flat);
		}
		for (int t : ORB_TEXT)
		{
			dirty |= hide(orb(t), flat);
		}
		// sweep every root-level child of the orbs group: anything we do not place
		// ourselves gets hidden, which catches the stray orbs whatever their id.
		// the four orb CONTAINERS are exempt, and so is anything parented to them, or the
		// sweep takes the value text with it whenever the client rebuilds those children
		Widget orbRoot = orb(0);
		Widget[] rootKids = orbRoot != null ? orbRoot.getStaticChildren() : null;
		if (rootKids != null)
		{
			for (Widget k : rootKids)
			{
				int child = k.getId() & 0xffff;
				boolean ours = false;
				for (int p : PLACED)
				{
					ours |= child == p;
				}
				for (int t : ORB_TEXT)
				{
					ours |= child == t;
				}
				if (!ours)
				{
					dirty |= hide(k, true);
				}
			}
		}
		boolean extras = config.showExtraOrbs();
		dirty |= hide(orb(XP_ORB), !extras);
		dirty |= hide(orb(WORLD_MAP_ORB), !config.showWorldMap());

		// settle the containers so canvas positions below are current. only when
		// something actually moved, otherwise this walks the subtree every frame
		if (dirty)
		{
			revalidateDeep(block);
		}

		// ---- canvas-space placement ----
		// the containers do not always land exactly where the originals say (scripts
		// force some of them), so everything below is positioned against where the map
		// ACTUALLY is on screen and self-corrects each frame
		if (host == null)
		{
			mapBounds = null;
			return;
		}
		// everything anchors to the CONTAINER's real on-screen rectangle: that is where
		// the engine actually draws the map. published as mapBounds so the overlay,
		// compass and scroll zone all agree.
		Widget innerW = top(MINIMAP_INNER);
		net.runelite.api.Point innerLoc = innerW == null ? null : innerW.getCanvasLocation();
		net.runelite.api.Point hostLoc = host.getCanvasLocation();
		if (innerLoc == null || hostLoc == null)
		{
			mapBounds = null;
			return;
		}
		int hx = hostLoc.getX();
		int hy = hostLoc.getY();

		// nudge: aligns all of OUR chrome onto wherever the engine actually paints the
		// map, tuned live by eye; the container itself stays put
		int visX = innerLoc.getX() + config.frameNudgeX();
		int visY = innerLoc.getY() + config.frameNudgeY();
		mapW = innerW.getWidth();
		int mapH = innerW.getHeight();
		int rowCanvasY = visY + mapH + ROW_GAP;
		int slot = slotWidth();
		int[] order = orbChildren();
		for (int i = 0; i < order.length; i++)
		{
			Widget w = orb(order[i]);
			if (w != null)
			{
				// centre the orb art inside its slot; the container is 57 wide but the
				// clickable circle occupies x 27..53, so shift left to balance it
				// never centre upwards into the map when the row is shorter than the orb
				int cx = visX + rowInsetX() + i * (slot + BLOCK_GAP) + (slot - ORB_W) / 2;
				int cy = rowCanvasY + Math.max(0, (rowH - ORB_H) / 2);
				if (set(w, cx - hx, cy - hy, null, null))
				{
					w.revalidate();
				}
			}
		}

		// xp orb beside the map's left edge
		if (extras)
		{
			Widget xp = orb(XP_ORB);
			if (xp != null)
			{
				// below the compass slot
				if (set(xp, visX - 38 - hx, visY + 42 - hy, null, null))
				{
					xp.revalidate();
				}
			}
		}
		// world map orb inside the map's bottom right corner
		if (config.showWorldMap())
		{
			Widget world = orb(WORLD_MAP_ORB);
			if (world != null)
			{
				// right-anchored: originalX is measured from the host's right edge.
				// mirrors the compass across the map's diagonal
				int desiredX = visX + mapW - 30 - ORB_INSET_X;
				if (set(world, hx + host.getWidth() - 30 - desiredX,
					visY + mapH - 30 - ORB_INSET - hy, null, null))
				{
					world.revalidate();
				}
			}
		}


		mapBounds = new Rectangle(visX, visY, mapW, mapH);
	}

	/**
	 * The engine sizes the minimap draw from mask sprite 1178, NOT from the widget, so
	 * this is what actually controls how much map is rendered. It must go through the
	 * GLOBAL sprite override keyed by sprite id: the widget-keyed table is not consulted
	 * on this path, and routing it there left the stock 152x152 mask in force, which is
	 * why the map ignored the height slider and spilled past its frame.
	 */
	private void overrideMask(int w, int h)
	{
		Map<Integer, net.runelite.api.SpritePixels> overrides = client.getSpriteOverrides();
		if (overrides == null)
		{
			return;
		}
		if (mask == null || maskW != w || maskH != h)
		{
			// fully transparent, so nothing of the stock circle survives
			mask = client.createSpritePixels(new int[w * h], w, h);
			maskW = w;
			maskH = h;
		}
		if (overrides.get(MASK_SPRITE) != mask)
		{
			if (!maskSaved)
			{
				previousMask = overrides.get(MASK_SPRITE);
				maskSaved = true;
			}
			overrides.put(MASK_SPRITE, mask);
			// the client caches widget sprites after first draw; without this reset a
			// relog or resource-pack refresh re-caches the circle and the square is lost
			client.getWidgetSpriteCache().reset();
		}
	}

	private void clearMaskOverride()
	{
		if (!maskSaved)
		{
			return;
		}
		Map<Integer, net.runelite.api.SpritePixels> overrides = client.getSpriteOverrides();
		if (overrides != null)
		{
			if (previousMask != null)
			{
				overrides.put(MASK_SPRITE, previousMask);
			}
			else
			{
				overrides.remove(MASK_SPRITE);
			}
			client.getWidgetSpriteCache().reset();
		}
		maskSaved = false;
		previousMask = null;
		mask = null;
	}

	// ---- save/restore plumbing ----

	private void remember(Widget w)
	{
		saved.computeIfAbsent(w, k -> new int[]{
			k.getOriginalX(), k.getOriginalY(), k.getOriginalWidth(), k.getOriginalHeight()
		});
	}

	/** Returns true when a value actually changed, so callers can skip revalidating. */
	private boolean set(Widget w, Integer x, Integer y, Integer width, Integer height)
	{
		remember(w);
		boolean changed = false;
		if (x != null && w.getOriginalX() != x)
		{
			w.setOriginalX(x);
			changed = true;
		}
		if (y != null && w.getOriginalY() != y)
		{
			w.setOriginalY(y);
			changed = true;
		}
		if (width != null && w.getOriginalWidth() != width)
		{
			w.setOriginalWidth(width);
			changed = true;
		}
		if (height != null && w.getOriginalHeight() != height)
		{
			w.setOriginalHeight(height);
			changed = true;
		}
		return changed;
	}

	private boolean hide(Widget w, boolean hidden)
	{
		if (w == null)
		{
			return false;
		}
		savedHidden.computeIfAbsent(w, Widget::isSelfHidden);
		if (w.isSelfHidden() == hidden)
		{
			return false;
		}
		w.setHidden(hidden);
		return true;
	}

	private void restoreAll()
	{
		for (Map.Entry<Widget, int[]> e : saved.entrySet())
		{
			int[] v = e.getValue();
			Widget w = e.getKey();
			w.setOriginalX(v[0]);
			w.setOriginalY(v[1]);
			w.setOriginalWidth(v[2]);
			w.setOriginalHeight(v[3]);
		}
		for (Map.Entry<Widget, Boolean> e : savedHidden.entrySet())
		{
			e.getKey().setHidden(e.getValue());
		}
		clearMaskOverride();
		Widget innerW = top(MINIMAP_INNER);
		if (innerW != null)
		{
			innerW.setNoClickThrough(false);
			// stock width mode tracks the parent block
			innerW.setWidthMode(net.runelite.api.widgets.WidgetSizeMode.MINUS);
		}
		Widget hostW = top(ORB_HOST);
		if (hostW != null)
		{
			hostW.setNoClickThrough(false);
		}

		List<Widget> roots = new ArrayList<>(saved.keySet());
		saved.clear();
		savedHidden.clear();
		mapBounds = null;
		for (Widget w : roots)
		{
			w.revalidate();
		}
		Widget block = top(MINIMAP_BLOCK);
		if (block != null)
		{
			revalidateDeep(block);
		}
	}

	private void revalidateDeep(Widget w)
	{
		w.revalidate();
		Widget[] kids = w.getStaticChildren();
		if (kids != null)
		{
			for (Widget k : kids)
			{
				revalidateDeep(k);
			}
		}
	}
}
