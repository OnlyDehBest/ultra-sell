package it.onlynelchilling.ultrasell.database;

public final class PlayerStats {
    private long itemsSold;
    private double moneyEarned;
    private volatile boolean autoSell;

    public PlayerStats(long itemsSold, double moneyEarned) {
        this(itemsSold, moneyEarned, false);
    }

    public PlayerStats(long itemsSold, double moneyEarned, boolean autoSell) {
        this.itemsSold = itemsSold;
        this.moneyEarned = moneyEarned;
        this.autoSell = autoSell;
    }

    public long itemsSold() { return itemsSold; }
    public double moneyEarned() { return moneyEarned; }
    public boolean autoSell() { return autoSell; }
    public void setAutoSell(boolean v) { this.autoSell = v; }

    public void add(long items, double money) {
        this.itemsSold += items;
        this.moneyEarned += money;
    }
}
