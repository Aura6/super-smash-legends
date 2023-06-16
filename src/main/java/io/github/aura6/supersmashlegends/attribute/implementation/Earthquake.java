package io.github.aura6.supersmashlegends.attribute.implementation;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import io.github.aura6.supersmashlegends.SuperSmashLegends;
import io.github.aura6.supersmashlegends.attribute.RightClickAbility;
import io.github.aura6.supersmashlegends.damage.AttackSettings;
import io.github.aura6.supersmashlegends.kit.Kit;
import io.github.aura6.supersmashlegends.utils.entity.EntityUtils;
import io.github.aura6.supersmashlegends.utils.block.BlockUtils;
import io.github.aura6.supersmashlegends.utils.effect.ParticleBuilder;
import io.github.aura6.supersmashlegends.utils.entity.finder.EntityFinder;
import io.github.aura6.supersmashlegends.utils.entity.finder.selector.HitBoxSelector;
import io.github.aura6.supersmashlegends.utils.entity.finder.selector.EntitySelector;
import io.github.aura6.supersmashlegends.utils.math.MathUtils;
import io.github.aura6.supersmashlegends.utils.math.VectorUtils;
import net.minecraft.server.v1_8_R3.EnumParticle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;

public class Earthquake extends RightClickAbility {
    private BukkitTask quakeTask;
    private BukkitTask uprootTask;
    private BukkitTask stopTask;

    public Earthquake(SuperSmashLegends plugin, Section config, Kit kit) {
        super(plugin, config, kit);
    }

    @Override
    public boolean invalidate(PlayerInteractEvent event) {
        return super.invalidate(event) || this.stopTask != null;
    }

    @Override
    public void onClick(PlayerInteractEvent event) {
        this.player.getWorld().playSound(this.player.getLocation(), Sound.IRONGOLEM_THROW, 1, 0.5f);

        double horizontal = this.config.getDouble("HorizontalRange");
        double vertical = this.config.getDouble("VerticalRange");

        this.quakeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!EntityUtils.isPlayerGrounded(this.player)) return;

            Location location = this.player.getLocation().add(0, 0.3, 0);
            new ParticleBuilder(EnumParticle.REDSTONE).setRgb(139, 69, 19).ring(location, 90, 0, 1.5, 30);
            new ParticleBuilder(EnumParticle.REDSTONE).setRgb(160, 82, 45).ring(location, 90, 0, 0.75, 15);

            EntitySelector selector = new HitBoxSelector(horizontal, vertical, horizontal);

            new EntityFinder(this.plugin, selector).findAll(this.player).forEach(target -> {
                if (!target.isOnGround()) return;

                AttackSettings settings = new AttackSettings(this.config, VectorUtils.fromTo(this.player, target));

                if (this.plugin.getDamageManager().attack(target, this, settings)) {
                    this.player.getWorld().playSound(target.getLocation(), Sound.ANVIL_LAND, 1, 1);
                    this.uproot(target.getLocation());
                }
            });
        }, 0, this.config.getInt("UprootInterval"));

        this.uprootTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!EntityUtils.isPlayerGrounded(this.player)) return;

            Location center = this.player.getLocation();

            int x = (int) MathUtils.randSpread(center.getBlockX(), horizontal);
            int z = (int) MathUtils.randSpread(center.getBlockZ(), horizontal);

            Location currLoc = new Location(this.player.getWorld(), x, center.getBlockY() + vertical, z);
            int movedDown = 0;

            while (movedDown < vertical * 2 && currLoc.getBlock().getType() == Material.AIR) {
                currLoc.subtract(0, 1, 0);
                movedDown++;
            }

            Location uprootLocation = new Location(this.player.getWorld(), x, currLoc.getY() + 1, z);

            if (uprootLocation.getBlock().getType() == Material.AIR) {
                this.uproot(uprootLocation);
            }
        }, 0, this.config.getInt("UprootInterval"));

        this.stopTask = Bukkit.getScheduler().runTaskLater(this.plugin, this::reset, this.config.getInt("Duration"));
    }

    private void uproot(Location loc) {
        this.player.getWorld().playSound(loc, Sound.DIG_GRASS, 1, 1);

        Block groundBlock = loc.clone().subtract(0, 0.5, 0).getBlock();
        BlockUtils.setBlockFast(loc, groundBlock.getTypeId(), groundBlock.getData());

        int id = Material.AIR.getId();
        int duration = this.config.getInt("UprootDuration");
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> BlockUtils.setBlockFast(loc, id, (byte) 2), duration);
    }

    private void reset() {
        if (this.stopTask == null) return;

        this.stopTask.cancel();
        this.stopTask = null;

        this.quakeTask.cancel();
        this.uprootTask.cancel();

        this.startCooldown();
        this.player.getWorld().playSound(this.player.getLocation(), Sound.IRONGOLEM_DEATH, 1, 1);
    }

    @Override
    public void deactivate() {
        this.reset();
        super.deactivate();
    }
}
