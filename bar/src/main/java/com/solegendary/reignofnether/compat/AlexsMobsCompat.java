package com.solegendary.reignofnether.compat;

import com.github.alexthe666.alexsmobs.entity.AMEntityRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/**
 * Entry point for Alex's Mobs (GPL-3, by sbom_xela) integration. Alex's Mobs and its library Citadel are hard
 * dependencies (mods.toml) installed alongside our jar, never bundled in it. For now this class only holds a direct
 * compile-time reference to their entity registry, which proves the dependency links at compile time as well as at
 * runtime (the {@code alexs_mobs_is_loaded} GameTest).
 */
public final class AlexsMobsCompat {

    public static final String MOD_ID = "alexsmobs";

    private AlexsMobsCompat() {}

    /** Alex's Mobs' grizzly bear type, read straight from their registry class (not by name) to force linking. */
    public static EntityType<?> grizzlyBear() {
        return AMEntityRegistry.GRIZZLY_BEAR.get();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
