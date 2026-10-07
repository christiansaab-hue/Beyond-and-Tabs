package dev.beyondtabs.engine;

import dev.beyondtabs.engine.gen.AbilityDef;
import dev.beyondtabs.engine.gen.UnitDef;
import dev.beyondtabs.engine.gen.WeaponDef;

/** Combat classes, fighting styles and the bookkeeping of one engagement (who fought whom, in which style, how it went). */
public final class Combat {
    private Combat() { }

    /**
     * How a unit fights its current enemy.
     * AGGRESSIVE close in and keep swinging; FLANK arc round to the side/back and prefer soft targets;
     * SKIRMISH strike then step out of reach while the weapon recovers; HOLD keep position and let them come (spears
     * keep enemies at the tip); KITE shoot and back away from melee; FOCUS pile onto the target allies are hitting.
     */
    public enum Style { AGGRESSIVE, FLANK, SKIRMISH, HOLD, KITE, FOCUS }

    static final Style[] MELEE = {Style.AGGRESSIVE, Style.FLANK, Style.SKIRMISH, Style.HOLD};
    static final Style[] REACH = {Style.HOLD, Style.AGGRESSIVE, Style.SKIRMISH};
    static final Style[] RANGED = {Style.KITE, Style.HOLD, Style.FOCUS};
    static final Style[] SUPPORT = {Style.HOLD, Style.AGGRESSIVE};
    static final Style[] SIEGE = {Style.HOLD, Style.FOCUS};

    static Style[] stylesFor(String cls) {
        return switch (cls) {
            case "ranged", "flyer" -> RANGED;
            case "reach" -> REACH;
            case "support" -> SUPPORT;
            case "siege" -> SIEGE;
            default -> MELEE;
        };
    }

    public static String classOf(UnitDef d, WeaponDef w, AbilityDef a) {
        String body = d.body();
        if ("support".equals(w.kind())) return "support";
        if (body.equals("mounted") || body.equals("quadruped_large")) return "cavalry";
        if (body.equals("flyer")) return "flyer";
        if (body.equals("large") || body.equals("flyer_large")) return "large";
        if (body.equals("siege") || body.equals("vehicle_large") || "siege".equals(d.role())) return "siege";
        if ("ranged".equals(w.kind())) return "ranged";
        if (w.id().equals("melee_reach") || a.kind().equals("brace")) return "reach";
        if (w.id().equals("melee_shield") || a.kind().startsWith("shield")) return "shield";
        return "infantry";
    }

    /** One stretch of fighting against one class of enemy, scored when it ends. */
    static final class Engagement {
        final String enemyCls; final Style style; final float start;
        float dealt, taken; int kills; float enemyHp;
        Engagement(String enemyCls, Style style, float start, float enemyHp) { this.enemyCls = enemyCls; this.style = style; this.start = start; this.enemyHp = enemyHp; }
    }
}
