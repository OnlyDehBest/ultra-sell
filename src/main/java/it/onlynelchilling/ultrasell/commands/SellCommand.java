package it.onlynelchilling.ultrasell.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import it.onlynelchilling.ultrasell.UltraSell;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("sell")
public class SellCommand extends BaseCommand {

    private final UltraSell plugin;

    public SellCommand(UltraSell plugin) {
        this.plugin = plugin;
    }

    @Default
    @CommandPermission("ultrasell.sell")
    public void onSell(Player player) {
        plugin.getSellGUISystem().openSellGUI(player);
    }

    @Subcommand("hand")
    public void onSellHand(Player player) {
        plugin.getSellGUISystem().sellHand(player);
    }

    @Subcommand("auto")
    @CommandPermission("ultrasell.auto")
    public void onAuto(Player player) {
        if (!plugin.getConfigManager().isAutoSellEnabled()) {
            plugin.getMessageUtils().send(player, "auto-sell-disabled-global");
            return;
        }
        var stats = plugin.getPlayerCache().get(player).stats();
        boolean now = !stats.autoSell();
        stats.setAutoSell(now);
        plugin.getMessageUtils().send(player, now ? "auto-sell-enabled" : "auto-sell-disabled");
    }

    @Subcommand("reload")
    @CommandPermission("ultrasell.reload")
    public void onReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        plugin.getSellGUISystem().rebuildGUICache();
        plugin.rebuildWorthLoreCache();
        plugin.getAutoSellTask().start();

        if (sender instanceof Player player) {
            plugin.getMessageUtils().send(player, "reload-success");
        }
    }
}
