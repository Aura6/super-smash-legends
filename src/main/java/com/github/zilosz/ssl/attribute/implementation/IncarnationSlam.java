package com.github.zilosz.ssl.attribute.implementation;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import com.github.zilosz.ssl.SSL;
import com.github.zilosz.ssl.attribute.RightClickAbility;
import com.github.zilosz.ssl.damage.AttackSettings;
import com.github.zilosz.ssl.kit.Kit;
import com.github.zilosz.ssl.utils.DisguiseUtils;
import com.github.zilosz.ssl.utils.entity.EntityUtils;
import com.github.zilosz.ssl.utils.effect.Effects;
import com.github.zilosz.ssl.utils.entity.finder.EntityFinder;
import com.github.zilosz.ssl.utils.entity.finder.selector.EntitySelector;
import com.github.zilosz.ssl.utils.entity.finder.selector.HitBoxSelector;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import me.libraryaddict.disguise.disguisetypes.watchers.SlimeWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class IncarnationSlam extends RightClickAbility {
    private BukkitTask task;
    private boolean active = false;

    public IncarnationSlam(SSL plugin, Section config, Kit kit) {
        super(plugin, config, kit);
    }

    @Override
    public boolean invalidate(PlayerInteractEvent event) {
        return super.invalidate(event) || active;
    }

    @Override
    public void onClick(PlayerInteractEvent event) {
        active = true;

        MobDisguise disguise = new MobDisguise(DisguiseType.SLIME);
        ((SlimeWatcher) disguise.getWatcher()).setSize(config.getInt("SlimeSize"));
        DisguiseAPI.disguiseToAll(player, DisguiseUtils.applyDisguiseParams(player, disguise));

        Vector direction = player.getEyeLocation().getDirection();
        player.setVelocity(direction.multiply(config.getDouble("Velocity")));

        player.getWorld().playSound(player.getLocation(), Sound.SLIME_WALK, 3, 0.75f);

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (EntityUtils.isPlayerGrounded(player)) {
                player.getWorld().playSound(player.getLocation(), Sound.IRONGOLEM_DEATH, 2, 2);
                DisguiseAPI.undisguiseToAll(player);
                startCooldown();
                reset();
                return;
            }

            EntitySelector selector = new HitBoxSelector(config.getDouble("HitBox"));

            new EntityFinder(plugin, selector).findAll(player).forEach(target -> {
                AttackSettings settings = new AttackSettings(this.config, this.player.getLocation().getDirection());
                plugin.getDamageManager().attack(target, this, settings);

                target.getWorld().playSound(target.getLocation(), Sound.SLIME_ATTACK, 2, 2);
                Effects.itemBoom(plugin, target.getLocation(), new ItemStack(Material.SLIME_BALL), 4, 0.3, 5);
            });
        }, 4, 0);
    }

    public void reset() {
        active = false;
        task.cancel();
        DisguiseAPI.undisguiseToAll(this.player);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        if (active) {
            reset();
        }
    }
}