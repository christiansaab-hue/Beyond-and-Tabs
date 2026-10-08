package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;

public class ActiveProduction {
    public boolean completed;
    public float ticksLeft;
    public ProductionItem item;
    // BAR flow economy: resources actually paid so far (serverside), refunded if the item is cancelled
    public float metalSpent = 0;
    public float energySpent = 0;
    // fraction of the cost paid, only used for items with a production time of 0 ticks
    public float paidFraction = 0;
    public ActiveProduction(ProductionItem item, boolean isClientside, String ownerName) {
        this.item = item;
        this.ticksLeft = item.getCost(isClientside, ownerName).ticks;
    }

    public void complete(ProductionPlacement placement) {
        this.item.recordScore(placement);
        this.item.onComplete.accept(placement.getLevel(), placement);
        this.completed = true;
    }
}
