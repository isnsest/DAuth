package org.isnsest.dauth;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class Command2FA implements CommandExecutor {
    private final DAuth plugin;
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();

    public Command2FA(DAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("dauth.admin.2fa")) {
                sender.sendMessage(plugin.component("2fa.admin-no-perm", "&cNo permission."));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            UUID tUUID = target != null ? target.getUniqueId() : Bukkit.getOfflinePlayer(args[1]).getUniqueId();
            plugin.db().remove2FASecret(tUUID);

            String msg = plugin.mes("2fa.reset-success", "&aReset for %player%").replace("%player%", args[1]);
            sender.sendMessage(plugin.component("2fa.reset-success", msg).replaceText(b -> b.matchLiteral("%player%").replacement(args[1])));
            return true;
        }


        if (!(sender instanceof Player player)) {
            if (args.length == 0 || !args[0].equalsIgnoreCase("reset")) {
                sender.sendMessage(plugin.component("2fa.only-players", "&cOnly players."));
                if (sender.hasPermission("dauth.admin")) {
                    sender.sendMessage(plugin.component("2fa.usage-reset", "&cUsage: /2fa reset <player>"));
                }
            }
            return true;
        }


        if (args.length > 0 && args[0].equalsIgnoreCase("setup")) {
            if (plugin.db().get2FASecret(player.getUniqueId()) != null) {
                player.sendMessage(plugin.component("2fa.already-enabled", "&cAlready enabled!"));
                return true;
            }

            String secret = plugin.pendingSetupSecrets.computeIfAbsent(player.getUniqueId(), k -> gAuth.createCredentials().getKey());
            String url = "https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=otpauth://totp/Minecraft:" + player.getName() + "?secret=" + secret;

            Component qrMsg = plugin.component("2fa.setup-click-qr", "&6[Click for QR]")
                    .clickEvent(ClickEvent.openUrl(url));

            Component codeMsg = plugin.component("2fa.setup-click-code", "&bCode: %code%")
                    .replaceText(builder -> builder.matchLiteral("%code%").replacement(secret))
                    .clickEvent(ClickEvent.copyToClipboard(secret));

            player.sendMessage(qrMsg);
            player.sendMessage(codeMsg);
            player.sendMessage(plugin.component("2fa.setup-info", "&eType /2fa code <code>"));
            return true;
        }


        if (args.length > 1 && args[0].equalsIgnoreCase("code")) {
            String secret = plugin.pendingSetupSecrets.get(player.getUniqueId());
            if (secret == null) {
                player.sendMessage(plugin.component("2fa.no-setup", "&cNo setup pending."));
                return true;
            }
            try {
                if (gAuth.authorize(secret, Integer.parseInt(args[1]))) {
                    plugin.db().save2FASecret(player.getUniqueId(), secret);
                    plugin.pendingSetupSecrets.remove(player.getUniqueId());
                    player.sendMessage(plugin.component("2fa.enabled", "&aEnabled!"));
                } else {
                    player.sendMessage(plugin.component("2fa.wrong-code", "&cWrong code."));
                }
            } catch (Exception e) {
                player.sendMessage(plugin.component("2fa.nan-error", "&cNumbers only."));
            }
            return true;
        }


        if (args.length > 0 && args[0].equalsIgnoreCase("remove")) {
            plugin.db().remove2FASecret(player.getUniqueId());
            player.sendMessage(plugin.component("2fa.removed", "&eRemoved."));
            return true;
        }


        for (String line : plugin.list("2fa.help")) {
            sender.sendMessage(line);
        }

        if (sender.hasPermission("dauth.admin.2fa")) {
            sender.sendMessage(plugin.component("2fa.help-admin", "&cAdmin help..."));
        }

        return true;
    }
}