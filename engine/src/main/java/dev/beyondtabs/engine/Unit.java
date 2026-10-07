package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.BodyDef;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.engine.gen.WeaponDef;
import java.util.ArrayDeque;

public final class Unit {
    public final int id; public final int team; public final UnitDef def; public final UnitStats stats; public final WeaponDef weapon; public final BodyDef body;
    public float x, z, yaw, vx, vz;          // controller (where the unit wants to be)
    public float hp; public final float maxHp;
    public boolean alive = true; public float deadFor;
    public final ArrayDeque<Order> orders = new ArrayDeque<>();
    public Unit target; float retargetIn; float cooldown; public float attackAnim = -1; boolean hitDealt;
    public float walkPhase; public float walkAmount;
    public boolean knocked; public float downFor;
    public final Ragdoll ragdoll;
    public int lod;                           // 0 near, 1 mid, 2 far
    public final float speed, radius, range;

    Unit(int id, int team, UnitDef def, UnitStats stats, Rig rig, float x, float z, float yaw) {
        this.id = id; this.team = team; this.def = def; this.stats = stats; this.x = x; this.z = z; this.yaw = yaw;
        this.weapon = stats.weapon(); this.body = BodyDef.byId(def.physicsProfile());
        this.maxHp = this.hp = stats.hp();
        float sz = stats.size() * (float) def.scale();
        this.speed = (float) body.moveSpeed() * stats.speedMult();
        this.radius = (float) body.radius() * sz;
        this.range = (float) weapon.range();
        this.ragdoll = new Ragdoll(rig, sz);
    }
    public boolean isMelee() { return "melee".equals(weapon.kind()); }
    public boolean isSupport() { return "support".equals(weapon.kind()); }
}
