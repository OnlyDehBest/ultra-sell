package it.onlynelchilling.ultrasell.autosell;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.cache.CachedPlayer;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.config.ConfigManager.SoundEntry;
import it.onlynelchilling.ultrasell.utils.MessageUtils;
import it.onlynelchilling.ultrasell.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Map;

public final class AutoSellTask {

    public static final String PERMISSION = "ultrasell.auto";

    private final UltraSell plugin;
    private Runnable cancel;

    public AutoSellTask(UltraSell plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        ConfigManager cfg = plugin.getConfigManager();
        if (!cfg.isAutoSellEnabled()) return;
        long period = Math.max(20L, cfg.getAutoSellIntervalSeconds() * 20L);
        cancel = SchedulerUtil.runGlobalRepeating(plugin, this::tick, period, period);
    }

    public void stop() {
        if (cancel != null) {
            try { cancel.run(); } catch (Throwable ignored) {}
            cancel = null;
        }
    }

    private void tick() {
        for (CachedPlayer cp : plugin.getPlayerCache().all().values()) {
            if (!cp.stats().autoSell()) continue;
            Player player = Bukkit.getPlayer(cp.uuid());
            if (player == null || !player.isOnline()) continue;
            if (!player.hasPermission(PERMISSION)) continue;
            SchedulerUtil.runForEntity(plugin, player, () -> process(player), null);
        }
    }

    private void process(Player player) {
        ConfigManager cfg = plugin.getConfigManager();
        Map<Material, Double> prices = cfg.getPrices();
        PlayerInventory inv = player.getInventory();
        int handSlot = inv.getHeldItemSlot();
        boolean ignoreHand = cfg.isAutoSellIgnoreHand();

        double total = 0.0;
        int count = 0;

        for (int i = 0; i < 36; i++) {
            if (ignoreHand && i == handSlot) continue;
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Double price = prices.get(item.getType());
            if (price == null) continue;
            if (item.hasItemMeta()) continue;
            total += price * item.getAmount();
            count += item.getAmount();
            inv.setItem(i, null);
        }

        if (count == 0 || total <= 0) return;

        double multiplier = plugin.getPlayerCache().multiplier(player);
        double finalPrice = total * multiplier;

        plugin.getVaultHook().depositPlayer(player, finalPrice);
        plugin.getPlayerCache().get(player).stats().add(count, finalPrice);

        if (!cfg.isAutoSellNotify()) return;

        MessageUtils msg = plugin.getMessageUtils();
        String formatted = cfg.formatPrice(finalPrice);
        String currency = plugin.getVaultHook().getCurrencyName();

        if (multiplier > 1.0) {
            String mul = String.format(Locale.US, "%.1f", multiplier);
            msg.sendActionBar(player, "auto-sell-success-multiplier",
                    "{amount}", count, "{total}", formatted, "{currency}", currency, "{multiplier}", mul);
        } else {
            msg.sendActionBar(player, "auto-sell-success",
                    "{amount}", count, "{total}", formatted, "{currency}", currency);
        }

        SoundEntry sound = cfg.getSellSuccessSound();
        if (sound != null) player.playSound(player.getLocation(), sound.sound(), sound.volume(), sound.pitch());
    }
}

