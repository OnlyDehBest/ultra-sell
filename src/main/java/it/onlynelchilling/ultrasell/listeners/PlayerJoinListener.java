package it.onlynelchilling.ultrasell.listeners;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.utils.SchedulerUtil;
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
        SchedulerUtil.runAsync(plugin, () -> plugin.getPlayerCache().loadAsync(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerCache().unload(event.getPlayer().getUniqueId());
    }
}

