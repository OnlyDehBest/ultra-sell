package it.onlynelchilling.ultrasell.cache;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.database.PlayerStats;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class CachedPlayer {

    private final UUID uuid;
    private final String name;
    private final double multiplier;
    private final PlayerStats stats;

    public CachedPlayer(UUID uuid, String name, double multiplier, PlayerStats stats) {
        this.uuid = uuid;
        this.name = name;
        this.multiplier = multiplier;
        this.stats = stats;
    }

    static double resolveMultiplier(UltraSell plugin, Player p) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isMultiplierEnabled() || cfg.getMultiplierEntries().isEmpty()) return 1.0;
        for (ConfigManager.MultiplierEntry e : cfg.getMultiplierEntries())
            if (p.hasPermission(e.permission())) return e.multiplier();
        return 1.0;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public double multiplier() { return multiplier; }
    public PlayerStats stats() { return stats; }
}

