package dev.beyondtabs.engine;

/**
 * An active ragdoll (position-based dynamics / Verlet), the TABS way: every body part is a particle with mass, bones are
 * distance constraints, and "muscles" pull each part toward an animated pose with a strength scaled by the unit's balance.
 * Hits push parts and drain balance, so units wobble, stagger and fall over, then get back up.
 */
public final class Ragdoll {
    public final Rig rig;
    public final float[] x, y, z, px, py, pz, tx, ty, tz;
    final float[] invMass, rest;
    public final float totalMass;
    public float scale = 1f;
    /** 1 = fully in control, 0 = limp. */
    public float balance = 1f;
    public boolean limp;          // dead: muscles off
    /** Knocked down: muscles mostly off until balance recovers, then the unit pushes itself back up. */
    public boolean down;
    public boolean sleeping;
    /** 0 = relaxed, 1+ = set for a fight: muscles hold the pose harder so weapons stay on target. */
    public float stance;
    int stillTicks;

    static final float GRAVITY = -24f;      // blocks/s^2 (Minecraft-scale units are ~1.8 blocks tall)
    static final float DAMPING = 0.985f;
    static final float POSE_K = 0.35f;

    public Ragdoll(Rig rig, float scale) {
        this.rig = rig; this.scale = scale; int n = rig.n;
        x = new float[n]; y = new float[n]; z = new float[n]; px = new float[n]; py = new float[n]; pz = new float[n];
        tx = new float[n]; ty = new float[n]; tz = new float[n]; invMass = new float[n];
        float tm = 0;
        for (int i = 0; i < n; i++) { invMass[i] = 1f / rig.mass[i]; tm += rig.mass[i]; }
        totalMass = tm;
        rest = new float[rig.ca.length];
        for (int c = 0; c < rest.length; c++) {
            int a = rig.ca[c], b = rig.cb[c];
            float dx = rig.rx[a] - rig.rx[b], dy = rig.ry[a] - rig.ry[b], dz = rig.rz[a] - rig.rz[b];
            rest[c] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * scale;
        }
    }

    /** Computes the target pose: rest skeleton placed at (rootX, groundY, rootZ) facing yaw, plus walk and attack motion. */
    public void pose(float rootX, float groundY, float rootZ, float yaw, float walkPhase, float walkAmount, float attack) {
        float s = (float) Math.sin(yaw), c = (float) Math.cos(yaw), sc = scale;
        float stride = (float) Math.sin(walkPhase) * 0.35f * walkAmount, bob = Math.abs((float) Math.cos(walkPhase)) * 0.06f * walkAmount;
        for (int i = 0; i < rig.n; i++) {
            float lx = rig.rx[i], ly = rig.ry[i] + bob, lz = rig.rz[i];
            if (rig.n == Rig.HUMANOID_PARTICLES) {
                switch (i) {
                    case Rig.FOOT_L -> { lz += stride; ly += Math.max(0, -stride) * .4f; }
                    case Rig.KNEE_L -> lz += stride * .5f + .05f * walkAmount;
                    case Rig.FOOT_R -> { lz -= stride; ly += Math.max(0, stride) * .4f; }
                    case Rig.KNEE_R -> lz -= stride * .5f - .05f * walkAmount;
                    case Rig.HAND_L -> lz -= stride * .6f;
                    case Rig.HAND_R -> {   // attack: raise then swing through forward
                        float a = attack < .5f ? attack * 2 : 2 - attack * 2;
                        ly += a * .9f; lz += stride * .6f + (attack > 0 ? (attack < .5f ? -.3f * a : .9f * (1 - a)) : 0);
                    }
                    case Rig.ELBOW_R -> ly += (attack < .5f ? attack * 2 : 2 - attack * 2) * .5f;
                    case Rig.HEAD, Rig.NECK, Rig.TORSO -> lz += .08f * walkAmount;
                    default -> { }
                }
            }
            lx *= sc; ly *= sc; lz *= sc;
            tx[i] = rootX + lx * c + lz * s;
            ty[i] = groundY + ly;
            tz[i] = rootZ - lx * s + lz * c;
        }
    }

    /** Places the ragdoll exactly on its pose (spawn, or far LOD where nothing is simulated). */
    public void snapToPose() {
        for (int i = 0; i < rig.n; i++) { x[i] = px[i] = tx[i]; y[i] = py[i] = ty[i]; z[i] = pz[i] = tz[i]; }
    }

    public void impulse(int part, float ix, float iy, float iz, float dt) {
        float k = invMass[part] * dt;
        px[part] -= ix * k; py[part] -= iy * k; pz[part] -= iz * k;
        sleeping = false; stillTicks = 0;
    }

    /** Pushes the whole body (explosions, trample) and knocks balance. */
    public void impulseAll(float ix, float iy, float iz, float dt, float balanceLoss) {
        for (int i = 0; i < rig.n; i++) {
            float k = dt / totalMass * rig.n;
            px[i] -= ix * k; py[i] -= iy * k; pz[i] -= iz * k;
        }
        balance = Math.max(0, balance - balanceLoss); sleeping = false; stillTicks = 0;
    }

    /**
     * One physics step. iterations: constraint passes (2 near, 1 mid). Returns false if the body is asleep.
     */
    public boolean step(float dt, Terrain terrain, int iterations, float balanceRecovery) {
        if (sleeping) return false;
        int n = rig.n;
        float muscle = limp ? 0f : down ? POSE_K * 0.02f : POSE_K * (0.1f + 0.9f * balance * balance) * (1 + .6f * Math.min(1.5f, stance));
        float g = GRAVITY * dt * dt;
        float maxMove = 0;
        for (int i = 0; i < n; i++) {
            float vx = (x[i] - px[i]) * DAMPING, vy = (y[i] - py[i]) * DAMPING, vz = (z[i] - pz[i]) * DAMPING;
            px[i] = x[i]; py[i] = y[i]; pz[i] = z[i];
            x[i] += vx; y[i] += vy + g; z[i] += vz;
            float m = muscle * rig.poseStrength[i];
            if (m > 0) {
                x[i] += (tx[i] - x[i]) * m; z[i] += (tz[i] - z[i]) * m;
                // muscles fight gravity more than they move horizontally: the TABS "upright" feel
                y[i] += (ty[i] - y[i]) * Math.min(1f, m * 1.6f);
            }
        }
        for (int it = 0; it < iterations; it++) {
            for (int c = 0; c < rest.length; c++) {
                int a = rig.ca[c], b = rig.cb[c];
                float dx = x[b] - x[a], dy = y[b] - y[a], dz = z[b] - z[a];
                float d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < 1e-9f) continue;
                float d = (float) Math.sqrt(d2), w = invMass[a] + invMass[b];
                float corr = (d - rest[c]) / (d * w);
                x[a] += dx * corr * invMass[a]; y[a] += dy * corr * invMass[a]; z[a] += dz * corr * invMass[a];
                x[b] -= dx * corr * invMass[b]; y[b] -= dy * corr * invMass[b]; z[b] -= dz * corr * invMass[b];
            }
            for (int i = 0; i < n; i++) {
                if (it == 0) {   // water: drag and a little buoyancy below the surface
                    float wd = terrain.waterDepth(x[i], z[i]);
                    if (wd > 0 && y[i] < terrain.groundY(x[i], z[i]) + wd) {
                        px[i] = x[i] - (x[i] - px[i]) * .8f; py[i] = y[i] - (y[i] - py[i]) * .8f; pz[i] = z[i] - (z[i] - pz[i]) * .8f;
                        y[i] += .35f * -GRAVITY * dt * dt;
                    }
                }
                float floor = terrain.groundY(x[i], z[i]) + rig.radius[i] * scale;
                if (y[i] < floor) {
                    y[i] = floor;
                    // ground friction: kill most sliding
                    px[i] = x[i] - (x[i] - px[i]) * 0.6f; pz[i] = z[i] - (z[i] - pz[i]) * 0.6f;
                    if (py[i] < floor) py[i] = floor;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            float mv = Math.abs(x[i] - px[i]) + Math.abs(y[i] - py[i]) + Math.abs(z[i] - pz[i]);
            if (mv > maxMove) maxMove = mv;
        }
        if (!limp) balance = Math.min(1f, balance + balanceRecovery * dt);
        if (limp && maxMove < 0.002f) { if (++stillTicks > 20) sleeping = true; } else stillTicks = 0;
        return true;
    }

    public float hipX() { return x[0]; }
    public float hipY() { return y[0]; }
    public float hipZ() { return z[0]; }
    /** How far the body has drifted from its pose (used to decide when a knocked unit is down). */
    public float poseError() {
        float dx = x[0] - tx[0], dy = y[0] - ty[0], dz = z[0] - tz[0];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
