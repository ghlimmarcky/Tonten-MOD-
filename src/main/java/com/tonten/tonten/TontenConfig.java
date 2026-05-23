package com.tonten.tonten;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TontenConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.IntValue SOLIDIFY_SPACE_BLOCK_LIFETIME_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("solidify_space_block");
        SOLIDIFY_SPACE_BLOCK_LIFETIME_TICKS = builder
                .comment("Lifetime of placed Solidify Space Blocks in ticks. 20 ticks = 1 second. Default is 30 minutes.")
                .defineInRange("lifetimeTicks", 30 * 60 * 20, 20, 24 * 60 * 60 * 20);
        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private TontenConfig() {
    }
}
