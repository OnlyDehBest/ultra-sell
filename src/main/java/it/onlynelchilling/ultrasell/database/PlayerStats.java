package it.onlynelchilling.ultrasell.database;

public final class PlayerStats {
    private long itemsSold;
    private double moneyEarned;

    public PlayerStats(long itemsSold, double moneyEarned) {
        this.itemsSold = itemsSold;
        this.moneyEarned = moneyEarned;
    }

    public long itemsSold() { return itemsSold; }
    public double moneyEarned() { return moneyEarned; }

    public void add(long items, double money) {
        this.itemsSold += items;
        this.moneyEarned += money;
    }
}

