package dev.beyondtabs.mod.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Low-poly primitive emitter (QUADS, position + colour). Every face is flat shaded through {@link Look#light}, with a
 * soft darkening towards the ground ("contact shade") so models sit in the terrain instead of floating on it.
 * Primitives are given in a local frame (origin, yaw, scale); every primitive call is one "part", which lets buildings
 * rise part by part while under construction.
 */
final class Mesh {
    VertexConsumer vc;          // null = count / measure only
    Matrix4f m;
    float ox, oy, oz, cos = 1, sin = 0, sc = 1;
    float alpha = 1; int tint = -1; float tintAmt;
    float groundY = Float.NaN, shadeH = 1.2f, shadeAmt = .22f;
    int part, from, limit = Integer.MAX_VALUE;
    float maxY = -1e9f;

    private final float[] wx = new float[4], wy = new float[4], wz = new float[4];

    Mesh to(VertexConsumer vc, Matrix4f m) { this.vc = vc; this.m = m; return this; }

    Mesh frame(float x, float y, float z, float yawRad, float scale) {
        ox = x; oy = y; oz = z; cos = (float) Math.cos(yawRad); sin = (float) Math.sin(yawRad); sc = scale; return this;
    }

    Mesh reset() {
        part = 0; from = 0; limit = Integer.MAX_VALUE; alpha = 1; tint = -1; tintAmt = 0; maxY = -1e9f; groundY = Float.NaN;
        return frame(0, 0, 0, 0, 1);
    }

    /** Darken vertices close to this ground height (world y), fading out over `h` blocks. */
    Mesh ground(float y, float h, float amt) { groundY = y; shadeH = h; shadeAmt = amt; return this; }

    boolean part() { int i = part++; return vc != null && i >= from && i < limit; }

    // ---- frame transform ----
    float X(float x, float z) { return ox + sc * (x * cos + z * sin); }
    float Y(float y) { return oy + sc * y; }
    float Z(float x, float z) { return oz + sc * (-x * sin + z * cos); }

    /** Emits one quad given in world space; normal is flipped to point away from (cx,cy,cz). */
    void quadW(int color, float cx, float cy, float cz) {
        for (int i = 0; i < 4; i++) if (wy[i] > maxY) maxY = wy[i];
        if (vc == null) return;
        float ax = wx[1] - wx[0], ay = wy[1] - wy[0], az = wz[1] - wz[0];
        float bx = wx[2] - wx[0], by = wy[2] - wy[0], bz = wz[2] - wz[0];
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-9f) {   // first triangle degenerate: use the other one
            bx = wx[3] - wx[0]; by = wy[3] - wy[0]; bz = wz[3] - wz[0];
            ax = wx[2] - wx[0]; ay = wy[2] - wy[0]; az = wz[2] - wz[0];
            nx = ay * bz - az * by; ny = az * bx - ax * bz; nz = ax * by - ay * bx;
            l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9f) return;
        }
        nx /= l; ny /= l; nz /= l;
        float fx = (wx[0] + wx[1] + wx[2] + wx[3]) * .25f - cx, fy = (wy[0] + wy[1] + wy[2] + wy[3]) * .25f - cy, fz = (wz[0] + wz[1] + wz[2] + wz[3]) * .25f - cz;
        if (nx * fx + ny * fy + nz * fz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        int c = tint >= 0 ? Look.mix(color, tint, tintAmt) : color;
        float lt = Look.light(nx, ny, nz), r = Look.r(c) * lt, g = Look.g(c) * lt, b = Look.b(c) * lt;
        for (int i = 0; i < 4; i++) {
            float s = 1;
            if (!Float.isNaN(groundY)) { float t = 1 - (wy[i] - groundY) / shadeH; if (t > 0) s = 1 - shadeAmt * Math.min(1, t); }
            vc.vertex(m, wx[i], wy[i], wz[i]).color(Math.min(1, r * s), Math.min(1, g * s), Math.min(1, b * s), alpha).endVertex();
        }
    }

    void setW(int i, float x, float y, float z) { wx[i] = x; wy[i] = y; wz[i] = z; }
    void setL(int i, float x, float y, float z) { wx[i] = X(x, z); wy[i] = Y(y); wz[i] = Z(x, z); }

    // ---- local-frame primitives ----

    /** Axis-aligned box in the local frame. */
    Mesh box(float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        if (!part()) { track(y1); return this; }
        float cx = X((x0 + x1) / 2, (z0 + z1) / 2), cy = Y((y0 + y1) / 2), cz = Z((x0 + x1) / 2, (z0 + z1) / 2);
        float[][] f = {
                {x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0},
                {x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
        for (float[] q : f) { for (int i = 0; i < 4; i++) setL(i, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]); quadW(c, cx, cy, cz); }
        return this;
    }

    /** Vertical (optionally tapered) n-gon column in the local frame. */
    Mesh cyl(float x, float z, float y0, float y1, float r0, float r1, int sides, int c) {
        return prismL(x, y0, z, x, y1, z, r0, r1, sides, c);
    }

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
        float cx, cy = Y(y + h / 3), cz;
        cx = X((x0 + x1) / 2, (z0 + z1) / 2); cz = Z((x0 + x1) / 2, (z0 + z1) / 2);
        if (alongX) {
            float zm = (z0 + z1) / 2, xa = x0 - over * .5f, xb = x1 + over * .5f;
            setL(0, xa, y, z0 - over); setL(1, xb, y, z0 - over); setL(2, xb, y + h, zm); setL(3, xa, y + h, zm); quadW(roof, cx, cy, cz);
            setL(0, xa, y, z1 + over); setL(1, xb, y, z1 + over); setL(2, xb, y + h, zm); setL(3, xa, y + h, zm); quadW(roof, cx, cy, cz);
            setL(0, x0, y, z0); setL(1, x0, y, z1); setL(2, x0, y + h * (1 - over / Math.max(.01f, (z1 - z0) / 2 + over)), zm); setL(3, x0, y + h * .999f, zm); quadW(end, cx, cy, cz);
            setL(0, x1, y, z0); setL(1, x1, y, z1); setL(2, x1, y + h * (1 - over / Math.max(.01f, (z1 - z0) / 2 + over)), zm); setL(3, x1, y + h * .999f, zm); quadW(end, cx, cy, cz);
            setL(0, xa, y, z0 - over); setL(1, xb, y, z0 - over); setL(2, xb, y, z1 + over); setL(3, xa, y, z1 + over); quadW(Look.darker(roof, .25f), cx, cy, cz);
        } else {
            float xm = (x0 + x1) / 2, za = z0 - over * .5f, zb = z1 + over * .5f;
            setL(0, x0 - over, y, za); setL(1, x0 - over, y, zb); setL(2, xm, y + h, zb); setL(3, xm, y + h, za); quadW(roof, cx, cy, cz);
            setL(0, x1 + over, y, za); setL(1, x1 + over, y, zb); setL(2, xm, y + h, zb); setL(3, xm, y + h, za); quadW(roof, cx, cy, cz);
            setL(0, x0, y, z0); setL(1, x1, y, z0); setL(2, xm, y + h * .999f, z0); setL(3, xm, y + h, z0); quadW(end, cx, cy, cz);
            setL(0, x0, y, z1); setL(1, x1, y, z1); setL(2, xm, y + h * .999f, z1); setL(3, xm, y + h, z1); quadW(end, cx, cy, cz);
            setL(0, x0 - over, y, za); setL(1, x1 + over, y, za); setL(2, x1 + over, y, zb); setL(3, x0 - over, y, zb); quadW(Look.darker(roof, .25f), cx, cy, cz);
        }
        return this;
    }

    /** Hipped roof / frustum: base rectangle at y (plus overhang) rising h to a top inset by `inset` on every side. */
    Mesh hip(float x0, float z0, float x1, float z1, float y, float h, float over, float inset, int c) {
        if (!part()) { track(y + h); return this; }
        float a0 = x0 - over, b0 = z0 - over, a1 = x1 + over, b1 = z1 + over;
        float ti = Math.min(inset, Math.min((a1 - a0) / 2, (b1 - b0) / 2));
        float t0 = a0 + ti, u0 = b0 + ti, t1 = a1 - ti, u1 = b1 - ti, yt = y + h;
        float cx = X((x0 + x1) / 2, (z0 + z1) / 2), cy = Y(y + h * .3f), cz = Z((x0 + x1) / 2, (z0 + z1) / 2);
        setL(0, a0, y, b0); setL(1, a1, y, b0); setL(2, t1, yt, u0); setL(3, t0, yt, u0); quadW(c, cx, cy, cz);
        setL(0, a0, y, b1); setL(1, a1, y, b1); setL(2, t1, yt, u1); setL(3, t0, yt, u1); quadW(c, cx, cy, cz);
        setL(0, a0, y, b0); setL(1, a0, y, b1); setL(2, t0, yt, u1); setL(3, t0, yt, u0); quadW(c, cx, cy, cz);
        setL(0, a1, y, b0); setL(1, a1, y, b1); setL(2, t1, yt, u1); setL(3, t1, yt, u0); quadW(c, cx, cy, cz);
        if (t1 - t0 > 1e-3f || u1 - u0 > 1e-3f) { setL(0, t0, yt, u0); setL(1, t1, yt, u0); setL(2, t1, yt, u1); setL(3, t0, yt, u1); quadW(c, cx, cy - 1, cz); }
        setL(0, a0, y, b0); setL(1, a1, y, b0); setL(2, a1, y, b1); setL(3, a0, y, b1); quadW(Look.darker(c, .25f), cx, cy + 1, cz);
        return this;
    }

    /** Flat horizontal n-gon (pads, shadows, water), single quad fan. */
    Mesh disc(float x, float y, float z, float r, int sides, int c) {
        if (!part()) return this;
        float cx = X(x, z), cy = Y(y), cz = Z(x, z);
        for (int i = 0; i < sides; i += 2) {
            double a0 = i * 2 * Math.PI / sides, a1 = (i + 1) * 2 * Math.PI / sides, a2 = (i + 2) * 2 * Math.PI / sides;
            setW(0, cx, cy, cz);
            setL(1, x + (float) Math.cos(a0) * r, y, z + (float) Math.sin(a0) * r);
            setL(2, x + (float) Math.cos(a1) * r, y, z + (float) Math.sin(a1) * r);
            setL(3, x + (float) Math.cos(a2) * r, y, z + (float) Math.sin(a2) * r);
            quadW(c, cx, cy - 1, cz);
        }
        return this;
    }

    /** A thin rectangle (flag, banner, sail) hanging from (x,y,z) along local x, rippling with `wave`. */
    Mesh flag(float x, float y, float z, float w, float h, float wave, int c) {
        if (!part()) return this;
        int seg = 3;
        for (int i = 0; i < seg; i++) {
            float u0 = (float) i / seg, u1 = (float) (i + 1) / seg;
            float d0 = (float) Math.sin(wave + u0 * 3) * .18f * u0 * w, d1 = (float) Math.sin(wave + u1 * 3) * .18f * u1 * w;
            setL(0, x + u0 * w, y, z + d0); setL(1, x + u1 * w, y, z + d1); setL(2, x + u1 * w, y - h, z + d1); setL(3, x + u0 * w, y - h, z + d0);
            float cx = X(x + w / 2, z), cz = Z(x + w / 2, z);
            quadW(c, cx + (float) Math.sin(wave) * .01f, Y(y - h / 2), cz + 1);
        }
        return this;
    }

    /** A sail: quad from the segment (x0,y0)-(x1,y1) in the local plane z, offset sideways by (px,py). */
    Mesh quadSail(float x0, float y0, float x1, float y1, float px, float py, float z, int c) {
        if (!part()) return this;
        setL(0, x0, y0, z); setL(1, x1, y1, z); setL(2, x1 + px, y1 + py, z); setL(3, x0 + px, y0 + py, z);
        quadW(c, X((x0 + x1) / 2, z - 1), Y((y0 + y1) / 2), Z((x0 + x1) / 2, z - 1));
        return this;
    }

    // ---- world-space primitives (units) ----

    /** Tapered n-gon prism between world points; `flat` squashes the cross-section along the hint's perpendicular. */
    Mesh prismW(float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int sides, int c,
                float hx, float hy, float hz, float flat) {
        if (!part()) { trackW(Math.max(ay, by)); return this; }
        float dx = bx - ax, dy = by - ay, dz = bz - az, dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-5f) return this;
        dx /= dl; dy /= dl; dz /= dl;
        // u: hint made perpendicular to the axis (or any perpendicular)
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
        double off = sides == 4 ? Math.PI / 4 : 0;
        for (int i = 0; i < sides; i++) {
            double t0 = off + i * 2 * Math.PI / sides, t1 = off + (i + 1) * 2 * Math.PI / sides;
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0) * flat, c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1) * flat;
            float p0x = c0 * ux + s0 * vx, p0y = c0 * uy + s0 * vy, p0z = c0 * uz + s0 * vz;
            float p1x = c1 * ux + s1 * vx, p1y = c1 * uy + s1 * vy, p1z = c1 * uz + s1 * vz;
            setW(0, ax + p0x * ra, ay + p0y * ra, az + p0z * ra); setW(1, ax + p1x * ra, ay + p1y * ra, az + p1z * ra);
            setW(2, bx + p1x * rb, by + p1y * rb, bz + p1z * rb); setW(3, bx + p0x * rb, by + p0y * rb, bz + p0z * rb);
            quadW(c, cx, cy, cz);
            if (ra > 0 && i % 2 == 0) {   // caps as fans of quads (two triangles' worth each)
                double t2 = off + (i + 2) * 2 * Math.PI / sides;
                float c2 = (float) Math.cos(t2), s2 = (float) Math.sin(t2) * flat;
                setW(0, ax, ay, az); setW(1, ax + p0x * ra, ay + p0y * ra, az + p0z * ra); setW(2, ax + p1x * ra, ay + p1y * ra, az + p1z * ra);
                setW(3, ax + (c2 * ux + s2 * vx) * ra, ay + (c2 * uy + s2 * vy) * ra, az + (c2 * uz + s2 * vz) * ra);
                quadW(c, cx, cy, cz);
            }
            if (rb > 0 && i % 2 == 0) {
                double t2 = off + (i + 2) * 2 * Math.PI / sides;
                float c2 = (float) Math.cos(t2), s2 = (float) Math.sin(t2) * flat;
                setW(0, bx, by, bz); setW(1, bx + p0x * rb, by + p0y * rb, bz + p0z * rb); setW(2, bx + p1x * rb, by + p1y * rb, bz + p1z * rb);
                setW(3, bx + (c2 * ux + s2 * vx) * rb, by + (c2 * uy + s2 * vy) * rb, bz + (c2 * uz + s2 * vz) * rb);
                quadW(c, cx, cy, cz);
            }
        }
        return this;
    }

    Mesh prismW(float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int sides, int c) {
        return prismW(ax, ay, az, bx, by, bz, ra, rb, sides, c, 0, 0, 0, 1);
    }

    /** Low-poly ellipsoid in world space, yawed by (cy, sy). */
    Mesh ballW(float x, float y, float z, float rx, float ry, float rz, int slices, int stacks, int c, float cy, float sy) {
        if (!part()) { trackW(y + ry); return this; }
        for (int j = 0; j < stacks; j++) {
            double p0 = Math.PI * j / stacks - Math.PI / 2, p1 = Math.PI * (j + 1) / stacks - Math.PI / 2;
            float y0 = (float) Math.sin(p0) * ry, y1 = (float) Math.sin(p1) * ry, r0 = (float) Math.cos(p0), r1 = (float) Math.cos(p1);
            for (int i = 0; i < slices; i++) {
                double t0 = i * 2 * Math.PI / slices, t1 = (i + 1) * 2 * Math.PI / slices;
                float a0 = (float) Math.cos(t0), b0 = (float) Math.sin(t0), a1 = (float) Math.cos(t1), b1 = (float) Math.sin(t1);
                pt(0, x, y, z, a0 * r0 * rx, y0, b0 * r0 * rz, cy, sy); pt(1, x, y, z, a1 * r0 * rx, y0, b1 * r0 * rz, cy, sy);
                pt(2, x, y, z, a1 * r1 * rx, y1, b1 * r1 * rz, cy, sy); pt(3, x, y, z, a0 * r1 * rx, y1, b0 * r1 * rz, cy, sy);
                quadW(c, x, y, z);
            }
        }
        return this;
    }

    private void pt(int i, float x, float y, float z, float lx, float ly, float lz, float cy, float sy) {
        setW(i, x + lx * cy + lz * sy, y + ly, z - lx * sy + lz * cy);
    }

    /** Flat world-space quad from four points (shadows, wings, sails). */
    Mesh quad4(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int c) {
        if (!part()) return this;
        setW(0, x0, y0, z0); setW(1, x1, y1, z1); setW(2, x2, y2, z2); setW(3, x3, y3, z3);
        quadW(c, (x0 + x2) / 2, (y0 + y2) / 2 - 1, (z0 + z2) / 2);
        return this;
    }

    private void track(float localY) { float y = Y(localY); if (y > maxY) maxY = y; }
    private void trackW(float y) { if (y > maxY) maxY = y; }
}
