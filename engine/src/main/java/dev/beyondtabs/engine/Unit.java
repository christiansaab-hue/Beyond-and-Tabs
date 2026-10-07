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
    public Unit target; public Building targetB; float retargetIn; float cooldown; public float attackAnim = -1; boolean hitDealt;
    public float walkPhase; public float walkAmount;
    float[] path; int pathIdx; float pathGx = Float.NaN, pathGz; float pathAge;
    public boolean knocked; public float downFor;
    public final Ragdoll ragdoll;
    public int lod;                           // 0 near, 1 mid, 2 far
    public final float speed, radius, range;

    // ---- combat: abilities, effects, styles ----
    public final dev.beyondtabs.engine.gen.AbilityDef ability;
    /** Combat class used for matchups (infantry, shield, reach, ranged, cavalry, large, siege, flyer, support). */
    public final String cls;
    public float abilityCd; float abilityT; int abilityState; Unit abilityTarget; float ax0, az0, ax1, az1;
    java.util.Set<Integer> bowled;
    public float slowFor, slowMul = 1, poisonFor, poisonDps, stunFor; Unit poisonSrc;
    public float buffFor, buffMul = 1, buffStab;
    float lastHit = -99, nextStrikeMul = 1; int volleyShots;
    public boolean inCombat, bracing, charging, spinning, leaping;
    public Combat.Style style = Combat.Style.AGGRESSIVE;
    Combat.Engagement eng; float anchorX = Float.NaN, anchorZ;
    int attackers, attackersPrev; float noTargetFor;

    Unit(int id, int team, UnitDef def, UnitStats stats, Rig rig, float x, float z, float yaw) {
        this.id = id; this.team = team; this.def = def; this.stats = stats; this.x = x; this.z = z; this.yaw = yaw;
        this.weapon = stats.weapon(); this.body = BodyDef.byId(def.physicsProfile());
        this.maxHp = this.hp = stats.hp();
        float sz = stats.size() * (float) def.scale();
        this.speed = (float) body.moveSpeed() * stats.speedMult();
        this.radius = (float) body.radius() * sz;
        this.range = (float) weapon.range();
        this.ragdoll = new Ragdoll(rig, sz);
        this.ability = dev.beyondtabs.engine.gen.AbilityDef.byId(def.ability());
        this.cls = Combat.classOf(def, weapon, ability);
    }
    public String abilityKind() { return ability.kind(); }
    public boolean isMelee() { return "melee".equals(weapon.kind()); }
    public boolean isSupport() { return "support".equals(weapon.kind()); }
}
