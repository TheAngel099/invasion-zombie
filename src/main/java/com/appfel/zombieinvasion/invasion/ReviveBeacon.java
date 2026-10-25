package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Representa la Tumba / Faro de Resurrección de un compañero caído en la arena.
 * Gestiona el ArmorStand con la cabeza del jugador, partículas y el progreso de canalización.
 * Optimizado para Java 25 y Purpur 26.2.
 */
public class ReviveBeacon {

    private final ZombieInvasionPlugin plugin;
    private final UUID fallenPlayerId;
    private final String fallenPlayerName;
    private Location location;
    private final long createdAt;

    private ArmorStand markerStand;
    private double progress = 0.0;
    private UUID currentReviverId = null;
    private long lastClickTime = 0;
    private UUID lastClickerId = null;

    public ReviveBeacon(ZombieInvasionPlugin plugin, Player fallen, Location location) {
        this.plugin = plugin;
        this.fallenPlayerId = fallen.getUniqueId();
        this.fallenPlayerName = fallen.getName();
        this.location = location.clone();
        this.createdAt = System.currentTimeMillis();
        spawn();
    }

    private void spawn() {
        if (location.getWorld() == null) return;

        // Ground-snapping inteligente: Asegurar que la baliza no quede flotando en el aire si el jugador cayó saltando
        Location groundLoc = location.clone();
        for (int i = 0; i < 8; i++) {
            if (groundLoc.getBlock().getType().isSolid()) {
                groundLoc.setY(groundLoc.getBlockY() + 1.0);
                break;
            }
            if (groundLoc.getY() <= groundLoc.getWorld().getMinHeight()) break;
            groundLoc.subtract(0, 0.5, 0);
        }

        // Actualizar la ubicación canónica de la baliza al nivel exacto del suelo firme
        this.location = groundLoc.clone();

        // Ajustar altura para que la cabeza quede a la altura del pecho del jugador
        Location spawnLoc = groundLoc.clone().subtract(0, 0.35, 0);

        markerStand = (ArmorStand) groundLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        markerStand.setVisible(false);
        markerStand.setGravity(false);
        markerStand.setInvulnerable(true);
        markerStand.setSmall(true);
        markerStand.setBasePlate(false);
        markerStand.setArms(false);
        markerStand.setCanTick(false);
        markerStand.setCustomNameVisible(true);

        // Bloquear completamente la manipulación de equipamiento en Paper/Purpur para que no puedan robar la cabeza
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            markerStand.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
            markerStand.addEquipmentLock(slot, ArmorStand.LockType.REMOVING_OR_CHANGING);
        }

        // Equipar la cabeza del jugador caído
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(fallenPlayerId));
            skull.setItemMeta(meta);
        }
        markerStand.getEquipment().setHelmet(skull);

        // Tags en PDC para identificación estricta
        markerStand.setPersistent(false);
        markerStand.getPersistentDataContainer().set(
            InvasionKeys.HOLOGRAM_TYPE,
            PersistentDataType.STRING,
            "REVIVE_BEACON"
        );
        markerStand.getPersistentDataContainer().set(
            InvasionKeys.REVIVE_BEACON,
            PersistentDataType.STRING,
            fallenPlayerId.toString()
        );

        updateDisplay();
    }

    /**
     * Garantiza que la entidad ArmorStand esté viva y activa en el mundo,
     * re-spawneándola automáticamente si fue removida por un barrido de chunks o comando de limpieza.
     */
    public void ensureEntityAlive() {
        if (markerStand == null || !markerStand.isValid() || markerStand.isDead()) {
            spawn();
        }
    }

    public void updateDisplay() {
        ensureEntityAlive();
        if (markerStand == null || !markerStand.isValid()) return;

        if (progress > 0.0) {
            int pct = (int) Math.min(100, Math.max(0, progress));
            String bar = buildProgressBar(pct);
            markerStand.customName(ColorUtil.parse(
                "<gradient:#00f2fe:#4facfe><b>⚡ Reanimando:</b></gradient> <yellow>" + fallenPlayerName + 
                "</yellow> <green>[" + bar + "] " + pct + "%</green>"
            ));
        } else {
            markerStand.customName(ColorUtil.parse(
                "<gradient:#ff416c:#ff4b2b><b>☠ Caído:</b></gradient> <yellow>" + fallenPlayerName + 
                "</yellow> <gradient:#4facfe:#00f2fe><b>[Agáchate (Shift) o Clic]</b></gradient>"
            ));
        }
    }

    public void playAmbientParticles() {
        ensureEntityAlive();
        Location pLoc = getLocation();
        if (pLoc.getWorld() == null) return;
        // Efecto de aura de almas en el suelo
        pLoc.getWorld().spawnParticle(
            Particle.SOUL_FIRE_FLAME,
            pLoc.clone().add(0, 0.2, 0),
            4, 0.35, 0.1, 0.35, 0.01
        );
        pLoc.getWorld().spawnParticle(
            Particle.DUST,
            pLoc.clone().add(0, 0.5, 0),
            2, 0.2, 0.2, 0.2,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 242, 254), 1.2f)
        );
    }

    public void playRevivingParticles() {
        ensureEntityAlive();
        Location pLoc = getLocation();
        if (pLoc.getWorld() == null) return;
        pLoc.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            pLoc.clone().add(0, 0.7, 0),
            6, 0.4, 0.4, 0.4, 0.05
        );
    }

    public String buildProgressBar(int percent) {
        int totalBars = 10;
        int completedBars = (int) Math.round((percent / 100.0) * totalBars);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            if (i < completedBars) {
                sb.append("█");
            } else {
                sb.append("░");
            }
        }
        return sb.toString();
    }

    public void penalizeProgress(double penalty) {
        setProgress(Math.max(0.0, this.progress - penalty));
    }

    public void registerClick(UUID reviverId) {
        this.currentReviverId = reviverId;
        this.lastClickerId = reviverId;
        this.lastClickTime = System.currentTimeMillis();
    }

    public boolean isRecentlyClickedBy(UUID playerId) {
        return playerId != null && playerId.equals(lastClickerId) && (System.currentTimeMillis() - lastClickTime < 1500);
    }

    public void remove() {
        if (markerStand != null && markerStand.isValid()) {
            markerStand.remove();
            markerStand = null;
        }
    }

    // Getters y Setters
    public UUID getFallenPlayerId() { return fallenPlayerId; }
    public String getFallenPlayerName() { return fallenPlayerName; }
    public Location getLocation() {
        if (markerStand != null && markerStand.isValid()) {
            return markerStand.getLocation().clone().add(0, 0.35, 0);
        }
        return location.clone();
    }
    public double getProgress() { return progress; }
    public void setProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(100.0, progress));
        updateDisplay();
    }
    public UUID getCurrentReviverId() { return currentReviverId; }
    public void setCurrentReviverId(UUID currentReviverId) { this.currentReviverId = currentReviverId; }
    public long getCreatedAt() { return createdAt; }
    public ArmorStand getMarkerStand() { return markerStand; }
}

