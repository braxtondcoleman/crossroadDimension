package com.example.examplemod.item;

import java.util.Arrays;
import java.util.Optional;

import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;

public enum WispAttunement {
    COAL("coal", Tags.Blocks.ORES_COAL, Items.COAL, 0.25F, 0.25F, 0.25F),
    IRON("iron", Tags.Blocks.ORES_IRON, Items.IRON_INGOT, 0.85F, 0.90F, 0.95F),
    COPPER("copper", Tags.Blocks.ORES_COPPER, Items.COPPER_INGOT, 0.78F, 0.42F, 0.23F),
    GOLD("gold", Tags.Blocks.ORES_GOLD, Items.GOLD_INGOT, 1.00F, 0.88F, 0.20F),
    REDSTONE("redstone", Tags.Blocks.ORES_REDSTONE, Items.REDSTONE, 1.00F, 0.20F, 0.20F),
    LAPIS("lapis", Tags.Blocks.ORES_LAPIS, Items.LAPIS_LAZULI, 0.30F, 0.50F, 1.00F),
    DIAMOND("diamond", Tags.Blocks.ORES_DIAMOND, Items.DIAMOND, 0.40F, 0.95F, 1.00F),
    EMERALD("emerald", Tags.Blocks.ORES_EMERALD, Items.EMERALD, 0.30F, 1.00F, 0.45F),
    QUARTZ("quartz", Tags.Blocks.ORES_QUARTZ, Items.QUARTZ, 1.00F, 0.97F, 0.90F),
    ANCIENT_DEBRIS("ancient_debris", Tags.Blocks.ORES_NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
            0.84F, 0.48F, 0.29F);

    private final String id;
    private final TagKey<Block> targetTag;
    private final Item material;
    private final float red;
    private final float green;
    private final float blue;

    WispAttunement(String id, TagKey<Block> targetTag, Item material, float red, float green, float blue) {
        this.id = id;
        this.targetTag = targetTag;
        this.material = material;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    public String id() {
        return this.id;
    }

    public TagKey<Block> targetTag() {
        return this.targetTag;
    }

    public Item material() {
        return this.material;
    }

    public float red() {
        return this.red;
    }

    public float green() {
        return this.green;
    }

    public float blue() {
        return this.blue;
    }

    public Component displayName() {
        return Component.translatable("attunement.crossroaddimension." + this.id);
    }

    public static Optional<WispAttunement> byId(String id) {
        return Arrays.stream(values()).filter(attunement -> attunement.id.equals(id)).findFirst();
    }

    public static Optional<WispAttunement> fromMaterial(ItemStack stack) {
        return Arrays.stream(values()).filter(attunement -> stack.is(attunement.material)).findFirst();
    }
}
