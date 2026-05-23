package com.tonten.tonten;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class TontenEvents {
    private TontenEvents() {
    }

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack output = event.getOutput();
        if (!isUnbreakingOnlyItem(event.getLeft()) && !isUnbreakingOnlyItem(output)) {
            return;
        }

        var enchantments = EnchantmentHelper.getEnchantments(output);
        for (var entry : enchantments.entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (enchantment != Enchantments.UNBREAKING) {
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
