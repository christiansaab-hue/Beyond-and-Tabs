// GENERATED from design/races.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record RaceDef(String id, String name, String tabsFactions, String color, String iconStyle, boolean firstPlayable, String status) {
    public static final RaceDef ANCIENT_WORLD = new RaceDef("ancient_world", "Ancient World", "TRIBAL,ANCIENT,VIKING", "#C8763A", "stone", true, "planned");
    public static final RaceDef KINGDOMS = new RaceDef("kingdoms", "Kingdoms", "MEDIEVAL,ASIA,RENAISSANCE", "#3A6EC8", "banner", true, "planned");
    public static final RaceDef GUNPOWDER = new RaceDef("gunpowder", "Gunpowder", "PIRATE,WESTERN", "#7A5A3A", "powder", false, "later");
    public static final RaceDef FANTASY = new RaceDef("fantasy", "Fantasy", "FANTASYGOOD,FANTASYEVIL,HALLOWEEN", "#8A3AC8", "rune", false, "later");
    public static final RaceDef NEON = new RaceDef("neon", "Neon", "NEON", "#22E6FF", "circuit", false, "later");
    public static final List<RaceDef> ALL = List.of(ANCIENT_WORLD, KINGDOMS, GUNPOWDER, FANTASY, NEON);
    private static final Map<String, RaceDef> BY_ID = new LinkedHashMap<>();
    static { for (RaceDef x : ALL) BY_ID.put(x.id(), x); }
    public static RaceDef byId(String id) { RaceDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown races id " + id); return x; }
}
