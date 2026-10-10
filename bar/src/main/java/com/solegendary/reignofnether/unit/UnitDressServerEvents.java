package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.faction.FactionTraits;
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

    // per-faction colours and armour pieces live in FactionTraits.Livery (Sunforged white/gold, Gravebound bone/soul-blue,
    // Horde rust/bone, Verdant green/silver); a faction without a livery is not dressed

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
            boolean dressed = le.getTags().contains(DRESSED_TAG);
            if (dressed && !commander)
                continue;   // cheap early out before any faction / registry lookup (hundreds of units at 8v8)
            FactionTraits.Livery livery = FactionTraits.of(Factions.getFaction(unit)).livery;
            if (livery == null)
                continue;   // neutral / undesigned factions wear their own gear (they used to get Sunforged kit)
            // commanders are promoted after spawning; they pick up the crown on a later pass (once only: a head
            // slot holding real gear or the crown already is left alone)
            boolean needsCrown = false;
            if (commander) {
                ItemStack head = mob.getItemBySlot(EquipmentSlot.HEAD);
                Item helm = resolve(livery.commanderHelm());
                needsCrown = helm != null && !head.is(helm) && (head.isEmpty() || isLivery(head));
            }
            if (dressed && !needsCrown)
                continue;
            if (le.getTags().contains(OLD_DRESSED_TAG)) {
                stripOldLivery(mob);
                le.removeTag(OLD_DRESSED_TAG);
            }
            le.addTag(DRESSED_TAG);
            dress(mob, unit, commander, livery);
        }
    }

    static void dress(Mob mob, Unit unit, boolean commander, FactionTraits.Livery livery) {
        // the biome a villager spawns in picks its robe (swamp/jungle = dark olive, near-invisible on grass);
        // the Kingdom always wears plains cloth so its units read at a glance
        if (mob instanceof net.minecraft.world.entity.npc.Villager v
                && v.getVillagerData().getType() != net.minecraft.world.entity.npc.VillagerType.PLAINS)
            v.setVillagerData(v.getVillagerData().setType(net.minecraft.world.entity.npc.VillagerType.PLAINS));
        if (mob instanceof net.minecraft.world.entity.monster.ZombieVillager zv
                && zv.getVillagerData().getType() != net.minecraft.world.entity.npc.VillagerType.PLAINS)
            zv.setVillagerData(zv.getVillagerData().setType(net.minecraft.world.entity.npc.VillagerType.PLAINS));
        int colour = livery.colour(), accent = livery.accent();

        // the faction colour stays on the legs and boots (dyed leather, or a dyeable gambeson) so a unit's side
        // reads at a glance; the helmet and chest carry the faction's armour style from Epic Knights when that
        // mod is installed (plain leather/vanilla otherwise). Every piece keeps the zero-armour modifier.
        Kit kit = kitFor(livery, unit, commander);
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

    static Item resolve(FactionTraits.Piece piece) {
        if (piece == null)
            return null;
        return piece.epicKnightsId() == null ? piece.fallback() : ek(piece.epicKnightsId(), piece.fallback());
    }

    /**
     * Role picks the cut (legs on commanders and melee only, the same for every faction); the livery picks the pieces.
     * Equivalent to the old per-faction chains for the three live factions.
     */
    static Kit kitFor(FactionTraits.Livery l, Unit unit, boolean commander) {
        boolean worker = unit instanceof WorkerUnit, ranged = unit instanceof RangedAttackerUnit;
        if (commander) return new Kit(resolve(l.commanderHelm()), resolve(l.commanderChest()), Items.LEATHER_LEGGINGS);
        if (worker) return new Kit(null, resolve(l.workerChest()), null);
        if (ranged) return new Kit(resolve(l.rangedHead()), resolve(l.rangedChest()), null);
        // no melee chest piece = the faction's skins carry their own clothes (Verdant): leggings would hide the robe too
        Item meleeChest = resolve(l.meleeChest());
        return new Kit(resolve(l.meleeHead()), meleeChest, meleeChest == null ? null : Items.LEATHER_LEGGINGS);
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
