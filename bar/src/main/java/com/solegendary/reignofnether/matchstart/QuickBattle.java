package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Skirmish from the title screen (Beyond and Tabs). One button opens the skirmish lobby ({@link SkirmishSetupScreen}:
 * your faction and colour, the opponents, arena size, metal richness, spawn distance). Start Battle makes a FRESH
 * world every time - so an old, finished match can never bleed into a new one - enters the RTS camera, and sends the
 * lobby choices to the server, which lays your capitol, seats your commander and calls in the bots. The long way
 * round (make a world, press F12, pick a faction, place your base, /bot add ...) still works and is what multiplayer
 * uses.
 */
public class QuickBattle {

    static final String WORLD_DIR_PREFIX = "beyondandtabs_skirmish_";
    static final String WORLD_NAME_PREFIX = "Beyond and Tabs - Skirmish ";

    static SkirmishSetupScreen.Settings pending = null;
    static int phase = 0;   // 0 idle, 1 waiting for world, 2 send the lobby choices, 3 waiting for the rts start
    static int waitTicks = 0;

    @SubscribeEvent
    public static void onTitleScreen(ScreenEvent.Init.Post evt) {
        if (!(evt.getScreen() instanceof TitleScreen title))
            return;
        int w = 200, x = title.width / 2 - w / 2, y = title.height / 4 + 48 - 26;
        evt.addListener(Button.builder(Component.translatable("quickbattle.reignofnether.button"),
                b -> Minecraft.getInstance().setScreen(new SkirmishSetupScreen(title))).bounds(x, y, w, 20).build());
    }

    /** Called by the lobby's Start Battle: make a fresh world and queue the start. */
    static void launch(Screen from, SkirmishSetupScreen.Settings settings) {
        pending = settings;
        phase = 1;
        waitTicks = 0;
        Minecraft mc = Minecraft.getInstance();
        // a fresh world per skirmish; number them so the Singleplayer list stays readable
        int n = 1;
        while (mc.getLevelSource().levelExists(WORLD_DIR_PREFIX + n))
            n++;
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(true, null);
        LevelSettings levelSettings = new LevelSettings(WORLD_NAME_PREFIX + n, GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD_DIR_PREFIX + n, levelSettings,
                new WorldOptions(WorldOptions.randomSeed(), true, false), QuickBattle::battlefieldDimensions);
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
        if (mc.player == null || mc.level == null)
            return;
        // the RTS view itself is a screen (TopdownGui) - only a foreign screen (pause menu, chat...) pauses us
        if (mc.screen != null && !(mc.screen instanceof com.solegendary.reignofnether.guiscreen.TopdownGui))
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
            case 2 -> {   // hand the lobby choices to the server: it starts us, lays the capitol, calls in the bots
                if (waitTicks > 20 && pending != null) {
                    List<SkirmishServerboundPacket.BotSpec> bots = new ArrayList<>();
                    for (int i = 0; i < pending.botCount; i++)
                        bots.add(new SkirmishServerboundPacket.BotSpec(pending.botFaction[i], pending.botDifficulty[i]));
                    SkirmishServerboundPacket.send(pending.faction, pending.colorMapId(), bots,
                            pending.arena, pending.metal, pending.spawnDistance);
                    phase = 3;
                    waitTicks = 0;
                }
            }
            case 3 -> {   // once the server has us as an RTS player we're done
                if (PlayerClientEvents.isRTSPlayer(mc.player.getName().getString()) || waitTicks > 300)
                    phase = 0;
            }
            default -> phase = 0;
        }
    }
}
