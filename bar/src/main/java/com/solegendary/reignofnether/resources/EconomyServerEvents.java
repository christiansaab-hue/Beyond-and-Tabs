package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.sandbox.SandboxServer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * BAR-style flow economy (serverside).
 *
 * Resources: METAL (stored in Resources.ore) and ENERGY (stored in Resources.wood).
 *
 * Nothing is paid upfront. Every "consumer" (a building under construction, a unit being trained, a research in
 * progress) calls {@link #requestFlow} each tick with its full cost and the fraction of progress df it wants to make.
 * The consumer may advance by df * granted and has already paid cost * df * granted when the call returns.
 *
 * Stall: per player we sum the demand of all consumers during a tick. At the end of the tick (after income) we
 * compute stall = min(1, metal / metalDemand, energy / energyDemand) and every consumer is throttled by it during the
 * next tick, so when a player overspends ALL of their production slows down evenly, exactly like BAR. A consumer is
 * additionally clamped to what is really left in the pool, so resources never go negative even in the tick
 * where demand first appears.
 *
 * Income: +2 metal/s and +20 energy/s while the player owns at least one completed capitol (commander-style income),
 * plus whatever every completed building returns from Building.getMetalIncome()/getEnergyIncome().
 * Income is capped by storage: 1000 metal / 1000 energy base, +500/+500 while a completed capitol stands (BAR's
 * start storage + commander storage = 1500/1500), more from storage buildings (vaults +2000/+3000, stockpiles).
 * Overflow (BAR): income that doesn't fit is split evenly among allies with room; only what nobody on the team
 * can hold is wasted. When storage shrinks (a vault or capitol dies) whatever was held in the lost storage is lost.
 *
 * Building income mapping (workers no longer gather, so the old resource buildings became passive income):
 *   - Capitols (Town Centre, Mausoleum, Central Portal): the base +2 metal/s +20 energy/s (once per player)
 *   - Farms (Wheat Farm, Pumpkin Farm, Netherwart Farm - food was RoN's main resource): +1.5 metal/s, +5 energy/s
 *   - Stockpiles (Oak/Spruce stockpile - resource drop-offs): +0.5 metal/s, +500 metal and +500 energy storage
 *   - Civilian Portal (piglin resource drop-off): +1 metal/s, +10 energy/s
 *   - Population buildings (Villager House, Haunted House, Sculk Catalyst, Pocket Portal): +5 energy/s
 * New extractor/generator buildings only need to set Building.metalIncome/energyIncome (or override the getters).
 */
public class EconomyServerEvents {

    public static final float BASE_METAL_INCOME = 2f;    // per second, while a completed capitol is owned
    public static final float BASE_ENERGY_INCOME = 20f;  // per second, while a completed capitol is owned
    public static final float DEFAULT_METAL_STORAGE = 1000f;
    public static final float DEFAULT_ENERGY_STORAGE = 1000f;
    // playtest (lovish, Verdant vs 3 bots): both bars pinned at 1000 within a minute. Storage only - income is left
    // alone so the pacing stays slow. BAR starts at 1000 + the commander's 500 of each.
    public static final float CAPITOL_METAL_STORAGE = 500f;
    public static final float CAPITOL_ENERGY_STORAGE = 500f;
    // energy converters: energy spent per metal made, and the energy fill level above which they run
    public static final float CONVERSION_RATIO = 50f;
    public static final float CONVERSION_THRESHOLD = 0.5f;

    private static final int TICKS_PER_SECOND = 20;
    private static final int SYNC_INTERVAL_TICKS = 5; // 4x per second
    private static final int INCOME_RECALC_TICKS = 20;

    public static class PlayerEconomy {
        public final String ownerName;
        public float metalStorage = DEFAULT_METAL_STORAGE;
        public float energyStorage = DEFAULT_ENERGY_STORAGE;
        public float metalIncome = 0; // per second
        public float energyIncome = 0; // per second
        public float metalExpense = 0; // per second, measured over the last second
        public float energyExpense = 0; // per second, measured over the last second
        // fraction (0-1) of the requested progress every consumer receives this tick; 1 = no stall
        public float stall = 1f;
        // energy/s the player's completed converters can turn into metal
        public float conversionCapacity = 0;
        // metal per second the converters made, measured over the last second (shown as income, like BAR)
        public float metalConverted = 0;
        private float metalConvertedWindow = 0;
        // energy per second the converters drained, measured over the last second. Already part of energyExpense;
        // sent separately so the HUD tooltip can explain why energy sits at the 50% conversion threshold
        public float energyConverted = 0;
        private float energyConvertedWindow = 0;
        // metal actually banked this window from income+converters / from reclaim, flushed once per second into
        // the RTSPlayer's match totals for the end-of-match awards (avoids a player lookup every tick)
        private float metalProducedWindow = 0;
        private float metalReclaimedWindow = 0;
        // overflow (BAR): per second sent to allies / thrown away at full storage, measured over the last second.
        // The HUD's WASTING label shows only the wasted part; "sharing" shows what went to allies.
        public float metalShared = 0, energyShared = 0;
        public float metalWasted = 0, energyWasted = 0;
        private float metalSharedWindow = 0, energySharedWindow = 0;
        private float metalWastedWindow = 0, energyWastedWindow = 0;
        // income that didn't fit this tick, offered to allies once every player has banked (two passes, so an
        // ally's room is measured after its own income)
        private float metalOverflowTick = 0, energyOverflowTick = 0;

        // demand registered during the current tick
        private float metalDemand = 0;
        private float energyDemand = 0;
        // spending accumulated over the current 1s window
        private float metalSpentWindow = 0;
        private float energySpentWindow = 0;
        private int windowTicks = 0;

        public PlayerEconomy(String ownerName) {
            this.ownerName = ownerName;
        }
    }

    private static final Map<String, PlayerEconomy> economies = new HashMap<>();
    private static long tickCount = 0;

    public static PlayerEconomy getEconomy(String ownerName) {
        return economies.computeIfAbsent(ownerName, PlayerEconomy::new);
    }

    /**
     * Metal from reclaim (wrecks) goes straight into the pool, capped by storage like income.
     * @return how much was actually accepted (0 if the owner has no pool or storage is full)
     */
    public static float addReclaimedMetal(String ownerName, float amount) {
        if (amount <= 0)
            return 0;
        for (Resources res : ResourcesServerEvents.resourcesList) {
            if (!res.ownerName.equals(ownerName))
                continue;
            float room = getEconomy(ownerName).metalStorage - res.getMetal();
            float got = Math.max(0, Math.min(amount, room));
            if (got > 0) {
                res.addMetal(got);
                getEconomy(ownerName).metalReclaimedWindow += got;
            }
            return got;
        }
        return 0;
    }

    /**
     * Energy converters, BAR-style: only the energy above {@link #CONVERSION_THRESHOLD} of storage is converted,
     * so they never starve construction, and the metal made is capped by metal storage. Public for the game test.
     */
    public static void convertEnergy(Resources res, PlayerEconomy eco) {
        if (eco.conversionCapacity <= 0)
            return;
        float spare = res.getEnergy() - eco.energyStorage * CONVERSION_THRESHOLD;
        float metalRoom = eco.metalStorage - res.getMetal();
        if (spare <= 0 || metalRoom <= 0)
            return;
        float energy = Math.min(spare, Math.min(eco.conversionCapacity / TICKS_PER_SECOND, metalRoom * CONVERSION_RATIO));
        res.addEnergy(-energy);
        res.addMetal(energy / CONVERSION_RATIO);
        eco.energySpentWindow += energy;           // converters show up as energy expense ...
        eco.energyConvertedWindow += energy;
        eco.metalConvertedWindow += energy / CONVERSION_RATIO;   // ... and as metal income
        eco.metalProducedWindow += energy / CONVERSION_RATIO;
    }

    public static float getStall(String ownerName) {
        PlayerEconomy eco = economies.get(ownerName);
        return eco == null ? 1f : eco.stall;
    }

    private static Resources getResources(String ownerName) {
        for (Resources res : ResourcesServerEvents.resourcesList)
            if (res.ownerName.equals(ownerName))
                return res;
        return null;
    }

    /**
     * Ask to advance a consumer by fraction df (0-1) of its total cost.
     *
     * @param ownerName  the paying player
     * @param metalCost  the consumer's TOTAL metal cost
     * @param energyCost the consumer's TOTAL energy cost
     * @param df         the fraction of the whole job the consumer wants to complete this tick
     * @return the multiplier (0-1) to apply to df. The resources for df * multiplier have already been deducted,
     *         i.e. metalCost * df * multiplier metal and energyCost * df * multiplier energy were spent.
     *         Players without a resource pool (neutral/AI owners) and free consumers always get 1.
     */
    public static float requestFlow(String ownerName, float metalCost, float energyCost, float df) {
        if (df <= 0)
            return 1f;
        metalCost = Math.max(0, metalCost);
        energyCost = Math.max(0, energyCost);
        if (metalCost <= 0 && energyCost <= 0)
            return 1f;
        if (ownerName == null || ownerName.isEmpty())
            return 1f;
        Resources res = getResources(ownerName);
        if (res == null)
            return 1f;

        PlayerEconomy eco = getEconomy(ownerName);
        float wantMetal = metalCost * df;
        float wantEnergy = energyCost * df;
        eco.metalDemand += wantMetal;
        eco.energyDemand += wantEnergy;

        float granted = eco.stall;
        if (wantMetal > 0)
            granted = Math.min(granted, Math.max(0, res.getMetal()) / wantMetal);
        if (wantEnergy > 0)
            granted = Math.min(granted, Math.max(0, res.getEnergy()) / wantEnergy);
        granted = Math.max(0, Math.min(1, granted));
        if (granted <= 0)
            return 0f;

        float paidMetal = wantMetal * granted;
        float paidEnergy = wantEnergy * granted;
        res.addMetal(-paidMetal);
        res.addEnergy(-paidEnergy);
        eco.metalSpentWindow += paidMetal;
        eco.energySpentWindow += paidEnergy;
        return granted;
    }

    // refund resources (eg. a cancelled build or production), as whole numbers so the HUD can animate it
    public static void refund(String ownerName, float metal, float energy) {
        if (ownerName == null || ownerName.isEmpty())
            return;
        int m = Math.round(metal);
        int e = Math.round(energy);
        if (m > 0 || e > 0)
            ResourcesServerEvents.addSubtractResources(new Resources(ownerName, 0, Math.max(0, e), Math.max(0, m)));
    }

    // recompute income and storage from the buildings each player owns. Public for the GameTest.
    public static void recalculateIncomeAndStorage() {
        Map<String, Boolean> ownsCapitol = new HashMap<>();
        Map<String, float[]> totals = new HashMap<>(); // metalIncome, energyIncome, metalStorage, energyStorage, conversion

        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings()) {
            if (!bpl.isBuilt || bpl.ownerName == null || bpl.ownerName.isEmpty())
                continue;
            if (bpl.isCapitol)
                ownsCapitol.put(bpl.ownerName, true);
            float[] t = totals.computeIfAbsent(bpl.ownerName, k -> new float[5]);
            t[0] += bpl.getMetalIncome();
            t[1] += bpl.getEnergyIncome();
            t[2] += bpl.getMetalStorage();
            t[3] += bpl.getEnergyStorage();
            t[4] += bpl.getBuilding().getEnergyConversion();
        }
        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);
            float[] t = totals.getOrDefault(res.ownerName, new float[5]);
            boolean capitol = ownsCapitol.getOrDefault(res.ownerName, false);
            eco.metalIncome = (capitol ? BASE_METAL_INCOME : 0) + t[0];
            eco.energyIncome = (capitol ? BASE_ENERGY_INCOME : 0) + t[1];
            float metalStorage = DEFAULT_METAL_STORAGE + (capitol ? CAPITOL_METAL_STORAGE : 0) + t[2];
            float energyStorage = DEFAULT_ENERGY_STORAGE + (capitol ? CAPITOL_ENERGY_STORAGE : 0) + t[3];
            setStorage(res, eco, metalStorage, energyStorage);
            eco.conversionCapacity = t[4];
        }
    }

    /**
     * Change a player's storage. When it shrinks, what was held in the lost storage is lost with it (BAR): the pool
     * drops by up to the storage lost, but never below the new cap - so a refund sitting briefly above the cap is
     * not eaten by an unrelated loss. Sandbox pools (999999) are left alone.
     */
    private static void setStorage(Resources res, PlayerEconomy eco, float metalStorage, float energyStorage) {
        float metalLost = eco.metalStorage - metalStorage, energyLost = eco.energyStorage - energyStorage;
        eco.metalStorage = metalStorage;
        eco.energyStorage = energyStorage;
        if (res == null || (metalLost <= 0 && energyLost <= 0) || SandboxServer.isSandboxPlayer(res.ownerName))
            return;
        if (metalLost > 0 && res.getMetal() > metalStorage)
            res.addMetal(-Math.min(metalLost, res.getMetal() - metalStorage));
        if (energyLost > 0 && res.getEnergy() > energyStorage)
            res.addEnergy(-Math.min(energyLost, res.getEnergy() - energyStorage));
    }

    /**
     * A completed storage building died: drop its storage now (the 1 s recalc agrees, but the loss should land the
     * moment it blows up) and trim what it held.
     * @return {metal, energy} actually lost, for the burst FX
     */
    public static float[] onStorageLost(String ownerName, float metalStorage, float energyStorage) {
        Resources res = getResources(ownerName);
        if (res == null)
            return new float[2];
        PlayerEconomy eco = getEconomy(ownerName);
        float metalBefore = res.getMetal(), energyBefore = res.getEnergy();
        setStorage(res, eco, Math.max(DEFAULT_METAL_STORAGE, eco.metalStorage - metalStorage),
            Math.max(DEFAULT_ENERGY_STORAGE, eco.energyStorage - energyStorage));
        return new float[] { metalBefore - res.getMetal(), energyBefore - res.getEnergy() };
    }

    private static float room(String ownerName, boolean metal) {
        Resources res = getResources(ownerName);
        if (res == null)
            return 0;
        PlayerEconomy eco = getEconomy(ownerName);
        return metal ? eco.metalStorage - res.getMetal() : eco.energyStorage - res.getEnergy();
    }

    /**
     * BAR overflow: hand income that didn't fit in the owner's storage to allies with room, split evenly between
     * them (a second pass offers what a nearly-full ally couldn't take to the others). Enemies never receive
     * anything - only {@link AlliancesServerEvents#getAllAllies} are considered. Public for the GameTest.
     * @return how much the allies took; the rest is waste
     */
    public static float shareOverflow(String ownerName, float amount, boolean metal) {
        if (amount <= 0)
            return 0;
        Set<String> allies = AlliancesServerEvents.getAllAllies(ownerName);
        if (allies.isEmpty())
            return 0;
        float left = amount;
        for (int pass = 0; pass < 2 && left > 0.0001f; pass++) {
            int withRoom = 0;
            for (String ally : allies)
                if (room(ally, metal) > 0.0001f)
                    withRoom++;
            if (withRoom == 0)
                break;
            float share = left / withRoom;
            for (String ally : allies) {
                float give = Math.min(share, room(ally, metal));
                if (give <= 0.0001f)
                    continue;
                Resources res = getResources(ally);
                if (metal)
                    res.addMetal(give);
                else
                    res.addEnergy(give);
                left -= give;
            }
        }
        return amount - Math.max(0, left);
    }

    // called once per server tick (END phase, after every building and unit has ticked) by ResourcesServerEvents
    public static void onServerTickEnd() {
        tickCount += 1;
        if (tickCount % INCOME_RECALC_TICKS == 1)
            recalculateIncomeAndStorage();

        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);

            // income, capped at storage; what doesn't fit is offered to allies below, after everyone has banked
            float metalIn = eco.metalIncome / TICKS_PER_SECOND;
            float energyIn = eco.energyIncome / TICKS_PER_SECOND;
            eco.metalOverflowTick = 0;
            eco.energyOverflowTick = 0;
            if (metalIn > 0) {
                float banked = Math.max(0, Math.min(metalIn, eco.metalStorage - res.getMetal()));
                if (banked > 0) {
                    res.addMetal(banked);
                    eco.metalProducedWindow += banked;
                }
                eco.metalOverflowTick = metalIn - banked;
            }
            if (energyIn > 0) {
                float banked = Math.max(0, Math.min(energyIn, eco.energyStorage - res.getEnergy()));
                if (banked > 0)
                    res.addEnergy(banked);
                eco.energyOverflowTick = energyIn - banked;
            }

            convertEnergy(res, eco);

            // stall for the next tick, from the demand registered this tick
            float stallMetal = eco.metalDemand <= 0 ? 1f : Math.max(0, res.getMetal()) / eco.metalDemand;
            float stallEnergy = eco.energyDemand <= 0 ? 1f : Math.max(0, res.getEnergy()) / eco.energyDemand;
            eco.stall = Math.max(0, Math.min(1f, Math.min(stallMetal, stallEnergy)));
            eco.metalDemand = 0;
            eco.energyDemand = 0;

            // expense per second over a rolling 1 second window
            eco.windowTicks += 1;
            if (eco.windowTicks >= TICKS_PER_SECOND) {
                eco.metalExpense = eco.metalSpentWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyExpense = eco.energySpentWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.metalConverted = eco.metalConvertedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyConverted = eco.energyConvertedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.metalSpentWindow = 0;
                eco.energySpentWindow = 0;
                eco.metalConvertedWindow = 0;
                eco.energyConvertedWindow = 0;
                eco.metalShared = eco.metalSharedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyShared = eco.energySharedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.metalWasted = eco.metalWastedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyWasted = eco.energyWastedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.metalSharedWindow = eco.energySharedWindow = 0;
                eco.metalWastedWindow = eco.energyWastedWindow = 0;
                eco.windowTicks = 0;
                if (eco.metalProducedWindow > 0 || eco.metalReclaimedWindow > 0) {
                    RTSPlayer rtsPlayer = PlayerServerEvents.getRTSPlayer(res.ownerName);
                    if (rtsPlayer != null) {
                        rtsPlayer.metalProduced += eco.metalProducedWindow;
                        rtsPlayer.metalReclaimed += eco.metalReclaimedWindow;
                    }
                    eco.metalProducedWindow = 0;
                    eco.metalReclaimedWindow = 0;
                }
            }
        }
        // second pass: overflow to allies (BAR), measured after every player has banked its own income this tick.
        // Shared/wasted land in the window that is flushed next tick at the latest - close enough for a 1 s average
        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);
            if (eco.metalOverflowTick > 0) {
                float shared = shareOverflow(res.ownerName, eco.metalOverflowTick, true);
                eco.metalSharedWindow += shared;
                eco.metalWastedWindow += eco.metalOverflowTick - shared;
                eco.metalOverflowTick = 0;
            }
            if (eco.energyOverflowTick > 0) {
                float shared = shareOverflow(res.ownerName, eco.energyOverflowTick, false);
                eco.energySharedWindow += shared;
                eco.energyWastedWindow += eco.energyOverflowTick - shared;
                eco.energyOverflowTick = 0;
            }
        }
        if (tickCount % SYNC_INTERVAL_TICKS == 0)
            syncAll();
    }

    public static void syncAll() {
        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);
            EconomyClientboundPacket.sync(res, eco);
        }
    }

    public static void reset() {
        economies.clear();
    }
}
