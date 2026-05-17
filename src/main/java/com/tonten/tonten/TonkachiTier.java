package com.tonten.tonten;

public enum TonkachiTier {
    WOOD(1, 5, false),
    STONE(3, 10, false),
    IRON(5, 20, false),
    DIAMOND(7, 30, true);

    private final int flatSize;
    private final int lineLimit;
    private final boolean airPlace;

    TonkachiTier(int flatSize, int lineLimit, boolean airPlace) {
        this.flatSize = flatSize;
        this.lineLimit = lineLimit;
        this.airPlace = airPlace;
    }

    public int flatSize() {
        return this.flatSize;
    }

    public int lineLimit() {
        return this.lineLimit;
    }

    public boolean canAirPlace() {
        return this.airPlace;
    }
}
