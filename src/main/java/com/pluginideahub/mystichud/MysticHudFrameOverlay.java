package com.pluginideahub.mystichud;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Paints the whole block chrome ABOVE the widgets: row band, orb blocks with live
 * fills, icons and numbers, the unified frame and the compass. Painting on top is
 * deliberate: the engine's minimap draw does not reliably respect the widget height,
 * and whatever it spills below the map is simply covered by the row band. The real orb
 * widgets still sit underneath for clicks and menus; their own art is hidden.
 */
public class MysticHudFrameOverlay extends Overlay
{
	private static final Color EDGE = new Color(0x141414);
	private static final Color HIGHLIGHT = new Color(0x383838);
	private static final Color FILL = new Color(0x232323);
	private static final Color PANEL = new Color(0x1D1D1D);
	private static final Color TEXT = new Color(0xFFFF00);

	private static final Color HP_C = new Color(0xA8, 0x32, 0x28);
	private static final Color PRAYER_C = new Color(0x4F, 0x43, 0x82);
	private static final Color RUN_C = new Color(0xC0, 0xA5, 0x38);
	private static final Color SPEC_C = new Color(0x3A, 0x9A, 0xB5);

	private static final int COMPASS_SIZE = MysticHudPlugin.COMPASS_SIZE;
	private static final int COMPASS_SPRITE = 169; // 51x51 compass rose, verified in cache

	// the inventory's OWN border art, bundled from the Mystic pack, so the block's frame
	// is pixel-identical to the panel's: 8x8 silver-bevel corners, 4px edge tiles
	private static final BufferedImage C_TL = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_top_left.png");
	private static final BufferedImage C_TR = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_top_right.png");
	private static final BufferedImage C_BL = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_bottom_left.png");
	private static final BufferedImage C_BR = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_bottom_right.png");
	private static final BufferedImage E_TOP = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_top.png");
	private static final BufferedImage E_BOTTOM = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_bottom.png");
	private static final BufferedImage E_LEFT = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_left.png");
	private static final BufferedImage E_RIGHT = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_right.png");
	private static final BufferedImage E_MID = net.runelite.client.util.ImageUtil
		.loadImageResource(MysticHudFrameOverlay.class, "side_border_middle.png");

	private final Client client;
	private final SpriteManager spriteManager;
	private final MysticHudPlugin plugin;
	private final MysticHudConfig config;
	private final Map<Integer, BufferedImage> icons = new HashMap<>();
	private double maxYaw;

	@Inject
	MysticHudFrameOverlay(Client client, SpriteManager spriteManager,
		MysticHudPlugin plugin, MysticHudConfig config)
	{
		this.client = client;
		this.spriteManager = spriteManager;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Rectangle mb = plugin.layoutBounds();
		if (!plugin.active() || mb == null)
		{
			return null;
		}

		int rowY = mb.y + mb.height + MysticHudPlugin.ROW_GAP;
		int rowH = plugin.rowH();

		Widget inv = client.getWidget(MysticHudPlugin.TOPLEVEL << 16 | MysticHudPlugin.INV_PANEL);
		boolean bridged = config.attachInventory()
			&& inv != null && !inv.isHidden() && inv.getHeight() > 0;
		BufferedImage bar = trimmed(sprite(STEEL_H));

		// free mode closes the frame on the row's bottom edge, and the band is drawn
		// INSIDE the rect, so it covers the last few pixels of every block. attached mode
		// carries that band on down to the inventory instead and covers nothing. the
		// blocks are the same height in both, but not the same VISIBLE height, so the
		// contents centre on what is actually on show. without this the same icon and
		// value nudges do not hold across the two modes. taken from the art rather than
		// hardcoded, because the pack's bar trims to a different thickness than stock.
		int rowCover = !bridged && config.mapOutline() && bar != null ? bar.getHeight() : 0;

		if (config.drawBlocks())
		{
			g.setColor(EDGE);
			g.fillRect(mb.x + 6, rowY, mb.width - 12, rowH);

			// bar under the minimap, and vertical bars between the blocks: the same
			// frame art dividing every section
			if (bar != null)
			{
				tileH(g, bar, mb.x + 6, mb.x + mb.width - 6, mb.y + mb.height);
			}

			int slot = plugin.slotWidth();
			int inset = plugin.rowInsetX();
			int[] order = plugin.orbChildren();
			for (int i = 0; i < 4; i++)
			{
				int bx = mb.x + inset + i * (slot + MysticHudPlugin.BLOCK_GAP);
				block(g, bx, rowY, slot, rowH, rowCover, order[i]);
				if (bar != null && i < 3)
				{
					tileV(g, rotated(bar), bx + slot, rowY, rowY + rowH);
				}
			}
		}

		if (bridged)
		{
			// ONE panel: the seam band (row bottom to just past the inventory's top
			// corners) is filled over, so the inventory's own top border disappears, and
			// our side edges run down to meet the inventory's identical side edges
			Rectangle ib = inv.getBounds();
			int rowBottom = rowY + rowH;
			// steel bar right under the blocks, then straight into the inventory: any
			// remaining sliver down to the inventory's interior is filled panel-dark
			g.setColor(PANEL);
			g.fillRect(mb.x + 6, rowBottom, mb.width - 12, Math.max(0, ib.y + 8 - rowBottom));
			if (bar != null)
			{
				tileH(g, bar, mb.x + 6, mb.x + mb.width - 6, rowBottom);
			}
			borderFrame(g, mb.x, mb.y, mb.width, ib.y + 8 - mb.y, true);
		}
		else if (config.mapOutline())
		{
			// blocks sit flush against the closing bottom edge
			borderFrame(g, mb.x, mb.y, mb.width, mb.height + rowH + 6, false);
		}

		if (config.showCompass())
		{
			Rectangle cb = MysticHudPlugin.compassBounds(mb);
			compass(g, cb.x, cb.y);
		}

		// build marker: tiny tag at the map's bottom-left. if the tag on screen does not
		// match the latest build number, the client is running stale code and NOTHING
		// else is worth debugging until it is relaunched
		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(Color.BLACK);
		g.drawString(MysticHudPlugin.BUILD_TAG, mb.x + 9, mb.y + mb.height - 4);
		g.setColor(Color.WHITE);
		g.drawString(MysticHudPlugin.BUILD_TAG, mb.x + 8, mb.y + mb.height - 5);

		if (config.orbDebug())
		{
			// the layout's own numbers, so a slider that looks stuck can be read rather
			// than guessed at. align first: it is the setting that caps the icon.
			String[] lines = {
				config.orbTextAlign().name(),
				debugGeom,
				debugIcon,
			};
			int ly = mb.y + 14;
			for (String line : lines)
			{
				g.setColor(Color.BLACK);
				g.drawString(line, mb.x + 41, ly + 1);
				g.setColor(Color.WHITE);
				g.drawString(line, mb.x + 40, ly);
				ly += 10;
			}
		}
		return null;
	}

	private static final int TEXT_EDGE_PAD = 3; // right inset when the value is right-aligned
	// the prayer star, biggest of the four stock icons. above this an icon size can only
	// have been asked for to make things bigger
	private static final int MAX_NATIVE_ICON = 20;

	// deriving the font allocates, so it is cached rather than rebuilt four times a frame
	private Font valueFont;
	private int valueFontSize;

	// what the last block actually laid out, for the debug readout. guessing at why a
	// slider looks stuck is slower than reading the numbers off the screen.
	private String debugGeom = "";
	private String debugIcon = "";

	private Font valueFont(int size)
	{
		if (valueFont == null || valueFontSize != size)
		{
			valueFont = FontManager.getRunescapeSmallFont().deriveFont((float) size);
			valueFontSize = size;
		}
		return valueFont;
	}

	/**
	 * @param h     the block's full height; the fill and border use all of it
	 * @param cover pixels of the block's bottom hidden under the frame band, so the icon
	 *              and value centre on the visible part instead of sliding under it
	 */
	private void block(Graphics2D g, int x, int y, int w, int h, int cover, int orbChild)
	{
		int vh = Math.max(8, h - cover);

		Color c = orbColor(orbChild);
		g.setColor(new Color(c.getRed() / 3, c.getGreen() / 3, c.getBlue() / 3));
		g.fillRect(x, y, w, h);
		int fh = Math.round(h * fraction(orbChild));
		g.setColor(c);
		g.fillRect(x, y + h - fh, w, fh);

		// one uniform 1px border, the same EDGE colour as everything else
		g.setColor(EDGE);
		g.drawRect(x, y, w - 1, h - 1);

		// icon and value. the four stock icons are pixel art authored at different sizes
		// and aspects (15x14, 20x20, 15x18, 16x16), so the size setting is a CAP rather
		// than a target: an icon already inside it draws 1:1 and keeps every pixel, and
		// only the oversized ones shrink, nearest-neighbour so they stay hard-edged
		// rather than smeared. the cap is clamped to the block too, so a big icon on a
		// short row can never spill past the edges.
		BufferedImage icon = icon(orbChild);
		String value = value(orbChild);
		Font prev = g.getFont();
		g.setFont(valueFont(config.orbFontSize()));
		FontMetrics fm = g.getFontMetrics();
		int tw = fm.stringWidth(value);

		int padX = config.orbIconPadX();
		int gap = icon != null ? config.orbTextGap() : 0;
		MysticHudConfig.TextAlign align = config.orbTextAlign();
		boolean stacked = align == MysticHudConfig.TextAlign.STACKED;
		boolean over = align == MysticHudConfig.TextAlign.OVER_ICON;
		int th = fm.getAscent();
		int iw = 0;
		int ih = 0;
		if (icon != null)
		{
			int nw = icon.getWidth();
			int nh = icon.getHeight();
			int cap = config.orbIconSize();
			// NOTHING here may depend on the gap. it used to be part of both budgets, so
			// widening the gap shrank the icon, and since the icon is anchored on its left
			// or its centre the retreating edge read as the gap shoving it away. the gap
			// moves the value and only the value; past the point where the value would
			// leave the block it stops moving rather than the icon giving up size.
			if (over)
			{
				// the value is painted on top and reserves nothing, so the icon gets the
				// whole block. the only way to go properly big at a normal row height,
				// since every side-by-side layout is boxed in by the ~43px block width.
				cap = Math.min(cap, Math.min(w - 2, vh - 2));
			}
			else if (stacked)
			{
				// stacked spends the row's HEIGHT on the icon, which is the axis that
				// actually grows, so this is the only layout where a tall row buys a
				// bigger icon
				cap = Math.min(cap, Math.min(w - 2, Math.max(6, vh - th - 2)));
			}
			else
			{
				// side by side the block is a fixed ~43px wide however tall the row gets,
				// so the icon runs out of width long before height. reserve the value's
				// width first and give the icon the rest, or a big icon silently clips
				// the last digit off. measured on the WIDEST value a block can show, not
				// the current one, so all four agree and nothing resizes as stats move.
				int room = w - padX - fm.stringWidth("000") - TEXT_EDGE_PAD;
				cap = Math.min(cap, Math.min(vh - 2, Math.max(6, room)));
			}
			double s = Math.min(cap / (double) nw, cap / (double) nh);
			// asking for more than the biggest native icon can only mean "grow them", so
			// the slider acts on its own up there rather than sitting dead until the
			// upscale toggle is found. below native the toggle still decides, because
			// that is where leaving an icon alone keeps it pixel-exact.
			boolean resize = s < 1
				|| config.orbIconUpscale()
				|| config.orbIconSize() > MAX_NATIVE_ICON;
			iw = resize ? Math.max(1, (int) Math.round(nw * s)) : nw;
			ih = resize ? Math.max(1, (int) Math.round(nh * s)) : nh;
			if (config.orbDebug())
			{
				debugGeom = "slot" + w + " row" + h + " cov" + cover + " vh" + vh;
				debugIcon = "set" + config.orbIconSize() + " cap" + cap
					+ " " + nw + "x" + nh + ">" + iw + "x" + ih
					+ (resize ? "" : " NORESIZE");
			}
		}

		int ix;
		int tx;
		switch (align)
		{
			case OVER_ICON:
			case STACKED:
				ix = x + (w - iw) / 2;
				tx = x + (w - tw) / 2;
				break;
			case CENTRED:
				// gap is left out of the centring on purpose: it should push the value
				// away from the icon, not slide the icon along with it
				ix = x + Math.max(padX, (w - (iw + tw)) / 2);
				tx = ix + iw + gap;
				break;
			case RIGHT_EDGE:
				// never let the right-alignment pull the value back over the icon
				ix = x + padX;
				tx = Math.max(ix + iw + gap, x + w - TEXT_EDGE_PAD - tw);
				break;
			default:
				ix = x + padX;
				tx = ix + iw + gap;
				break;
		}
		if (!stacked && !over)
		{
			// the value runs out of block before the icon does; hold it at the edge
			tx = Math.min(tx, x + w - TEXT_EDGE_PAD - tw);
		}

		// stacked centres the icon and value as a column; the rest centre each on the row.
		// gap is left out of the centring here too, so widening it drops the value down
		// the block and leaves the icon where it was
		int top = y + (vh - (ih + th)) / 2;
		if (icon != null)
		{
			int iy = (stacked && !over ? top : y + (vh - ih) / 2) + config.orbIconNudgeY();
			if (iw == icon.getWidth() && ih == icon.getHeight())
			{
				g.drawImage(icon, ix, iy, null);
			}
			else
			{
				Object hint = g.getRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION);
				g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
					java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g.drawImage(icon, ix, iy, iw, ih, null);
				if (hint != null)
				{
					g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, hint);
				}
			}
		}

		// no fudge on the baseline: the -2 that used to be here sat the value 2px above
		// true centre, which is the other half of why the nudges differed by mode
		int ty = (stacked && !over ? Math.min(top + ih + gap + th, y + vh - 1) : y + (vh + th) / 2)
			+ config.orbTextNudgeY();
		g.setColor(Color.BLACK);
		if (over)
		{
			// sitting on top of the icon, a single drop shadow does not hold the value
			// apart from whatever is behind it; outline all four sides instead
			g.drawString(value, tx - 1, ty);
			g.drawString(value, tx + 1, ty);
			g.drawString(value, tx, ty - 1);
			g.drawString(value, tx, ty + 1);
		}
		g.drawString(value, tx + 1, ty + 1);
		g.setColor(TEXT);
		g.drawString(value, tx, ty);
		g.setFont(prev);
	}

	/**
	 * The Mystic panel frame: a 4px band drawn INSIDE the rectangle's edge with
	 * chamfered corners, exactly how the inventory panel draws its own border. Drawing
	 * it outside the rect was the recurring bug: with the map flush to the inventory the
	 * outside band missed the panel's band by 4px on the left and fell off the canvas
	 * entirely on the right.
	 */
	// the iron-rivet steel border, the frame the inventory ACTUALLY wears (the "little
	// silver bits" are its corner knots). loaded override-aware, so whatever the client
	// shows on the inventory is what we draw.
	private static final int STEEL_TL = 310;
	private static final int STEEL_TR = 311;
	private static final int STEEL_BL = 312;
	private static final int STEEL_BR = 313;
	private static final int STEEL_H = 314;
	private static final int STEEL_V = 315;

	/**
	 * Style B, the user's circled look: Mystic's thin dark window-edge language for the
	 * runs, with the steel corner knots as the silver accents. Knots come from the cache
	 * (pack overrides make them invisible); the thin edges are Mystic's palette.
	 */
	private void borderFrame(Graphics2D g, int x, int y, int w, int h, boolean openBottom)
	{
		// all four edges from the pack's horizontal bar (the one steel piece Mystic
		// keeps visible), rotated for the verticals: full 6px thickness matching the
		// corner knots' arms, one art family throughout
		BufferedImage eh = trimmed(sprite(STEEL_H));
		if (eh == null)
		{
			return;
		}
		BufferedImage ev = rotated(eh);
		int t = eh.getHeight(); // true bar thickness after trimming
		tileH(g, eh, x, x + w, y);
		int sideEnd = y + h - (openBottom ? 0 : t);
		tileV(g, ev, x, y + t, sideEnd);
		tileV(g, ev, x + w - ev.getWidth(), y + t, sideEnd);
		if (!openBottom)
		{
			tileH(g, eh, x, x + w, y + h - t);
		}

		// corners OVERRIDE-AWARE: the pack restyles these to its own palette, and that
		// restyled version is exactly what the inventory's corners show
		BufferedImage tl = sprite(STEEL_TL), tr = sprite(STEEL_TR);
		if (tl != null && tr != null)
		{
			g.drawImage(tl, x, y, null);
			g.drawImage(tr, x + w - tr.getWidth(), y, null);
		}
		if (!openBottom)
		{
			BufferedImage bl = sprite(STEEL_BL), br = sprite(STEEL_BR);
			if (bl != null && br != null)
			{
				g.drawImage(bl, x, y + h - bl.getHeight(), null);
				g.drawImage(br, x + w - br.getWidth(), y + h - br.getHeight(), null);
			}
		}
	}

	private void tileH(Graphics2D g, BufferedImage tile, int x0, int x1, int y)
	{
		int tw = tile.getWidth(), th = tile.getHeight();
		for (int x = x0; x < x1; x += tw)
		{
			int w = Math.min(tw, x1 - x);
			g.drawImage(tile, x, y, x + w, y + th, 0, 0, w, th, null);
		}
	}

	private void tileV(Graphics2D g, BufferedImage tile, int x, int y0, int y1)
	{
		int tw = tile.getWidth(), th = tile.getHeight();
		for (int y = y0; y < y1; y += th)
		{
			int h = Math.min(th, y1 - y);
			g.drawImage(tile, x, y, x + tw, y + h, 0, 0, tw, h, null);
		}
	}

	/** Small round compass rotating with the camera, styled like the orbs. */
	private void compass(Graphics2D g, int x, int y)
	{
		BufferedImage img = sprite(COMPASS_SPRITE);
		double v = client.getCameraFpYaw();
		maxYaw = Math.max(maxYaw, Math.abs(v));
		double turn = maxYaw <= 7d ? Math.PI * 2 : maxYaw <= 2048d ? 2048d : 65536d;
		double theta = -(v / turn) * Math.PI * 2;

		java.awt.Shape oldClip = g.getClip();
		g.setColor(EDGE);
		g.fillOval(x - 3, y - 3, COMPASS_SIZE + 6, COMPASS_SIZE + 6);
		g.setColor(HIGHLIGHT);
		g.drawOval(x - 2, y - 2, COMPASS_SIZE + 3, COMPASS_SIZE + 3);
		g.setColor(PANEL);
		g.fillOval(x, y, COMPASS_SIZE, COMPASS_SIZE);
		g.setClip(new Ellipse2D.Float(x, y, COMPASS_SIZE, COMPASS_SIZE));
		double cx = x + COMPASS_SIZE / 2d;
		double cy = y + COMPASS_SIZE / 2d;
		if (img != null)
		{
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
				java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			AffineTransform t = new AffineTransform();
			t.translate(cx, cy);
			t.rotate(theta);
			double s = (double) COMPASS_SIZE / Math.max(img.getWidth(), img.getHeight());
			t.scale(s, s);
			t.translate(-img.getWidth() / 2d, -img.getHeight() / 2d);
			g.drawImage(img, t, null);
		}
		else
		{
			// sprite not loaded yet: draw a simple needle so the compass is never blank
			int r = COMPASS_SIZE / 2 - 3;
			int nx = (int) Math.round(cx + r * Math.sin(theta));
			int ny = (int) Math.round(cy - r * Math.cos(theta));
			g.setColor(new Color(0xC0392B));
			g.drawLine((int) cx, (int) cy, nx, ny);
			g.setColor(Color.WHITE);
			g.drawLine((int) cx, (int) cy, (int) (2 * cx) - nx, (int) (2 * cy) - ny);
		}
		g.setClip(oldClip);
	}

	// ---- data ----

	private float fraction(int orbChild)
	{
		float f;
		switch (orbChild)
		{
			case MysticHudPlugin.HP:
				f = (float) client.getBoostedSkillLevel(Skill.HITPOINTS)
					/ Math.max(1, client.getRealSkillLevel(Skill.HITPOINTS));
				break;
			case MysticHudPlugin.PRAYER:
				f = (float) client.getBoostedSkillLevel(Skill.PRAYER)
					/ Math.max(1, client.getRealSkillLevel(Skill.PRAYER));
				break;
			case MysticHudPlugin.RUN:
				f = client.getEnergy() / 10000f;
				break;
			default:
				f = client.getVarpValue(VarPlayerID.SA_ENERGY) / 1000f;
		}
		return Math.max(0f, Math.min(1f, f));
	}

	private String value(int orbChild)
	{
		switch (orbChild)
		{
			case MysticHudPlugin.HP:
				return Integer.toString(client.getBoostedSkillLevel(Skill.HITPOINTS));
			case MysticHudPlugin.PRAYER:
				return Integer.toString(client.getBoostedSkillLevel(Skill.PRAYER));
			case MysticHudPlugin.RUN:
				return Integer.toString(client.getEnergy() / 100);
			default:
				return Integer.toString(client.getVarpValue(VarPlayerID.SA_ENERGY) / 10);
		}
	}

	private static Color orbColor(int orbChild)
	{
		switch (orbChild)
		{
			case MysticHudPlugin.HP: return HP_C;
			case MysticHudPlugin.PRAYER: return PRAYER_C;
			case MysticHudPlugin.RUN: return RUN_C;
			default: return SPEC_C;
		}
	}

	// hp heart, prayer star, run boot, spec swords: the stock orb icon sprites
	private int iconSprite(int orbChild)
	{
		switch (orbChild)
		{
			case MysticHudPlugin.HP: return 1067;
			case MysticHudPlugin.PRAYER: return 1068;
			case MysticHudPlugin.RUN: return 1069;
			default: return 1610;
		}
	}

	private BufferedImage icon(int orbChild)
	{
		return icons.computeIfAbsent(iconSprite(orbChild), this::loadSprite);
	}

	private BufferedImage sprite(int id)
	{
		return icons.computeIfAbsent(id, this::loadSprite);
	}

	// resource-pack override first so Mystic's art wins, cache art as fallback
	private BufferedImage loadSprite(int id)
	{
		Map<Integer, SpritePixels> overrides = client.getSpriteOverrides();
		SpritePixels sp = overrides != null ? overrides.get(id) : null;
		return sp != null ? sp.toBufferedImage() : spriteManager.getSprite(id, 0);
	}

	private final Map<Integer, BufferedImage> cacheOnly = new HashMap<>();

	// straight from the game cache, immune to pack overrides
	private BufferedImage cacheSprite(int id)
	{
		return cacheOnly.computeIfAbsent(id, i -> spriteManager.getSprite(i, 0));
	}

	private BufferedImage trimSrc;
	private BufferedImage trimCached;

	/**
	 * Cropped to its opaque pixels: pack override art often floats the visible bar
	 * inside a padded transparent canvas, which offset every tiled edge.
	 */
	private BufferedImage trimmed(BufferedImage src)
	{
		if (src == null)
		{
			return null;
		}
		if (trimCached != null && trimSrc == src)
		{
			return trimCached;
		}
		int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
		for (int yy = 0; yy < src.getHeight(); yy++)
		{
			for (int xx = 0; xx < src.getWidth(); xx++)
			{
				if ((src.getRGB(xx, yy) >>> 24) != 0)
				{
					minX = Math.min(minX, xx);
					minY = Math.min(minY, yy);
					maxX = Math.max(maxX, xx);
					maxY = Math.max(maxY, yy);
				}
			}
		}
		if (maxX < 0)
		{
			return null; // fully transparent override; nothing usable
		}
		trimSrc = src;
		trimCached = src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
		return trimCached;
	}

	private BufferedImage rotSrc;
	private BufferedImage rotCached;

	/** The horizontal edge bar turned 90 degrees for the vertical runs. */
	private BufferedImage rotated(BufferedImage src)
	{
		if (rotCached == null || rotSrc != src)
		{
			BufferedImage out = new BufferedImage(src.getHeight(), src.getWidth(),
				BufferedImage.TYPE_INT_ARGB);
			Graphics2D rg = out.createGraphics();
			rg.rotate(Math.PI / 2, 0, 0);
			rg.drawImage(src, 0, -src.getHeight(), null);
			rg.dispose();
			rotSrc = src;
			rotCached = out;
		}
		return rotCached;
	}
}
