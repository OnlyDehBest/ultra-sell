package it.onlynelchilling.ultrasell.listeners;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.cache.CachedPlayer;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.config.ConfigManager.SoundEntry;
import it.onlynelchilling.ultrasell.utils.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;

public final class AutoPickupListener implements Listener {

    private static final String PERMISSION = "ultrasell.pickup";

    private final UltraSell plugin;

    public AutoPickupListener(UltraSell plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfigManager().isAutoPickupEnabled()) return;
        if (!event.isDropItems()) return;

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        if (!player.hasPermission(PERMISSION)) return;

        CachedPlayer cp = plugin.getPlayerCache().get(player);
        if (cp == null || !cp.stats().autoPickup()) return;

        Collection<ItemStack> drops;
        try {
            drops = event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player);
        } catch (Throwable t) {
            return;
        }
        if (drops == null || drops.isEmpty()) return;

        int totalCount = 0;
        try {
            for (ItemStack drop : drops) {
                if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
                int original = drop.getAmount();
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(drop.clone());
                int leftoverSum = 0;
                for (ItemStack left : leftover.values()) {
                    if (left != null) leftoverSum += left.getAmount();
                }
                totalCount += original - leftoverSum;
                for (ItemStack left : leftover.values()) {
                    if (left != null && !left.getType().isAir() && left.getAmount() > 0) {
                        player.getWorld().dropItemNaturally(event.getBlock().getLocation(), left);
                    }
                }
            }
        } catch (Throwable t) {
            return;
        }

        if (totalCount <= 0) return;

        event.setDropItems(false);

        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isAutoPickupNotify()) return;

        MessageUtils msg = plugin.getMessageUtils();
        msg.sendActionBar(player, "auto-pickup-collected", "{amount}", totalCount);

        SoundEntry sound = cfg.getSellSuccessSound();
        if (sound != null) sound.play(player);
    }
}
