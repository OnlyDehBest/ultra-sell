package it.onlynelchilling.ultrasell.cache;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.database.PlayerStats;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerCache {

    private final UltraSell plugin;
    private final Map<UUID, CachedPlayer> players = new ConcurrentHashMap<>();

    public PlayerCache(UltraSell plugin) {
        this.plugin = plugin;
    }

    public void loadAsync(Player player) {
        UUID id = player.getUniqueId();
        if (players.containsKey(id)) return;
        double mul = CachedPlayer.resolveMultiplier(plugin, player);
        plugin.getDatabaseManager().load(id).thenAccept(stats ->
                players.computeIfAbsent(id, k -> new CachedPlayer(k, player.getName(), mul, stats)));
    }

    public CachedPlayer get(Player player) {
        return players.computeIfAbsent(player.getUniqueId(),
                id -> new CachedPlayer(id, player.getName(),
                        CachedPlayer.resolveMultiplier(plugin, player), new PlayerStats(0, 0)));
    }

    public double multiplier(Player player) {
        return get(player).multiplier();
    }

    public void unload(UUID id) {
        CachedPlayer cp = players.remove(id);
        if (cp != null) plugin.getDatabaseManager().saveAsync(id, cp.name(), cp.stats());
    }

    public void flushAll() {
        players.forEach((id, cp) -> plugin.getDatabaseManager().save(id, cp.name(), cp.stats()));
        players.clear();
    }

    public Map<UUID, CachedPlayer> all() { return players; }
}

