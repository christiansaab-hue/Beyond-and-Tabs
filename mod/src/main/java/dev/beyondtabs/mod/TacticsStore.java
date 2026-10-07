package dev.beyondtabs.mod;

import dev.beyondtabs.engine.Team;
import dev.beyondtabs.engine.World;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;

/** Keeps each race's combat memory between matches (config/beyondtabs/tactics/<race>.txt). */
final class TacticsStore {
    private TacticsStore() { }

    static Path file(String race) { return FMLPaths.CONFIGDIR.get().resolve("beyondtabs").resolve("tactics").resolve(race + ".txt"); }

    static void load(World w) {
        for (Team t : w.teams) {
            try { Path p = file(t.race); if (Files.exists(p)) t.tactics.load(Files.readString(p)); }
            catch (Exception e) { BeyondTabs.LOG.warn("Could not read tactics for {}: {}", t.race, e.toString()); }
        }
    }

    static void save(World w) {
        for (Team t : w.teams) {
            try { Path p = file(t.race); Files.createDirectories(p.getParent()); Files.writeString(p, t.tactics.save()); }
            catch (Exception e) { BeyondTabs.LOG.warn("Could not save tactics for {}: {}", t.race, e.toString()); }
        }
    }
}
