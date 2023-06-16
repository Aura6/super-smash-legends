package io.github.aura6.supersmashlegends.event.attack;

import io.github.aura6.supersmashlegends.attribute.Attribute;
import io.github.aura6.supersmashlegends.damage.AttackSettings;
import io.github.aura6.supersmashlegends.event.CustomEvent;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.util.Vector;

import java.util.Optional;

@Getter
public class AttackEvent extends CustomEvent implements Cancellable {
    @Setter private boolean cancelled = false;
    private final LivingEntity victim;
    private final AttackSettings attackSettings;
    private final Attribute attribute;

    public AttackEvent(LivingEntity victim, AttackSettings attackSettings, Attribute attribute) {
        this.victim = victim;
        this.attackSettings = attackSettings;
        this.attribute = attribute;
    }

    public double getFinalDamage() {
        return this.attackSettings.getDamageSettings().getFinalDamage(this.victim);
    }

    public double getFinalKb() {
        return this.attackSettings.getKbSettings().getFinalKb(this.victim);
    }

    public Optional<Vector> getFinalKbVector() {
        return this.attackSettings.getKbSettings().getFinalKbVector(this.victim);
    }
}

