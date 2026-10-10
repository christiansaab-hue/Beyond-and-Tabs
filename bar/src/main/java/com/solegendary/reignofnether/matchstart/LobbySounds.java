package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/**
 * The lobby's console sounds (match-start screen, skirmish setup, faction picker): short servo clicks, a relay
 * "chunk", a rising two-tone confirm chirp and a stinger per faction, in the spirit of a late-90s RTS battle room.
 * All original and procedurally generated (tools/gen_ui_sounds.py). Client only.
 *
 * Everything goes through SimpleSoundInstance.forUI, i.e. the MASTER source like vanilla button clicks, so the
 * master slider governs it; the "Lobby sounds" checkbox in the mod's config screen turns the whole set off.
 */
public final class LobbySounds {

    private LobbySounds() { }

    // hover ticks fire on every row change while sweeping the mouse down a list; one per 70 ms reads as a ticking
    // dial rather than a buzz
    private static final long HOVER_GAP_MS = 70;
    private static long lastHover = 0;

    private static final float VOL_HOVER = 0.12f;
    private static final float VOL_SERVO = 0.5f;
    private static final float VOL_RELAY = 0.6f;
    private static final float VOL_CHIRP = 0.4f;
    // the buzz is the densest sound of the set (highest RMS at the same peak), so it plays quieter
    private static final float VOL_DENIED = 0.32f;
    private static final float VOL_READY = 0.6f;
    private static final float VOL_JOIN = 0.35f;
    private static final float VOL_STINGER = 0.7f;

    static boolean enabled() {
        try {
            return ReignOfNetherClientConfigs.LOBBY_SOUNDS.get();
        } catch (IllegalStateException e) {   // client config not loaded yet
            return true;
        }
    }

    private static void play(String key, float volume, float pitch, int delayTicks) {
        if (!enabled())
            return;
        SoundEvent ev = SoundRegistrar.ui(key);
        Minecraft mc = Minecraft.getInstance();
        if (ev == null || mc == null)
            return;
        SimpleSoundInstance s = SimpleSoundInstance.forUI(ev, pitch, volume);
        if (delayTicks > 0)
            mc.getSoundManager().playDelayed(s, delayTicks);
        else
            mc.getSoundManager().play(s);
    }

    /** The mouse moved onto a new list row or colour swatch. Rate limited, so call it on every change. */
    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHover < HOVER_GAP_MS)
            return;
        lastHover = now;
        // a hair of pitch drift keeps a fast sweep from sounding like one sample on repeat
        play("hover_tick", VOL_HOVER, 0.95f + (float) (Math.random() * 0.1), 0);
    }

    /** A picker (faction list, colour palette) opens. */
    public static void open() {
        play("servo_open", VOL_SERVO, 1.0f, 0);
    }

    /** A picker closes without a choice. */
    public static void close() {
        play("servo_close", VOL_SERVO, 1.0f, 0);
    }

    /** A faction was chosen: the confirm chirp at once, then that faction's stinger (Random gets the chirp only). */
    public static void faction(Faction f) {
        play("confirm_chirp", VOL_CHIRP, 1.0f, 0);
        String stinger = stingerOf(f);
        if (stinger != null)
            play(stinger, VOL_STINGER, 1.0f, 2);
    }

    static String stingerOf(Faction f) {
        if (f == Factions.VILLAGERS) return "faction_sunforged";
        if (f == Factions.MONSTERS) return "faction_gravebound";
        if (f == Factions.PIGLINS) return "faction_horde";
        if (f == Factions.VERDANT_COURT) return "faction_verdant";
        if (f == Factions.TIDEWROUGHT) return "faction_tidewrought";
        return null;
    }

    /** A colour (or a coloured slot) was taken: the relay chunk, then the chirp once it has landed. */
    public static void colour() {
        play("relay_chunk", VOL_RELAY, 1.0f, 0);
        play("confirm_chirp", VOL_CHIRP, 1.0f, 3);
    }

    /** Not allowed: a preview faction, a colour or slot someone else holds. */
    public static void denied() {
        play("denied_buzz", VOL_DENIED, 1.0f, 0);
    }

    public static void ready(boolean nowReady) {
        if (nowReady)
            play("ready_chime", VOL_READY, 1.0f, 0);
        else
            play("unready_click", VOL_READY, 1.0f, 0);
    }

    /** Someone (a player or an AI) took a slot. */
    public static void join() {
        play("join_chirp", VOL_JOIN, 1.0f, 0);
    }

    /** A slot was left. */
    public static void leave() {
        play("servo_close", VOL_SERVO, 0.85f, 0);
    }
}
