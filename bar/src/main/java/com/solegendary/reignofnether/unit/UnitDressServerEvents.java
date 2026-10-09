package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Faction liveries: every owned unit gets its faction's colours - dyed leather on the chest and feet (identical
 * pieces for every faction, so the stats stay perfectly equal; only the dye differs). The Kingdom marches in
 * royal blue, the Fallen in dusk purple, the Gilded Legion in molten gold. Heads stay bare so every mob's face
 * still reads. Pieces never drop, and a unit that already wears something keeps it.
 */
public class UnitDressServerEvents {

    static final String DRESSED_TAG = "bt_dressed";

    static final int KINGDOM_BLUE = 0x2B4FA8;
    static final int FALLEN_DUSK = 0x3B2D4F;
    static final int LEGION_GOLD = 0xC9961A;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        if (evt.getServer().getTickCount() % 40 != 0)
            return;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit unit) || !(le instanceof Mob mob) || !le.isAlive())
                continue;
            if (unit.getOwnerName() == null || unit.getOwnerName().isBlank())
                continue;
            if (le.getTags().contains(DRESSED_TAG))
                continue;
            le.addTag(DRESSED_TAG);

            Faction faction = Factions.getFaction(unit);
            int colour;
            if (faction != null && faction.equals(Factions.MONSTERS))
                colour = FALLEN_DUSK;
            else if (faction != null && faction.equals(Factions.PIGLINS))
                colour = LEGION_GOLD;
            else
                colour = KINGDOM_BLUE;

            equipDyed(mob, EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE.getDefaultInstance(), colour);
            equipDyed(mob, EquipmentSlot.FEET, Items.LEATHER_BOOTS.getDefaultInstance(), colour);
        }
    }

    static void equipDyed(Mob mob, EquipmentSlot slot, ItemStack stack, int colour) {
        if (!mob.getItemBySlot(slot).isEmpty())
            return;   // never override gear a unit already wears
        if (stack.getItem() instanceof DyeableLeatherItem dyeable)
            dyeable.setColor(stack, colour);
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0f);
    }
}
