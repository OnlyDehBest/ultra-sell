package it.onlynelchilling.ultrasell.listeners;

import it.onlynelchilling.ultrasell.UltraSell;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

    private final UltraSell plugin;

    public PlayerJoinListener(UltraSell plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getPlayerCache().loadAsync(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerCache().unload(event.getPlayer().getUniqueId());
    }
}

