package com.solegendary.reignofnether.attackwarnings;

import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

public class AttackWarningServerEvents {

    // Every hit used to send a packet to every player; in an 8v8 brawl that is thousands a second for a warning the
    // client rate-limits anyway. One per owner per half second is plenty for the alert, the "go to" position and the
    // minimap combat ping. Commanders have their own slot so an ally's commander ping isn't swallowed by army hits.
    private static final int WARN_INTERVAL_TICKS = 10;
    private static final Map<String, Long> lastWarnTick = new HashMap<>();
    private static final Map<String, Long> lastCommanderWarnTick = new HashMap<>();

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt)  {
        if (evt.getEntity().level().isClientSide())
            return;

        if (evt.getEntity() instanceof Unit unit &&
                !evt.getSource().is(DamageTypeTags.IS_FALL) &&
                evt.getSource() != evt.getEntity().damageSources().starve() &&
                evt.getSource() != evt.getEntity().damageSources().inWall() &&
                evt.getSource() != evt.getEntity().damageSources().outOfBorder())
            warn(evt.getEntity().level(), unit.getOwnerName(), evt.getEntity().getOnPos(),
                CommanderServerEvents.isCommander(evt.getEntity()));
    }

    /** Rate-limited attack warning to clients; also called when a building loses blocks to an attack. */
    public static void warn(Level level, String ownerName, BlockPos pos, boolean isCommander) {
        if (ownerName == null || ownerName.isEmpty())
            return;
        Map<String, Long> slots = isCommander ? lastCommanderWarnTick : lastWarnTick;
        long now = level.getGameTime();
        Long last = slots.get(ownerName);
        if (last != null && now >= last && now - last < WARN_INTERVAL_TICKS)
            return;
        slots.put(ownerName, now);
        AttackWarningClientboundPacket.sendWarning(ownerName, pos, isCommander);
    }
}
