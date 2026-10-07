package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Primitive emitter (QUADS, position + colour) with baked lighting:
 * <ul>
 * <li>round shapes (columns, limbs, domes, heads) are smooth shaded with per-vertex normals; boxes, roofs and blades
 * keep crisp faceted edges;</li>
 * <li>a sun + sky/ground hemisphere light with cool-tinted shadow sides ({@link Look#shade});</li>
 * <li>contact shading towards the ground so models sit in the terrain;</li>
 * <li>an optional ink outline (inverted hull: the primitive is emitted again, inflated, wound inside-out and drawn
 * with back-face culling, which leaves a crisp dark rim around every silhouette).</li>
 * </ul>
 * Faces are wound counter-clockwise seen from outside so they can be drawn with culling. Primitives are given in a
 * local frame (origin, yaw, scale); every primitive call is one "part", which lets buildings rise part by part.
 */
final class Mesh {
    VertexConsumer vc;          // null = count / measure only
    VertexConsumer vt;          // where see-through quads go (shadows, smoke, blueprints); null = vc
    Matrix4f m;
    float ox, oy, oz, cos = 1, sin = 0, sc = 1;
    float alpha = 1; int tint = -1; float tintAmt;
    float groundY = Float.NaN, shadeH = 1.2f, shadeAmt = .25f;
    /** Outline width in blocks (0 = none) and colour. */
    float ow; int outline = Look.INK;
    /** Emissive: ignores lighting (windows, lanterns, team lights, fire). */
    boolean glow;
    int part, from, limit = Integer.MAX_VALUE;
    float maxY = -1e9f;

    private final float[] wx = new float[4], wy = new float[4], wz = new float[4];
    private final float[] nx = new float[4], ny = new float[4], nz = new float[4];
    private boolean ol;   // emitting the outline hull right now

    Mesh to(VertexConsumer vc, Matrix4f m) { this.vc = vc; this.m = m; return this; }

    Mesh frame(float x, float y, float z, float yawRad, float scale) {
        ox = x; oy = y; oz = z; cos = (float) Math.cos(yawRad); sin = (float) Math.sin(yawRad); sc = scale; return this;
    }

    Mesh reset() {
        part = 0; from = 0; limit = Integer.MAX_VALUE; alpha = 1; tint = -1; tintAmt = 0; maxY = -1e9f; groundY = Float.NaN;
        ow = 0; glow = false; outline = Look.INK;
        return frame(0, 0, 0, 0, 1);
    }

    /** Darken vertices close to this ground height (world y), fading out over `h` blocks. */
    Mesh ground(float y, float h, float amt) { groundY = y; shadeH = h; shadeAmt = amt; return this; }

    boolean part() { int i = part++; return vc != null && i >= from && i < limit; }
    private boolean inked() { return ow > 0 && alpha >= .99f && !glow; }

    // ---- frame transform ----
    float X(float x, float z) { return ox + sc * (x * cos + z * sin); }
    float Y(float y) { return oy + sc * y; }
    float Z(float x, float z) { return oz + sc * (-x * sin + z * cos); }
    float NX(float x, float z) { return x * cos + z * sin; }
    float NZ(float x, float z) { return -x * sin + z * cos; }

    void setW(int i, float x, float y, float z) { wx[i] = x; wy[i] = y; wz[i] = z; }
    void setL(int i, float x, float y, float z) { wx[i] = X(x, z); wy[i] = Y(y); wz[i] = Z(x, z); }
    void setN(int i, float x, float y, float z) { float l = (float) Math.sqrt(x * x + y * y + z * z); if (l < 1e-9f) l = 1; nx[i] = x / l; ny[i] = y / l; nz[i] = z / l; }

    /**
     * Emits the quad in wx..wz. (cx,cy,cz) is a point inside the solid, used to orient the face outward.
     * smooth: use the per-vertex normals in nx..nz; twoSided: thin surfaces (flags, sails) get both windings.
     */
    void emit(int color, float cx, float cy, float cz, boolean smooth, boolean twoSided) {
        if (!ol) for (int i = 0; i < 4; i++) if (wy[i] > maxY) maxY = wy[i];
        if (vc == null) return;
        float ax = wx[1] - wx[0], ay = wy[1] - wy[0], az = wz[1] - wz[0];
        float bx = wx[2] - wx[0], by = wy[2] - wy[0], bz = wz[2] - wz[0];
        float fx = ay * bz - az * by, fy = az * bx - ax * bz, fz = ax * by - ay * bx;
        float l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l < 1e-10f) {
            ax = wx[2] - wx[0]; ay = wy[2] - wy[0]; az = wz[2] - wz[0]; bx = wx[3] - wx[0]; by = wy[3] - wy[0]; bz = wz[3] - wz[0];
            fx = ay * bz - az * by; fy = az * bx - ax * bz; fz = ax * by - ay * bx;
            l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (l < 1e-10f) return;
        }
        fx /= l; fy /= l; fz /= l;
        float ccx = (wx[0] + wx[1] + wx[2] + wx[3]) * .25f, ccy = (wy[0] + wy[1] + wy[2] + wy[3]) * .25f, ccz = (wz[0] + wz[1] + wz[2] + wz[3]) * .25f;
        boolean reversed = fx * (ccx - cx) + fy * (ccy - cy) + fz * (ccz - cz) < 0;   // winding points inward
        if (reversed) { fx = -fx; fy = -fy; fz = -fz; }
        if (ol) {   // inside-out hull, flat ink colour
            float r = Look.r(outline), g = Look.g(outline), b = Look.b(outline);
            if (reversed) for (int i = 0; i < 4; i++) put(i, r, g, b, 1);
            else for (int i = 3; i >= 0; i--) put(i, r, g, b, 1);
            return;
        }
        int c = tint >= 0 ? Look.mix(color, tint, tintAmt) : color;
        float[] rgb = new float[12];
        for (int i = 0; i < 4; i++) {
            float vx = fx, vy = fy, vz = fz;
            if (smooth) { vx = nx[i]; vy = ny[i]; vz = nz[i]; if (vx * fx + vy * fy + vz * fz < 0) { vx = -vx; vy = -vy; vz = -vz; } }
            float s = 1;
            if (!Float.isNaN(groundY)) { float t = 1 - (wy[i] - groundY) / shadeH; if (t > 0) s = 1 - shadeAmt * Math.min(1, t); }
            int sh = glow ? Look.lighter(c, .12f) : Look.shade(c, vx, vy, vz, s);
            rgb[i * 3] = Look.r(sh); rgb[i * 3 + 1] = Look.g(sh); rgb[i * 3 + 2] = Look.b(sh);
        }
        if (!reversed || twoSided) for (int i = 0; i < 4; i++) put(i, rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2], alpha);
        if (reversed || twoSided) for (int i = 3; i >= 0; i--) put(i, rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2], alpha);
    }

    private void put(int i, float r, float g, float b, float a) {
        (a < .99f && vt != null ? vt : vc).vertex(m, wx[i], wy[i], wz[i]).color(r, g, b, a).endVertex();
    }

    // ================================================================== local-frame primitives

    /** Axis-aligned box in the local frame (crisp edges). */
    Mesh box(float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        if (!part()) { track(y1); return this; }
        if (inked()) { float w = ow / sc; ol = true; boxImpl(x0 - w, y0 - w, z0 - w, x1 + w, y1 + w, z1 + w, c); ol = false; }
        boxImpl(x0, y0, z0, x1, y1, z1, c);
        return this;
    }

    private void boxImpl(float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        float cx = X((x0 + x1) / 2, (z0 + z1) / 2), cy = Y((y0 + y1) / 2), cz = Z((x0 + x1) / 2, (z0 + z1) / 2);
        setL(0, x0, y0, z0); setL(1, x1, y0, z0); setL(2, x1, y1, z0); setL(3, x0, y1, z0); emit(c, cx, cy, cz, false, false);
        setL(0, x0, y0, z1); setL(1, x1, y0, z1); setL(2, x1, y1, z1); setL(3, x0, y1, z1); emit(c, cx, cy, cz, false, false);
        setL(0, x0, y0, z0); setL(1, x0, y0, z1); setL(2, x0, y1, z1); setL(3, x0, y1, z0); emit(c, cx, cy, cz, false, false);
        setL(0, x1, y0, z0); setL(1, x1, y0, z1); setL(2, x1, y1, z1); setL(3, x1, y1, z0); emit(c, cx, cy, cz, false, false);
        setL(0, x0, y1, z0); setL(1, x1, y1, z0); setL(2, x1, y1, z1); setL(3, x0, y1, z1); emit(c, cx, cy, cz, false, false);
        setL(0, x0, y0, z0); setL(1, x1, y0, z0); setL(2, x1, y0, z1); setL(3, x0, y0, z1); emit(c, cx, cy, cz, false, false);
    }

    /** Vertical (optionally tapered) n-gon column in the local frame. */
    Mesh cyl(float x, float z, float y0, float y1, float r0, float r1, int sides, int c) { return prismL(x, y0, z, x, y1, z, r0, r1, sides, c); }

    /** Cone / spire. */
    Mesh cone(float x, float z, float y0, float y1, float r, int sides, int c) { return prismL(x, y0, z, x, y1, z, r, 0, sides, c); }

    /** Tapered n-gon prism between two local points. */
    Mesh prismL(float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int sides, int c) {
        return prismW(X(ax, az), Y(ay), Z(ax, az), X(bx, bz), Y(by), Z(bx, bz), ra * sc, rb * sc, sides, c, 0, 0, 0, 1);
    }

    /** Ellipsoid in the local frame. */
    Mesh ball(float x, float y, float z, float rx, float ry, float rz, int slices, int stacks, int c) {
        return ballW(X(x, z), Y(y), Z(x, z), rx * sc, ry * sc, rz * sc, slices, stacks, c, cos, sin);
    }

    /** Gable roof: eaves at y, ridge h above; ridge along x if alongX, else along z. Gable ends get `end` colour. */
    Mesh gable(float x0, float z0, float x1, float z1, float y, float h, boolean alongX, float over, int roof, int end) {
        if (!part()) { track(y + h); return this; }
        if (inked()) { float w = ow / sc; ol = true; gableImpl(x0 - w, z0 - w, x1 + w, z1 + w, y - w, h + w * 2, alongX, over, roof, end); ol = false; }
        gableImpl(x0, z0, x1, z1, y, h, alongX, over, roof, end);
        return this;
    }

    private void gableImpl(float x0, float z0, float x1, float z1, float y, float h, boolean alongX, float over, int roof, int end) {
        float cx = X((x0 + x1) / 2, (z0 + z1) / 2), cy = Y(y + h / 3), cz = Z((x0 + x1) / 2, (z0 + z1) / 2);
        int under = Look.darker(roof, .35f);
        if (alongX) {
            float zm = (z0 + z1) / 2, xa = x0 - over * .5f, xb = x1 + over * .5f, ze = (z1 - z0) / 2 + over, hy = h * (1 - over / Math.max(.01f, ze));
            setL(0, xa, y, z0 - over); setL(1, xb, y, z0 - over); setL(2, xb, y + h, zm); setL(3, xa, y + h, zm); emit(roof, cx, cy, cz, false, false);
            setL(0, xa, y, z1 + over); setL(1, xb, y, z1 + over); setL(2, xb, y + h, zm); setL(3, xa, y + h, zm); emit(roof, cx, cy, cz, false, false);
            setL(0, x0, y, z0); setL(1, x0, y, z1); setL(2, x0, y + hy, zm); setL(3, x0, y + h * .999f, zm); emit(end, cx, cy, cz, false, false);
            setL(0, x1, y, z0); setL(1, x1, y, z1); setL(2, x1, y + hy, zm); setL(3, x1, y + h * .999f, zm); emit(end, cx, cy, cz, false, false);
            setL(0, xa, y, z0 - over); setL(1, xb, y, z0 - over); setL(2, xb, y, z1 + over); setL(3, xa, y, z1 + over); emit(under, cx, cy, cz, false, false);
            // closing the overhang ends so the solid is watertight for culling
            setL(0, xa, y, z0 - over); setL(1, xa, y, z1 + over); setL(2, xa, y + h, zm); setL(3, xa, y + h, zm); emit(roof, cx, cy, cz, false, false);
            setL(0, xb, y, z0 - over); setL(1, xb, y, z1 + over); setL(2, xb, y + h, zm); setL(3, xb, y + h, zm); emit(roof, cx, cy, cz, false, false);
        } else {
            float xm = (x0 + x1) / 2, za = z0 - over * .5f, zb = z1 + over * .5f;
            setL(0, x0 - over, y, za); setL(1, x0 - over, y, zb); setL(2, xm, y + h, zb); setL(3, xm, y + h, za); emit(roof, cx, cy, cz, false, false);
            setL(0, x1 + over, y, za); setL(1, x1 + over, y, zb); setL(2, xm, y + h, zb); setL(3, xm, y + h, za); emit(roof, cx, cy, cz, false, false);
            setL(0, x0 - over, y, za); setL(1, x1 + over, y, za); setL(2, xm, y + h, za); setL(3, xm, y + h, za); emit(roof, cx, cy, cz, false, false);
            setL(0, x0 - over, y, zb); setL(1, x1 + over, y, zb); setL(2, xm, y + h, zb); setL(3, xm, y + h, zb); emit(roof, cx, cy, cz, false, false);
            setL(0, x0, y, z0); setL(1, x1, y, z0); setL(2, xm, y + h * .9f, z0); setL(3, xm, y + h * .9f, z0); emit(end, cx, cy, cz, false, false);
            setL(0, x0, y, z1); setL(1, x1, y, z1); setL(2, xm, y + h * .9f, z1); setL(3, xm, y + h * .9f, z1); emit(end, cx, cy, cz, false, false);
            setL(0, x0 - over, y, za); setL(1, x1 + over, y, za); setL(2, x1 + over, y, zb); setL(3, x0 - over, y, zb); emit(under, cx, cy, cz, false, false);
        }
    }

    /** Hipped roof / frustum: base rectangle at y (plus overhang) rising h to a top inset by `inset` on every side. */
    Mesh hip(float x0, float z0, float x1, float z1, float y, float h, float over, float inset, int c) {
        if (!part()) { track(y + h); return this; }
        if (inked()) { float w = ow / sc; ol = true; hipImpl(x0 - w, z0 - w, x1 + w, z1 + w, y - w, h + w * 2, over, inset, c); ol = false; }
        hipImpl(x0, z0, x1, z1, y, h, over, inset, c);
        return this;
    }

    private void hipImpl(float x0, float z0, float x1, float z1, float y, float h, float over, float inset, int c) {
        float a0 = x0 - over, b0 = z0 - over, a1 = x1 + over, b1 = z1 + over;
        float ti = Math.min(inset, Math.min((a1 - a0) / 2, (b1 - b0) / 2) - .001f);
        float t0 = a0 + ti, u0 = b0 + ti, t1 = a1 - ti, u1 = b1 - ti, yt = y + h;
        float cx = X((x0 + x1) / 2, (z0 + z1) / 2), cy = Y(y + h * .3f), cz = Z((x0 + x1) / 2, (z0 + z1) / 2);
        setL(0, a0, y, b0); setL(1, a1, y, b0); setL(2, t1, yt, u0); setL(3, t0, yt, u0); emit(c, cx, cy, cz, false, false);
        setL(0, a0, y, b1); setL(1, a1, y, b1); setL(2, t1, yt, u1); setL(3, t0, yt, u1); emit(c, cx, cy, cz, false, false);
        setL(0, a0, y, b0); setL(1, a0, y, b1); setL(2, t0, yt, u1); setL(3, t0, yt, u0); emit(c, cx, cy, cz, false, false);
        setL(0, a1, y, b0); setL(1, a1, y, b1); setL(2, t1, yt, u1); setL(3, t1, yt, u0); emit(c, cx, cy, cz, false, false);
        setL(0, t0, yt, u0); setL(1, t1, yt, u0); setL(2, t1, yt, u1); setL(3, t0, yt, u1); emit(c, cx, Y(y), cz, false, false);
        setL(0, a0, y, b0); setL(1, a1, y, b0); setL(2, a1, y, b1); setL(3, a0, y, b1); emit(Look.darker(c, .35f), cx, Y(y + h * .5f), cz, false, false);
    }

    /** Flat horizontal n-gon (pads, shadows, water); faces up. */
    Mesh disc(float x, float y, float z, float r, int sides, int c) {
        if (!part()) return this;
        float cx = X(x, z), cy = Y(y), cz = Z(x, z);
        for (int i = 0; i < sides; i += 2) {
            double a0 = i * 2 * Math.PI / sides, a1 = (i + 1) * 2 * Math.PI / sides, a2 = (i + 2) * 2 * Math.PI / sides;
            setW(0, cx, cy, cz);
            setL(1, x + (float) Math.cos(a0) * r, y, z + (float) Math.sin(a0) * r);
            setL(2, x + (float) Math.cos(a1) * r, y, z + (float) Math.sin(a1) * r);
            setL(3, x + (float) Math.cos(a2) * r, y, z + (float) Math.sin(a2) * r);
            emit(c, cx, cy - 1, cz, false, false);
        }
        return this;
    }

    /** A flat ring on the ground (selection / team markers). */
    Mesh ring(float x, float y, float z, float r0, float r1, int sides, int c) {
        if (!part()) return this;
        float cx = X(x, z), cy = Y(y), cz = Z(x, z);
        for (int i = 0; i < sides; i++) {
            double a0 = i * 2 * Math.PI / sides, a1 = (i + 1) * 2 * Math.PI / sides;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            setL(0, x + c0 * r0, y, z + s0 * r0); setL(1, x + c1 * r0, y, z + s1 * r0); setL(2, x + c1 * r1, y, z + s1 * r1); setL(3, x + c0 * r1, y, z + s0 * r1);
            emit(c, cx, cy - 1, cz, false, false);
        }
        return this;
    }

    /** A cloth rectangle (flag, banner) hanging from (x,y,z) along local x, rippling with `wave`. Two-sided. */
    Mesh flag(float x, float y, float z, float w, float h, float wave, int c) {
        if (!part()) return this;
        int seg = 5;
        for (int i = 0; i < seg; i++) {
            float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
            float d0 = (float) Math.sin(wave + u0 * 4) * .16f * u0 * w, d1 = (float) Math.sin(wave + u1 * 4) * .16f * u1 * w;
            setL(0, x + u0 * w, y, z + d0); setL(1, x + u1 * w, y, z + d1); setL(2, x + u1 * w, y - h + u1 * h * .1f, z + d1); setL(3, x + u0 * w, y - h + u0 * h * .1f, z + d0);
            float a = (float) Math.cos(wave + (u0 + u1) * 2) * .5f;
            for (int k = 0; k < 4; k++) setN(k, NX(-a, 1), 0, NZ(-a, 1));
            emit(c, X(x + w / 2, z - 1), Y(y - h / 2), Z(x + w / 2, z - 1), true, true);
        }
        return this;
    }

    /** A hanging banner on a wall: falls along -y from (x,y,z), facing +z (local). Two-sided, with a dark trim. */
    Mesh banner(float x, float y, float z, float w, float h, int c, int trim) {
        if (!part()) return this;
        setL(0, x - w / 2, y, z); setL(1, x + w / 2, y, z); setL(2, x + w / 2, y - h * .8f, z); setL(3, x - w / 2, y - h * .8f, z);
        emit(c, X(x, z - 1), Y(y), Z(x, z - 1), false, true);
        setL(0, x - w / 2, y - h * .8f, z); setL(1, x + w / 2, y - h * .8f, z); setL(2, x, y - h, z); setL(3, x - w / 2, y - h * .8f, z);
        emit(c, X(x, z - 1), Y(y), Z(x, z - 1), false, true);
        setL(0, x - w / 2 - .03f, y + .02f, z + .01f); setL(1, x + w / 2 + .03f, y + .02f, z + .01f); setL(2, x + w / 2 + .03f, y - .1f, z + .01f); setL(3, x - w / 2 - .03f, y - .1f, z + .01f);
        emit(trim, X(x, z - 1), Y(y), Z(x, z - 1), false, true);
        return this;
    }

    /** A sail: quad from the segment (x0,y0)-(x1,y1) in the local plane z, offset sideways by (px,py). Two-sided. */
    Mesh quadSail(float x0, float y0, float x1, float y1, float px, float py, float z, int c) {
        if (!part()) return this;
        setL(0, x0, y0, z); setL(1, x1, y1, z); setL(2, x1 + px, y1 + py, z); setL(3, x0 + px, y0 + py, z);
        emit(c, X((x0 + x1) / 2, z - 1), Y((y0 + y1) / 2), Z((x0 + x1) / 2, z - 1), false, true);
        return this;
    }

    // ================================================================== world-space primitives

    /**
     * Tapered n-gon prism between world points. `flat` squashes the cross-section along the hint's perpendicular.
     * Six or more sides are smooth shaded; fewer stay faceted (blades, planks, boots).
     */
    Mesh prismW(float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int sides, int c,
                float hx, float hy, float hz, float flat) {
        if (!part()) { trackW(Math.max(ay, by) + Math.max(ra, rb)); return this; }
        if (inked()) {
            float dx = bx - ax, dy = by - ay, dz = bz - az, l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l > 1e-5f) {
                float e = ow / l; ol = true;
                prismImpl(ax - dx * e, ay - dy * e, az - dz * e, bx + dx * e * (rb > 0 ? 1 : 2.5f), by + dy * e * (rb > 0 ? 1 : 2.5f), bz + dz * e * (rb > 0 ? 1 : 2.5f),
                        ra + ow, rb > 0 ? rb + ow : 0, sides, c, hx, hy, hz, flat, ow);
                ol = false;
            }
        }
        prismImpl(ax, ay, az, bx, by, bz, ra, rb, sides, c, hx, hy, hz, flat, 0);
        return this;
    }

    Mesh prismW(float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int sides, int c) {
        return prismW(ax, ay, az, bx, by, bz, ra, rb, sides, c, 0, 0, 0, 1);
    }

    private void prismImpl(float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int sides, int c,
                           float hx, float hy, float hz, float flat, float pad) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-5f) return;
        dx /= dl; dy /= dl; dz /= dl;
        float ux = hx, uy = hy, uz = hz;
        float hd = ux * dx + uy * dy + uz * dz; ux -= hd * dx; uy -= hd * dy; uz -= hd * dz;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul < 1e-4f) {
            if (Math.abs(dy) < .9f) { ux = -dz; uy = 0; uz = dx; } else { ux = 1; uy = 0; uz = 0; hd = ux * dx; ux -= hd * dx; uy -= hd * dy; uz -= hd * dz; }
            ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        }
        ux /= ul; uy /= ul; uz /= ul;
        float vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;
        float cx = (ax + bx) / 2, cy = (ay + by) / 2, cz = (az + bz) / 2;
        boolean smooth = sides >= 6;
        float fl = flat; float ef = pad > 0 && flat < 1 ? Math.min(1, flat + pad / Math.max(.01f, Math.max(ra, rb))) : flat;   // keep the ink rim even on flat blades
        fl = ef;
        float slope = (ra - rb) / dl;
        double off = sides == 4 ? Math.PI / 4 : 0;
        for (int i = 0; i < sides; i++) {
            double t0 = off + i * 2 * Math.PI / sides, t1 = off + (i + 1) * 2 * Math.PI / sides;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0) * fl, c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1) * fl;
            float p0x = c0 * ux + s0 * vx, p0y = c0 * uy + s0 * vy, p0z = c0 * uz + s0 * vz;
            float p1x = c1 * ux + s1 * vx, p1y = c1 * uy + s1 * vy, p1z = c1 * uz + s1 * vz;
            setW(0, ax + p0x * ra, ay + p0y * ra, az + p0z * ra); setW(1, ax + p1x * ra, ay + p1y * ra, az + p1z * ra);
            setW(2, bx + p1x * rb, by + p1y * rb, bz + p1z * rb); setW(3, bx + p0x * rb, by + p0y * rb, bz + p0z * rb);
            if (smooth) {
                float q0c = (float) Math.cos(t0), q0s = (float) Math.sin(t0) / Math.max(.05f, fl), q1c = (float) Math.cos(t1), q1s = (float) Math.sin(t1) / Math.max(.05f, fl);
                float n0x = q0c * ux + q0s * vx + slope * dx, n0y = q0c * uy + q0s * vy + slope * dy, n0z = q0c * uz + q0s * vz + slope * dz;
                float n1x = q1c * ux + q1s * vx + slope * dx, n1y = q1c * uy + q1s * vy + slope * dy, n1z = q1c * uz + q1s * vz + slope * dz;
                setN(0, n0x, n0y, n0z); setN(1, n1x, n1y, n1z); setN(2, n1x, n1y, n1z); setN(3, n0x, n0y, n0z);
            }
            emit(c, cx, cy, cz, smooth, false);
            if (ra > 0 && i % 2 == 0) {   // caps: fans of quads, flat shaded
                double t2 = off + (i + 2) * 2 * Math.PI / sides;
                float c2 = (float) Math.cos(t2), s2 = (float) Math.sin(t2) * fl;
                setW(0, ax, ay, az); setW(1, ax + p0x * ra, ay + p0y * ra, az + p0z * ra); setW(2, ax + p1x * ra, ay + p1y * ra, az + p1z * ra);
                setW(3, ax + (c2 * ux + s2 * vx) * ra, ay + (c2 * uy + s2 * vy) * ra, az + (c2 * uz + s2 * vz) * ra);
                emit(c, cx, cy, cz, false, false);
            }
            if (rb > 0 && i % 2 == 0) {
                double t2 = off + (i + 2) * 2 * Math.PI / sides;
                float c2 = (float) Math.cos(t2), s2 = (float) Math.sin(t2) * fl;
                setW(0, bx, by, bz); setW(1, bx + p0x * rb, by + p0y * rb, bz + p0z * rb); setW(2, bx + p1x * rb, by + p1y * rb, bz + p1z * rb);
                setW(3, bx + (c2 * ux + s2 * vx) * rb, by + (c2 * uy + s2 * vy) * rb, bz + (c2 * uz + s2 * vz) * rb);
                emit(c, cx, cy, cz, false, false);
            }
        }
    }

    /** Ellipsoid in world space, yawed by (cy, sy). Smooth shaded. */
    Mesh ballW(float x, float y, float z, float rx, float ry, float rz, int slices, int stacks, int c, float cy, float sy) {
        if (!part()) { trackW(y + ry); return this; }
        if (inked()) { ol = true; ballImpl(x, y, z, rx + ow, ry + ow, rz + ow, slices, stacks, c, cy, sy); ol = false; }
        ballImpl(x, y, z, rx, ry, rz, slices, stacks, c, cy, sy);
        return this;
    }

    private void ballImpl(float x, float y, float z, float rx, float ry, float rz, int slices, int stacks, int c, float cy, float sy) {
        boolean smooth = slices >= 5;
        for (int j = 0; j < stacks; j++) {
            double p0 = Math.PI * j / stacks - Math.PI / 2, p1 = Math.PI * (j + 1) / stacks - Math.PI / 2;
            float y0 = (float) Math.sin(p0), y1 = (float) Math.sin(p1), r0 = (float) Math.cos(p0), r1 = (float) Math.cos(p1);
            for (int i = 0; i < slices; i++) {
                double t0 = i * 2 * Math.PI / slices, t1 = (i + 1) * 2 * Math.PI / slices;
                float a0 = (float) Math.cos(t0), b0 = (float) Math.sin(t0), a1 = (float) Math.cos(t1), b1 = (float) Math.sin(t1);
                pt(0, x, y, z, a0 * r0, y0, b0 * r0, rx, ry, rz, cy, sy); pt(1, x, y, z, a1 * r0, y0, b1 * r0, rx, ry, rz, cy, sy);
                pt(2, x, y, z, a1 * r1, y1, b1 * r1, rx, ry, rz, cy, sy); pt(3, x, y, z, a0 * r1, y1, b0 * r1, rx, ry, rz, cy, sy);
                emit(c, x, y, z, smooth, false);
            }
        }
    }

    /** Sets vertex i of a unit-sphere direction (ux,uy,uz) scaled by the radii, with its ellipsoid normal. */
    private void pt(int i, float x, float y, float z, float ux, float uy, float uz, float rx, float ry, float rz, float cy, float sy) {
        float lx = ux * rx, ly = uy * ry, lz = uz * rz;
        setW(i, x + lx * cy + lz * sy, y + ly, z - lx * sy + lz * cy);
        float qx = ux / Math.max(1e-4f, rx), qy = uy / Math.max(1e-4f, ry), qz = uz / Math.max(1e-4f, rz);
        setN(i, qx * cy + qz * sy, qy, -qx * sy + qz * cy);
    }

    /** Flat world-space quad from four points (shadows, wings, capes). Two-sided. */
    Mesh quad4(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int c) {
        if (!part()) return this;
        setW(0, x0, y0, z0); setW(1, x1, y1, z1); setW(2, x2, y2, z2); setW(3, x3, y3, z3);
        emit(c, (x0 + x2) / 2, (y0 + y2) / 2 - 1, (z0 + z2) / 2, false, true);
        return this;
    }

    private void track(float localY) { float y = Y(localY); if (y > maxY) maxY = y; }
    private void trackW(float y) { if (y > maxY) maxY = y; }
}
