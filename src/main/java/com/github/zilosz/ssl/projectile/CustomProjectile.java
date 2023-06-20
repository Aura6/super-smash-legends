package com.github.zilosz.ssl.projectile;

import com.github.zilosz.ssl.damage.AttackSettings;
import com.github.zilosz.ssl.event.projectile.ProjectileRemoveEvent;
import com.github.zilosz.ssl.utils.entity.finder.EntityFinder;
import com.github.zilosz.ssl.utils.entity.finder.selector.HitBoxSelector;
import com.github.zilosz.ssl.utils.math.VectorUtils;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import com.github.zilosz.ssl.SSL;
import com.github.zilosz.ssl.attribute.Ability;
import com.github.zilosz.ssl.event.projectile.ProjectileHitBlockEvent;
import com.github.zilosz.ssl.event.projectile.ProjectileLaunchEvent;
import com.github.zilosz.ssl.game.state.InGameState;
import com.github.zilosz.ssl.utils.NmsUtils;
import com.github.zilosz.ssl.utils.Reflector;
import com.github.zilosz.ssl.utils.block.BlockHitResult;
import com.github.zilosz.ssl.utils.file.YamlReader;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public abstract class CustomProjectile<T extends Entity> extends BukkitRunnable implements Listener {
    protected final SSL plugin;

    @Getter protected final Ability ability;
    @Getter protected Player launcher;
    @Getter protected T entity;
    protected final Section config;

    @Setter protected Location overrideLocation;
    @Getter @Setter protected Double speed;

    @Getter @Setter protected float spread;
    @Getter @Setter protected int lifespan;
    @Getter @Setter protected boolean hasGravity;
    @Getter @Setter protected int maxBounces;
    @Getter @Setter protected double hitBox;
    @Getter @Setter protected boolean hitsMultiple;
    @Getter @Setter protected boolean removeOnEntityHit;
    @Getter @Setter protected double distanceFromEye;
    @Getter @Setter protected boolean removeOnBlockHit;
    @Getter protected boolean invisible;
    @Getter protected AttackSettings attackSettings;

    @Getter protected int ticksAlive = 0;
    protected Vector launchVelocity;
    @Getter protected double launchSpeed;
    protected int timesBounced = 0;

    public CustomProjectile(SSL plugin, Ability ability, Section config) {
        this.plugin = plugin;
        this.ability = ability;
        this.config = config;

        launcher = ability.getPlayer();
        attackSettings = new AttackSettings(config, null);

        spread = config.getFloat("Spread");
        lifespan = config.getOptionalInt("Lifespan").orElse(Integer.MAX_VALUE);
        hasGravity = config.getOptionalBoolean("HasGravity").orElse(true);
        maxBounces = config.getInt("MaxBounces");
        hitBox = config.getOptionalDouble("HitBox").orElse(defaultHitBox());
        hitsMultiple = config.getBoolean("HitsMultiple");
        removeOnEntityHit = config.getOptionalBoolean("RemoveOnEntityHit").orElse(true);
        removeOnBlockHit = config.getOptionalBoolean("RemoveOnBlockHit").orElse(true);
        distanceFromEye = config.getOptionalDouble("DistanceFromEye").orElse(1.0);
        invisible = config.getBoolean("Invisible");
    }

    public Vector getLaunchVelocity() {
        return this.launchVelocity.clone();
    }

    @SuppressWarnings("unchecked")
    public CustomProjectile<T> copy(Ability ability) {
        return (CustomProjectile<T>) Reflector.newInstance(getClass(), this.plugin, ability, this.config);
    }

    public double defaultHitBox() {
        return 0.8;
    }

    public abstract T createEntity(Location location);

    private void applyEntityParams() {
        if (invisible) {
            NmsUtils.broadcastPacket(new PacketPlayOutEntityDestroy(1, entity.getEntityId()));
        }
    }

    public void onLaunch() {}

    public void launch() {
        speed = this.speed == null ? config.getDouble("Speed") : this.speed;

        ProjectileLaunchEvent projectileLaunchEvent = new ProjectileLaunchEvent(this, speed);
        Bukkit.getPluginManager().callEvent(projectileLaunchEvent);

        if (projectileLaunchEvent.isCancelled()) return;

        Bukkit.getPluginManager().registerEvents(this, plugin);

        Location location = overrideLocation == null ? ability.getPlayer().getEyeLocation() : overrideLocation.clone();
        location.setDirection(VectorUtils.getRandomVectorInDirection(location, this.spread));

        location.add(location.getDirection().multiply(distanceFromEye));

        launchSpeed = projectileLaunchEvent.getSpeed();
        launchVelocity = location.getDirection().multiply(launchSpeed);

        entity = createEntity(location);
        applyEntityParams();
        entity.setVelocity(launchVelocity);

        config.getOptionalSection("LaunchSound").ifPresent(soundConfig -> YamlReader.noise(soundConfig).playForAll(location));

        runTaskTimer(plugin, 0, 0);
        onLaunch();
    }

    public void onRemove(ProjectileRemoveReason reason) {}

    public void remove(ProjectileRemoveReason reason) {
        ProjectileRemoveEvent event = new ProjectileRemoveEvent(this, reason);
        Bukkit.getPluginManager().callEvent(event);

        if (!event.isCancelled()) {
            HandlerList.unregisterAll(this);
            this.entity.remove();
            this.cancel();
            this.onRemove(reason);
        }
    }

    public void onBlockHit(BlockHitResult result) {}

    protected void handleBlockHitResult(BlockHitResult result) {
        if (result == null) return;

        ProjectileHitBlockEvent event = new ProjectileHitBlockEvent(this, result);
        Bukkit.getPluginManager().callEvent(event);

        this.onBlockHit(result);

        config.getOptionalSection("BlockHitSound").ifPresent(section -> YamlReader.noise(section).playForAll(entity.getLocation()));

        if (result.getFace() == null) return;

        if (++timesBounced > maxBounces) {

            if (removeOnBlockHit) {
                remove(ProjectileRemoveReason.HIT_BLOCK);
            }

            return;
        }

        Vector velocity = hasGravity ? launchVelocity : entity.getVelocity();

        switch (result.getFace()) {

            case UP:
            case DOWN:
                velocity.setY(-velocity.getY());
                break;

            case NORTH:
            case SOUTH:
                velocity.setZ(-velocity.getZ());
                break;

            default:
                velocity.setX(-velocity.getX());
        }

        if (this instanceof ActualProjectile) {
            entity = createEntity(entity.getLocation());
            applyEntityParams();
        }

        setVelocity(velocity);
    }

    public void onTick() {}

    public void onTargetHit(LivingEntity target) {}

    protected void handleTargetHit(LivingEntity target) {
        this.attackSettings.modifyKb(kb -> kb.setDirection(this.entity.getVelocity()));

        if (!this.plugin.getDamageManager().attack(target, this.ability, this.attackSettings)) return;

        this.launcher.playSound(this.launcher.getLocation(), Sound.SUCCESSFUL_HIT, 2, 1);
        this.config.getOptionalSection("TargetHitSound").ifPresent(sound -> YamlReader.noise(sound).playForAll(this.entity.getLocation()));

        this.onTargetHit(target);

        if (this.removeOnEntityHit) {
            this.remove(ProjectileRemoveReason.HIT_ENTITY);
        }
    }

    protected EntityFinder getFinder() {
        return new EntityFinder(plugin, new HitBoxSelector(hitBox));
    }

    protected void searchForHit() {
        EntityFinder finder = getFinder();

        if (hitsMultiple) {
            finder.findAll(launcher, entity.getLocation()).forEach(this::handleTargetHit);

        } else {
            finder.findClosest(launcher, entity.getLocation()).ifPresent(this::handleTargetHit);
        }
    }

    @Override
    public void run() {
        this.ticksAlive++;
        ProjectileRemoveReason reason = null;

        if (!this.entity.isValid()) {
            reason = ProjectileRemoveReason.ENTITY_DEATH;

        } else if (!(this.plugin.getGameManager().getState() instanceof InGameState)) {
            reason = ProjectileRemoveReason.DEACTIVATION;

        } else if (this.ticksAlive >= lifespan) {
            reason = ProjectileRemoveReason.LIFESPAN;
        }

        if (reason != null) {
            this.remove(reason);
            return;
        }

        if (!(this instanceof ActualProjectile) || this.config.isNumber("HitBox")) {
            this.searchForHit();
        }

        if (!this.hasGravity) {
            this.entity.setVelocity(this.launchVelocity);
        }

        this.onTick();
    }

    public void setVelocity(Vector velocity) {
        if (hasGravity) {
            entity.setVelocity(velocity);
        } else {
            launchVelocity = velocity;
        }
    }
}