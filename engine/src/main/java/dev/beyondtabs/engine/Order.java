package dev.beyondtabs.engine;

/** One queued command (BAR-style: orders queue with shift, area orders carry a radius). */
public record Order(Type type, float x, float z, float radius, Unit target, Building building) {
    public enum Type { MOVE, ATTACK_MOVE, ATTACK, PATROL, GUARD, AREA_ATTACK, BUILD, STOP }
    public static Order move(float x, float z) { return new Order(Type.MOVE, x, z, 0, null, null); }
    public static Order attackMove(float x, float z) { return new Order(Type.ATTACK_MOVE, x, z, 0, null, null); }
    public static Order attack(Unit t) { return new Order(Type.ATTACK, t.x, t.z, 0, t, null); }
    public static Order patrol(float x, float z) { return new Order(Type.PATROL, x, z, 0, null, null); }
    public static Order guard(Unit t) { return new Order(Type.GUARD, t.x, t.z, 0, t, null); }
    public static Order areaAttack(float x, float z, float r) { return new Order(Type.AREA_ATTACK, x, z, r, null, null); }
    public static Order build(Building b) { return new Order(Type.BUILD, b.x, b.z, 0, null, b); }
}
