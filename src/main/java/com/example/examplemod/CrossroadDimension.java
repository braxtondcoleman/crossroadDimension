package com.example.examplemod;

import org.slf4j.Logger;

import com.example.examplemod.client.render.ClientRenderRegistration;
import com.example.examplemod.network.TravelNetwork;
import com.example.examplemod.item.WispJarData;
import com.example.examplemod.item.WispJarItem;
import com.example.examplemod.portal.CrossroadCrystalBlockEntity;
import com.example.examplemod.portal.CrossroadsCrystalBlock;
import com.example.examplemod.portal.RealmCrystalManager;
import com.example.examplemod.realm.PocketRealmManager;
import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
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
        public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
                DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
        public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
        public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
        public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
        // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "CrossroadDimension" namespace
        public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

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
        public static final DeferredHolder<DataComponentType<?>, DataComponentType<WispJarData>> WISP_JAR_DATA =
                DATA_COMPONENT_TYPES.register("wisp_jar_data", () -> DataComponentType.<WispJarData>builder()
                        .persistent(WispJarData.CODEC)
                        .networkSynchronized(WispJarData.STREAM_CODEC)
                        .build());
        public static final DeferredItem<WispJarItem> WISP_JAR = ITEMS.registerItem(
                "wisp_jar",
                WispJarItem::new,
                properties -> properties.durability(100)
                        .component(WISP_JAR_DATA.get(), WispJarData.EMPTY));
        public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WISP_BREADCRUMB_PARTICLE =
                PARTICLE_TYPES.register("wisp_breadcrumb", () -> new SimpleParticleType(false));
        public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_IDLE_LOOP = registerSound("crystal_idle_loop");
        public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_REFORM = registerSound("crystal_reform");
        public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL_SLAM = registerSound("crystal_slam");
        public static final DeferredHolder<SoundEvent, SoundEvent> WISP_ATTUNEMENT = registerSound("wisp_attunement");

          // Creates a creative tab with the id "CrossroadDimension:example_tab" for the example item, that is placed after the combat tab
        public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.CrossroadDimension")) //The language key for the title of your CreativeModeTab
                .withTabsBefore(CreativeModeTabs.COMBAT)
                .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                        output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
                        output.accept(WISP_JAR.get());
                }).build());

        // The constructor for the mod class is the first code that is run when your mod is loaded.
        // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
        public CrossroadDimension(IEventBus modEventBus, ModContainer modContainer) {
                modEventBus.addListener(TravelNetwork::register);

                // Register the Deferred Register to the mod event bus so blocks get registered
                BLOCKS.register(modEventBus);
                // Register the Deferred Register to the mod event bus so items get registered
                DATA_COMPONENT_TYPES.register(modEventBus);
                ITEMS.register(modEventBus);
                PARTICLE_TYPES.register(modEventBus);
                SOUND_EVENTS.register(modEventBus);
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

        private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
                return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                        Identifier.fromNamespaceAndPath(MODID, name)));
        }
}
