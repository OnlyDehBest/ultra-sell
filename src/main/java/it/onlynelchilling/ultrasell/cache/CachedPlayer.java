package it.onlynelchilling.ultrasell.cache;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.database.PlayerStats;
import org.bukkit.entity.Player;

import java.util.UUID;

public record CachedPlayer(UUID uuid, String name, double multiplier, PlayerStats stats) {

    static double resolveMultiplier(UltraSell plugin, Player p) {
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isMultiplierEnabled() || cfg.getMultiplierEntries().isEmpty()) return 1.0;
        for (ConfigManager.MultiplierEntry e : cfg.getMultiplierEntries())
            if (p.hasPermission(e.permission())) return e.multiplier();
        return 1.0;
    }
}

