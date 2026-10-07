package dev.beyondtabs.engine;

/**
 * Particle skeleton template for an active ragdoll. Layout follows the TABS humanoid rig (hip, torso, neck/head, arms,
 * elbows, hands, legs, knees, feet). At runtime the rest positions and masses are replaced by the ones read from the
 * player's TABS install; the defaults below are generic human proportions so the engine runs without TABS (benches).
 */
public final class Rig {
    public static final int HIP = 0, TORSO = 1, NECK = 2, HEAD = 3, SHOULDER_L = 4, ELBOW_L = 5, HAND_L = 6,
            SHOULDER_R = 7, ELBOW_R = 8, HAND_R = 9, LEG_L = 10, KNEE_L = 11, FOOT_L = 12, LEG_R = 13, KNEE_R = 14, FOOT_R = 15;
    public static final int HUMANOID_PARTICLES = 16;
    public static final String[] NAMES = {"Hip", "Torso", "Neck", "Head", "Arm_Left", "Elbow_Left", "Hand_Left", "Arm_Right",
            "Elbow_Right", "Hand_Right", "Leg_Left", "Knee_Left", "Foot_Left", "Leg_Right", "Knee_Right", "Foot_Right"};

    public final int n;
    public final float[] rx, ry, rz;     // rest positions, unit space (y up, +z forward), feet at y=0
    public final float[] mass;
    public final float[] radius;
    public final int[] ca, cb;            // distance constraints (bones)
    public final float[] poseStrength;    // how hard muscles pull each particle to its pose (TABS: upper body stronger)
    /** Reduced rig for mid LOD: indices into the full rig that are simulated; others follow the pose. */
    public final int[] midLod;

    public Rig(float[] rx, float[] ry, float[] rz, float[] mass, float[] radius, int[] ca, int[] cb, float[] pose, int[] midLod) {
        this.n = rx.length; this.rx = rx; this.ry = ry; this.rz = rz; this.mass = mass; this.radius = radius;
        this.ca = ca; this.cb = cb; this.poseStrength = pose; this.midLod = midLod;
    }

    public static Rig humanoidDefault() {
        float[] x = {0, 0, 0, 0, -.15f, -.48f, -.85f, .15f, .48f, .85f, -.08f, -.16f, -.22f, .08f, .16f, .22f};
        float[] y = {1.08f, 1.36f, 1.70f, 1.93f, 1.62f, 1.60f, 1.59f, 1.62f, 1.60f, 1.59f, 0.99f, 0.54f, 0.06f, 0.99f, 0.54f, 0.06f};
        float[] z = new float[16];
        // arms hang at the sides in the pose (TABS units fight with arms forward; the attack animation moves them)
        x[5] = -.20f; y[5] = 1.32f; z[5] = .05f; x[6] = -.22f; y[6] = 1.05f; z[6] = .15f;
        x[8] = .20f; y[8] = 1.32f; z[8] = .05f; x[9] = .22f; y[9] = 1.05f; z[9] = .15f;
        float[] m = {25, 25, 5, 10, 5, 7, 2, 5, 7, 2, 12, 5, 3, 12, 5, 3};
        float[] r = {.18f, .2f, .08f, .14f, .07f, .06f, .06f, .07f, .06f, .06f, .09f, .07f, .07f, .09f, .07f, .07f};
        int[][] bones = {{0, 1}, {1, 2}, {2, 3}, {1, 4}, {4, 5}, {5, 6}, {1, 7}, {7, 8}, {8, 9}, {0, 10}, {10, 11}, {11, 12},
                {0, 13}, {13, 14}, {14, 15}, {4, 7}, {10, 13}, {0, 4}, {0, 7}, {1, 10}, {1, 13}, {2, 4}, {2, 7}};
        int[] a = new int[bones.length], b = new int[bones.length];
        for (int i = 0; i < bones.length; i++) { a[i] = bones[i][0]; b[i] = bones[i][1]; }
        float[] pose = {1f, 1f, .9f, .9f, .7f, .5f, .5f, .7f, .5f, .5f, .8f, .6f, .9f, .8f, .6f, .9f};
        return new Rig(x, y, z, m, r, a, b, pose, new int[]{HIP, TORSO, HEAD, FOOT_L, FOOT_R});
    }

    /** Simple rigs for non-humanoid bodies (mounts, siege, vehicles): a chain hull of n particles. */
    public static Rig blob(int particles, float length, float height, float totalMass) {
        int n = Math.max(2, particles);
        float[] x = new float[n], y = new float[n], z = new float[n], m = new float[n], r = new float[n], p = new float[n];
        for (int i = 0; i < n; i++) {
            float t = n == 1 ? 0 : (float) i / (n - 1) - .5f;
            x[i] = (i % 2 == 0 ? -1 : 1) * .25f * length; z[i] = t * length; y[i] = height * (i % 3 == 0 ? .4f : .8f);
            m[i] = totalMass / n; r[i] = .25f * height; p[i] = .9f;
        }
        int bones = 0; int[] a = new int[n * 3], b = new int[n * 3];
        for (int i = 0; i < n; i++) for (int k = 1; k <= 3 && i + k < n; k++) { a[bones] = i; b[bones] = i + k; bones++; }
        return new Rig(x, y, z, m, r, java.util.Arrays.copyOf(a, bones), java.util.Arrays.copyOf(b, bones), p,
                new int[]{0, n / 2, n - 1});
    }
}
