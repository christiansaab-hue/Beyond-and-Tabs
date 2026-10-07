package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Projectile;
import dev.beyondtabs.engine.Unit;
import dev.beyondtabs.engine.World;
import dev.beyondtabs.engine.gen.UnitDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

public final class Network {
    static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BeyondTabs.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    static final float DETAIL_RANGE = 72f;   // blocks: full ragdoll particles sent within this distance of the player

    static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.beyondtabs.mod.client.ClientMatch.accept(msg)));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    static void sendSnapshot(ServerPlayer p, Match m) {
        World w = m.world; Snapshot s = new Snapshot();
        float px = (float) p.getX(), pz = (float) p.getZ();
        s.tick = w.tick;
        for (Unit u : w.units) {
            Snapshot.U su = new Snapshot.U();
            su.id = u.id; su.team = (byte) u.team; su.def = (short) UnitDef.ALL.indexOf(u.def);
            su.alive = u.alive; su.knocked = u.knocked; su.x = u.x; su.z = u.z; su.yaw = u.yaw;
            su.walkPhase = u.walkPhase; su.walkAmount = u.walkAmount; su.attack = u.attackAnim; su.hp = u.hp / u.maxHp;
            float dx = u.x - px, dz = u.z - pz;
            if (u.lod < 2 && dx * dx + dz * dz < DETAIL_RANGE * DETAIL_RANGE) {
                var r = u.ragdoll; int n = r.rig.n; su.parts = new float[n * 3];
                for (int i = 0; i < n; i++) { su.parts[i * 3] = r.x[i]; su.parts[i * 3 + 1] = r.y[i]; su.parts[i * 3 + 2] = r.z[i]; }
            }
            s.units.add(su);
        }
        for (Projectile pr : w.projectiles) s.projectiles.add(new float[]{pr.x, pr.y, pr.z, pr.vx, pr.vy, pr.vz});
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s);
    }
}
