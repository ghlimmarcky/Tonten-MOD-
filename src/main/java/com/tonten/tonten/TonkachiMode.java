package com.tonten.tonten;

import net.minecraft.network.chat.Component;

public enum TonkachiMode {
    FLAT("flat"),
    VERTICAL("vertical"),
    VERTICAL_UP("vertical_up"),
    VERTICAL_DOWN("vertical_down"),
    STAIRS("stairs"),
    EXTEND("extend"),
    UPSIDE_DOWN("upside_down"),
    ROTATE("rotate"),
    SPACING("spacing"),
    RANDOM("random"),
    FRAME("frame"),
    AIR("air"),
    VERTICAL_LEFT("vertical_left"),
    VERTICAL_RIGHT("vertical_right");

    private static final TonkachiMode[] DIAMOND_MODES = { FLAT, VERTICAL, STAIRS, EXTEND, AIR };
    private static final TonkachiMode[] COPPER_MODES = { FLAT, VERTICAL, STAIRS, EXTEND, SPACING, RANDOM };
    private static final TonkachiMode[] GOLD_MODES = { FLAT, VERTICAL, VERTICAL_LEFT, VERTICAL_RIGHT, STAIRS, EXTEND, FRAME };
    private static final TonkachiMode[] IRON_MODES = { FLAT, VERTICAL, VERTICAL_UP, VERTICAL_DOWN, STAIRS, EXTEND };
    private static final TonkachiMode[] STONE_MODES = { FLAT, VERTICAL, STAIRS, EXTEND, UPSIDE_DOWN, ROTATE };
    private static final TonkachiMode[] STANDARD_MODES = { FLAT, VERTICAL, STAIRS, EXTEND };
    private static final TonkachiMode[] WOOD_MODES = { VERTICAL, STAIRS, EXTEND };
    private final String key;

    TonkachiMode(String key) {
        this.key = key;
    }

    public Component displayName() {
        return Component.translatable("mode.tonten." + this.key);
    }

    public TonkachiMode cycle(int direction, TonkachiTier tier) {
        TonkachiMode[] available = availableModes(tier);
        int index = -1;
        for (int i = 0; i < available.length; i++) {
            if (available[i] == this) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return available[0];
        }
        int next = Math.floorMod(index + direction, available.length);
        return available[next];
    }

    private static TonkachiMode[] availableModes(TonkachiTier tier) {
        if (tier == TonkachiTier.WOOD) {
            return WOOD_MODES;
        }
        if (tier == TonkachiTier.COPPER) {
            return COPPER_MODES;
        }
        if (tier == TonkachiTier.IRON) {
            return IRON_MODES;
        }
        if (tier == TonkachiTier.GOLD) {
            return GOLD_MODES;
        }
        if (tier == TonkachiTier.STONE) {
            return STONE_MODES;
        }
        return tier.canAirPlace() ? DIAMOND_MODES : STANDARD_MODES;
    }

    public static TonkachiMode byOrdinal(int ordinal) {
        TonkachiMode[] modes = values();
        if (ordinal < 0 || ordinal >= modes.length) {
            return FLAT;
        }
        return modes[ordinal];
    }
}
