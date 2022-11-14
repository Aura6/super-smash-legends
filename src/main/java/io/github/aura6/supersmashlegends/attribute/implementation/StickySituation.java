package io.github.aura6.supersmashlegends.attribute.implementation;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import io.github.aura6.supersmashlegends.SuperSmashLegends;
import io.github.aura6.supersmashlegends.attribute.PassiveAbility;
import io.github.aura6.supersmashlegends.event.EnergyEvent;
import io.github.aura6.supersmashlegends.event.RegenEvent;
import io.github.aura6.supersmashlegends.kit.Kit;
import io.github.aura6.supersmashlegends.utils.effect.Effects;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class StickySituation extends PassiveAbility {
    private boolean active = false;

    public StickySituation(SuperSmashLegends plugin, Section config, Kit kit) {
        super(plugin, config, kit);
    }

    @Override
    public String getUseType() {
        return "Low Health";
    }

    public void reset() {
        active = false;
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        reset();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReceivingDamage(EntityDamageEvent event) {
        if (event.getEntity() == player && !active && event.getCause() != EntityDamageEvent.DamageCause.FALL && player.getHealth() - event.getFinalDamage() <= config.getDouble("HealthThreshold")) {
            active = true;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, config.getInt("Speed")));
            player.getWorld().playSound(player.getLocation(), Sound.ZOMBIE_PIG_ANGRY, 1, 1);
            Effects.launchFirework(player.getEyeLocation(), Color.fromRGB(0, 255, 0), 1);
        }
    }

    @EventHandler
    public void onRegen(RegenEvent event) {
        if (event.getPlayer() == player && active && player.getHealth() > config.getDouble("HealthThreshold")) {
            reset();
            player.getWorld().playSound(player.getLocation(), Sound.ZOMBIE_PIG_DEATH, 1, 1);
            Effects.launchFirework(player.getEyeLocation(), Color.fromRGB(0, 102, 0), 1);
        }
    }

    @EventHandler
    public void onEnergy(EnergyEvent event) {
        if (event.getPlayer() == player && active) {
            event.setEnergy(event.getEnergy() * config.getFloat("EnergyMultiplier"));
        }
    }
}
