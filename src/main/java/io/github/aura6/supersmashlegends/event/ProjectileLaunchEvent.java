package io.github.aura6.supersmashlegends.event;

import io.github.aura6.supersmashlegends.projectile.CustomProjectile;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;

public class ProjectileLaunchEvent extends ProjectileEvent implements Cancellable {
    @Getter @Setter private double speed;
    @Getter @Setter private boolean cancelled = false;

    public ProjectileLaunchEvent(CustomProjectile<? extends Entity> projectile, double speed) {
        super(projectile);
        this.speed = speed;
    }
}
