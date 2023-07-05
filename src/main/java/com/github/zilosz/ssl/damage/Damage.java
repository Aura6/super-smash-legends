package com.github.zilosz.ssl.damage;

import com.github.zilosz.ssl.SSL;
import com.github.zilosz.ssl.kit.KitManager;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import lombok.Getter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@Getter
public class Damage {
    private double damage;
    private boolean factorsArmor;

    public Damage(Section config) {
        this(config.getDouble("Damage"), config.getOptionalBoolean("FactorsArmor").orElse(true));
    }

    public Damage(double damage, boolean factorsArmor) {
        this.damage = damage;
        this.factorsArmor = factorsArmor;
    }

    public Damage setDamage(double damage) {
        this.damage = damage;
        return this;
    }

    public Damage setFactorsArmor(boolean factorsArmor) {
        this.factorsArmor = factorsArmor;
        return this;
    }

    public double getFinalDamage(LivingEntity victim) {
        double damageValue = this.damage;

        if (this.factorsArmor && victim instanceof Player) {
            KitManager kitManager = SSL.getInstance().getKitManager();
            damageValue *= kitManager.getSelectedKit((Player) victim).getArmor();
        }

        return damageValue;
    }
}