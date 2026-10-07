// GENERATED from design/races.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record RaceDef(String id, String name, String tabsFactions, String color, String iconStyle, boolean firstPlayable, String status, String tagline) {
    public static final RaceDef ANCIENT_WORLD = new RaceDef("ancient_world", "Bronzeborn Clans", "TRIBAL,ANCIENT,VIKING", "#C8763A", "stone", true, "planned", "Tribes, titans and raiders of the old world.");
    public static final RaceDef KINGDOMS = new RaceDef("kingdoms", "Sovereign Crown", "MEDIEVAL,ASIA,RENAISSANCE", "#3A6EC8", "banner", true, "planned", "Knights, dynasties and Renaissance war machines.");
    public static final RaceDef GUNPOWDER = new RaceDef("gunpowder", "Blackpowder Syndicate", "PIRATE,WESTERN", "#7A5A3A", "powder", false, "later", "Pirates, outlaws and powder kegs.");
    public static final RaceDef FANTASY = new RaceDef("fantasy", "Mythborne Covenant", "FANTASYGOOD,FANTASYEVIL,HALLOWEEN", "#8A3AC8", "rune", false, "later", "Paladins, necromancers and things that go bump in the night.");
    public static final RaceDef NEON = new RaceDef("neon", "Neon", "NEON", "#22E6FF", "circuit", false, "later", "Glowing, fast and strange.");
    public static final List<RaceDef> ALL = List.of(ANCIENT_WORLD, KINGDOMS, GUNPOWDER, FANTASY, NEON);
    private static final Map<String, RaceDef> BY_ID = new LinkedHashMap<>();
    static { for (RaceDef x : ALL) BY_ID.put(x.id(), x); }
    public static RaceDef byId(String id) { RaceDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown races id " + id); return x; }
}
