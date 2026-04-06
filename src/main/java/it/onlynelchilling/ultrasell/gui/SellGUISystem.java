package it.onlynelchilling.ultrasell.gui;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager.DecorationItem;
import it.onlynelchilling.ultrasell.config.ConfigManager.MultiplierEntry;
import it.onlynelchilling.ultrasell.config.ConfigManager.SoundEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SellGUISystem {

    private final UltraSell plugin;

    public SellGUISystem(UltraSell plugin) {
        this.plugin = plugin;
    }

    public void openSellGUI(Player player) {
        var cfg = plugin.getConfigManager();
        var title = plugin.getMessageUtils().toLegacy(cfg.getGuiTitle());
        var gui = new SellInventory(this, title, cfg.getGuiSize(), cfg.getDecorations());

        player.openInventory(gui.getInventory());
    }

    public void handleInventoryClose(Player player, Inventory inventory) {
        var sellInv = (SellInventory) inventory.getHolder();
        var items = new ArrayList<ItemStack>();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (sellInv != null && sellInv.isDecorationSlot(i)) continue;

            var item = inventory.getItem(i);

            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }

        inventory.clear();

        if (items.isEmpty()) {
            plugin.getMessageUtils().send(player, "empty");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            var result = processSellItems(items);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                returnUnsellableItems(player, result.itemsReturned());
                handlePayment(player, result);
            });
        });
    }

    public void sellHand(Player player) {
        var item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            plugin.getMessageUtils().send(player, "hand-air");
            return;
        }

        var price = plugin.getConfigManager().getPrices().get(item.getType());

        if (price == null) {
            plugin.getMessageUtils().send(player, "hand-not-sellable");
            playSound(player, plugin.getConfigManager().getSellFailSound());
            return;
        }

        var amount = item.getAmount();

        player.getInventory().setItemInMainHand(null);

        var totalPrice = price * amount;
        var result = new SellResult(totalPrice, amount, List.of());

        handlePayment(player, result);
    }

    public double getMultiplier(Player player) {
        var cfg = plugin.getConfigManager();

        if (!cfg.isMultiplierEnabled() || cfg.getMultiplierEntries().isEmpty()) {
            return 1.0;
        }

        for (MultiplierEntry entry : cfg.getMultiplierEntries()) {
            if (player.hasPermission(entry.permission())) {
                return entry.multiplier();
            }
        }

        return 1.0;
    }


    private SellResult processSellItems(List<ItemStack> items) {
        var prices = plugin.getConfigManager().getPrices();

        var totalPrice = 0.0;
        var totalItemsSold = 0;
        var itemsReturned = new ArrayList<ItemStack>();

        for (ItemStack item : items) {
            if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                var shulkerResult = processShulkerBox(item, prices);

                totalPrice += shulkerResult.price();
                totalItemsSold += shulkerResult.itemCount();

                if (!shulkerResult.unsellable().isEmpty()) {
                    itemsReturned.addAll(shulkerResult.unsellable());
                }
                continue;
            }

            var price = prices.get(item.getType());

            if (price != null) {
                totalPrice += price * item.getAmount();
                totalItemsSold += item.getAmount();
            } else {
                itemsReturned.add(item);
            }
        }

        return new SellResult(totalPrice, totalItemsSold, itemsReturned);
    }

    private ShulkerResult processShulkerBox(ItemStack shulkerItem, Map<Material, Double> prices) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta meta
                && meta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return new ShulkerResult(0.0, 0, List.of(shulkerItem));
        }

        var price = 0.0;
        var itemCount = 0;
        var unsellable = new ArrayList<ItemStack>();

        var shulkerPrice = prices.get(shulkerItem.getType());
        if (shulkerPrice != null) {
            price += shulkerPrice;
            itemCount++;
        }

        for (ItemStack content : shulkerBox.getInventory().getContents()) {
            if (content == null || content.getType().isAir()) continue;

            var contentPrice = prices.get(content.getType());

            if (contentPrice != null) {
                price += contentPrice * content.getAmount();
                itemCount += content.getAmount();
            } else {
                unsellable.add(content.clone());
            }
        }

        return new ShulkerResult(price, itemCount, unsellable);
    }

    private void returnUnsellableItems(Player player, List<ItemStack> itemsReturned) {
        if (itemsReturned.isEmpty()) return;

        var playerInv = player.getInventory();
        var playerLoc = player.getLocation();
        var world = player.getWorld();

        for (ItemStack item : itemsReturned) {
            var leftovers = playerInv.addItem(item);
            leftovers.values().forEach(leftover -> world.dropItem(playerLoc, leftover));
        }
    }

    private void handlePayment(Player player, SellResult result) {
        var msg = plugin.getMessageUtils();
        var cfg = plugin.getConfigManager();

        if (result.totalPrice() <= 0) {
            if (!result.itemsReturned().isEmpty()) {
                notifyNoSellableItems(player, result.itemsReturned().size());
            }
            return;
        }

        var multiplier = getMultiplier(player);
        var finalPrice = result.totalPrice() * multiplier;

        plugin.getVaultHook().depositPlayer(player, finalPrice);

        var formattedPrice = cfg.formatPrice(finalPrice);
        var currencyName = plugin.getVaultHook().getCurrencyName();

        if (multiplier > 1.0) {
            var formattedMultiplier = String.format(Locale.US, "%.1f", multiplier);

            msg.sendActionBar(player, "sold-success-multiplier",
                    "{amount}", result.totalItemsSold(),
                    "{total}", formattedPrice,
                    "{currency}", currencyName,
                    "{multiplier}", formattedMultiplier
            );

            msg.send(player, "sold-success-multiplier",
                    "{amount}", result.totalItemsSold(),
                    "{total}", formattedPrice,
                    "{currency}", currencyName,
                    "{multiplier}", formattedMultiplier
            );
        } else {
            msg.sendActionBar(player, "sold-success",
                    "{amount}", result.totalItemsSold(),
                    "{total}", formattedPrice,
                    "{currency}", currencyName
            );

            msg.send(player, "sold-success",
                    "{amount}", result.totalItemsSold(),
                    "{total}", formattedPrice,
                    "{currency}", currencyName
            );
        }

        playSound(player, cfg.getSellSuccessSound());

        if (!result.itemsReturned().isEmpty()) {
            notifyUnsellableItems(player, result.itemsReturned().size());
        }
    }

    private void notifyUnsellableItems(Player player, int count) {
        playSound(player, plugin.getConfigManager().getSellFailSound());
        plugin.getMessageUtils().send(player, "unsellable-items", "{count}", count);
    }

    private void notifyNoSellableItems(Player player, int count) {
        playSound(player, plugin.getConfigManager().getSellFailSound());
        plugin.getMessageUtils().send(player, "no-sellable-items", "{count}", count);
    }

    private void playSound(Player player, SoundEntry sound) {
        if (sound != null) {
            player.playSound(player.getLocation(), sound.sound(), sound.volume(), sound.pitch());
        }
    }

    private record ShulkerResult(double price, int itemCount, List<ItemStack> unsellable) {}

    private record SellResult(double totalPrice, int totalItemsSold, List<ItemStack> itemsReturned) {}

    public static class SellInventory implements PluginGui {

        private final SellGUISystem system;
        private final Inventory inventory;
        private final Set<Integer> decorationSlots = ConcurrentHashMap.newKeySet();

        SellInventory(SellGUISystem system, String title, int size, List<DecorationItem> decorations) {
            this.system = system;
            this.inventory = Bukkit.createInventory(this, size, title);

            applyDecorations(decorations);
        }

        private void applyDecorations(List<DecorationItem> decorations) {
            var msgUtils = system.plugin.getMessageUtils();

            for (var decoration : decorations) {
                var item = new ItemStack(decoration.material());
                var meta = item.getItemMeta();

                if (meta != null) {
                    meta.setDisplayName(msgUtils.toLegacy(decoration.name()));

                    if (!decoration.lore().isEmpty()) {
                        meta.setLore(decoration.lore().stream()
                                .map(msgUtils::toLegacy)
                                .toList());
                    }

                    item.setItemMeta(meta);
                }

                for (int slot : decoration.slots()) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        inventory.setItem(slot, item);
                        decorationSlots.add(slot);
                    }
                }
            }
        }

        public boolean isDecorationSlot(int slot) {
            return decorationSlots.contains(slot);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public SellGUISystem getSystem() {
            return system;
        }
    }
}
