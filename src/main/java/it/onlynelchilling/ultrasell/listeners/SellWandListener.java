package it.onlynelchilling.ultrasell.listeners;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager.SellWandSettings;
import it.onlynelchilling.ultrasell.wand.SellWandManager;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SellWandListener implements Listener {

    private final UltraSell plugin;
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public SellWandListener(UltraSell plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    private void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        SellWandManager wand = plugin.getSellWandManager();
        if (!wand.isWand(item)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        SellWandSettings cfg = plugin.getConfigManager().getSellWand();

        if (!cfg.enabled()) {
            plugin.getMessageUtils().send(player, "wand-disabled-global");
            return;
        }

        if (!player.hasPermission(SellWandManager.USE_PERMISSION)) {
            plugin.getMessageUtils().send(player, "wand-no-permission");
            return;
        }

        if (event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof Container container)) {
            return;
        }

        if (cfg.cooldownMillis() > 0) {
            long now = System.currentTimeMillis();
            Long last = lastUse.put(player.getUniqueId(), now);
            if (last != null && now - last < cfg.cooldownMillis()) return;
        }

        wand.sellContainer(player, container.getInventory());
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }
}
