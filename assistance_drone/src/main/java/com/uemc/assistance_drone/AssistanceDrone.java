package com.uemc.assistance_drone;

import com.uemc.assistance_drone.advancements.ModCriteriaTriggers;
import com.uemc.assistance_drone.entities.ModEntities;
import com.uemc.assistance_drone.items.ModItems;
import com.uemc.assistance_drone.menus.ModMenus;
import com.uemc.assistance_drone.sounds.ModSounds;
import com.uemc.assistance_drone.util.SiteMarkersRegister;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(AssistanceDrone.MODID)
public class AssistanceDrone
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "assistance_drone";

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public AssistanceDrone(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for mod loading
        modEventBus.addListener(this::commonSetup);
        // Register the Deferred Register to the mod event bus so blocks get registered
        // ModBlocks.BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ModItems.ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so data components get registered
        SiteMarkersRegister.DATA_COMPONENTS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        ModItems.CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so entities get registered
        ModEntities.ENTITY_TYPES.register(modEventBus);

        ModMenus.MENU_TYPES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModCriteriaTriggers.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {}
}