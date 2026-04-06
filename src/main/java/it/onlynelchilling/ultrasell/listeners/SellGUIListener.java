package it.onlynelchilling.ultrasell.listeners;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.gui.SellGUISystem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class SellGUIListener implements Listener {

    private final UltraSell plugin;

    public SellGUIListener(UltraSell plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellGUISystem.SellInventory sellInventory)) {
            return;
        }

        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }

        if (sellInventory.isDecorationSlot(event.getSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellGUISystem.SellInventory sellInventory)) {
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot < event.getInventory().getSize() && sellInventory.isDecorationSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellGUISystem.SellInventory sellInventory)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        sellInventory.getSystem().handleInventoryClose(player, event.getInventory());
    }
}
