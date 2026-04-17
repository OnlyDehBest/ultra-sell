package it.onlynelchilling.ultrasell.gui;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.config.ConfigManager.DecorationItem;
import it.onlynelchilling.ultrasell.config.ConfigManager.SoundEntry;
import it.onlynelchilling.ultrasell.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SellGUISystem {

    private final UltraSell plugin;

    private String cachedTitle;
    private int cachedSize;
    private ItemStack[] cachedDecorationTemplate;
    private Set<Integer> cachedDecorationSlots;

    public SellGUISystem(UltraSell plugin) {
        this.plugin = plugin;
        rebuildGUICache();
    }

    public void rebuildGUICache() {
        ConfigManager cfg = plugin.getConfigManager();
        cachedTitle = plugin.getMessageUtils().toLegacy(cfg.getGuiTitle());
        cachedSize = cfg.getGuiSize();

        ItemStack[] template = new ItemStack[cachedSize];
        HashSet<Integer> decoSlots = new HashSet<>();
        MessageUtils msgUtils = plugin.getMessageUtils();

        for (DecorationItem decoration : cfg.getDecorations()) {
            ItemStack item = new ItemStack(decoration.material());
            ItemMeta meta = item.getItemMeta();

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
                if (slot >= 0 && slot < cachedSize) {
                    template[slot] = item;
                    decoSlots.add(slot);
                }
            }
        }

        cachedDecorationTemplate = template;
        cachedDecorationSlots = Set.copyOf(decoSlots);
    }

    public void openSellGUI(Player player) {
        SellInventory gui = new SellInventory(this);
        player.openInventory(gui.getInventory());
    }

    public void handleInventoryClose(Player player, Inventory inventory) {
        SellInventory sellInv = (SellInventory) inventory.getHolder();
        ArrayList<ItemStack> items = new ArrayList<>();

        for (int i = 0; i < inventory.getSize(); i++) {
            if (sellInv != null && sellInv.isDecorationSlot(i)) continue;

            ItemStack item = inventory.getItem(i);

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
            SellResult result = processSellItems(items);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                returnUnsellableItems(player, result.itemsReturned());
                handlePayment(player, result);
            });
        });
    }

    public void sellHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            plugin.getMessageUtils().send(player, "hand-air");
            return;
        }

        Double price = plugin.getConfigManager().getPrices().get(item.getType());

        if (price == null) {
            plugin.getMessageUtils().send(player, "hand-not-sellable");
            playSound(player, plugin.getConfigManager().getSellFailSound());
            return;
        }

        int amount = item.getAmount();

        player.getInventory().setItemInMainHand(null);

        double totalPrice = price * amount;
        SellResult result = new SellResult(totalPrice, amount, List.of());

        handlePayment(player, result);
    }

    public double getMultiplier(Player player) {
        return plugin.getPlayerCache().multiplier(player);
    }


    private SellResult processSellItems(List<ItemStack> items) {
        Map<Material, Double> prices = plugin.getConfigManager().getPrices();

        double totalPrice = 0.0;
        int totalItemsSold = 0;
        ArrayList<ItemStack> itemsReturned = new ArrayList<>();

        for (ItemStack item : items) {
            if (Tag.SHULKER_BOXES.isTagged(item.getType())) {
                ShulkerResult shulkerResult = processShulkerBox(item, prices);

                totalPrice += shulkerResult.price();
                totalItemsSold += shulkerResult.itemCount();

                if (!shulkerResult.unsellable().isEmpty()) {
                    itemsReturned.addAll(shulkerResult.unsellable());
                }
                continue;
            }

            Double price = prices.get(item.getType());

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

        double price = 0.0;
        int itemCount = 0;
        ArrayList<ItemStack> unsellable = new ArrayList<>();

        Double shulkerPrice = prices.get(shulkerItem.getType());
        if (shulkerPrice != null) {
            price += shulkerPrice;
            itemCount++;
        }

        for (ItemStack content : shulkerBox.getInventory().getContents()) {
            if (content == null || content.getType().isAir()) continue;

            Double contentPrice = prices.get(content.getType());

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

        org.bukkit.inventory.PlayerInventory playerInv = player.getInventory();
        org.bukkit.Location playerLoc = player.getLocation();
        org.bukkit.World world = player.getWorld();

        for (ItemStack item : itemsReturned) {
            Map<Integer, ItemStack> leftovers = playerInv.addItem(item);
            leftovers.values().forEach(leftover -> world.dropItem(playerLoc, leftover));
        }
    }

    private void handlePayment(Player player, SellResult result) {
        MessageUtils msg = plugin.getMessageUtils();
        ConfigManager cfg = plugin.getConfigManager();

        if (result.totalPrice() <= 0) {
            if (!result.itemsReturned().isEmpty()) {
                notifyNoSellableItems(player, result.itemsReturned().size());
            }
            return;
        }

        double multiplier = getMultiplier(player);
        double finalPrice = result.totalPrice() * multiplier;

        plugin.getVaultHook().depositPlayer(player, finalPrice);
        plugin.getPlayerCache().get(player).stats().add(result.totalItemsSold(), finalPrice);

        String formattedPrice = cfg.formatPrice(finalPrice);
        String currencyName = plugin.getVaultHook().getCurrencyName();

        if (multiplier > 1.0) {
            String formattedMultiplier = String.format(Locale.US, "%.1f", multiplier);

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
        private final Set<Integer> decorationSlots;

        SellInventory(SellGUISystem system) {
            this.system = system;
            this.inventory = Bukkit.createInventory(this, system.cachedSize, system.cachedTitle);
            this.decorationSlots = system.cachedDecorationSlots;

            ItemStack[] template = system.cachedDecorationTemplate;
            for (int i = 0; i < template.length; i++) {
                if (template[i] != null) {
                    inventory.setItem(i, template[i]);
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
