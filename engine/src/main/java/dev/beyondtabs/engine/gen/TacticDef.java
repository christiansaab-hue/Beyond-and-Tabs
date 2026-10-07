// GENERATED from design/tactics.json by tools/gen_java.py - do not edit.
package dev.beyondtabs.engine.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record TacticDef(String id, String myClass, String enemyClass, String style, String note) {
    public static final TacticDef RANGED_VS_INFANTRY = new TacticDef("ranged_vs_infantry", "ranged", "infantry", "kite", "Keep the distance, shoot while backing off.");
    public static final TacticDef RANGED_VS_SHIELD = new TacticDef("ranged_vs_shield", "ranged", "shield", "kite", "Shields block frontal arrows; keep away and let flankers work.");
    public static final TacticDef RANGED_VS_CAVALRY = new TacticDef("ranged_vs_cavalry", "ranged", "cavalry", "hold", "You can't outrun horses; stand by the spears.");
    public static final TacticDef RANGED_VS_RANGED = new TacticDef("ranged_vs_ranged", "ranged", "ranged", "focus", "Win the archer duel by focusing one target at a time.");
    public static final TacticDef RANGED_VS_LARGE = new TacticDef("ranged_vs_large", "ranged", "large", "focus", "Pour everything into the big one.");
    public static final TacticDef RANGED_VS_ANY = new TacticDef("ranged_vs_any", "ranged", "any", "kite", "Default for shooters: keep range.");
    public static final TacticDef REACH_VS_CAVALRY = new TacticDef("reach_vs_cavalry", "reach", "cavalry", "hold", "Brace and let them run onto the points.");
    public static final TacticDef REACH_VS_LARGE = new TacticDef("reach_vs_large", "reach", "large", "hold", "Keep the giant at spear length.");
    public static final TacticDef REACH_VS_ANY = new TacticDef("reach_vs_any", "reach", "any", "hold", "Keep enemies at the tip of the weapon.");
    public static final TacticDef INFANTRY_VS_RANGED = new TacticDef("infantry_vs_ranged", "infantry", "ranged", "flank", "Go round the front line to the archers.");
    public static final TacticDef INFANTRY_VS_SUPPORT = new TacticDef("infantry_vs_support", "infantry", "support", "flank", "Kill the healers first.");
    public static final TacticDef INFANTRY_VS_LARGE = new TacticDef("infantry_vs_large", "infantry", "large", "skirmish", "Hit and step back out of its reach.");
    public static final TacticDef INFANTRY_VS_REACH = new TacticDef("infantry_vs_reach", "infantry", "reach", "aggressive", "Get inside the spear points fast.");
    public static final TacticDef INFANTRY_VS_ANY = new TacticDef("infantry_vs_any", "infantry", "any", "aggressive", "Default for foot soldiers: close and fight.");
    public static final TacticDef SHIELD_VS_RANGED = new TacticDef("shield_vs_ranged", "shield", "ranged", "aggressive", "Advance behind the shield.");
    public static final TacticDef SHIELD_VS_ANY = new TacticDef("shield_vs_any", "shield", "any", "hold", "Hold the line.");
    public static final TacticDef CAVALRY_VS_RANGED = new TacticDef("cavalry_vs_ranged", "cavalry", "ranged", "flank", "Ride round and charge the archers.");
    public static final TacticDef CAVALRY_VS_REACH = new TacticDef("cavalry_vs_reach", "cavalry", "reach", "skirmish", "Don't charge spears head-on.");
    public static final TacticDef CAVALRY_VS_ANY = new TacticDef("cavalry_vs_any", "cavalry", "any", "aggressive", "Default for riders: charge in.");
    public static final TacticDef LARGE_VS_ANY = new TacticDef("large_vs_any", "large", "any", "aggressive", "Default for monsters: wade in.");
    public static final TacticDef SIEGE_VS_ANY = new TacticDef("siege_vs_any", "siege", "any", "hold", "Stay behind the army.");
    public static final TacticDef FLYER_VS_ANY = new TacticDef("flyer_vs_any", "flyer", "any", "kite", "Default for flyers: circle at range.");
    public static final TacticDef SUPPORT_VS_ANY = new TacticDef("support_vs_any", "support", "any", "hold", "Stay behind your troops.");
    public static final List<TacticDef> ALL = List.of(RANGED_VS_INFANTRY, RANGED_VS_SHIELD, RANGED_VS_CAVALRY, RANGED_VS_RANGED, RANGED_VS_LARGE, RANGED_VS_ANY, REACH_VS_CAVALRY, REACH_VS_LARGE, REACH_VS_ANY, INFANTRY_VS_RANGED, INFANTRY_VS_SUPPORT, INFANTRY_VS_LARGE, INFANTRY_VS_REACH, INFANTRY_VS_ANY, SHIELD_VS_RANGED, SHIELD_VS_ANY, CAVALRY_VS_RANGED, CAVALRY_VS_REACH, CAVALRY_VS_ANY, LARGE_VS_ANY, SIEGE_VS_ANY, FLYER_VS_ANY, SUPPORT_VS_ANY);
    private static final Map<String, TacticDef> BY_ID = new LinkedHashMap<>();
    static { for (TacticDef x : ALL) BY_ID.put(x.id(), x); }
    public static TacticDef byId(String id) { TacticDef x = BY_ID.get(id); if (x == null) throw new IllegalArgumentException("unknown tactics id " + id); return x; }
}
