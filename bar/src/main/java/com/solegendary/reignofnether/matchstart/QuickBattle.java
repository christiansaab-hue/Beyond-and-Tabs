package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * One click from the title screen to a skirmish: "Quick Battle" opens a tiny faction picker, creates (or reopens) a
 * dedicated peaceful world, drops you straight into the RTS camera with your faction started where you stand, and
 * calls in a bot opponent on the far side. The long way round (make a world, press F12, pick a faction, place your
 * base, /bot add ...) still works and is what multiplayer uses.
 */
public class QuickBattle {

    static final String WORLD_NAME = "Beyond and Tabs - Quick Battle";
    static final String WORLD_DIR = "beyondandtabs_quick_battle";

    // what the pending quick battle should set up once the world is running (client-local; integrated server only)
    static Faction pendingFaction = null;
    static String pendingBotFaction = null;
    static int phase = 0;   // 0 idle, 1 waiting for world, 2 waiting to start rts, 3 waiting for rts to begin, 4 add bot
    static int waitTicks = 0;

    @SubscribeEvent
    public static void onTitleScreen(ScreenEvent.Init.Post evt) {
        if (!(evt.getScreen() instanceof TitleScreen title))
            return;
        int w = 200, x = title.width / 2 - w / 2, y = title.height / 4 + 48 - 26;
        evt.addListener(Button.builder(Component.translatable("quickbattle.reignofnether.button"),
                b -> Minecraft.getInstance().setScreen(new FactionPickScreen(title))).bounds(x, y, w, 20).build());
    }

    /** Minimal picker: your faction (bots can only play villagers/monsters, so the bot plays the other one). */
    static class FactionPickScreen extends Screen {
        final Screen parent;

        FactionPickScreen(Screen parent) {
            super(Component.translatable("quickbattle.reignofnether.title"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int w = 180, x = width / 2 - w / 2, y = height / 2 - 40;
            addRenderableWidget(Button.builder(Component.translatable("quickbattle.reignofnether.villagers"),
                    b -> start(Factions.VILLAGERS, "monsters")).bounds(x, y, w, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("quickbattle.reignofnether.monsters"),
                    b -> start(Factions.MONSTERS, "villagers")).bounds(x, y + 24, w, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),
                    b -> Minecraft.getInstance().setScreen(parent)).bounds(x, y + 56, w, 20).build());
        }

        @Override
        public void render(net.minecraft.client.gui.GuiGraphics gg, int mx, int my, float pt) {
            renderBackground(gg);
            gg.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF);
            super.render(gg, mx, my, pt);
        }

        void start(Faction faction, String botFaction) {
            pendingFaction = faction;
            pendingBotFaction = botFaction;
            phase = 1;
            waitTicks = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getLevelSource().levelExists(WORLD_DIR)) {
                mc.createWorldOpenFlows().loadLevel(this, WORLD_DIR);
                return;
            }
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(true, null);
            LevelSettings settings = new LevelSettings(WORLD_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    rules, WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD_DIR, settings,
                    new WorldOptions(WorldOptions.randomSeed(), true, false), QuickBattle::battlefieldDimensions);
        }
    }

    /** The Battlefield world preset (gentle rolling grassland built for matches); vanilla terrain as the fallback. */
    static net.minecraft.world.level.levelgen.WorldDimensions battlefieldDimensions(net.minecraft.core.RegistryAccess registryAccess) {
        try {
            var presets = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET);
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.WORLD_PRESET,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "battlefield"));
            var preset = presets.get(key);
            if (preset != null)
                return preset.createWorldDimensions();
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[QuickBattle] battlefield preset unavailable, using vanilla terrain", e);
        }
        return WorldPresets.createNormalWorldDimensions(registryAccess);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || phase == 0)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null)
            return;
        waitTicks++;
        switch (phase) {
            case 1 -> {   // in the world: give it a moment to settle, then enter the RTS camera
                if (waitTicks > 40) {
                    if (!OrthoviewClientEvents.isEnabled())
                        OrthoviewClientEvents.toggleEnable();
                    phase = 2;
                    waitTicks = 0;
                }
            }
            case 2 -> {   // start our faction where we stand
                if (waitTicks > 20) {
                    PlayerServerboundPacket.startRTS(pendingFaction, mc.player.getX(), mc.player.getY(), mc.player.getZ());
                    phase = 3;
                    waitTicks = 0;
                }
            }
            case 3 -> {   // once the server has us as an RTS player, call in the opponent
                if (PlayerClientEvents.isRTSPlayer(mc.player.getName().getString())) {
                    if (pendingBotFaction != null && mc.player.connection != null)
                        mc.player.connection.sendCommand("bot add " + pendingBotFaction);
                    phase = 0;
                } else if (waitTicks > 200) {
                    phase = 0;   // didn't start (locked server, etc) - give up quietly
                }
            }
            default -> phase = 0;
        }
    }
}
