package com.github.zilosz.ssl.command.implementation;

import com.github.zilosz.ssl.attack.Damage;
import com.github.zilosz.ssl.command.ArgumentValidator;
import com.github.zilosz.ssl.command.CommandProcessor;
import com.github.zilosz.ssl.command.NumberValidator;
import com.github.zilosz.ssl.command.PlayerValidator;
import com.github.zilosz.ssl.command.SenderRestriction;
import com.github.zilosz.ssl.event.attack.DamageEvent;
import com.github.zilosz.ssl.utils.message.Chat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DamageCommand extends CommandProcessor {

    @Override
    public SenderRestriction getSenderRestriction() {
        return SenderRestriction.NONE;
    }

    @Override
    public ArgumentValidator[] getRequiredValidators() {
        return new ArgumentValidator[]{new NumberValidator("damage")};
    }

    @Override
    public ArgumentValidator[] getOptionalValidators() {
        return new ArgumentValidator[]{new PlayerValidator("player")};
    }

    @Override
    public void processCommand(CommandSender sender, String[] arguments) {
        double damage = Double.parseDouble(arguments[0]);
        Player player;

        if (arguments.length == 1) {

            if (!(sender instanceof Player)) {
                Chat.COMMAND.send(sender, "&7You must pass a player argument from the console.");
                return;
            }

            player = (Player) sender;

        } else {
            player = Bukkit.getPlayer(arguments[1]);
        }

        Damage settings = new Damage(damage, false);
        DamageEvent event = new DamageEvent(player, settings, false, null);
        Bukkit.getPluginManager().callEvent(event);

        double finalDamage = event.getFinalDamage();
        player.damage(finalDamage);
        Chat.COMMAND.send(sender, String.format("&7Damaged &5%s &7for &e%f &7damage.", player.getName(), finalDamage));
    }
}
