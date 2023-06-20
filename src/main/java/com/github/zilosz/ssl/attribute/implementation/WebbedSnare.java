package com.github.zilosz.ssl.attribute.implementation;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import com.github.zilosz.ssl.SSL;
import com.github.zilosz.ssl.attribute.Ability;
import com.github.zilosz.ssl.attribute.RightClickAbility;
import com.github.zilosz.ssl.event.attack.AttributeDamageEvent;
import com.github.zilosz.ssl.kit.Kit;
import com.github.zilosz.ssl.projectile.ItemProjectile;
import com.github.zilosz.ssl.projectile.ProjectileRemoveReason;
import com.github.zilosz.ssl.utils.block.BlockHitResult;
import com.github.zilosz.ssl.utils.effect.ParticleBuilder;
import com.github.zilosz.ssl.utils.entity.EntityUtils;
import com.github.zilosz.ssl.utils.math.VectorUtils;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class WebbedSnare extends RightClickAbility {
    private Set<LivingEntity> hitEntities;

    public WebbedSnare(SSL plugin, Section config, Kit kit) {
        super(plugin, config, kit);
    }

    private void launch(Location source, boolean first) {
        SnareProjectile projectile = new SnareProjectile(this.plugin, this, this.config.getSection("Projectile"));
        projectile.setOverrideLocation(source);

        if (first) {
            projectile.setSpread(0);
        }

        projectile.launch();
    }

    @Override
    public void onClick(PlayerInteractEvent event) {
        this.player.getWorld().playSound(this.player.getLocation(), Sound.SPIDER_DEATH, 2, 2);
        this.player.setVelocity(this.player.getEyeLocation().getDirection().multiply(this.config.getDouble("Velocity")));

        this.hitEntities = new HashSet<>();

        Vector direction = this.player.getEyeLocation().getDirection().multiply(-1);
        Location source = EntityUtils.center(this.player).setDirection(direction);

        this.launch(source, true);

        double angle = this.config.getDouble("ConicAngle");
        int count = this.config.getInt("ExtraWebCount");

        for (Vector vector : VectorUtils.getConicVectors(source, angle, count)) {
            this.launch(source.setDirection(vector), false);
        }
    }

    @EventHandler
    public void onHitEntity(AttributeDamageEvent event) {
        if (event.getAttribute() != this) return;

        if (this.hitEntities.contains(event.getVictim())) {
            event.setCancelled(true);
        }

        this.hitEntities.add(event.getVictim());
    }

    private static class SnareProjectile extends ItemProjectile {
        private Block webBlock;

        public SnareProjectile(SSL plugin, Ability ability, Section config) {
            super(plugin, ability, config);
        }

        @Override
        public void onTick() {

            if (this.entity.getLocation().getBlock().getType() == Material.WEB) {
                this.remove(ProjectileRemoveReason.HIT_BLOCK);

            } else if (this.ticksAlive % 2 == 0) {
                new ParticleBuilder(EnumParticle.SNOWBALL).show(this.entity.getLocation());
            }
        }

        private void turnIntoWeb(LivingEntity target) {

            if (target == null) {
                this.webBlock = this.entity.getLocation().getBlock();

            } else {
                this.webBlock = target.getLocation().getBlock();
            }

            this.webBlock.setType(Material.WEB);
            int duration = this.config.getInt("WebDuration");

            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (this.webBlock.getType() == Material.WEB) {
                    this.webBlock.setType(Material.AIR);
                }
            }, duration);
        }

        @Override
        public void onTargetHit(LivingEntity target) {
            this.turnIntoWeb(target);
            target.setVelocity(new Vector(0, 0, 0));
        }

        @Override
        public void onBlockHit(BlockHitResult result) {
            this.turnIntoWeb(null);
        }
    }
}