package it.onlynelchilling.ultrasell.database;

public final class PlayerStats {
    private long itemsSold;
    private double moneyEarned;
    private volatile boolean autoSell;
    private volatile boolean autoPickup;

    public PlayerStats(long itemsSold, double moneyEarned) {
        this(itemsSold, moneyEarned, false, false);
    }

    public PlayerStats(long itemsSold, double moneyEarned, boolean autoSell) {
        this(itemsSold, moneyEarned, autoSell, false);
    }

    public PlayerStats(long itemsSold, double moneyEarned, boolean autoSell, boolean autoPickup) {
        this.itemsSold = itemsSold;
        this.moneyEarned = moneyEarned;
        this.autoSell = autoSell;
        this.autoPickup = autoPickup;
    }

    public long itemsSold() { return itemsSold; }
    public double moneyEarned() { return moneyEarned; }
    public boolean autoSell() { return autoSell; }
    public void setAutoSell(boolean v) { this.autoSell = v; }
    public boolean autoPickup() { return autoPickup; }
    public void setAutoPickup(boolean v) { this.autoPickup = v; }

    public void add(long items, double money) {
        this.itemsSold += items;
        this.moneyEarned += money;
    }
}
