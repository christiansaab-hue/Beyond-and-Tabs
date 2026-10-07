package dev.beyondtabs.mod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(BeyondTabs.MODID)
public final class BeyondTabs {
    public static final String MODID = "beyondtabs";
    public static final Logger LOG = LoggerFactory.getLogger("BeyondTabs");

    public BeyondTabs() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
    }

    private void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(Network::register);
        LOG.info("Beyond and Tabs loaded: engine with {} unit types", dev.beyondtabs.engine.gen.UnitDef.ALL.size());
    }
}
