package com.tonten.tonten;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

public final class TontenEvents {
    private TontenEvents() {
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack output = event.getOutput();
        if (!isUnbreakingOnlyItem(event.getLeft()) && !isUnbreakingOnlyItem(output)) {
            return;
        }

        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(output);
        for (var entry : enchantments.entrySet()) {
            net.minecraft.core.Holder<Enchantment> enchantment = entry.getKey();
            if (!enchantment.is(Enchantments.UNBREAKING)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    private static boolean isUnbreakingOnlyItem(ItemStack stack) {
        return stack.getItem() instanceof TonkachiItem
                || stack.getItem() instanceof UtsusemiBlockItem
                || stack.getItem() instanceof SolidifySpaceBlockItem;
    }
}
