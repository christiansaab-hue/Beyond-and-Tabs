package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.ThicketCover;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * One server-side answer to "can this player's side see / target that unit?" for the two ways a unit hides from its
 * enemies: the Shade Ranger's cloak ({@link ShadeRangerUnit#isCloaked}) and Verdant thicket cover
 * ({@link ThicketCover#isHidden}). The server is the authority here - the client also refuses to hover / select a unit
 * that is invisible to the local player (UnitClientEvents), but a modified client could still send its id, so orders
 * (UnitActionItem), area attack (AreaCommands) and the per-tick target sync (Unit.tick) all ask this.
 * <p>
 * Hidden is not invulnerable: nothing here touches damage, so splash / AoE still hits a hidden unit.
 * <p>
 * Cheap enough for per-unit-per-tick use: the concealed test is a flag read plus an empty-map check in the common
 * case, and the alliance lookup only runs for a unit that actually is concealed.
 */
public class Concealment {

    /** Is this entity hiding right now (from its enemies)? Server side. */
    public static boolean isConcealed(Entity e) {
        return ShadeRangerUnit.isCloaked(e) || ThicketCover.isHidden(e);
    }

    /**
     * Is the target hidden from the side of {@code viewerOwner}? A concealed unit is hidden from everyone but its
     * owner and the owner's allies; an ownerless viewer (neutral creeps, an unowned attacker) doesn't see it either.
     */
    public static boolean isHiddenFrom(Entity target, String viewerOwner) {
        if (!(target instanceof Unit u) || !isConcealed(target))
            return false;
        if (viewerOwner == null || viewerOwner.isBlank())
            return true;
        String o = u.getOwnerName();
        return !(viewerOwner.equals(o) || AlliancesServerEvents.isAllied(viewerOwner, o));
    }

    /**
     * Strips a unit of both kinds of cover for {@code ticks} (Holy Bell): it can't re-cloak or re-hide in a thicket
     * until the time is up, so the bell's glow and fog reveal actually let the enemy shoot at it.
     */
    public static void revealFor(LivingEntity le, int ticks) {
        ThicketCover.revealFor(le, ticks);
        if (le instanceof ShadeRangerUnit sr)
            sr.revealFor(ticks);
    }
}
