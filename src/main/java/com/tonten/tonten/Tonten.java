package com.tonten.tonten;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(Tonten.MODID)
public class Tonten {
    public static final String MODID = "tonten";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<UtsusemiBlock> UTSUSEMI_BLOCK = BLOCKS.register(
            "utsusemi_block",
            () -> new UtsusemiBlock(BlockBehaviour.Properties.of().strength(0.2F, 0.2F).sound(SoundType.WOOD)));
    public static final RegistryObject<BlockItem> UTSUSEMI_BLOCK_ITEM = ITEMS.register(
            "utsusemi_block",
            () -> new UtsusemiBlockItem(UTSUSEMI_BLOCK.get(), new Item.Properties().durability(300).fireResistant()));
    public static final RegistryObject<SolidifySpaceBlock> SOLIDIFY_SPACE_BLOCK = BLOCKS.register(
            "solidify_space_block",
            () -> new SolidifySpaceBlock(BlockBehaviour.Properties.of().strength(1.0F, 1.0F).sound(SoundType.GLASS).lightLevel(state -> 15)));
    public static final RegistryObject<BlockItem> SOLIDIFY_SPACE_BLOCK_ITEM = ITEMS.register(
            "solidify_space_block",
            () -> new SolidifySpaceBlockItem(SOLIDIFY_SPACE_BLOCK.get(), new Item.Properties().durability(500).fireResistant()));

    public static final RegistryObject<TonkachiItem> WOODEN_TONKACHI = ITEMS.register(
            "wooden_tonkachi",
            () -> new TonkachiItem(TonkachiTier.WOOD, new Item.Properties().durability(96)));
    public static final RegistryObject<TonkachiItem> STONE_TONKACHI = ITEMS.register(
            "stone_tonkachi",
            () -> new TonkachiItem(TonkachiTier.STONE, new Item.Properties().durability(192)));
    public static final RegistryObject<TonkachiItem> COPPER_TONKACHI = ITEMS.register(
            "cupper_tonkachi",
            () -> new TonkachiItem(TonkachiTier.COPPER, new Item.Properties().durability(256)));
    public static final RegistryObject<TonkachiItem> IRON_TONKACHI = ITEMS.register(
            "iron_tonkachi",
            () -> new TonkachiItem(TonkachiTier.IRON, new Item.Properties().durability(384)));
    public static final RegistryObject<TonkachiItem> GOLDEN_TONKACHI = ITEMS.register(
            "golden_tonkachi",
            () -> new TonkachiItem(TonkachiTier.GOLD, new Item.Properties().durability(128)));
    public static final RegistryObject<TonkachiItem> DIAMOND_TONKACHI = ITEMS.register(
            "diamond_tonkachi",
            () -> new TonkachiItem(TonkachiTier.DIAMOND, new Item.Properties().durability(768)));
    public static final RegistryObject<Item> TONTEN_ICON = ITEMS.register("tonten_icon", () -> new Item(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TONTEN_TAB = CREATIVE_MODE_TABS.register("tonten_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tonten"))
            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .icon(() -> TONTEN_ICON.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(WOODEN_TONKACHI.get());
                output.accept(STONE_TONKACHI.get());
                output.accept(COPPER_TONKACHI.get());
                output.accept(IRON_TONKACHI.get());
                output.accept(GOLDEN_TONKACHI.get());
                output.accept(DIAMOND_TONKACHI.get());
                output.accept(UTSUSEMI_BLOCK_ITEM.get());
                output.accept(SOLIDIFY_SPACE_BLOCK_ITEM.get());
            })
            .build());

    public Tonten() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TontenConfig.COMMON_SPEC);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(TontenEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        TontenNetwork.register();
        LOGGER.info("Tonten common setup complete");
    }

    public static boolean isTonkachi(Item item) {
        return item instanceof TonkachiItem;
    }

    public static boolean isUtsusemiBlockItem(Item item) {
        return item == UTSUSEMI_BLOCK_ITEM.get();
    }

    public static boolean isSolidifySpaceBlockItem(Item item) {
        return item == SOLIDIFY_SPACE_BLOCK_ITEM.get();
    }
}
