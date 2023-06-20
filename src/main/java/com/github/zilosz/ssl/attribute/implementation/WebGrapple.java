package com.github.zilosz.ssl.attribute.implementation;

import com.comphenix.protocol.ProtocolLibrary;
import com.github.zilosz.ssl.attribute.RightClickAbility;
import com.github.zilosz.ssl.projectile.ProjectileRemoveReason;
import com.github.zilosz.ssl.utils.math.VectorUtils;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import com.github.zilosz.ssl.SSL;
import com.github.zilosz.ssl.attribute.Ability;
import com.github.zilosz.ssl.event.attack.DamageEvent;
import com.github.zilosz.ssl.kit.Kit;
import com.github.zilosz.ssl.projectile.ItemProjectile;
import com.github.zilosz.ssl.utils.SoundCanceller;
import com.github.zilosz.ssl.utils.block.BlockHitResult;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class WebGrapple extends RightClickAbility {
    private SoundCanceller batSoundCanceller;
    private GrappleProjectile grappleProjectile;

    public WebGrapple(SSL plugin, Section config, Kit kit) {
        super(plugin, config, kit);
    }

    @Override
    public void onClick(PlayerInteractEvent event) {
        this.grappleProjectile = new GrappleProjectile(this.plugin, this, this.config.getSection("Projectile"));
        this.grappleProjectile.launch();

        this.player.getWorld().playSound(this.player.getLocation(), Sound.MAGMACUBE_JUMP, 1, 1);
    }

    @Override
    public void activate() {
        super.activate();

        this.batSoundCanceller = new SoundCanceller(this.plugin, "mob.bat.idle");
        ProtocolLibrary.getProtocolManager().addPacketListener(this.batSoundCanceller);
    }

    @Override
    public void deactivate() {
        super.deactivate();

        if (this.batSoundCanceller != null) {
            ProtocolLibrary.getProtocolManager().removePacketListener(this.batSoundCanceller);
        }

        if (this.grappleProjectile != null) {
            this.grappleProjectile.remove(ProjectileRemoveReason.DEACTIVATION);
        }
    }

    private static class GrappleProjectile extends ItemProjectile {
        private Bat bat;

        public GrappleProjectile(SSL plugin, Ability ability, Section config) {
            super(plugin, ability, config);
        }

        @Override
        public Item createEntity(Location location) {
            Item item = super.createEntity(location);
            this.bat = location.getWorld().spawn(this.launcher.getEyeLocation(), Bat.class);
            this.bat.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 10_000, 1));
            this.bat.setLeashHolder(item);
            this.launcher.setPassenger(this.bat);
            return item;
        }

        private void pull() {
            Vector direction = VectorUtils.fromTo(this.launcher, this.entity).normalize();
            Vector extraY = new Vector(0, this.config.getDouble("ExtraY"), 0);
            this.launcher.setVelocity(direction.multiply(this.config.getDouble("PullSpeed")).add(extraY));
        }

        @Override
        public void onTargetHit(LivingEntity target) {
            this.pull();
        }

        @Override
        public void onBlockHit(BlockHitResult result) {
            this.pull();
            this.launcher.playSound(this.launcher.getLocation(), Sound.DIG_WOOD, 1, 1);
        }

        @Override
        public void onRemove(ProjectileRemoveReason reason) {
            this.bat.remove();
        }

        @EventHandler
        public void onBatDamage(DamageEvent event) {
            if (event.getVictim() == this.bat) {
                event.setCancelled(true);
            }
        }

        @EventHandler
        public void onDeath(EntityDeathEvent event) {
            if (event.getEntity() == this.bat) {
                this.remove(ProjectileRemoveReason.ENTITY_DEATH);
            }
        }
    }
}