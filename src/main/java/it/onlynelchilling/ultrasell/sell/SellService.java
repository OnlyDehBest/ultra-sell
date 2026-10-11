package it.onlynelchilling.ultrasell.sell;

import it.onlynelchilling.ultrasell.UltraSell;
import it.onlynelchilling.ultrasell.config.ConfigManager;
import it.onlynelchilling.ultrasell.utils.MessageUtils;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class SellService {

    private final UltraSell plugin;

    public SellService(UltraSell plugin) {
        this.plugin = plugin;
    }

    public Payment deposit(Player player, double total, int count) {
        return deposit(player, total, count, 1.0);
    }

    public Payment deposit(Player player, double total, int count, double extraMultiplier) {
        double multiplier = plugin.getPlayerCache().multiplier(player) * extraMultiplier;
        double finalPrice = total * multiplier;

        plugin.getVaultHook().depositPlayer(player, finalPrice);
        plugin.getPlayerCache().get(player).stats().add(count, finalPrice);

        return new Payment(finalPrice, multiplier, count);
    }

    public void notify(Player player, Payment payment, String baseKey, boolean chat) {
        MessageUtils msg = plugin.getMessageUtils();
        ConfigManager cfg = plugin.getConfigManager();

        String key;
        Object[] replacements;

        if (payment.multiplier() > 1.0) {
            key = baseKey + "-multiplier";
            replacements = new Object[]{
                    "{amount}", payment.count(),
                    "{total}", cfg.formatPrice(payment.finalPrice()),
                    "{currency}", plugin.getVaultHook().getCurrencyName(),
                    "{multiplier}", String.format(Locale.US, "%.1f", payment.multiplier())
            };
        } else {
            key = baseKey;
            replacements = new Object[]{
                    "{amount}", payment.count(),
                    "{total}", cfg.formatPrice(payment.finalPrice()),
                    "{currency}", plugin.getVaultHook().getCurrencyName()
            };
        }

        msg.sendActionBar(player, key, replacements);
        if (chat) msg.send(player, key, replacements);
    }

    public record Payment(double finalPrice, double multiplier, int count) {}
}
