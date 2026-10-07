package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.beyondtabs.mod.BeyondTabs;
import dev.beyondtabs.mod.Network;
import dev.beyondtabs.mod.RtsAction;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client side of the RTS view: the toggle key (V, rebindable), camera updates, fixed FOV, chunk-loading follow. */
@Mod.EventBusSubscriber(modid = BeyondTabs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RtsClient {
    public static final KeyMapping TOGGLE = new KeyMapping("key.beyondtabs.rts_view", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.beyondtabs");

    @Mod.EventBusSubscriber(modid = BeyondTabs.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(TOGGLE); }
    }

    public static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (!RtsCamera.active && !ClientMatch.live() && (ClientLobby.lobby == null || !ClientLobby.lobby.started)) {
            Network.send(new dev.beyondtabs.mod.LobbyAction(dev.beyondtabs.mod.LobbyAction.Op.REQUEST));   // no battle yet: open the lobby
            return;
        }
        RtsAction a = new RtsAction(); a.kind = RtsAction.Kind.MODE;
        if (!RtsCamera.active) {
            RtsCamera.enter();
            if (!RtsCamera.active) return;
            a.on = true; Network.send(a);
            mc.setScreen(new RtsScreen());
        } else {
            RtsCamera.exit();
            a.on = false; Network.send(a);
            if (mc.screen instanceof RtsScreen) mc.setScreen(null);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        while (TOGGLE.consumeClick()) toggle();
        if (!RtsCamera.active || mc.player == null) return;
        if (!(mc.screen instanceof RtsScreen) && mc.screen == null) mc.setScreen(new RtsScreen());
        // keep the (spectating) player under the camera so the chunks there stay loaded; small steps keep the server happy
        double dx = RtsCamera.focusX - mc.player.getX(), dz = RtsCamera.focusZ - mc.player.getZ();
        double d = Math.sqrt(dx * dx + dz * dz), step = Math.min(d, 6);
        double ty = RtsCamera.ground(RtsCamera.focusX, RtsCamera.focusZ) + 10;
        if (d > .5 || Math.abs(mc.player.getY() - ty) > 1)
            mc.player.setPos(mc.player.getX() + (d > 0 ? dx / d * step : 0), mc.player.getY() + Math.max(-6, Math.min(6, ty - mc.player.getY())), mc.player.getZ() + (d > 0 ? dz / d * step : 0));
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START && RtsCamera.active) RtsCamera.update(e.renderTickTime);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) { if (RtsCamera.active) e.setFOV(Proj.FOV); }
}
