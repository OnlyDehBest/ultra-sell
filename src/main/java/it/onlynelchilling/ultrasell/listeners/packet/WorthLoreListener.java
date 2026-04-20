package it.onlynelchilling.ultrasell.listeners.packet;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemContainerContents;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.utils.SchedulerUtil;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.gui.PluginGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorthLoreListener extends SimplePacketListenerAbstract implements Listener {

    private static final String WORTH_MARKER = "ultrasell_worth";

    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();

    private final UltraSell plugin;

    private final Map<String, Double> stringPriceCache = new ConcurrentHashMap<>();
    private final Map<ItemType, Double> resolvedPriceCache = new ConcurrentHashMap<>();
    private final Map<ItemType, Boolean> shulkerCheckCache = new ConcurrentHashMap<>();
    private final Map<Double, Component> worthLineCache = new ConcurrentHashMap<>();

    private static final Set<String> SHULKER_NAMES = Set.of(
            "minecraft:shulker_box", "minecraft:white_shulker_box", "minecraft:orange_shulker_box",
            "minecraft:magenta_shulker_box", "minecraft:light_blue_shulker_box", "minecraft:yellow_shulker_box",
            "minecraft:lime_shulker_box", "minecraft:pink_shulker_box", "minecraft:gray_shulker_box",
            "minecraft:light_gray_shulker_box", "minecraft:cyan_shulker_box", "minecraft:purple_shulker_box",
            "minecraft:blue_shulker_box", "minecraft:brown_shulker_box", "minecraft:green_shulker_box",
            "minecraft:red_shulker_box", "minecraft:black_shulker_box"
    );

    public WorthLoreListener(UltraSell plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        rebuildPriceCache();
    }

    public void rebuildPriceCache() {
        stringPriceCache.clear();
        resolvedPriceCache.clear();
        shulkerCheckCache.clear();
        worthLineCache.clear();

        if (plugin.getConfigManager().isWorthLoreEnabled()) {
            for (Map.Entry<Material, Double> entry : plugin.getConfigManager().getPrices().entrySet()) {
                stringPriceCache.put("minecraft:" + entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduleUpdate(player);
        }
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinEvent event) {
        scheduleUpdate(event.getPlayer(), 5L);
    }

    @EventHandler
    private void onGameModeChange(PlayerGameModeChangeEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    private void onDropItem(PlayerDropItemEvent event) {
        scheduleUpdate(event.getPlayer());
    }

    @EventHandler
    private void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scheduleUpdate(player);
        }
    }

    private void scheduleUpdate(Player player) {
        scheduleUpdate(player, 1L);
    }

    private void scheduleUpdate(Player player, long delay) {
        UUID uuid = player.getUniqueId();
        if (!pendingUpdates.add(uuid)) return;

        SchedulerUtil.runForEntityLater(plugin, player, () -> {
            pendingUpdates.remove(uuid);
            if (player.isOnline()) player.updateInventory();
        }, () -> pendingUpdates.remove(uuid), delay);
    }

    @Override
    public void onPacketPlaySend(PacketPlaySendEvent event) {
        if (stringPriceCache.isEmpty()) return;

        PacketTypeCommon type = event.getPacketType();
        if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            processWindowItems(event);
        } else if (type == PacketType.Play.Server.SET_SLOT) {
            processSetSlot(event);
        }
    }

    private void processWindowItems(PacketPlaySendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (isPluginGui(player)) return;

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);

        List<ItemStack> items = wrapper.getItems();
        boolean changed = false;

        for (int i = 0, size = items.size(); i < size; i++) {
            ItemStack item = items.get(i);
            ItemStack processed = creative ? strip(item) : inject(item);
            if (processed != item) {
                items.set(i, processed);
                changed = true;
            }
        }

        if (changed) wrapper.setItems(items);

        wrapper.getCarriedItem().ifPresent(carried -> {
            ItemStack processed = creative ? strip(carried) : inject(carried);
            if (processed != carried) {
                wrapper.setCarriedItem(processed);
                event.markForReEncode(true);
            }
        });

        if (changed) event.markForReEncode(true);
    }

    private void processSetSlot(PacketPlaySendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (isPluginGui(player)) return;

        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
        ItemStack current = wrapper.getItem();

        ItemStack processed = creative ? strip(current) : inject(current);
        if (processed != current) {
            wrapper.setItem(processed);
            event.markForReEncode(true);
        }
    }

    private ItemStack inject(ItemStack peItem) {
        if (peItem == null || peItem.isEmpty()) return peItem;

        ItemType itemType = peItem.getType();
        double unitPrice = getPrice(itemType);
        boolean isShulker = isShulkerBox(itemType);
        if (unitPrice <= 0 && !isShulker) return peItem;

        double total = calcTotal(peItem, unitPrice, isShulker);
        if (total <= 0) return peItem;

        Component worthLine = buildWorthLine(total);
        ItemLore loreComponent = peItem.getComponent(ComponentTypes.LORE).orElse(null);

        List<Component> lines;

        if (loreComponent != null && !loreComponent.getLines().isEmpty()) {
            List<Component> existing = loreComponent.getLines();
            Component lastLine = existing.getLast();

            if (isWorthLine(lastLine)) {
                lines = new ArrayList<>(existing);
                lines.set(lines.size() - 1, worthLine);
            } else {
                lines = new ArrayList<>(existing.size() + 1);
                lines.addAll(existing);
                lines.add(worthLine);
            }
        } else {
            lines = List.of(worthLine);
        }

        if (lines.size() > 256) return peItem;

        ItemStack clone = peItem.copy();
        clone.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        return clone;
    }

    private boolean isWorthLine(Component component) {
        return WORTH_MARKER.equals(component.insertion());
    }

    private double calcTotal(ItemStack peItem, double unitPrice, boolean isShulker) {
        double selfPrice = unitPrice * peItem.getAmount();

        if (!isShulker) return selfPrice;

        ItemContainerContents contents = peItem.getComponent(ComponentTypes.CONTAINER).orElse(null);
        if (contents == null) return selfPrice;

        double contentTotal = 0;
        for (ItemStack inner : contents.getItems()) {
            if (inner == null || inner.isEmpty()) continue;
            double innerPrice = getPrice(inner.getType());
            if (innerPrice > 0) {
                contentTotal += innerPrice * inner.getAmount();
            }
        }

        return selfPrice + contentTotal;
    }

    private boolean isShulkerBox(ItemType type) {
        if (type == null) return false;
        return shulkerCheckCache.computeIfAbsent(type,
                t -> SHULKER_NAMES.contains(t.getName().toString()));
    }

    private ItemStack strip(ItemStack peItem) {
        if (peItem == null || peItem.isEmpty()) return peItem;

        ItemLore loreComponent = peItem.getComponent(ComponentTypes.LORE).orElse(null);
        if (loreComponent == null) return peItem;

        List<Component> lines = loreComponent.getLines();
        if (lines.isEmpty()) return peItem;

        if (!isWorthLine(lines.getLast())) return peItem;

        ItemStack clone = peItem.copy();
        if (lines.size() == 1) {
            clone.unsetComponent(ComponentTypes.LORE);
        } else {
            clone.setComponent(ComponentTypes.LORE, new ItemLore(lines.subList(0, lines.size() - 1)));
        }
        return clone;
    }

    private double getPrice(ItemType type) {
        if (type == null) return 0;
        return resolvedPriceCache.computeIfAbsent(type,
                t -> stringPriceCache.getOrDefault(t.getName().toString(), 0.0));
    }

    private Component buildWorthLine(double total) {
        return worthLineCache.computeIfAbsent(total, t -> {
            ConfigManager cfg = plugin.getConfigManager();
            String format = cfg.getWorthLoreFormat()
                    .replace("{price}", cfg.formatPriceSmart(t))
                    .replace("{currency}", plugin.getVaultHook().getCurrencyName());

            return Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .insertion(WORTH_MARKER)
                    .append(plugin.getMessageUtils().deserialize(format));
        });
    }

    private boolean isPluginGui(Player player) {
        try {
            return player.getOpenInventory().getTopInventory().getHolder() instanceof PluginGui;
        } catch (Throwable ignored) {
            return false;
        }
    }
}

