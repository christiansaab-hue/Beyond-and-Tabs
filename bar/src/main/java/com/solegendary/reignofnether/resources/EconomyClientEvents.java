package com.solegendary.reignofnether.resources;

import java.util.HashMap;
import java.util.Map;

// clientside copy of each player's BAR flow economy state, for the HUD resource bar and production prediction
public class EconomyClientEvents {

    public static class ClientEconomy {
        public float metalStorage = EconomyServerEvents.DEFAULT_METAL_STORAGE;
        public float energyStorage = EconomyServerEvents.DEFAULT_ENERGY_STORAGE;
        public float metalIncome = 0;
        public float energyIncome = 0;
        public float metalExpense = 0;
        public float energyExpense = 0;
        public float stall = 1f;
        public float energyConverted = 0;
        public float metalConverted = 0;
        public float conversionCapacity = 0;
    }

    private static final Map<String, ClientEconomy> economies = new HashMap<>();

    public static ClientEconomy getEconomy(String ownerName) {
        if (ownerName == null)
            return new ClientEconomy();
        return economies.computeIfAbsent(ownerName, k -> new ClientEconomy());
    }

    // efficiency of this player's construction/production (1 = full speed)
    public static float getStall(String ownerName) {
        ClientEconomy eco = ownerName == null ? null : economies.get(ownerName);
        return eco == null ? 1f : eco.stall;
    }

    public static void sync(EconomyClientboundPacket packet) {
        ClientEconomy eco = getEconomy(packet.ownerName);
        eco.metalStorage = packet.metalStorage;
        eco.energyStorage = packet.energyStorage;
        eco.metalIncome = packet.metalIncome;
        eco.energyIncome = packet.energyIncome;
        eco.metalExpense = packet.metalExpense;
        eco.energyExpense = packet.energyExpense;
        eco.stall = packet.stall;
        eco.energyConverted = packet.energyConverted;
        eco.metalConverted = packet.metalConverted;
        eco.conversionCapacity = packet.conversionCapacity;

        // continuous spending doesn't send ADD_SUBTRACT packets, so set the absolute amounts here.
        // Any pending HUD animation (xToAdd) is kept so the displayed value still ends up at the server value.
        Resources res = ResourcesClientEvents.getResources(packet.ownerName);
        if (res != null) {
            res.ore = packet.metal - res.oreToAdd;
            res.wood = packet.energy - res.woodToAdd;
        }
    }
}
