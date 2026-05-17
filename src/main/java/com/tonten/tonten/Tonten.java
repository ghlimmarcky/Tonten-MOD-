package com.tonten.tonten;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Tonten.MODID)
public class Tonten {
    public static final String MODID = "tonten";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredBlock<UtsusemiBlock> UTSUSEMI_BLOCK = BLOCKS.registerBlock(
            "utsusemi_block",
            UtsusemiBlock::new,
            properties -> properties.strength(0.2F, 0.2F).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> UTSUSEMI_BLOCK_ITEM = ITEMS.registerItem(
            "utsusemi_block",
            properties -> new UtsusemiBlockItem(UTSUSEMI_BLOCK.get(), properties),
            properties -> properties.durability(300).repairable(ItemTags.LOGS).enchantable(5).fireResistant());
    public static final DeferredBlock<SolidifySpaceBlock> SOLIDIFY_SPACE_BLOCK = BLOCKS.registerBlock(
            "solidify_space_block",
            SolidifySpaceBlock::new,
            properties -> properties.strength(1.0F, 1.0F).sound(SoundType.GLASS).lightLevel(state -> 15));
    public static final DeferredItem<BlockItem> SOLIDIFY_SPACE_BLOCK_ITEM = ITEMS.registerItem(
            "solidify_space_block",
            properties -> new SolidifySpaceBlockItem(SOLIDIFY_SPACE_BLOCK.get(), properties),
            properties -> properties.durability(500).repairable(Items.GOLD_INGOT).enchantable(5).fireResistant());

    public static final DeferredItem<TonkachiItem> WOODEN_TONKACHI = ITEMS.registerItem(
            "wooden_tonkachi",
            properties -> new TonkachiItem(TonkachiTier.WOOD, properties),
            properties -> properties.durability(96).repairable(net.minecraft.tags.ItemTags.LOGS).enchantable(5));
    public static final DeferredItem<TonkachiItem> STONE_TONKACHI = ITEMS.registerItem(
            "stone_tonkachi",
            properties -> new TonkachiItem(TonkachiTier.STONE, properties),
            properties -> properties.durability(192).repairable(Items.COBBLESTONE).enchantable(8));
    public static final DeferredItem<TonkachiItem> IRON_TONKACHI = ITEMS.registerItem(
            "iron_tonkachi",
            properties -> new TonkachiItem(TonkachiTier.IRON, properties),
            properties -> properties.durability(384).repairable(Items.IRON_INGOT).enchantable(14));
    public static final DeferredItem<TonkachiItem> DIAMOND_TONKACHI = ITEMS.registerItem(
            "diamond_tonkachi",
            properties -> new TonkachiItem(TonkachiTier.DIAMOND, properties),
            properties -> properties.durability(768).repairable(Items.DIAMOND).enchantable(10));
    public static final DeferredItem<Item> TONTEN_ICON = ITEMS.registerSimpleItem("tonten_icon");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TONTEN_TAB = CREATIVE_MODE_TABS.register("tonten_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tonten"))
            .withTabsBefore(CreativeModeTabs.TOOLS_AND_UTILITIES)
            .icon(() -> TONTEN_ICON.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(WOODEN_TONKACHI.get());
                output.accept(STONE_TONKACHI.get());
                output.accept(IRON_TONKACHI.get());
                output.accept(DIAMOND_TONKACHI.get());
                output.accept(UTSUSEMI_BLOCK_ITEM.get());
                output.accept(SOLIDIFY_SPACE_BLOCK_ITEM.get());
            })
            .build());

    public Tonten(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(TontenNetwork::registerPayloads);
        modContainer.registerConfig(ModConfig.Type.COMMON, TontenConfig.COMMON_SPEC);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        NeoForge.EVENT_BUS.register(TontenEvents.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
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
