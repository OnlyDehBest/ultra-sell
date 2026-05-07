package it.onlynelchilling.ultrasell.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.database.PlayerStats;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

public final class PlayerCache {

    private final UltraSell plugin;
    private final Cache<UUID, CachedPlayer> players = Caffeine.newBuilder().build();
    private final ConcurrentMap<UUID, CachedPlayer> map = players.asMap();

    public PlayerCache(UltraSell plugin) {
        this.plugin = plugin;
    }

    public void loadAsync(Player player) {
        UUID id = player.getUniqueId();
        if (map.containsKey(id)) return;
        double mul = CachedPlayer.resolveMultiplier(plugin, player);
        plugin.getDatabaseManager().load(id).thenAccept(stats ->
                map.computeIfAbsent(id, k -> new CachedPlayer(k, player.getName(), mul, stats)));
    }

    public CachedPlayer get(Player player) {
        return map.computeIfAbsent(player.getUniqueId(),
                id -> new CachedPlayer(id, player.getName(),
                        CachedPlayer.resolveMultiplier(plugin, player), new PlayerStats(0, 0)));
    }

    public double multiplier(Player player) {
        return get(player).multiplier();
    }

    public void unload(UUID id) {
        CachedPlayer cp = map.remove(id);
        if (cp != null) plugin.getDatabaseManager().saveAsync(id, cp.name(), cp.stats());
    }

    public void flushAll() {
        map.forEach((id, cp) -> plugin.getDatabaseManager().save(id, cp.name(), cp.stats()));
        players.invalidateAll();
    }

    public Map<UUID, CachedPlayer> all() { return map; }
}
