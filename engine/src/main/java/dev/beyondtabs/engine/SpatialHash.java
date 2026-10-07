package dev.beyondtabs.engine;

/** Fixed-size uniform grid hash rebuilt every tick: O(n) build, near-constant neighbour queries, no allocation. */
public final class SpatialHash {
    final float cell; final int mask; final int[] head; int[] next = new int[1024]; Unit[] items = new Unit[1024]; int count;

    public SpatialHash(float cell, int buckets) {
        this.cell = cell; int b = Integer.highestOneBit(buckets - 1) << 1; mask = b - 1; head = new int[b];
    }
    int key(int cx, int cz) { return (cx * 73856093 ^ cz * 19349663) & mask; }
    public void clear() { java.util.Arrays.fill(head, -1); count = 0; }
    public void add(Unit u) {
        if (count == items.length) { items = java.util.Arrays.copyOf(items, count * 2); next = java.util.Arrays.copyOf(next, count * 2); }
        int k = key((int) Math.floor(u.x / cell), (int) Math.floor(u.z / cell));
        items[count] = u; next[count] = head[k]; head[k] = count++;
    }
    public interface Visitor { void visit(Unit u); }
    /** Visits every unit whose cell overlaps the square around (x,z); callers check exact distance. */
    public void query(float x, float z, float r, Visitor v) {
        int x0 = (int) Math.floor((x - r) / cell), x1 = (int) Math.floor((x + r) / cell);
        int z0 = (int) Math.floor((z - r) / cell), z1 = (int) Math.floor((z + r) / cell);
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++)
            for (int i = head[key(cx, cz)]; i != -1; i = next[i]) {
                Unit u = items[i];
                if ((int) Math.floor(u.x / cell) == cx && (int) Math.floor(u.z / cell) == cz) v.visit(u);
            }
    }
}
