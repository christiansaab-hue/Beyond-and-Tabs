package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * BAR-style "graphs" for the results screen: one player's metal income, energy income and army value (summed metal
 * cost of their living units) sampled every {@link #SAMPLE_TICKS} of match time. Lives on the RTSPlayer, so a fresh
 * one starts every match; a defeated player's history simply stops growing.
 *
 * Bounded: at most {@link #MAX_SAMPLES} points. When full, every other point is dropped and the interval doubles, so
 * a 2 hour match still covers its whole length in the same few hundred bytes (BAR's graphs work the same way).
 * Not saved with the world: losing it on a reload only shortens the graph.
 */
public class MatchHistory {
    public static final int SAMPLE_TICKS = 30 * 20;
    public static final int MAX_SAMPLES = 64;   // even: compaction keeps the timeline aligned (see offer)

    public final float[] metal = new float[MAX_SAMPLES];
    public final float[] energy = new float[MAX_SAMPLES];
    public final float[] army = new float[MAX_SAMPLES];
    private int size = 0;
    private int intervalTicks = SAMPLE_TICKS;
    private int offered = 0;   // global samples seen, so a doubled interval keeps every other one

    public int size() {
        return size;
    }

    public int getIntervalTicks() {
        return intervalTicks;
    }

    /** One global sample tick (every SAMPLE_TICKS). Recorded only if it falls on this history's current interval. */
    public void offer(float metalIncome, float energyIncome, float armyValue) {
        int stride = intervalTicks / SAMPLE_TICKS;
        int k = offered++;
        if (k % stride != 0)
            return;
        if (size >= MAX_SAMPLES) {
            // sample k = MAX_SAMPLES * stride is a multiple of the doubled stride too, so it is kept after compacting
            for (int i = 0; i < MAX_SAMPLES / 2; i++) {
                metal[i] = metal[i * 2];
                energy[i] = energy[i * 2];
                army[i] = army[i * 2];
            }
            size = MAX_SAMPLES / 2;
            intervalTicks *= 2;
        }
        metal[size] = sane(metalIncome);
        energy[size] = sane(energyIncome);
        army[size] = sane(armyValue);
        size++;
    }

    private static float sane(float v) {
        return Float.isFinite(v) && v > 0 ? v : 0;
    }

    public float[] metalSamples() { return java.util.Arrays.copyOf(metal, size); }
    public float[] energySamples() { return java.util.Arrays.copyOf(energy, size); }
    public float[] armySamples() { return java.util.Arrays.copyOf(army, size); }

    /** Army value per owner: summed metal cost of every living unit. One pass over all units, every 30 s. */
    public static Map<String, Float> armyValues() {
        Map<String, Float> out = new HashMap<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit u) || !le.isAlive())
                continue;
            String owner = u.getOwnerName();
            if (owner == null || owner.isEmpty())
                continue;
            ResourceCost cost = u.getCost();
            if (cost != null && cost.metal() > 0)
                out.merge(owner, (float) cost.metal(), Float::sum);
        }
        return out;
    }

    /** Takes one sample for every player still in the match. Called by PlayerServerEvents every SAMPLE_TICKS. */
    public static void sampleAll(Collection<RTSPlayer> players) {
        Map<String, Float> armies = armyValues();
        for (RTSPlayer p : players) {
            if (p == null || p.name == null)
                continue;
            EconomyServerEvents.PlayerEconomy eco = EconomyServerEvents.getEconomy(p.name);
            // metal income as the HUD shows it: base + buildings + what converters made
            p.history.offer(eco.metalIncome + eco.metalConverted, eco.energyIncome, armies.getOrDefault(p.name, 0f));
        }
    }
}
