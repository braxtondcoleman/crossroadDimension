package com.example.examplemod;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.example.examplemod.client.render.ClientRenderRegistration;
import com.example.examplemod.network.TravelNetwork;
import com.example.examplemod.item.SurveyScopeItem;
import com.example.examplemod.portal.CrossroadCrystalBlockEntity;
import com.example.examplemod.portal.CrossroadsCrystalBlock;
import com.example.examplemod.portal.RealmCrystalManager;
import com.example.examplemod.realm.PocketRealmManager;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(CrossroadDimension.MODID)
public class CrossroadDimension {
        // Define mod id in a common place for everything to reference
        public static final String MODID = "crossroaddimension";
        // Directly reference a slf4j logger
        public static final Logger LOGGER = LogUtils.getLogger();
        // Create a Deferred Register to hold Blocks which will all be registered under the "CrossroadDimension" namespace
        public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
        // Create a Deferred Register to hold Items which will all be registered under the "CrossroadDimension" namespace
        public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
        public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
        // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "CrossroadDimension" namespace
        public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
        private static final List<DeferredItem<SurveyScopeItem>> SURVEY_SCOPE_ITEMS = new ArrayList<>();

        // Creates a new Block with the id "CrossroadDimension:example_block", combining the namespace and path
        public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", p -> p.mapColor(MapColor.STONE));
        // Creates a new BlockItem with the id "CrossroadDimension:example_block", combining the namespace and path
        public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
        public static final DeferredBlock<Block> CROSSROADS_CRYSTAL = BLOCKS.registerBlock(
                "crossroads_gate",
                CrossroadsCrystalBlock::new,
                properties -> properties.mapColor(MapColor.COLOR_PURPLE).noCollision().lightLevel(state -> 10).strength(-1.0F, 3600000.0F)
        );
        public static final DeferredItem<BlockItem> CROSSROADS_CRYSTAL_ITEM = ITEMS.registerSimpleBlockItem("crossroads_gate", CROSSROADS_CRYSTAL);
        public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrossroadCrystalBlockEntity>> CROSSROADS_CRYSTAL_BLOCK_ENTITY =
                BLOCK_ENTITY_TYPES.register("crossroads_gate", () -> new BlockEntityType<>(CrossroadCrystalBlockEntity::new, CROSSROADS_CRYSTAL.get()));

        // Creates a new food item with the id "CrossroadDimension:example_id", nutrition 1 and saturation 2
        public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", p -> p.food(new FoodProperties.Builder()
                .alwaysEdible().nutrition(1).saturationModifier(2f).build()));
        public static final DeferredItem<SurveyScopeItem> COAL_SURVEY_SCOPE =
                registerSurveyScope("coal_survey_scope", Tags.Blocks.ORES_COAL,
                        0.25F, 0.25F, 0.25F);

        public static final DeferredItem<SurveyScopeItem> IRON_SURVEY_SCOPE =
                registerSurveyScope("iron_survey_scope", Tags.Blocks.ORES_IRON,
                        0.85F, 0.90F, 0.95F);

        public static final DeferredItem<SurveyScopeItem> COPPER_SURVEY_SCOPE =
                registerSurveyScope("copper_survey_scope", Tags.Blocks.ORES_COPPER,
                        0.78F, 0.42F, 0.23F);

        public static final DeferredItem<SurveyScopeItem> GOLD_SURVEY_SCOPE =
                registerSurveyScope("gold_survey_scope", Tags.Blocks.ORES_GOLD,
                        1.00F, 0.88F, 0.20F);

        public static final DeferredItem<SurveyScopeItem> REDSTONE_SURVEY_SCOPE =
                registerSurveyScope("redstone_survey_scope", Tags.Blocks.ORES_REDSTONE,
                        1.00F, 0.20F, 0.20F);

        public static final DeferredItem<SurveyScopeItem> LAPIS_SURVEY_SCOPE =
                registerSurveyScope("lapis_survey_scope", Tags.Blocks.ORES_LAPIS,
                        0.30F, 0.50F, 1.00F);

        public static final DeferredItem<SurveyScopeItem> DIAMOND_SURVEY_SCOPE =
                registerSurveyScope("diamond_survey_scope", Tags.Blocks.ORES_DIAMOND,
                        0.40F, 0.95F, 1.00F);

        public static final DeferredItem<SurveyScopeItem> EMERALD_SURVEY_SCOPE =
                registerSurveyScope("emerald_survey_scope", Tags.Blocks.ORES_EMERALD,
                        0.30F, 1.00F, 0.45F);

        public static final DeferredItem<SurveyScopeItem> QUARTZ_SURVEY_SCOPE =
                registerSurveyScope("quartz_survey_scope", Tags.Blocks.ORES_QUARTZ,
                        1.00F, 0.97F, 0.90F);

        public static final DeferredItem<SurveyScopeItem> ANCIENT_DEBRIS_SURVEY_SCOPE =
                registerSurveyScope("ancient_debris_survey_scope", Tags.Blocks.ORES_NETHERITE_SCRAP,
                        0.84F, 0.48F, 0.29F);
        public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SURVEY_WISP_PARTICLE =
                PARTICLE_TYPES.register("survey_wisp", () -> new SimpleParticleType(false));

          // Creates a creative tab with the id "CrossroadDimension:example_tab" for the example item, that is placed after the combat tab
        public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.CrossroadDimension")) //The language key for the title of your CreativeModeTab
                .withTabsBefore(CreativeModeTabs.COMBAT)
                .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                        output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
                        SURVEY_SCOPE_ITEMS.forEach(scope -> output.accept(scope.get()));
                }).build());

        private static DeferredItem<SurveyScopeItem> registerSurveyScope(
                String name,
                TagKey<Block> targetTag,
                float red,
                float green,
                float blue) {

                DeferredItem<SurveyScopeItem> scope = ITEMS.registerItem(
                        name,
                        properties -> new SurveyScopeItem(properties, targetTag, red, green, blue),
                        properties -> properties.durability(100));

                SURVEY_SCOPE_ITEMS.add(scope);
                return scope;
        }

        // The constructor for the mod class is the first code that is run when your mod is loaded.
        // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
        public CrossroadDimension(IEventBus modEventBus, ModContainer modContainer) {
                modEventBus.addListener(TravelNetwork::register);

                // Register the Deferred Register to the mod event bus so blocks get registered
                BLOCKS.register(modEventBus);
                // Register the Deferred Register to the mod event bus so items get registered
                ITEMS.register(modEventBus);
                PARTICLE_TYPES.register(modEventBus);
                BLOCK_ENTITY_TYPES.register(modEventBus);
                // Register the Deferred Register to the mod event bus so tabs get registered
                CREATIVE_MODE_TABS.register(modEventBus);

                // Register ourselves for server and other game events we are interested in.
                // Note that this is necessary if and only if we want *this* class (CrossroadDimension) to respond directly to events.
                // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
                NeoForge.EVENT_BUS.register(this);

                // Register the item to a creative tab
                modEventBus.addListener(this::addCreative);

                // Register our mod's ModConfigSpec so that FML can create and load the config file for us
                modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

                if (FMLEnvironment.getDist() == Dist.CLIENT) {
                modEventBus.addListener(ClientRenderRegistration::registerRenderers);
                }
        }

        // Add the example block item to the building blocks tab
        private void addCreative(BuildCreativeModeTabContentsEvent event) {
                if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
                event.accept(EXAMPLE_BLOCK_ITEM);
                event.accept(CROSSROADS_CRYSTAL_ITEM);
                }
        }

        @SubscribeEvent
        public void onServerStarted(ServerStartedEvent event) {
                new PocketRealmManager().initialize(event.getServer());
                new RealmCrystalManager().initialize(event.getServer());
        }
}
