package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import com.solegendary.reignofnether.resources.EconomyClientEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientEvents;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;
import com.solegendary.reignofnether.startpos.CapturePointsClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * BAR-style spoken notifications, done the Minecraft way: a short line under the economy bar plus a distinct chord of
 * vanilla sounds per alert type, so a player looking at their base still hears "commander under attack" across the
 * map. Every type has its own cooldown so a big fight produces one "unit lost" every few seconds, not a wall of
 * them. Everything is client-side: the triggers are packets/state the client already has (attack warnings, building
 * isBuilt transitions, unit deaths, economy syncs, idle-worker syncs, capture-site syncs and the T3 chat line).
 *
 * Rendering keeps a fixed 3-slot ring and pre-measured widths, so drawing allocates nothing per frame.
 *
 * Announcer voice: when on (and the line's ogg is present), each alert is spoken by a calm synthetic assistant voice
 * instead of the chord. There is exactly one voice channel: lines wait in a tiny priority queue (commander attacked
 * first, storage-full last), never overlap, and are dropped if they waited more than VOICE_STALE_MS - a "unit lost"
 * from five seconds ago is noise, not information.
 */
public class NotificationClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    public enum Alert {
        // key, cooldown, text colour (-1 = voice only, no text line), voice priority (higher speaks first)
        COMMANDER_ATTACKED("commander_attacked", 10_000, 0xFF5A5A, 100),
        COMMANDER_KILLED("commander_killed", 5_000, 0xFF3030, 95),
        EXPERIMENTAL_DETECTED("experimental_detected", 15_000, 0xFF60FF, 90),
        BASE_ATTACKED("base_attacked", 20_000, 0xFF7A5A, 80),
        ALLY_NEEDS_HELP("ally_needs_help", 20_000, 0xFFA040, 70),
        UNITS_ATTACKED("units_attacked", 20_000, 0xFF9A7A, 65),
        UNIT_LOST("unit_lost", 8_000, 0xC8C8C8, 60),
        CAPTURE_LOST("capture_lost", 5_000, 0xFF7070, 55),
        METAL_STALL("metal_stall", 20_000, 0xFF8A6A, 50),
        ENERGY_STALL("energy_stall", 20_000, 0xFF8A6A, 50),
        EXPERIMENTAL_READY("experimental_ready", 10_000, 0xE0A0FF, 45),
        CAPTURE_TAKEN("capture_taken", 5_000, 0x80E0FF, 40),
        CONSTRUCTION_COMPLETE("construction_complete", 4_000, 0x8CFF8C, 30),
        RESEARCH_COMPLETE("research_complete", 4_000, -1, 30),   // ResearchClient already shows its own line
        IDLE_CONSTRUCTOR("idle_constructor", 15_000, 0xFFE070, 25),
        METAL_FULL("metal_full", 30_000, 0xB4BEC8, 20),
        ENERGY_FULL("energy_full", 30_000, 0xF0C83C, 20),
        // match moments: the game already shows a title for these, so voice only
        VICTORY("victory", 0, -1, 110),
        DEFEAT("defeat", 0, -1, 110),
        GAME_START("game_start", 0, -1, 110);

        public final String key;
        public final long cooldownMs;
        public final int color;
        public final int priority;

        Alert(String key, long cooldownMs, int color, int priority) {
            this.key = key;
            this.cooldownMs = cooldownMs;
            this.color = color;
            this.priority = priority;
        }
    }

    private static final Alert[] ALERTS = Alert.values();
    private static final long[] lastFiredMs = new long[ALERTS.length];

    // --- on-screen stack: newest in slot 0, max 3 ---
    private static final int MAX_LINES = 3;
    private static final long LINE_MS = 3000, FADE_MS = 800;
    private static final String[] lines = new String[MAX_LINES];
    private static final int[] lineWidths = new int[MAX_LINES];
    private static final int[] lineColors = new int[MAX_LINES];
    private static final long[] lineStartMs = new long[MAX_LINES];

    // --- delayed sound notes, so an alert can be a two-step motif (horn then answer) rather than one chord ---
    private static final int MAX_PENDING = 8;
    private static final SoundEvent[] pendingSound = new SoundEvent[MAX_PENDING];
    private static final float[] pendingPitch = new float[MAX_PENDING];
    private static final float[] pendingVol = new float[MAX_PENDING];
    private static final int[] pendingTicks = new int[MAX_PENDING];

    // no alerts for the first few seconds of being an RTS player: joining syncs idle workers, capture owners and
    // storage in bulk, and those would otherwise all "change" at once
    private static final int WARMUP_TICKS = 200;
    private static int rtsTicks = 0;
    private static boolean isRts = false;
    private static String myName = "";

    // economy/idle polling at 2 Hz (economy syncs arrive at 4 Hz); conditions must hold for SUSTAIN_POLLS polls
    private static final int POLL_TICKS = 10, SUSTAIN_POLLS = 4;
    private static int pollTicks = 0;
    private static int metalFullPolls = 0, energyFullPolls = 0, stallPolls = 0;
    private static int prevIdleWorkers = 0;

    // --- announcer voice: one channel, a small priority queue, stale lines dropped ---
    private static final long VOICE_STALE_MS = 3000;
    // isActive can lag the play() call by a tick while the channel spins up; this guard covers that gap
    private static final long VOICE_MIN_GAP_MS = 350;
    private static final int VOICE_QUEUE = 6;
    private static final Alert[] voiceQueue = new Alert[VOICE_QUEUE];
    private static final long[] voiceDueMs = new long[VOICE_QUEUE];   // may start from here; stale VOICE_STALE_MS later
    private static SoundInstance voiceNow = null;
    private static long voiceBusyUntilMs = 0;
    // per alert: 0 = not checked yet, 1 = ogg present, 2 = missing (fall back to the chord). Checked once, lazily,
    // so a build without the generated oggs still has working alerts.
    private static final byte[] voiceFileState = new byte[ALERTS.length];
    private static final SoundEvent[] voiceEvents = new SoundEvent[ALERTS.length];

    // ------------------------------------------------------------------------------------------------------------
    // public entry points (hooks)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * From AttackWarningClientEvents: a commander was hit. Returns true when this hit is (or was just) announced by
     * our own "commander under attack" alert: it fired now, or fired within its cooldown for the same ongoing
     * assault. The caller then skips the old RoN danger sound so the player doesn't hear two alarms for one event.
     * False whenever alerts are disabled, so the old sound stays the only cue in that case.
     */
    public static boolean onCommanderAttacked(String ownerName) {
        if (!canAlert() || ownerName == null)
            return false;
        if (myName.equals(ownerName)) {
            fire(Alert.COMMANDER_ATTACKED, null);
            long last = lastFiredMs[Alert.COMMANDER_ATTACKED.ordinal()];
            return last != 0 && System.currentTimeMillis() - last < Alert.COMMANDER_ATTACKED.cooldownMs;
        }
        if (AlliancesClient.isAllied(myName, ownerName))
            fire(Alert.ALLY_NEEDS_HELP, ownerName);
        return false;
    }

    /**
     * From AttackWarningClientEvents: something of ours that is not the commander was hit somewhere off-screen
     * (the warning packets are already rate-limited per player by the server). A hit inside one of our buildings
     * means the base, anything else our units.
     */
    public static void onOwnAssetAttacked(BlockPos pos) {
        if (!canAlert() || pos == null)
            return;
        fire(BuildingUtils.findBuilding(true, pos) != null ? Alert.BASE_ATTACKED : Alert.UNITS_ATTACKED, null);
    }

    /** From ResearchClient.addResearch, for our own newly finished research. */
    public static void onResearchComplete() {
        if (canAlert())
            fire(Alert.RESEARCH_COMPLETE, null);
    }

    // Match moments bypass the warm-up (they happen exactly while it runs) and the cooldowns, and wait for the
    // game's own fanfare to finish so the voice doesn't talk over it.
    /** From PlayerClientEvents.addRTSPlayer, when we become an RTS player with a faction. */
    public static void onGameStart() {
        announce(Alert.GAME_START, 1000);
    }

    /** From PlayerClientEvents.victory (victory.ogg is ~3.6 s). */
    public static void onVictory() {
        announce(Alert.VICTORY, 3600);
    }

    /** From PlayerClientEvents.defeat (defeat.ogg is ~5.2 s). */
    public static void onDefeat() {
        announce(Alert.DEFEAT, 5300);
    }

    /** From BuildingPlacement.onBuilt (client side). */
    public static void onBuildingBuilt(BuildingPlacement placement) {
        // tickAge guard: a placement that arrives already complete (joining, coming into view) isn't news
        if (!canAlert() || placement.tickAge < 40 || !myName.equals(placement.ownerName))
            return;
        fire(Alert.CONSTRUCTION_COMPLETE, null);
    }

    /** From CapturePointsClient.sync, before the list is replaced. */
    public static void onCaptureSitesSynced(List<CapturePointsClient.Site> oldSites, List<CapturePointsClient.Site> newSites) {
        if (!canAlert() || oldSites.isEmpty())
            return;
        for (CapturePointsClient.Site n : newSites) {
            for (CapturePointsClient.Site o : oldSites) {
                if (o.x() != n.x() || o.y() != n.y() || o.z() != n.z())
                    continue;
                boolean wasMine = myName.equals(o.owner());
                boolean isMine = myName.equals(n.owner());
                if (wasMine != isMine)
                    fire(isMine ? Alert.CAPTURE_TAKEN : Alert.CAPTURE_LOST, siteName(n.kind()));
                break;
            }
        }
    }

    private static String siteName(int kind) {
        CapturePointServerEvents.Kind[] kinds = CapturePointServerEvents.Kind.values();
        if (kind < 0 || kind >= kinds.length)
            return "";
        return I18n.get("capture.reignofnether." + kinds[kind].name().toLowerCase());
    }

    // ------------------------------------------------------------------------------------------------------------
    // event-driven triggers
    // ------------------------------------------------------------------------------------------------------------

    // Own unit died. The server's removal arrives after the death animation, by which point health has synced to 0,
    // so isDeadOrDying separates deaths from units that merely left tracking range or the level unloading.
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent evt) {
        if (!evt.getLevel().isClientSide() || !(evt.getEntity() instanceof Unit unit)
                || !(evt.getEntity() instanceof LivingEntity le) || !le.isDeadOrDying())
            return;
        // the commander's death is its own, louder line: it also means the com-blast is about to go off
        if (canAlert() && myName.equals(unit.getOwnerName()))
            fire(CommanderServerEvents.looksLikeCommander(le) ? Alert.COMMANDER_KILLED : Alert.UNIT_LOST, null);
    }

    // The T3 announcement is a plain translatable chat line; reading its key avoids adding a packet for it.
    @SubscribeEvent
    public static void onChat(ClientChatReceivedEvent evt) {
        Component msg = evt.getMessage();
        if (msg == null || !(msg.getContents() instanceof TranslatableContents tc)
                || !"server.reignofnether.t3_arrived".equals(tc.getKey()) || !canAlert())
            return;
        Object[] args = tc.getArgs();
        if (args.length < 1)
            return;
        String owner = argString(args[0]);
        String unitName = args.length > 1 ? argString(args[1]) : "";
        if (owner.equals(myName))
            fire(Alert.EXPERIMENTAL_READY, unitName);
        else if (!AlliancesClient.isAllied(myName, owner))
            fire(Alert.EXPERIMENTAL_DETECTED, unitName);
    }

    // args survive the network either as raw strings or as components, depending on how they were serialised
    private static String argString(Object arg) {
        return arg instanceof Component c ? c.getString() : String.valueOf(arg);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        tickPendingSounds();
        tickVoice();

        if (--pollTicks > 0)
            return;
        pollTicks = POLL_TICKS;

        isRts = MC.player != null && MC.level != null && PlayerClientEvents.isRTSPlayer();
        if (!isRts) {
            rtsTicks = 0;
            metalFullPolls = energyFullPolls = stallPolls = 0;
            prevIdleWorkers = 0;
            return;
        }
        myName = MC.player.getName().getString();
        rtsTicks += POLL_TICKS;

        // idle constructor: count rising from zero (the server syncs the list; the HUD button reads the same one)
        int idle = UnitClientEvents.idleWorkerIds.size();
        if (idle > 0 && prevIdleWorkers == 0 && canAlert())
            fire(Alert.IDLE_CONSTRUCTOR, null);
        prevIdleWorkers = idle;

        // economy - same "at cap with positive net" rule as the HUD's WASTING label (EconomyBarRenderer)
        Resources res = ResourcesClientEvents.getResources(myName);
        if (res == null)
            return;
        EconomyClientEvents.ClientEconomy eco = EconomyClientEvents.getEconomy(myName);
        metalFullPolls = atCap(res.ore, eco.metalStorage, eco.metalIncome - eco.metalExpense) ? metalFullPolls + 1 : 0;
        energyFullPolls = atCap(res.wood, eco.energyStorage, eco.energyIncome - eco.energyExpense) ? energyFullPolls + 1 : 0;
        stallPolls = eco.stall < 0.8f ? stallPolls + 1 : 0;
        if (!canAlert())
            return;
        if (metalFullPolls >= SUSTAIN_POLLS)
            fire(Alert.METAL_FULL, null);
        if (energyFullPolls >= SUSTAIN_POLLS)
            fire(Alert.ENERGY_FULL, null);
        if (stallPolls >= SUSTAIN_POLLS)
            fire(EconomyBarRenderer.shortOnMetal(res, eco) ? Alert.METAL_STALL : Alert.ENERGY_STALL, null);
    }

    private static boolean atCap(int amount, float storage, float net) {
        return storage > 0 && amount >= storage - 1 && net > 0.05f;
    }

    // ------------------------------------------------------------------------------------------------------------
    // firing
    // ------------------------------------------------------------------------------------------------------------

    private static boolean canAlert() {
        return isRts && rtsTicks >= WARMUP_TICKS && ReignOfNetherClientConfigs.ALERTS_ENABLED.get();
    }

    private static void fire(Alert alert, String arg) {
        long now = System.currentTimeMillis();
        int i = alert.ordinal();
        if (lastFiredMs[i] != 0 && now - lastFiredMs[i] < alert.cooldownMs)
            return;
        lastFiredMs[i] = now;
        if (alert.color >= 0)
            pushLine(lineFor(alert, arg), alert.color, now);
        // the voice replaces the chord; the chord stays as the fallback (voice off, muted, or line not shipped)
        if (!queueVoice(alert, now))
            playSound(alert);
    }

    private static void announce(Alert alert, long delayMs) {
        if (ReignOfNetherClientConfigs.ALERTS_ENABLED.get())
            queueVoice(alert, System.currentTimeMillis() + delayMs);
    }

    // ------------------------------------------------------------------------------------------------------------
    // announcer voice
    // ------------------------------------------------------------------------------------------------------------

    private static boolean voiceOn() {
        return ReignOfNetherClientConfigs.ANNOUNCER_VOICE.get() && ReignOfNetherClientConfigs.VOICE_VOLUME.get() > 0;
    }

    private static SoundEvent voiceFor(Alert alert) {
        int i = alert.ordinal();
        if (voiceFileState[i] == 0) {
            SoundEvent ev = SoundRegistrar.announcer(alert.key);
            boolean present = ev != null && MC.getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(
                ReignOfNether.MOD_ID, "sounds/announcer/" + alert.key + ".ogg")).isPresent();
            voiceEvents[i] = ev;
            voiceFileState[i] = present ? (byte) 1 : (byte) 2;
        }
        return voiceFileState[i] == 1 ? voiceEvents[i] : null;
    }

    /** Queues the alert's line; false if it won't be spoken (voice off, no file, queue full of more urgent lines). */
    private static boolean queueVoice(Alert alert, long dueMs) {
        if (!voiceOn() || voiceFor(alert) == null)
            return false;
        int free = -1, lowest = -1;
        for (int q = 0; q < VOICE_QUEUE; q++) {
            Alert a = voiceQueue[q];
            if (a == alert) {          // already waiting: refresh it rather than saying it twice
                voiceDueMs[q] = dueMs;
                return true;
            }
            if (a == null) {
                if (free < 0)
                    free = q;
            } else if (lowest < 0 || a.priority < voiceQueue[lowest].priority) {
                lowest = q;
            }
        }
        if (free < 0) {
            if (voiceQueue[lowest].priority >= alert.priority)
                return false;
            free = lowest;             // evict the least urgent waiting line
        }
        voiceQueue[free] = alert;
        voiceDueMs[free] = dueMs;
        return true;
    }

    // One voice channel: the most urgent due line starts only once the previous one has finished.
    private static void tickVoice() {
        if (!voiceOn()) {
            for (int q = 0; q < VOICE_QUEUE; q++)
                voiceQueue[q] = null;
            return;
        }
        long now = System.currentTimeMillis();
        int best = -1;
        for (int q = 0; q < VOICE_QUEUE; q++) {
            Alert a = voiceQueue[q];
            if (a == null || now < voiceDueMs[q])
                continue;
            if (now - voiceDueMs[q] > VOICE_STALE_MS) {
                voiceQueue[q] = null;
                continue;
            }
            if (best < 0 || a.priority > voiceQueue[best].priority)
                best = q;
        }
        if (best < 0 || now < voiceBusyUntilMs
                || (voiceNow != null && MC.getSoundManager().isActive(voiceNow)))
            return;
        SoundEvent ev = voiceFor(voiceQueue[best]);
        voiceQueue[best] = null;
        if (ev == null)
            return;
        float v = Math.max(0, Math.min(100, ReignOfNetherClientConfigs.VOICE_VOLUME.get())) / 100f;
        voiceNow = SimpleSoundInstance.forUI(ev, 1.0f, v);
        MC.getSoundManager().play(voiceNow);
        voiceBusyUntilMs = now + VOICE_MIN_GAP_MS;
    }

    // faction-flavoured line if the lang file has one ("notification.reignofnether.<key>.<faction>"), else default
    private static String lineFor(Alert alert, String arg) {
        String base = "notification.reignofnether." + alert.key;
        Faction faction = PlayerClientEvents.getFaction();
        String key = base;
        if (faction != null && faction.key != null) {
            String fk = base + "." + faction.key.getPath();
            if (I18n.exists(fk))
                key = fk;
        }
        return arg == null ? I18n.get(key) : I18n.get(key, arg);
    }

    private static void pushLine(String text, int color, long now) {
        for (int s = MAX_LINES - 1; s > 0; s--) {
            lines[s] = lines[s - 1];
            lineWidths[s] = lineWidths[s - 1];
            lineColors[s] = lineColors[s - 1];
            lineStartMs[s] = lineStartMs[s - 1];
        }
        lines[0] = text;
        lineWidths[0] = MC.font.width(text);
        lineColors[0] = color & 0xFFFFFF;
        lineStartMs[0] = now;
    }

    // Each alert is a small chord (and sometimes a delayed answer note) of vanilla sounds, chosen so they are
    // distinguishable without looking: horns for threats, bells for good news, metal for losses and storage.
    private static void playSound(Alert alert) {
        float v = Math.max(0, Math.min(100, ReignOfNetherClientConfigs.ALERT_VOLUME.get())) / 100f;
        if (v <= 0)
            return;
        switch (alert) {
            case COMMANDER_ATTACKED -> {   // low war horn, twice
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.5f, 1.0f * v, 0);
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.8f * v, 0);
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6f, 1.0f * v, 6);
            }
            case COMMANDER_KILLED -> {     // dragon roar over a sinking horn: the blast is coming
                note(SoundEvents.ENDER_DRAGON_GROWL, 0.9f, 0.35f * v, 0);
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.5f, 1.0f * v, 0);
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 1.0f * v, 6);
            }
            case BASE_ATTACKED -> {        // horn, then a bell alarm
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6f, 0.9f * v, 0);
                note(SoundEvents.BELL_BLOCK, 0.8f, 0.5f * v, 5);
            }
            case UNITS_ATTACKED ->         // short horn
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.7f, 0.8f * v, 0);
            case EXPERIMENTAL_READY -> {   // beacon swell
                note(SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.6f * v, 0);
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.6f * v, 4);
            }
            case RESEARCH_COMPLETE ->      // enchanting-table shimmer
                note(SoundEvents.ENCHANTMENT_TABLE_USE, 1.2f, 0.6f * v, 0);
            case ALLY_NEEDS_HELP -> {      // higher horn answered by a pling
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.75f, 0.8f * v, 0);
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, 0.6f * v, 5);
            }
            case UNIT_LOST -> {            // dull anvil clunk + chain rattle
                note(SoundEvents.ANVIL_LAND, 0.6f, 0.25f * v, 0);
                note(SoundEvents.CHAIN_BREAK, 0.7f, 0.6f * v, 0);
            }
            case CONSTRUCTION_COMPLETE -> {   // village bell then a bright chime
                note(SoundEvents.BELL_BLOCK, 1.3f, 0.5f * v, 0);
                note(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.6f * v, 3);
            }
            case IDLE_CONSTRUCTOR -> {     // cow bell knock-knock
                note(SoundEvents.NOTE_BLOCK_COW_BELL.value(), 1.0f, 0.7f * v, 0);
                note(SoundEvents.NOTE_BLOCK_COW_BELL.value(), 0.8f, 0.7f * v, 4);
            }
            case METAL_FULL -> {           // ringing iron, falling
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 1.2f, 0.7f * v, 0);
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.9f, 0.7f * v, 4);
            }
            case ENERGY_FULL -> {          // crystal chime, falling
                note(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f * v, 0);
                note(SoundEvents.NOTE_BLOCK_BELL.value(), 0.9f, 0.6f * v, 4);
            }
            case METAL_STALL -> {          // iron note sinking into bass
                note(SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(), 0.6f, 0.7f * v, 0);
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.8f * v, 4);
            }
            case ENERGY_STALL -> {         // power-down whine
                note(SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.6f * v, 0);
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.8f * v, 2);
            }
            case EXPERIMENTAL_DETECTED -> {   // distant dragon growl over a low horn
                note(SoundEvents.ENDER_DRAGON_GROWL, 1.4f, 0.25f * v, 0);
                note(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.5f, 0.9f * v, 0);
            }
            case CAPTURE_TAKEN -> {
                note(SoundEvents.BEACON_ACTIVATE, 1.3f, 0.6f * v, 0);
                note(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 0.5f * v, 3);
            }
            case CAPTURE_LOST -> {
                note(SoundEvents.BEACON_DEACTIVATE, 0.8f, 0.6f * v, 0);
                note(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.8f * v, 3);
            }
        }
    }

    private static void note(SoundEvent sound, float pitch, float volume, int delayTicks) {
        if (delayTicks <= 0) {
            MC.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
            return;
        }
        for (int i = 0; i < MAX_PENDING; i++) {
            if (pendingSound[i] == null) {
                pendingSound[i] = sound;
                pendingPitch[i] = pitch;
                pendingVol[i] = volume;
                pendingTicks[i] = delayTicks;
                return;
            }
        }
        // queue full (several alerts in the same instant): drop the echo note, the first note already played
    }

    private static void tickPendingSounds() {
        for (int i = 0; i < MAX_PENDING; i++) {
            if (pendingSound[i] != null && --pendingTicks[i] <= 0) {
                MC.getSoundManager().play(SimpleSoundInstance.forUI(pendingSound[i], pendingPitch[i], pendingVol[i]));
                pendingSound[i] = null;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // rendering: small lines centred just under the economy bar (and its stall warning), fading out over FADE_MS
    // ------------------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (lines[0] == null || !OrthoviewClientEvents.isEnabled() || !(evt.getScreen() instanceof TopdownGui))
            return;
        long now = System.currentTimeMillis();
        GuiGraphics gg = evt.getGuiGraphics();
        int cx = MC.getWindow().getGuiScaledWidth() / 2;
        EconomyBarRenderer.Layout bar = HudClientEvents.economyBar;
        int y = bar != null ? bar.bottom() + 16 : 40;   // +16 clears the one-line stall warning under the bar
        for (int s = 0; s < MAX_LINES; s++) {
            String line = lines[s];
            if (line == null)
                continue;
            long age = now - lineStartMs[s];
            if (age >= LINE_MS) {
                lines[s] = null;
                continue;
            }
            long left = LINE_MS - age;
            float a = left < FADE_MS ? (float) left / FADE_MS : 1f;
            int alpha = Math.max(8, (int) (0xFF * a));   // font renderer treats alpha < 4 as opaque
            int w = lineWidths[s] + 8;
            gg.fill(cx - w / 2, y - 2, cx + w / 2, y + 10, ((int) (0xA0 * a) << 24) | 0x101216);
            gg.drawString(MC.font, line, cx - lineWidths[s] / 2, y, (alpha << 24) | lineColors[s]);
            y += 13;
        }
    }
}
