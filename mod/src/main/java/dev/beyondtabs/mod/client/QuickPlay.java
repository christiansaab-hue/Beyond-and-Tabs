package dev.beyondtabs.mod.client;

import dev.beyondtabs.mod.BeyondTabs;
import dev.beyondtabs.mod.LobbyAction;
import dev.beyondtabs.mod.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * One click from the Minecraft title screen to the battle lobby: a "Beyond & TABS" button creates (or reopens) a
 * dedicated peaceful world and opens the lobby as soon as you are in it.
 */
@Mod.EventBusSubscriber(modid = BeyondTabs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class QuickPlay {
    private QuickPlay() { }

    static final String WORLD = "BeyondTabsBattlefield";
    static boolean openLobbyOnJoin;
    static int joinDelay;

    @SubscribeEvent
    public static void titleButton(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof TitleScreen title)) return;
        int w = 200, x = title.width / 2 - w / 2, y = title.height / 4 + 48 - 26;
        e.addListener(Button.builder(Component.literal("⚔ Beyond & TABS: Quick Battle"), b -> start()).bounds(x, y, w, 20).build());
    }

    static void start() {
        Minecraft mc = Minecraft.getInstance();
        openLobbyOnJoin = true; joinDelay = 40;
        if (mc.getLevelSource().levelExists(WORLD)) { mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD); return; }
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(WorldOptions.randomSeed(), true, false), WorldPresets::createNormalWorldDimensions);
    }

    /** Once the world has loaded, open the lobby. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !openLobbyOnJoin) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        if (--joinDelay > 0) return;
        openLobbyOnJoin = false;
        Network.send(new LobbyAction(LobbyAction.Op.REQUEST));
    }
}
