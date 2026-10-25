package com.appfel.zombieinvasion.powerup;

import com.appfel.zombieinvasion.invasion.InvasionKeys;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

public class PowerUpDrop {

    private final PowerUpType type;
    private final Location baseLocation;
    private ArmorStand displayStand;
    private ArmorStand textStand;
    private int remainingTicks;
    private boolean valid;

    public PowerUpDrop(PowerUpType type, Location location) {
        this.type = type;
        this.baseLocation = location.clone().add(0, -0.6, 0);
        this.remainingTicks = 20 * 20; // 20 segundos de duración antes de desaparecer
        this.valid = true;
        spawn();
    }

    private void spawn() {
        World world = baseLocation.getWorld();
        if (world == null) {
            this.valid = false;
            return;
        }

        // Barrido físico preventivo de ArmorStands residuales en esta posición
        for (org.bukkit.entity.Entity ent : world.getNearbyEntities(baseLocation, 1.8, 2.5, 1.8)) {
            if (ent instanceof ArmorStand oldStand) {
                var pdc = oldStand.getPersistentDataContainer();
                if ("POWER_UP".equals(pdc.get(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING)) || oldStand.isMarker()) {
                    oldStand.remove();
                }
            }
        }

        // Stand inferior que sostiene el bloque giratorio
        displayStand = world.spawn(baseLocation, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setCanPickupItems(false);
            stand.setPersistent(false);
            stand.getPersistentDataContainer().set(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING, "POWER_UP");
            stand.getEquipment().setHelmet(new ItemStack(type.getIconMaterial()));
        });

        // Stand superior para el holograma de texto interactivo
        Location textLoc = baseLocation.clone().add(0, 1.25, 0);
        textStand = world.spawn(textLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setCustomNameVisible(true);
            stand.setPersistent(false);
            stand.getPersistentDataContainer().set(InvasionKeys.HOLOGRAM_TYPE, PersistentDataType.STRING, "POWER_UP");
            stand.customName(ColorUtil.parse(type.getDisplayName() + " &e(20s)"));
        });
    }

    public void tick(double rotationRadians) {
        if (!valid) return;

        remainingTicks -= 5;
        if (remainingTicks <= 0) {
            destroy();
            return;
        }

        World world = baseLocation.getWorld();
        if (world == null) {
            destroy();
            return;
        }

        // Rotación arcade giratoria
        if (displayStand != null && displayStand.isValid()) {
            displayStand.setHeadPose(new EulerAngle(0, rotationRadians, 0));
        }

        // Actualización de holograma cada segundo (20 ticks)
        if (textStand != null && textStand.isValid() && remainingTicks % 20 == 0) {
            int secondsLeft = remainingTicks / 20;
            textStand.customName(ColorUtil.parse(type.getDisplayName() + " &e(" + secondsLeft + "s)"));
        }

        // Partículas orbitales llamativas
        Location particleLoc = baseLocation.clone().add(0, 0.8, 0);
        world.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 1, 0.2, 0.2, 0.2, 0.02);
    }

    public void destroy() {
        this.valid = false;
        if (displayStand != null && displayStand.isValid()) {
            displayStand.remove();
        }
        if (textStand != null && textStand.isValid()) {
            textStand.remove();
        }
    }

    public boolean isValid() {
        return valid;
    }

    public PowerUpType getType() {
        return type;
    }

    public Location getLocation() {
        return baseLocation;
    }
}
