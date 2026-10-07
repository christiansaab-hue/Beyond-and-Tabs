package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Building;
import dev.beyondtabs.engine.Projectile;
import dev.beyondtabs.engine.Team;
import dev.beyondtabs.engine.Unit;
import dev.beyondtabs.engine.World;
import dev.beyondtabs.engine.gen.BuildingDef;
import dev.beyondtabs.engine.gen.TechDef;
import dev.beyondtabs.engine.gen.UnitDef;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Network {
    static final String VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BeyondTabs.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    static final float DETAIL_RANGE = 72f;   // blocks: full ragdoll particles sent within this distance of the player's camera

    static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, (msg, ctx) -> {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> dev.beyondtabs.mod.client.ClientMatch.accept(msg)));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, RtsAction.class, RtsAction::encode, RtsAction::decode, (msg, ctx) -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) ctx.get().enqueueWork(() -> ServerEvents.handle(p, msg));
            ctx.get().setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void send(RtsAction a) { CHANNEL.sendToServer(a); }

    static void sendSnapshot(ServerPlayer p, Match m, boolean withSpots) {
        World w = m.world; Snapshot s = new Snapshot();
        float px = (float) p.getX(), pz = (float) p.getZ();
        s.tick = w.tick; s.winner = w.winner; s.blockStyle = m.structures.blocks(); s.paused = m.paused; s.speed = m.speed;
        int team = m.teamOf(p.getUUID()); s.myTeam = team;
        if (team >= 0 && team < w.teams.size()) {
            Team t = w.teams.get(team);
            s.myRace = t.race;
            s.metal = (float) t.metal; s.metalMax = (float) t.metalStorage; s.metalIncome = (float) t.metalIncome; s.metalSpend = (float) t.metalSpend;
            s.energy = (float) t.energy; s.energyMax = (float) t.energyStorage; s.energyIncome = (float) t.energyIncome; s.energySpend = (float) t.energySpend;
            s.efficiency = (float) t.efficiency; s.supplyUsed = t.supplyUsed; s.supplyCap = t.supplyCap;
            s.researched.addAll(t.researched);
            if (t.alertAt > 0) { s.alertAge = w.time - t.alertAt; s.alertX = t.alertX; s.alertZ = t.alertZ; s.alertBuilding = t.alertBuilding; }
        }
        for (Unit u : w.units) {
            Snapshot.U su = new Snapshot.U();
            su.id = u.id; su.team = (byte) u.team; su.def = (short) UnitDef.ALL.indexOf(u.def);
            su.alive = u.alive; su.knocked = u.knocked; su.x = u.x; su.z = u.z; su.yaw = u.yaw;
            su.walkPhase = u.walkPhase; su.walkAmount = u.walkAmount; su.attack = u.attackAnim; su.hp = u.hp / u.maxHp;
            su.orders = (byte) Math.min(127, u.orders.size());
            if (u.team == team && u.inCombat) su.style = (byte) u.style.ordinal();
            float dx = u.x - px, dz = u.z - pz;
            if (u.lod < 2 && dx * dx + dz * dz < DETAIL_RANGE * DETAIL_RANGE) {
                var r = u.ragdoll; int n = r.rig.n; su.parts = new float[n * 3];
                for (int i = 0; i < n; i++) { su.parts[i * 3] = r.x[i]; su.parts[i * 3 + 1] = r.y[i]; su.parts[i * 3 + 2] = r.z[i]; }
            }
            s.units.add(su);
        }
        for (Building b : w.buildings) {
            if (!b.alive) continue;
            Snapshot.B sb = new Snapshot.B();
            sb.id = b.id; sb.team = (byte) b.team; sb.def = (short) BuildingDef.ALL.indexOf(b.def); sb.level = (byte) b.level;
            sb.upgrading = b.upgrading; sb.repeat = b.repeat; sb.x = b.x; sb.z = b.z; sb.progress = b.progress; sb.hp = b.hp / b.maxHp();
            sb.rallyX = b.rallyX; sb.rallyZ = b.rallyZ;
            if (b.producing != null) { sb.producing = (short) UnitDef.ALL.indexOf(b.producing); sb.produceFrac = (float) (b.produceWork / b.producing.buildWork()); }
            if (b.researching != null) { sb.researching = (short) TechDef.ALL.indexOf(b.researching); sb.researchFrac = 1f - b.researchLeft / b.researching.seconds(); }
            if (b.team == team) { sb.queue = new short[b.queue.size()]; int i = 0; for (UnitDef d : b.queue) sb.queue[i++] = (short) UnitDef.ALL.indexOf(d); }
            s.buildings.add(sb);
        }
        for (Projectile pr : w.projectiles) s.projectiles.add(new float[]{pr.x, pr.y, pr.z, pr.vx, pr.vy, pr.vz});
        if (withSpots) {
            s.hasSpots = true; s.metalSpots = new float[w.metalSpots.size() * 2];
            for (int i = 0; i < w.metalSpots.size(); i++) { s.metalSpots[i * 2] = w.metalSpots.get(i)[0]; s.metalSpots[i * 2 + 1] = w.metalSpots.get(i)[1]; }
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), s);
    }
}
