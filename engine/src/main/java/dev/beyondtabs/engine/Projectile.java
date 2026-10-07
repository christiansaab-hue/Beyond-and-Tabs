package dev.beyondtabs.engine;

/** Ballistic projectile (arrow, spear, boulder, bullet). Lives only in the sim; Minecraft draws them client-side. */
public final class Projectile {
    public float x, y, z, vx, vy, vz; public final Unit owner; public final int team; public float life;
    public final float damage, aoe, knockback, gravity; public final String visual; public boolean dead;
    int pierced; Unit lastHit;
    Projectile(Unit owner, float x, float y, float z, float vx, float vy, float vz, float damage, float aoe, float knockback, float gravity, String visual) {
        this(owner, owner.team, x, y, z, vx, vy, vz, damage, aoe, knockback, gravity, visual);
    }
    Projectile(Unit owner, int team, float x, float y, float z, float vx, float vy, float vz, float damage, float aoe, float knockback, float gravity, String visual) {
        this.owner = owner; this.team = team; this.x = x; this.y = y; this.z = z; this.vx = vx; this.vy = vy; this.vz = vz;
        this.damage = damage; this.aoe = aoe; this.knockback = knockback; this.gravity = gravity; this.visual = visual; this.life = 8f;
    }
}
