package net.felix.utilities.Overall.starforged;

import static net.felix.utilities.Overall.starforged.StarforgedAnimationMath.argb;
import static net.felix.utilities.Overall.starforged.StarforgedAnimationMath.clamp01;
import static net.felix.utilities.Overall.starforged.StarforgedAnimationMath.easeInCubic;
import static net.felix.utilities.Overall.starforged.StarforgedAnimationMath.easeOutBack;
import static net.felix.utilities.Overall.starforged.StarforgedAnimationMath.lerpRgb;
import static net.felix.utilities.Overall.starforged.StarforgedAnimationMath.progress;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Screen part of the Lester call: a smartphone slides up in the bottom right corner, rings and vibrates,
 * the call is accepted and Lester talks (subtitles at the bottom), then the call ends and the phone slides away.
 * Everything is drawn from plain fills, no textures or game assets.
 */
final class LesterCallOverlay {

	// Timeline in ms.
	static final float SLIDE_IN = 500f;
	static final float[][] RING_BURSTS = { { 300f, 1300f }, { 1800f, 2800f } };
	static final float ACCEPT = 3200f;
	/** Start of each subtitle line, relative to {@link #ACCEPT}; matches sounds/lester_call.ogg. */
	static final float[] LINE_STARTS = { 110f, 1620f, 6670f, 10750f, 14300f };
	/** End of the last spoken part of each line, relative to {@link #ACCEPT}. */
	static final float[] LINE_ENDS = { 1144f, 6324f, 10339f, 13650f, 16706f };
	/** Spoken parts of the recording (start, end), relative to {@link #ACCEPT}; drive the voice wave. */
	static final float[][] SPEECH = { { 110f, 1144f }, { 1618f, 4373f }, { 4820f, 6324f }, { 6667f, 10339f },
			{ 10750f, 12028f }, { 12376f, 13650f }, { 14296f, 15311f }, { 15600f, 16706f } };
	static final String[] LINES = {
			"Hey, it's Lester.",
			"A thousand crafts and not a single star? That's not bad luck, buddy.",
			"I hacked into the smithing system for a sec and removed your stars.",
			"Wasn't a problem at all. I'm just good at this stuff.",
			"Don't call me. I'll call you." };
	/** The recording is 17.06 s long. */
	static final float CALL_END = ACCEPT + 17100f;
	private static final float ENDED_HOLD = 1200f;
	private static final float SLIDE_OUT = 600f;
	static final float TOTAL_MS = CALL_END + ENDED_HOLD + SLIDE_OUT;

	private static final int PHONE_W = 92;
	private static final int PHONE_H = 170;
	private static final int MARGIN = 10;

	private static final int COLOR_BODY = 0x1A1A1D;
	private static final int COLOR_EDGE = 0x3A3A40;
	private static final int COLOR_SCREEN_TOP = 0x1F4466;
	private static final int COLOR_SCREEN_BOTTOM = 0x0A1422;
	private static final int COLOR_ACCENT = 0x5FC1E8;
	private static final int COLOR_GREEN = 0x3DCB5A;
	private static final int COLOR_RED = 0xE0453A;
	private static final int COLOR_SKIN = 0xE2B48E;
	private static final int COLOR_SHIRT = 0x5B6B3C;

	private static long startedAtMs = -1L;

	private LesterCallOverlay() {
	}

	static void start() {
		startedAtMs = Util.getMillis();
	}

	static void stop() {
		startedAtMs = -1L;
	}

	/** Time based, so it also ends when the HUD is hidden and render() is not called. */
	static boolean isRunning() {
		return startedAtMs >= 0L && Util.getMillis() - startedAtMs <= TOTAL_MS;
	}

	/** Elapsed ms of the running call, or -1. */
	static float elapsed() {
		return isRunning() ? Util.getMillis() - startedAtMs : -1f;
	}

	static boolean isRinging(float t) {
		for (float[] burst : RING_BURSTS) {
			if (t >= burst[0] && t < burst[1]) {
				return true;
			}
		}
		return false;
	}

	/** True while a spoken part of the recording plays at time t. */
	static boolean isTalking(float t) {
		float inCall = t - ACCEPT;
		for (float[] part : SPEECH) {
			if (inCall >= part[0] && inCall < part[1]) {
				return true;
			}
		}
		return false;
	}

	/** Index of the subtitle line shown at time t, or -1. */
	static int lineAt(float t) {
		if (t < ACCEPT || t >= CALL_END) {
			return -1;
		}
		float inCall = t - ACCEPT;
		int index = -1;
		for (int i = 0; i < LINE_STARTS.length; i++) {
			if (inCall >= LINE_STARTS[i]) {
				index = i;
			}
		}
		return index;
	}

	static void render(GuiGraphicsExtractor g, DeltaTracker tickCounter) {
		if (startedAtMs < 0L) {
			return;
		}
		float t = Util.getMillis() - startedAtMs;
		if (t > TOTAL_MS) {
			startedAtMs = -1L;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		Font font = client.font;
		int width = g.guiWidth();
		int height = g.guiHeight();

		float slideIn = easeOutBack(progress(t, 0f, SLIDE_IN));
		float slideOut = easeInCubic(progress(t, TOTAL_MS - SLIDE_OUT, SLIDE_OUT));
		float offsetY = (1f - slideIn) * (PHONE_H + MARGIN * 2) + slideOut * (PHONE_H + MARGIN * 2);
		int shakeX = isRinging(t) ? ((int) (t / 40f) % 2 == 0 ? 1 : -1) : 0;
		int x = width - PHONE_W - MARGIN + shakeX;
		int y = Math.round(height - PHONE_H - MARGIN + offsetY);

		renderPhone(g, font, t, x, y);
		renderSubtitle(g, font, t, width, height);
	}

	// ------------------------------------------------------------------ phone

	private static void renderPhone(GuiGraphicsExtractor g, Font font, float t, int x, int y) {
		// Body with a lighter rim, earpiece and home button.
		roundRect(g, x - 1, y - 1, PHONE_W + 2, PHONE_H + 2, 10, argb(1f, COLOR_EDGE));
		roundRect(g, x, y, PHONE_W, PHONE_H, 9, argb(1f, COLOR_BODY));
		g.fill(x + PHONE_W / 2 - 10, y + 6, x + PHONE_W / 2 + 10, y + 8, argb(1f, 0x45454C));
		g.fill(x + PHONE_W / 2 + 14, y + 6, x + PHONE_W / 2 + 16, y + 8, argb(1f, 0x2B3A55));
		circle(g, x + PHONE_W / 2, y + PHONE_H - 9, 5, argb(1f, 0x2C2C31));
		circle(g, x + PHONE_W / 2, y + PHONE_H - 9, 4, argb(1f, COLOR_BODY));

		int sx = x + 5;
		int sy = y + 14;
		int sw = PHONE_W - 10;
		int sh = PHONE_H - 32;
		// Vertical gradient as bands.
		int bands = 17;
		for (int i = 0; i < bands; i++) {
			int top = sy + sh * i / bands;
			int bottom = sy + sh * (i + 1) / bands;
			g.fill(sx, top, sx + sw, bottom, argb(1f, lerpRgb(COLOR_SCREEN_TOP, COLOR_SCREEN_BOTTOM, i / (bands - 1f))));
		}
		renderStatusBar(g, font, sx, sy, sw);

		int cx = sx + sw / 2;
		if (t < ACCEPT + 250f) {
			renderIncoming(g, font, t, cx, sx, sy, sw, sh);
		} else {
			renderInCall(g, font, t, cx, sy, sh);
		}
		// Accept flash.
		float flash = 1f - progress(t, ACCEPT, 350f);
		if (t >= ACCEPT && flash > 0f) {
			g.fill(sx, sy, sx + sw, sy + sh, argb(flash * 0.35f, COLOR_GREEN));
		}
	}

	private static void renderStatusBar(GuiGraphicsExtractor g, Font font, int sx, int sy, int sw) {
		g.fill(sx, sy, sx + sw, sy + 9, argb(0.35f, 0x000000));
		LocalTime now = LocalTime.now();
		String clock = String.format("%02d:%02d", now.getHour(), now.getMinute());
		drawSmall(g, font, clock, sx + 2, sy + 2, argb(1f, 0xFFFFFF));
		int right = sx + sw - 2;
		// Battery.
		g.fill(right - 9, sy + 2, right - 1, sy + 7, argb(1f, 0xFFFFFF));
		g.fill(right - 1, sy + 3, right, sy + 6, argb(1f, 0xFFFFFF));
		g.fill(right - 8, sy + 3, right - 4, sy + 6, argb(1f, COLOR_GREEN));
		// Signal bars.
		for (int i = 0; i < 4; i++) {
			int bx = right - 22 + i * 3;
			g.fill(bx, sy + 7 - (i + 1) * 1 - i / 2, bx + 2, sy + 7, argb(1f, 0xFFFFFF));
		}
	}

	private static void renderIncoming(GuiGraphicsExtractor g, Font font, float t, int cx, int sx, int sy, int sw,
			int sh) {
		float fadeOut = 1f - progress(t, ACCEPT, 250f);
		float pulse = 0.55f + 0.45f * (float) Math.sin(t / 180.0);
		drawCentered(g, font, "Eingehender Anruf", cx, sy + 15, argb(fadeOut * pulse, 0xCFE6F5), true);
		renderAvatar(g, cx, sy + 50, 16, fadeOut, t);
		drawCentered(g, font, "Lester", cx, sy + 72, argb(fadeOut, 0xFFFFFF), false);
		drawCentered(g, font, "Mobil", cx, sy + 83, argb(fadeOut * 0.7f, 0x9FB3C4), true);

		int by = sy + sh - 18;
		int left = sx + 18;
		int right = sx + sw - 18;
		// Pulsing ring around the accept button while ringing.
		float ringPulse = (t % 900f) / 900f;
		circle(g, right, by, Math.round(9 + ringPulse * 6), argb(fadeOut * (1f - ringPulse) * 0.5f, COLOR_GREEN));
		circle(g, left, by, 9, argb(fadeOut, COLOR_RED));
		circle(g, right, by, 9, argb(fadeOut, COLOR_GREEN));
		drawHandset(g, left, by, argb(fadeOut, 0xFFFFFF), true);
		drawHandset(g, right, by, argb(fadeOut, 0xFFFFFF), false);
	}

	private static void renderInCall(GuiGraphicsExtractor g, Font font, float t, int cx, int sy, int sh) {
		float fadeIn = progress(t, ACCEPT + 250f, 300f);
		boolean ended = t >= CALL_END;
		drawCentered(g, font, "Lester", cx, sy + 16, argb(fadeIn, 0xFFFFFF), false);
		float callMs = Math.min(t, CALL_END) - ACCEPT;
		int seconds = Math.max(0, (int) (callMs / 1000f));
		String timer = String.format("%02d:%02d", seconds / 60, seconds % 60);
		drawCentered(g, font, ended ? "Anruf beendet" : timer, cx, sy + 27,
				argb(fadeIn, ended ? COLOR_RED : 0x9FB3C4), true);
		renderAvatar(g, cx, sy + 58, 14, fadeIn, t);

		// Voice wave: bars move while Lester is talking.
		boolean talking = !ended && isTalking(t);
		int bars = 9;
		int waveY = sy + 88;
		for (int i = 0; i < bars; i++) {
			float amp = talking
					? 2f + 5f * Math.abs((float) Math.sin(t / 70.0 + i * 1.3) * (float) Math.cos(t / 130.0 + i * 0.7))
					: 1f;
			int bx = cx - bars * 2 + i * 4;
			g.fill(bx, Math.round(waveY - amp), bx + 2, Math.round(waveY + amp), argb(fadeIn * 0.9f, COLOR_ACCENT));
		}

		int by = sy + sh - 18;
		float endPress = ended ? 0.5f : 1f;
		circle(g, cx, by, 9, argb(fadeIn * endPress, COLOR_RED));
		drawHandset(g, cx, by, argb(fadeIn, 0xFFFFFF), true);
	}

	/** Generic contact picture: a bald man with glasses and a green shirt. */
	private static void renderAvatar(GuiGraphicsExtractor g, int cx, int cy, int r, float alpha, float t) {
		if (alpha <= 0.02f) {
			return;
		}
		circle(g, cx, cy, r + 1, argb(alpha, COLOR_ACCENT));
		circle(g, cx, cy, r, argb(alpha, 0x34495E));
		int head = Math.max(3, r * 2 / 5);
		// Shoulders, kept inside the circle.
		roundRect(g, cx - r * 2 / 3, cy + r / 3, r * 4 / 3, r * 2 / 3 - 1, 4, argb(alpha, COLOR_SHIRT));
		circle(g, cx, cy - r / 6, head, argb(alpha, COLOR_SKIN));
		// Glasses.
		int gy = cy - r / 6;
		int dark = argb(alpha, 0x1B1B1B);
		g.fill(cx - head + 1, gy - 1, cx - 1, gy + 1, dark);
		g.fill(cx + 1, gy - 1, cx + head - 1, gy + 1, dark);
		g.fill(cx - 1, gy - 1, cx + 1, gy, dark);
	}

	/** Tiny handset icon; tilted down for hang up. */
	private static void drawHandset(GuiGraphicsExtractor g, int cx, int cy, int color, boolean hangUp) {
		if (hangUp) {
			g.fill(cx - 5, cy - 1, cx + 5, cy + 1, color);
			g.fill(cx - 5, cy + 1, cx - 3, cy + 3, color);
			g.fill(cx + 3, cy + 1, cx + 5, cy + 3, color);
		} else {
			g.fill(cx - 4, cy - 4, cx - 2, cy + 4, color);
			g.fill(cx - 2, cy - 4, cx + 1, cy - 2, color);
			g.fill(cx - 2, cy + 2, cx + 1, cy + 4, color);
		}
	}

	// ------------------------------------------------------------------ subtitles

	private static void renderSubtitle(GuiGraphicsExtractor g, Font font, float t, int width, int height) {
		int line = lineAt(t);
		if (line < 0) {
			return;
		}
		float since = t - ACCEPT - LINE_STARTS[line];
		// Typed along with the speech: the line is complete when its last spoken part ends.
		float reveal = clamp01(since / Math.max(1f, LINE_ENDS[line] - LINE_STARTS[line]));
		int chars = Math.min(LINES[line].length(), (int) Math.ceil(reveal * LINES[line].length()));
		float fade = clamp01(since / 150f) * (1f - progress(t, CALL_END - 250f, 250f));
		if (fade <= 0.02f) {
			return;
		}
		String speaker = "Lester: ";
		int maxWidth = Math.max(120, Math.min(300, width - PHONE_W - MARGIN * 4));
		// Wrap the full line so the layout does not jump while typing.
		List<String> fullRows = wrap(font, LINES[line], maxWidth - font.width(speaker));
		List<String> rows = cut(fullRows, chars);
		int boxWidth = 0;
		for (String row : fullRows) {
			boxWidth = Math.max(boxWidth, font.width(row));
		}
		boxWidth += font.width(speaker);
		int cx = (width - PHONE_W - MARGIN) / 2;
		int top = height - 62 - (fullRows.size() - 1) * (font.lineHeight + 1);
		int left = cx - boxWidth / 2;
		g.fill(left - 4, top - 3, left + boxWidth + 4, top + fullRows.size() * (font.lineHeight + 1) + 2,
				argb(fade * 0.55f, 0x000000));
		g.text(font, speaker, left, top, argb(fade, COLOR_ACCENT), true);
		int textLeft = left + font.width(speaker);
		for (int i = 0; i < rows.size(); i++) {
			g.text(font, rows.get(i), textLeft, top + i * (font.lineHeight + 1), argb(fade, 0xFFFFFF), true);
		}
	}

	private static List<String> wrap(Font font, String text, int maxWidth) {
		List<String> rows = new ArrayList<>();
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = row.isEmpty() ? word : row + " " + word;
			if (!row.isEmpty() && font.width(candidate) > maxWidth) {
				rows.add(row.toString());
				row = new StringBuilder(word);
			} else {
				row = new StringBuilder(candidate);
			}
		}
		if (!row.isEmpty()) {
			rows.add(row.toString());
		}
		return rows;
	}

	/** First {@code chars} characters of the wrapped rows (the separating spaces count as one character). */
	private static List<String> cut(List<String> rows, int chars) {
		List<String> result = new ArrayList<>();
		int left = chars;
		for (String row : rows) {
			if (left <= 0) {
				break;
			}
			result.add(row.substring(0, Math.min(row.length(), left)));
			left -= row.length() + 1;
		}
		return result;
	}

	// ------------------------------------------------------------------ drawing helpers

	private static void drawCentered(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color,
			boolean small) {
		if ((color >>> 24) < 5) {
			return;
		}
		if (small) {
			float scale = 0.75f;
			int w = font.width(text);
			g.pose().pushMatrix();
			g.pose().translate(cx - w * scale / 2f, y);
			g.pose().scale(scale, scale);
			g.text(font, text, 0, 0, color, false);
			g.pose().popMatrix();
		} else {
			g.text(font, text, cx - font.width(text) / 2, y, color, true);
		}
	}

	private static void drawSmall(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(0.6f, 0.6f);
		g.text(font, text, 0, 0, color, false);
		g.pose().popMatrix();
	}

	/** Rectangle with rounded corners, built from one fill per corner row. */
	private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if ((color >>> 24) == 0 || w <= 0 || h <= 0) {
			return;
		}
		r = Math.min(r, Math.min(w, h) / 2);
		for (int i = 0; i < r; i++) {
			int inset = cornerInset(r, i);
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
			g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
		}
		g.fill(x, y + r, x + w, y + h - r, color);
	}

	private static void circle(GuiGraphicsExtractor g, int cx, int cy, int r, int color) {
		if ((color >>> 24) == 0 || r <= 0) {
			return;
		}
		roundRect(g, cx - r, cy - r, r * 2, r * 2, r, color);
	}

	private static int cornerInset(int r, int row) {
		double dy = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0.0, r * (double) r - dy * dy)));
	}
}
