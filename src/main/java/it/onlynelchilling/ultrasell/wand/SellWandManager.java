package it.onlynelchilling.ultrasell.wand;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.config.ConfigManager.SellWandSettings;
import it.onlynelchilling.ultrasell.config.ConfigManager.SellWandTemplate;
import it.onlynelchilling.ultrasell.config.ConfigManager.SoundEntry;
import it.onlynelchilling.ultrasell.sell.SellService;
import it.onlynelchilling.ultrasell.utils.MessageUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SellWandManager {

    public static final String USE_PERMISSION = "ultrasell.wand.use";

    private final UltraSell plugin;
    private final NamespacedKey wandKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey multiplierKey;
    private final NamespacedKey templateKey;

    private final Map<String, String> cachedNames = new HashMap<>();
    private final Map<String, List<String>> cachedLores = new HashMap<>();

    public SellWandManager(UltraSell plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "sell_wand");
        this.usesKey = new NamespacedKey(plugin, "sell_wand_uses");
        this.multiplierKey = new NamespacedKey(plugin, "sell_wand_multiplier");
        this.templateKey = new NamespacedKey(plugin, "sell_wand_template");
        rebuildCache();
    }

    public void rebuildCache() {
        SellWandSettings cfg = plugin.getConfigManager().getSellWand();
        MessageUtils msg = plugin.getMessageUtils();
        cachedNames.clear();
        cachedLores.clear();
        for (SellWandTemplate t : cfg.templates().values()) {
            cachedNames.put(t.id(), msg.toLegacy(t.name()));
            cachedLores.put(t.id(), t.lore().stream().map(msg::toLegacy).toList());
        }
    }

    public ItemStack createWand(int uses) {
        SellWandTemplate t = plugin.getConfigManager().getSellWand().defaultTemplate();
        return createWand(t.id(), uses, t.defaultMultiplier());
    }

    public ItemStack createWand(int uses, double multiplier) {
        return createWand(null, uses, multiplier);
    }

    public ItemStack createWand(String templateId, int uses, double multiplier) {
        SellWandTemplate t = plugin.getConfigManager().getSellWand().template(templateId);
        ItemStack item = new ItemStack(t.material());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(cachedNames.getOrDefault(t.id(), t.name()));
            if (t.glow()) meta.setEnchantmentGlintOverride(true);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(wandKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(usesKey, PersistentDataType.INTEGER, uses);
            pdc.set(multiplierKey, PersistentDataType.DOUBLE, multiplier);
            pdc.set(templateKey, PersistentDataType.STRING, t.id());

            applyLore(meta, t.id(), uses, multiplier);
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public String formatUses(int uses) {
        return uses < 0 ? "∞" : String.valueOf(uses);
    }

    public String formatMultiplier(double multiplier) {
        return String.format(Locale.US, "%.1f", multiplier);
    }

    public double readMultiplier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1.0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 1.0;
        Double stored = meta.getPersistentDataContainer().get(multiplierKey, PersistentDataType.DOUBLE);
        return (stored == null || stored <= 0) ? 1.0 : stored;
    }

    public String readTemplateId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(templateKey, PersistentDataType.STRING);
    }

    public void sellContainer(Player player, Inventory container) {
        Map<Material, Double> prices = plugin.getConfigManager().getPrices();

        double total = 0.0;
        int count = 0;

        ItemStack[] contents = container.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;
            if (item.hasItemMeta()) continue;

            Double price = prices.get(item.getType());
            if (price == null) continue;

            total += price * item.getAmount();
            count += item.getAmount();
            container.setItem(i, null);
        }

        MessageUtils msg = plugin.getMessageUtils();
        ConfigManager cfg = plugin.getConfigManager();

        if (count == 0) {
            msg.send(player, "wand-no-items");
            SoundEntry fail = cfg.getSellFailSound();
            if (fail != null) fail.play(player);
            return;
        }

        double wandMultiplier = readMultiplier(player.getInventory().getItemInMainHand());
        SellService sell = plugin.getSellService();
        SellService.Payment payment = sell.deposit(player, total, count, wandMultiplier);
        sell.notify(player, payment, "wand-sold", true);

        SoundEntry success = cfg.getSellSuccessSound();
        if (success != null) success.play(player);

        if (!consumeUse(player)) {
            msg.send(player, "wand-used-up");
        }
    }

    private boolean consumeUse(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack item = inv.getItemInMainHand();
        if (!isWand(item)) return true;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer stored = pdc.get(usesKey, PersistentDataType.INTEGER);
        int uses = stored != null ? stored : -1;
        if (uses < 0) return true;

        if (uses <= 1) {
            inv.setItemInMainHand(null);
            return false;
        }

        int remaining = uses - 1;
        Double storedMul = pdc.get(multiplierKey, PersistentDataType.DOUBLE);
        double multiplier = (storedMul == null || storedMul <= 0) ? 1.0 : storedMul;
        String templateId = pdc.get(templateKey, PersistentDataType.STRING);
        pdc.set(usesKey, PersistentDataType.INTEGER, remaining);
        applyLore(meta, templateId, remaining, multiplier);
        item.setItemMeta(meta);
        inv.setItemInMainHand(item);
        return true;
    }

    private void applyLore(ItemMeta meta, String templateId, int uses, double multiplier) {
        List<String> base = templateId != null ? cachedLores.get(templateId) : null;
        if (base == null) {
            SellWandTemplate fallback = plugin.getConfigManager().getSellWand().defaultTemplate();
            base = cachedLores.get(fallback.id());
        }
        if (base == null || base.isEmpty()) return;

        String usesText = formatUses(uses);
        String multiplierText = formatMultiplier(multiplier);
        List<String> lore = new ArrayList<>(base.size());
        for (String line : base) {
            lore.add(line.replace("{uses}", usesText).replace("{multiplier}", multiplierText));
        }
        meta.setLore(lore);
    }
}
