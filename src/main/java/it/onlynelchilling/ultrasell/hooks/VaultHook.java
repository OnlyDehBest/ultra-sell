package it.onlynelchilling.ultrasell.hooks;

import it.onlynelchilling.ultrasell.UltraSell;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final UltraSell plugin;
    private Economy economy;

    public VaultHook(UltraSell plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return true;
    }


    public void depositPlayer(Player player, double amount) {
        if (economy != null) {
            economy.depositPlayer(player, amount);
        }
    }

    public String getCurrencyName() {
        if (economy != null) {
            String name = economy.currencyNamePlural();
            return name != null ? name : "";
        }
        return "";
    }
}
