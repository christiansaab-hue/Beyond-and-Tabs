package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/**
 * Faction liveries and role silhouettes. Every owned unit wears its faction's colours, and the cut of the outfit
 * tells you its job at a glance:
 *   - workers: boots only (civilians)
 *   - melee fighters: tunic, leggings and boots (a full soldier's kit)
 *   - ranged fighters: tunic, boots and a hood in the faction's accent colour
 *   - commanders: a golden crown over their kit
 * The Kingdom marches in royal blue, the Fallen in dusk purple, the Gilded Legion in molten gold.
 *
 * Every livery piece carries an explicit zero-armour modifier (which replaces the item's default armour), so the
 * clothes are purely visual - no unit of any faction gains armour, toughness or knockback resistance from them.
 * Pieces never drop, and gear a unit already wears (piglin gold, skeleton helmets...) is never replaced.
 */
public class UnitDressServerEvents {

    /** bumped from "bt_dressed": units dressed by the first version (with real armour) get re-dressed. */
    static final String DRESSED_TAG = "bt_dressed4";
    static final String OLD_DRESSED_TAG = "bt_dressed";
    static final String LIVERY_NBT = "bt_livery";

    static final UUID LIVERY_UUID = UUID.fromString("6f1c2a9e-4b7d-4e2a-9c51-3a8d0f7e2b44");

    static final int KINGDOM_BLUE = 0xF0ECE0, KINGDOM_ACCENT = 0xD9A41E;   // Sunforged: white plate, gold trim
    static final int FALLEN_DUSK = 0xCFC6A8, FALLEN_ACCENT = 0x3FC6D8;   // Gravebound: bone with soul-blue (reads at night)
    static final int LEGION_GOLD = 0x9C3A1E, LEGION_ACCENT = 0xD8D0B8;   // Ironhide Horde: rust red, bone

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
            if (le instanceof HeroUnit)
                continue;   // heroes keep their bespoke looks
            boolean commander = le.getTags().contains(CommanderServerEvents.TAG);
            // commanders are promoted after spawning; they pick up the crown on a later pass (once only: a head
            // slot holding real gear or the crown already is left alone)
            ItemStack head = mob.getItemBySlot(EquipmentSlot.HEAD);
            boolean needsCrown = commander && !head.is(commanderHelm(Factions.getFaction(unit)))
                && (head.isEmpty() || isLivery(head));
            if (le.getTags().contains(DRESSED_TAG) && !needsCrown)
                continue;
            if (le.getTags().contains(OLD_DRESSED_TAG)) {
                stripOldLivery(mob);
                le.removeTag(OLD_DRESSED_TAG);
            }
            le.addTag(DRESSED_TAG);
            dress(mob, unit, commander);
        }
    }

    static void dress(Mob mob, Unit unit, boolean commander) {
        Faction faction = Factions.getFaction(unit);
        // the biome a villager spawns in picks its robe (swamp/jungle = dark olive, near-invisible on grass);
        // the Kingdom always wears plains cloth so its units read at a glance
        if (mob instanceof net.minecraft.world.entity.npc.Villager v
                && v.getVillagerData().getType() != net.minecraft.world.entity.npc.VillagerType.PLAINS)
            v.setVillagerData(v.getVillagerData().setType(net.minecraft.world.entity.npc.VillagerType.PLAINS));
        if (mob instanceof net.minecraft.world.entity.monster.ZombieVillager zv
                && zv.getVillagerData().getType() != net.minecraft.world.entity.npc.VillagerType.PLAINS)
            zv.setVillagerData(zv.getVillagerData().setType(net.minecraft.world.entity.npc.VillagerType.PLAINS));
        int colour, accent;
        if (faction != null && faction.equals(Factions.MONSTERS)) {
            colour = FALLEN_DUSK;
            accent = FALLEN_ACCENT;
        } else if (faction != null && faction.equals(Factions.PIGLINS)) {
            colour = LEGION_GOLD;
            accent = LEGION_ACCENT;
        } else {
            colour = KINGDOM_BLUE;
            accent = KINGDOM_ACCENT;
        }

        // the faction colour stays on the legs and boots (dyed leather, or a dyeable gambeson) so a unit's side
        // reads at a glance; the helmet and chest carry the faction's armour style from Epic Knights when that
        // mod is installed (plain leather/vanilla otherwise). Every piece keeps the zero-armour modifier.
        Kit kit = kitFor(faction, unit, commander);
        equip(mob, EquipmentSlot.FEET, Items.LEATHER_BOOTS, colour);
        equip(mob, EquipmentSlot.CHEST, kit.chest, colour);
        if (kit.legs != null)
            equip(mob, EquipmentSlot.LEGS, kit.legs, colour);
        if (kit.head != null)
            equip(mob, EquipmentSlot.HEAD, kit.head, accent);
    }

    /** What a unit wears on head, chest and legs (feet are always the faction-coloured boots). */
    record Kit(Item head, Item chest, Item legs) { }

    /** An item from Epic Knights ("magistuarmory") if the mod is loaded, else the vanilla fallback. */
    static Item ek(String id, Item fallback) {
        Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("magistuarmory", id));
        return item == null || item == Items.AIR ? fallback : item;
    }

    /** The commander's head piece for its faction (Sunforged: a plumed ceremonial armet; others a war helm). */
    static Item commanderHelm(Faction faction) {
        if (faction != null && faction.equals(Factions.MONSTERS))
            return ek("rustedgreathelm", Items.GOLDEN_HELMET);
        if (faction != null && faction.equals(Factions.PIGLINS))
            return ek("greathelm", Items.GOLDEN_HELMET);
        return ek("ceremonialarmet_with_plume", Items.GOLDEN_HELMET);
    }

    static Kit kitFor(Faction faction, Unit unit, boolean commander) {
        boolean worker = unit instanceof WorkerUnit, ranged = unit instanceof RangedAttackerUnit;
        if (faction != null && faction.equals(Factions.MONSTERS)) {          // Gravebound: rusted relics
            if (commander) return new Kit(commanderHelm(faction), ek("rustedcrusader_chestplate", Items.LEATHER_CHESTPLATE), Items.LEATHER_LEGGINGS);
            if (worker) return new Kit(null, Items.LEATHER_CHESTPLATE, null);
            if (ranged) return new Kit(ek("rustedkettlehat", Items.LEATHER_HELMET), ek("rustedchainmail_chestplate", Items.LEATHER_CHESTPLATE), null);
            return new Kit(ek("rustednorman_helmet", null), ek("rustedhalfarmor_chestplate", Items.LEATHER_CHESTPLATE), Items.LEATHER_LEGGINGS);
        }
        if (faction != null && faction.equals(Factions.PIGLINS)) {           // Ironhide Horde: lamellar and iron
            if (commander) return new Kit(commanderHelm(faction), ek("lamellar_chestplate", Items.LEATHER_CHESTPLATE), Items.LEATHER_LEGGINGS);
            if (worker) return new Kit(null, Items.LEATHER_CHESTPLATE, null);
            if (ranged) return new Kit(null, ek("lamellar_chestplate", Items.LEATHER_CHESTPLATE), null);
            return new Kit(ek("barbute", null), ek("brigandine_chestplate", Items.LEATHER_CHESTPLATE), Items.LEATHER_LEGGINGS);
        }
        // Sunforged Kingdom: knights in white and gold
        if (commander) return new Kit(commanderHelm(faction), ek("maximilian_chestplate", Items.LEATHER_CHESTPLATE), Items.LEATHER_LEGGINGS);
        if (worker) return new Kit(null, ek("gambeson_chestplate", Items.LEATHER_CHESTPLATE), null);
        if (ranged) return new Kit(ek("kettlehat", Items.LEATHER_HELMET), ek("gambeson_chestplate", Items.LEATHER_CHESTPLATE), null);
        return new Kit(ek("sallet", null), ek("knight_chestplate", Items.LEATHER_CHESTPLATE), Items.LEATHER_LEGGINGS);
    }

    static void equip(Mob mob, EquipmentSlot slot, Item item, int colour) {
        if (item == null)
            return;
        ItemStack current = mob.getItemBySlot(slot);
        // never override real gear; a livery piece may be swapped (e.g. a hood for a commander's crown)
        if (!current.isEmpty() && !isLivery(current))
            return;
        ItemStack stack = new ItemStack(item);
        if (colour >= 0 && stack.getItem() instanceof DyeableLeatherItem dyeable)
            dyeable.setColor(stack, colour);
        // an explicit modifier list replaces the item's default armour: these clothes give no protection at all
        stack.addAttributeModifier(Attributes.ARMOR,
            new AttributeModifier(LIVERY_UUID, "bt_livery", 0, AttributeModifier.Operation.ADDITION), slot);
        stack.getOrCreateTag().putBoolean(LIVERY_NBT, true);
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0f);
    }

    static boolean isLivery(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().getBoolean(LIVERY_NBT);
    }

    /** First-version liveries were plain leather chest + boots (with real armour): take them off. */
    static void stripOldLivery(Mob mob) {
        if (mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE))
            mob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        if (mob.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS))
            mob.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
    }
}
