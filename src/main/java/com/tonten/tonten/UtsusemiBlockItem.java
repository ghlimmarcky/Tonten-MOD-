package com.tonten.tonten;

import net.minecraft.core.Holder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;

public class UtsusemiBlockItem extends BlockItem {
    public UtsusemiBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.FAIL;
    }

    @Override
    public boolean isPrimaryItemFor(ItemStack stack, Holder<Enchantment> enchantment) {
        return isAllowedEnchantment(enchantment);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return isAllowedEnchantment(enchantment);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(ItemTags.LOGS);
    }

    private static boolean isAllowedEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.is(Enchantments.UNBREAKING);
    }
}
