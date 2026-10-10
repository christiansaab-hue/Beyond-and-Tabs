package com.solegendary.reignofnether.faction;

import com.solegendary.reignofnether.ability.abilities.CommanderAbility;
import com.solegendary.reignofnether.ability.abilities.CommanderDGun;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Everything that makes a faction look and play like itself, in one place, looked up with {@link #of(Faction)}.
 *
 * Why this exists (design/verdant_court_plan.md, "Fall-through to villagers"): about 35 call sites used to branch
 * "monsters? piglins? otherwise villagers", so any new faction - the Verdant Court preview first - silently got
 * Sunforged liveries, banners, scaffolds, D-gun, Formation and so on. Now every faction either carries an explicit
 * entry (set with {@link Faction#setTraits}) or falls back to {@link #NEUTRAL}, which is deliberately plain and
 * turns faction mechanics off. Adding a faction means filling in one entry here, not hunting call sites.
 *
 * The three live entries reproduce the behaviour the old if/else chains had exactly. Verdant gets its green and
 * silver look; behaviours that are not designed yet (debris, commander signature, D-gun flavour, quick-build,
 * faction mechanics) are off or generic until their slice lands.
 *
 * Class-specific behaviour (instanceof VillagerUnit, GhastUnit, SpiderLair...) and genuine one-faction mechanics
 * that never had an "else Sunforged" branch (Gravebound night healing, Horde nether terrain, RaiseDead...) stay
 * where they are.
 */
public final class FactionTraits {

    /** Smoke/flame over a working production building (BuildingAmbienceClientEvents). */
    public enum Ambience { NONE, CHIMNEY_SMOKE, SOUL_FLAMES, FORGE_FIRE, SPORES }

    /** What dresses the corners of a construction scaffold (ScaffoldRenderClientEvents). */
    public enum ScaffoldDecor { NONE, PENNANTS, BONE_LANTERNS, CHAINS, LANTERNS }

    /** One livery item: an Epic Knights ("magistuarmory") id when that mod is installed, else the vanilla fallback. */
    public record Piece(String epicKnightsId, Item fallback) {
        static Piece ek(String id, Item fallback) { return new Piece(id, fallback); }
        static Piece vanilla(Item item) { return new Piece(null, item); }
    }

    /**
     * A faction's clothes (UnitDressServerEvents). Legs and boots are role-based and the same for every faction;
     * colour dyes chest/legs/boots and accent dyes the head piece.
     */
    public record Livery(int colour, int accent, Piece commanderHelm, Piece commanderChest, Piece workerChest,
                         Piece rangedHead, Piece rangedChest, Piece meleeHead, Piece meleeChest) { }

    public final String name;
    /** ARGB, the unit info card's role line. */
    public final int accentArgb;
    /** null = not dressed at all. */
    public final Livery livery;
    /** null = the building flies no banner. */
    public final BlockState bannerCloth, bannerTrim;
    public final BlockState scaffoldPole, scaffoldRail;
    public final ScaffoldDecor scaffoldDecor;
    public final Ambience ambience;
    /** BarFx.N_* nanolathe beam tint. */
    public final byte nanoTint;
    /** BarFx.F_* death debris (3-bit wire field: F_NONE, four factions, room for three more). */
    public final byte deathDebris;
    public final CommanderDGun.Kind dgunKind;
    /** CommanderAbility.Kind.NONE = the commander gets no signature ability. */
    public final CommanderAbility.Kind commanderAbility;
    public final BlockState wreckBlock;
    /** Reclaim speed multiplier of this faction's workers (WreckServerEvents.factionReclaim). */
    public final float reclaimMultiplier;
    /** Sunforged Formation (FormationServerEvents) / Horde Momentum (MomentumServerEvents). */
    public final boolean formation, momentum;
    /** The extractor a worker queues by right-clicking a metal patch; supplier returning null = no quick-build. */
    public final Supplier<Building> metalExtractor;

    private FactionTraits(Builder b) {
        name = b.name;
        accentArgb = b.accentArgb;
        livery = b.livery;
        bannerCloth = b.bannerCloth;
        bannerTrim = b.bannerTrim;
        scaffoldPole = b.scaffoldPole;
        scaffoldRail = b.scaffoldRail;
        scaffoldDecor = b.scaffoldDecor;
        ambience = b.ambience;
        nanoTint = b.nanoTint;
        deathDebris = b.deathDebris;
        dgunKind = b.dgunKind;
        commanderAbility = b.commanderAbility;
        wreckBlock = b.wreckBlock;
        reclaimMultiplier = b.reclaimMultiplier;
        formation = b.formation;
        momentum = b.momentum;
        metalExtractor = b.metalExtractor;
    }

    /** The traits of a faction; factions without an explicit entry (and null) get {@link #NEUTRAL}, never Sunforged. */
    public static FactionTraits of(Faction faction) {
        return faction == null || faction.traits == null ? NEUTRAL : faction.traits;
    }

    /** True if the faction was given its own entry (the game test checks every live faction has one). */
    public static boolean hasExplicit(Faction faction) {
        return faction != null && faction.traits != null;
    }

    // ------------------------------------------------------------------ entries

    /** Fallback for neutral / NONE / any faction without an entry: plain, undressed, no faction mechanics. */
    public static final FactionTraits NEUTRAL = new Builder("neutral").build();

    /** Sunforged Kingdom (villagers): white plate and gold. */
    public static final FactionTraits SUNFORGED = new Builder("sunforged")
        .accent(0xFF6FB4FF)
        .livery(new Livery(0xF0ECE0, 0xD9A41E,
            Piece.ek("ceremonialarmet_with_plume", Items.GOLDEN_HELMET), Piece.ek("maximilian_chestplate", Items.LEATHER_CHESTPLATE),
            Piece.ek("gambeson_chestplate", Items.LEATHER_CHESTPLATE),
            Piece.ek("kettlehat", Items.LEATHER_HELMET), Piece.ek("gambeson_chestplate", Items.LEATHER_CHESTPLATE),
            Piece.ek("sallet", null), Piece.ek("knight_chestplate", Items.LEATHER_CHESTPLATE)))
        .banner(Blocks.WHITE_WOOL.defaultBlockState(), Blocks.YELLOW_WOOL.defaultBlockState())
        .scaffold(Blocks.STRIPPED_BIRCH_LOG.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), ScaffoldDecor.PENNANTS)
        .ambience(Ambience.CHIMNEY_SMOKE)
        .fx(BarFx.N_SUNFORGED, BarFx.F_SUNFORGED)
        .commander(CommanderDGun.Kind.SUNFIRE, CommanderAbility.Kind.RALLY)
        .wreck(Blocks.IRON_BLOCK.defaultBlockState(), 1f)
        .formation()
        .extractor(() -> Buildings.METAL_EXTRACTOR_VILLAGERS)
        .build();

    /** Gravebound (monsters): bone and soul-blue. */
    public static final FactionTraits GRAVEBOUND = new Builder("gravebound")
        .accent(0xFF8ED866)
        .livery(new Livery(0xCFC6A8, 0x3FC6D8,
            Piece.ek("rustedgreathelm", Items.GOLDEN_HELMET), Piece.ek("rustedcrusader_chestplate", Items.LEATHER_CHESTPLATE),
            Piece.vanilla(Items.LEATHER_CHESTPLATE),
            Piece.ek("rustedkettlehat", Items.LEATHER_HELMET), Piece.ek("rustedchainmail_chestplate", Items.LEATHER_CHESTPLATE),
            Piece.ek("rustednorman_helmet", null), Piece.ek("rustedhalfarmor_chestplate", Items.LEATHER_CHESTPLATE)))
        .banner(Blocks.CYAN_WOOL.defaultBlockState(), Blocks.BLACK_WOOL.defaultBlockState())
        .scaffold(Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), ScaffoldDecor.BONE_LANTERNS)
        .ambience(Ambience.SOUL_FLAMES)
        .fx(BarFx.N_GRAVEBOUND, BarFx.F_GRAVEBOUND)
        .commander(CommanderDGun.Kind.SOULREAPER, CommanderAbility.Kind.DREAD)
        .wreck(Blocks.BONE_BLOCK.defaultBlockState(), 2f)   // the Gravedigger reclaims double (design-factions.md)
        .extractor(() -> Buildings.METAL_EXTRACTOR_MONSTERS)
        .build();

    /** Ironhide Horde (piglins): rust red and bone. */
    public static final FactionTraits HORDE = new Builder("horde")
        .accent(0xFFFF9A40)
        .livery(new Livery(0x9C3A1E, 0xD8D0B8,
            Piece.ek("greathelm", Items.GOLDEN_HELMET), Piece.ek("lamellar_chestplate", Items.LEATHER_CHESTPLATE),
            Piece.vanilla(Items.LEATHER_CHESTPLATE),
            new Piece(null, null), Piece.ek("lamellar_chestplate", Items.LEATHER_CHESTPLATE),
            Piece.ek("barbute", null), Piece.ek("brigandine_chestplate", Items.LEATHER_CHESTPLATE)))
        .banner(Blocks.RED_WOOL.defaultBlockState(), Blocks.BONE_BLOCK.defaultBlockState())
        .scaffold(Blocks.CRIMSON_STEM.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), ScaffoldDecor.CHAINS)
        .ambience(Ambience.FORGE_FIRE)
        .fx(BarFx.N_HORDE, BarFx.F_HORDE)
        .commander(CommanderDGun.Kind.BLOODSTORM, CommanderAbility.Kind.WAR_HORN)
        .wreck(Blocks.EXPOSED_CUT_COPPER.defaultBlockState(), 1.5f)   // the Clan Builder reclaims half again
        .momentum()
        .extractor(() -> Buildings.METAL_EXTRACTOR_PIGLINS)
        .build();

    /**
     * Verdant Court: living wood, moss and lanterns in green and silver. Slice 1 (design/verdant_court_plan.md) gives
     * the Grove Warden its Thornburst cone and Wildstride and the Seedshapers their quick-build extractor; slice 2 its
     * death debris (leaves, petals and green sparkles). A faction mechanic (Living Terrain, slice 3) stays off.
     */
    public static final FactionTraits VERDANT = new Builder("verdant")
        .accent(0xFF5CD69A)
        .livery(new Livery(0x4E8A3C, 0xC0C8CC,
            Piece.vanilla(Items.CHAINMAIL_HELMET), Piece.vanilla(Items.LEATHER_CHESTPLATE),
            Piece.vanilla(Items.LEATHER_CHESTPLATE),
            Piece.vanilla(Items.LEATHER_HELMET), Piece.vanilla(Items.LEATHER_CHESTPLATE),
            new Piece(null, null), Piece.vanilla(Items.LEATHER_CHESTPLATE)))
        .banner(Blocks.GREEN_WOOL.defaultBlockState(), Blocks.LIGHT_GRAY_WOOL.defaultBlockState())
        .scaffold(Blocks.OAK_LOG.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(), ScaffoldDecor.LANTERNS)
        .ambience(Ambience.SPORES)
        .fx(BarFx.N_VERDANT, BarFx.F_VERDANT)
        .commander(CommanderDGun.Kind.THORNBURST, CommanderAbility.Kind.WILDSTRIDE)
        .wreck(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 1f)
        .extractor(() -> Buildings.METAL_EXTRACTOR_VERDANT)
        .build();

    /**
     * Tidewrought (preview, design/tidewrought_plan.md): corsairs and tide-priests in teal and brass - shipwreck
     * timber, prismarine and copper. Slice 0 sets only the look (accent, livery, banner, scaffold, wreck), so the
     * faction is never dressed or built as anyone else. Commander kit, debris, quick-build and the Tides mechanic stay
     * at the neutral defaults until slice 1.
     */
    public static final FactionTraits TIDEWROUGHT = new Builder("tidewrought")
        .accent(0xFF3CC8BE)
        .livery(new Livery(0x1E7F7A, 0xC9A23E,
            Piece.vanilla(Items.LEATHER_HELMET), Piece.vanilla(Items.LEATHER_CHESTPLATE),
            Piece.vanilla(Items.LEATHER_CHESTPLATE),
            Piece.vanilla(Items.LEATHER_HELMET), Piece.vanilla(Items.LEATHER_CHESTPLATE),
            new Piece(null, null), Piece.vanilla(Items.CHAINMAIL_CHESTPLATE)))
        .banner(Blocks.CYAN_WOOL.defaultBlockState(), Blocks.CUT_COPPER.defaultBlockState())
        .scaffold(Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState(), Blocks.PRISMARINE_BRICKS.defaultBlockState(), ScaffoldDecor.NONE)
        .wreck(Blocks.DARK_PRISMARINE.defaultBlockState(), 1f)
        .build();

    // ------------------------------------------------------------------ builder (defaults = NEUTRAL)

    static final class Builder {
        final String name;
        int accentArgb = 0xFFC8C8C8;
        Livery livery = null;
        BlockState bannerCloth = null, bannerTrim = null;
        BlockState scaffoldPole = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState scaffoldRail = Blocks.SPRUCE_PLANKS.defaultBlockState();
        ScaffoldDecor scaffoldDecor = ScaffoldDecor.NONE;
        Ambience ambience = Ambience.NONE;
        byte nanoTint = BarFx.N_NEUTRAL;
        byte deathDebris = BarFx.F_NONE;
        CommanderDGun.Kind dgunKind = CommanderDGun.Kind.PLAIN;
        CommanderAbility.Kind commanderAbility = CommanderAbility.Kind.NONE;
        BlockState wreckBlock = Blocks.COBBLESTONE.defaultBlockState();
        float reclaimMultiplier = 1f;
        boolean formation = false, momentum = false;
        Supplier<Building> metalExtractor = () -> null;

        Builder(String name) { this.name = name; }
        Builder accent(int argb) { accentArgb = argb; return this; }
        Builder livery(Livery l) { livery = l; return this; }
        Builder banner(BlockState cloth, BlockState trim) { bannerCloth = cloth; bannerTrim = trim; return this; }
        Builder scaffold(BlockState pole, BlockState rail, ScaffoldDecor decor) { scaffoldPole = pole; scaffoldRail = rail; scaffoldDecor = decor; return this; }
        Builder ambience(Ambience a) { ambience = a; return this; }
        Builder fx(byte nano, byte debris) { nanoTint = nano; deathDebris = debris; return this; }
        Builder commander(CommanderDGun.Kind dgun, CommanderAbility.Kind ability) { dgunKind = dgun; commanderAbility = ability; return this; }
        Builder wreck(BlockState block, float reclaim) { wreckBlock = block; reclaimMultiplier = reclaim; return this; }
        Builder formation() { formation = true; return this; }
        Builder momentum() { momentum = true; return this; }
        Builder extractor(Supplier<Building> s) { metalExtractor = s; return this; }
        FactionTraits build() { return new FactionTraits(this); }
    }
}
