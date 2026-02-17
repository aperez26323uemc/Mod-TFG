package com.uemc.pickup_dumper;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tag keys defined by the pickup-dumper addon.
 *
 * <p>Tag JSON files live under {@code data/pickup_dumper/tags/blocks/}.
 * Players and pack authors can extend any tag via a datapack without
 * touching mod code.</p>
 */
public final class ModTags {

    private ModTags() {}

    /**
     * Whitelist of blocks the drone is allowed to deposit items into.
     *
     * <p>The tag file aggregates community convention tags ({@code #c:chests},
     * {@code #c:barrels}, {@code #c:shulker_boxes}) so any well-behaved mod
     * that contributes to those tags is automatically supported. Processing
     * machines (furnaces, Mekanism machines, Create contraptions, etc.) are
     * excluded by omission — they simply do not appear in any storage tag.</p>
     *
     * <p>Tag location: {@code data/pickup_dumper/tags/blocks/valid_dump_containers.json}</p>
     */
    public static final TagKey<Block> VALID_DUMP_CONTAINERS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("pickup_dumper", "valid_dump_containers")
    );
}
