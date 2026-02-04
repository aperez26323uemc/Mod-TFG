package com.uemc.assistance_drone.advancements;

import com.uemc.assistance_drone.AssistanceDrone;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCriteriaTriggers {
    public static final DeferredRegister<net.minecraft.advancements.CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, AssistanceDrone.MODID);

    // Este trigger se disparará cuando el jugador esté siendo afectado por el mismo beacon que le da Haste a un dron
    public static final Supplier<PlayerTrigger> DRONE_HASTE_MINING = TRIGGERS.register("drone_haste_mining", PlayerTrigger::new);

    public static void register(IEventBus eventBus) {
        TRIGGERS.register(eventBus);
    }
}