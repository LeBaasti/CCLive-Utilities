package net.felix.utilities.Overall.starforged;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.felix.CCLiveUtilities;
import net.felix.CCLiveUtilitiesConfig;
import net.felix.Community.leaderboards.collectors.SmithSkillCollector;
import net.felix.Community.leaderboards.panel.SmithSkill;
import net.felix.utilities.Overall.itemviewer.data.ItemCatalog;
import net.felix.utilities.Overall.itemviewer.data.ItemData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fun easter egg: after {@link LesterCallCounter#THRESHOLD} crafts without "Sternengeschmiedet" (only crafts with
 * an item score that allows it, at smith level 100), Lester calls on a phone in the bottom right corner and
 * tells the player he removed the stars. Input is blocked while the call runs, Escape skips it.
 * <p>
 * The counter is saved per account in {@code config/cclive-utilities/lester-call.json}. The voice line is the sound
 * event {@code cclive-utilities:lester_call} ({@code sounds/lester_call.ogg}); without it only subtitles show.
 * Voice and ringtone follow the config volume {@code lesterCallVolume}.
 */
public final class LesterCallUtility {

	private static final Logger LOGGER = LoggerFactory.getLogger("cclive-utilities/lester-call");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Identifier VOICE = Identifier.fromNamespaceAndPath("cclive-utilities", "lester_call");
	/** Wait after the 1000th craft, so a "Sternengeschmiedet" state message of that very craft can still cancel. */
	private static final long TRIGGER_DELAY_MS = 1500L;

	// Only touched on the client thread.
	private static final Map<String, Integer> counts = new LinkedHashMap<>();
	private static boolean loaded = false;
	private static Integer lastSmithLevel;
	private static long pendingAtMs = -1L;
	private static float lastElapsed = -1f;
	private static SoundInstance voice;
	private static boolean initialized = false;

	private LesterCallUtility() {
	}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ClientTickEvents.END_CLIENT_TICK.register(LesterCallUtility::onClientTick);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("cclive-utilities", "lester_call"),
				LesterCallOverlay::render);
	}

	static boolean isEnabled() {
		return CCLiveUtilitiesConfig.HANDLER.instance().lesterCallAnimationEnabled
				&& CCLiveUtilitiesConfig.isModRuntimeEnabled();
	}

	static boolean isRunning() {
		return LesterCallOverlay.isRunning();
	}

	/**
	 * Counts a craft chat line (client thread). {@code batchItemName} is the last shift-clicked blueprint, needed
	 * because the batch message has no item name.
	 */
	static void onCraftMessage(String line, String batchItemName) {
		if (!isEnabled()) {
			return;
		}
		int crafts = StarforgedCraftTracker.craftCount(line);
		if (crafts <= 0) {
			return;
		}
		String name = StarforgedCraftTracker.craftedName(line);
		if (name == null) {
			name = batchItemName;
		}
		Minecraft client = Minecraft.getInstance();
		if (!LesterCallCounter.qualifies(itemScore(name), smithLevel(client))) {
			return;
		}
		String account = account(client);
		if (account == null) {
			return;
		}
		ensureLoaded();
		int count = LesterCallCounter.add(counts.getOrDefault(account, 0), crafts);
		counts.put(account, count);
		save();
		if (LesterCallCounter.reached(count) && pendingAtMs < 0L) {
			pendingAtMs = System.currentTimeMillis() + TRIGGER_DELAY_MS;
		}
	}

	/** A craft got "Sternengeschmiedet": the counter starts over (client thread). */
	static void onStarforged() {
		pendingAtMs = -1L;
		String account = account(Minecraft.getInstance());
		if (account == null) {
			return;
		}
		ensureLoaded();
		Integer previous = counts.put(account, 0);
		if (previous == null || previous != 0) {
			save();
		}
	}

	/** Plays the call regardless of the counter and the config toggle (for testing). */
	public static void playTest() {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> start(client));
	}

	/** Short status line for the test command. */
	public static String statusText() {
		Minecraft client = Minecraft.getInstance();
		String account = account(client);
		ensureLoaded();
		int count = account == null ? 0 : counts.getOrDefault(account, 0);
		Integer level = smithLevel(client);
		return "Crafts ohne Stern: " + count + "/" + LesterCallCounter.THRESHOLD + ", Schmiedelevel: "
				+ (level == null ? "unbekannt" : level) + ", aktiv: " + (isEnabled() ? "ja" : "nein");
	}

	static void cancel() {
		LesterCallOverlay.stop();
		stopVoice();
		lastElapsed = -1f;
	}

	private static void start(Minecraft client) {
		if (client.player == null || LesterCallOverlay.isRunning()) {
			return;
		}
		if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
			client.player.closeContainer();
		}
		LesterCallOverlay.start();
		lastElapsed = -1f;
	}

	private static void onClientTick(Minecraft client) {
		if (pendingAtMs >= 0L && System.currentTimeMillis() >= pendingAtMs && !StarforgedOverlay.isRunning()) {
			pendingAtMs = -1L;
			String account = account(client);
			if (account != null && isEnabled()) {
				counts.put(account, 0);
				save();
				start(client);
			}
		}
		float t = LesterCallOverlay.elapsed();
		if (t < 0f) {
			if (lastElapsed >= 0f) {
				// Ran out on its own.
				stopVoice();
				lastElapsed = -1f;
			}
			return;
		}
		playSounds(client, lastElapsed, t);
		lastElapsed = t;
	}

	// ------------------------------------------------------------------ sounds

	private static void playSounds(Minecraft client, float prev, float now) {
		// Ringtone: a fast two-tone trill in each ring burst, one note every 100 ms.
		for (float[] burst : LesterCallOverlay.RING_BURSTS) {
			for (float at = burst[0]; at < burst[1]; at += 100f) {
				if (crossed(prev, now, at)) {
					int step = Math.round((at - burst[0]) / 100f);
					float pitch = step % 2 == 0 ? 1.5f : 1.19f;
					ui(client, SoundEvents.NOTE_BLOCK_BIT.value(), pitch, 0.55f);
				}
			}
		}
		if (crossed(prev, now, LesterCallOverlay.ACCEPT)) {
			ui(client, SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.6f);
			playVoice(client);
		}
		if (crossed(prev, now, LesterCallOverlay.CALL_END)) {
			stopVoice();
			ui(client, SoundEvents.NOTE_BLOCK_BIT.value(), 1.0f, 0.5f);
		}
		if (crossed(prev, now, LesterCallOverlay.CALL_END + 180f)) {
			ui(client, SoundEvents.NOTE_BLOCK_BIT.value(), 0.75f, 0.5f);
		}
	}

	private static boolean crossed(float prev, float now, float at) {
		return prev < at && now >= at;
	}

	/** Configured call volume, 0..1. */
	private static float volume() {
		return Math.max(0, Math.min(100, CCLiveUtilitiesConfig.HANDLER.instance().lesterCallVolume)) / 100f;
	}

	private static void ui(Minecraft client, SoundEvent sound, float pitch, float volume) {
		float scaled = volume * volume();
		if (scaled > 0f) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, scaled));
		}
	}

	/** Plays the optional recording, if some resource (mod or resource pack) registers the sound event. */
	private static void playVoice(Minecraft client) {
		float volume = volume();
		if (volume <= 0f || client.getSoundManager().getSoundEvent(VOICE) == null) {
			return;
		}
		voice = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(VOICE), 1.0f, volume);
		client.getSoundManager().play(voice);
	}

	private static void stopVoice() {
		if (voice != null) {
			Minecraft.getInstance().getSoundManager().stop(voice);
			voice = null;
		}
	}

	// ------------------------------------------------------------------ lookups

	private static Double itemScore(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		ItemData item = ItemCatalog.findItemByNameFuzzy(StarforgedCraftTracker.itemName(name));
		return item == null ? null : LesterCallCounter.parseScore(item.itemScore);
	}

	/** Smith level from the tab list; the last known value is kept while the widget is missing. */
	private static Integer smithLevel(Minecraft client) {
		SmithSkill.Stand stand = SmithSkill.read(SmithSkillCollector.tabLines(client));
		if (stand != null) {
			lastSmithLevel = stand.level();
		}
		return lastSmithLevel;
	}

	private static String account(Minecraft client) {
		return client == null || client.player == null ? null : client.player.getUUID().toString();
	}

	// ------------------------------------------------------------------ persistence

	private static Path file() {
		return CCLiveUtilities.getConfigDir().resolve("cclive-utilities").resolve("lester-call.json");
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path file = file();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			JsonObject root = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
			if (root == null || !root.has("crafts") || !root.get("crafts").isJsonObject()) {
				return;
			}
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("crafts").entrySet()) {
				if (entry.getValue().isJsonPrimitive()) {
					counts.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
				}
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read {}", file, e);
		}
	}

	private static void save() {
		Path file = file();
		JsonObject crafts = new JsonObject();
		counts.forEach(crafts::addProperty);
		JsonObject root = new JsonObject();
		root.add("crafts", crafts);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", file, e);
		}
	}
}
