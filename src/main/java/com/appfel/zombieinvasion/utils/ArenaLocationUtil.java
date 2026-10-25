package com.appfel.zombieinvasion.utils;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.invasion.ArenaData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Random;

/**
 * Utilidad especializada en resolver ubicaciones seguras de spawn y suelo dentro de la arena.
 * Diseñado con protección estricta anti-techo:
 * - Prioriza los puntos de spawn establecidos por el usuario (/invasion setup zombie add).
 * - Detecta techos y cubiertas para evitar al 100% que mobs (sabuesos/zombies/jefes) o drops aparezcan sobre el tejado.
 * - Asegura que toda ubicación resultante esté en suelo sólido y tenga 2 bloques de aire para la cabeza.
 */
public final class ArenaLocationUtil {

    private static final Random random = new Random();

    private ArenaLocationUtil() {}

    /**
     * Obtiene una ubicación de spawn 100% segura dentro de la arena, respetando:
     * 1. Puntos de spawn configurados por el administrador (settings.arena.zombie-spawns).
     * 2. Detección estricta de techos / cubiertas (anti-techo).
     * 3. Búsqueda de suelo transitable bajo el techo.
     */
    public static Location getSafeSpawnLocation(ZombieInvasionPlugin plugin, ArenaData arena, Location center, double radius) {
        // 1. PRIORIDAD ABSOLUTA: Puntos de spawn establecidos manualmente por el administrador
        List<String> spawns = plugin.getConfig().getStringList("settings.arena.zombie-spawns");
        if (spawns != null && !spawns.isEmpty()) {
            String spawnStr = spawns.get(random.nextInt(spawns.size()));
            Location configured = parseSpawnLocation(spawnStr);
            if (configured != null) {
                // Aplicar una pequeña dispersión aleatoria (jitter) de ±0.7 bloques para no apilar los mobs
                Location jittered = applySafeJitter(configured, 0.7);
                if (isWalkable(jittered) && (arena == null || arena.isInside(jittered))) {
                    return jittered;
                }
                return configured;
            }
        }

        // 2. FALLBACK INTELIGENTE: Generación aleatoria dentro del radio de la arena
        if (center == null) return null;
        World world = center.getWorld();
        if (world == null) return null;

        double angle = random.nextDouble() * 2 * Math.PI;
        double r = random.nextDouble() * Math.max(2.0, radius * 0.75);
        double x = center.getX() + (r * Math.cos(angle));
        double z = center.getZ() + (r * Math.sin(angle));

        return findGroundUnderCeiling(world, x, z, center.getY(), arena);
    }

    /**
     * Parsea un string "world,x,y,z" a un objeto Location válido.
     */
    public static Location parseSpawnLocation(String spawnStr) {
        if (spawnStr == null || spawnStr.isBlank()) return null;
        String[] parts = spawnStr.split(",");
        if (parts.length >= 4) {
            World w = Bukkit.getWorld(parts[0].trim());
            if (w != null) {
                try {
                    return new Location(
                        w,
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Double.parseDouble(parts[3].trim())
                    );
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /**
     * Encuentra el bloque de suelo transitable en la coordenada (x, z) situándose
     * ESTRICTAMENTE POR DEBAJO de cualquier techo que exista en el recinto.
     */
    public static Location findGroundUnderCeiling(World world, double x, double z, double refY, ArenaData arena) {
        if (world == null) return null;
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int baseRefY = (int) Math.floor(refY);

        double maxUp = arena != null ? arena.heightUp() : 20.0;
        double maxDown = arena != null ? arena.heightDown() : 10.0;

        // Paso A: Detectar si hay un techo sólido por encima del nivel de referencia (refY)
        int ceilingY = -1;
        int maxCeilingScan = Math.min(world.getMaxHeight() - 1, baseRefY + (int) maxUp);
        for (int y = baseRefY + 2; y <= maxCeilingScan; y++) {
            Block b = world.getBlockAt(blockX, y, blockZ);
            if (isSolidCeiling(b)) {
                ceilingY = y;
                break;
            }
        }

        // Paso B: Si hay techo, el escaneo hacia abajo DEBE comenzar obligatoriamente
        // debajo del techo (ceilingY - 1). De este modo es IMPOSIBLE que termine sobre el tejado.
        // Si NO hay techo, comenzamos en baseRefY + 1 (altura de pies/ojos dentro del recinto).
        int startScanY = (ceilingY != -1) ? (ceilingY - 1) : (baseRefY + 1);
        int minScanY = Math.max(world.getMinHeight(), baseRefY - (int) maxDown);

        for (int y = startScanY; y >= minScanY; y--) {
            Block floorBlock = world.getBlockAt(blockX, y, blockZ);
            if (floorBlock.getType().isSolid()) {
                Block above1 = world.getBlockAt(blockX, y + 1, blockZ);
                Block above2 = world.getBlockAt(blockX, y + 2, blockZ);

                if (isPassable(above1) && isPassable(above2)) {
                    // Verificación extra: si había techo, asegurar que la cabeza no quede incrustada
                    if (ceilingY != -1 && (y + 2) >= ceilingY) {
                        continue;
                    }
                    return new Location(world, x, y + 1.0, z);
                }
            }
        }

        // Fallback defensivo: retornar refY a la altura del centro de la arena
        return new Location(world, x, refY, z);
    }

    /**
     * Ajusta una ubicación para colocarla exactamente sobre el suelo dentro de su recinto/techo.
     */
    public static Location getSafeFloorAt(Location loc, ArenaData arena) {
        if (loc == null || loc.getWorld() == null) return loc;
        return findGroundUnderCeiling(loc.getWorld(), loc.getX(), loc.getZ(), loc.getY(), arena);
    }

    public static Location getSafeFloorAt(Location loc) {
        return getSafeFloorAt(loc, null);
    }

    /**
     * Comprueba si un bloque actúa como techo sólido bloqueando el paso vertical.
     */
    private static boolean isSolidCeiling(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type.isSolid() && !type.isAir() && type != Material.BARRIER && !type.name().contains("AIR");
    }

    /**
     * Comprueba si un bloque es transitable para el cuerpo o cabeza de una entidad.
     */
    public static boolean isPassable(Block block) {
        if (block == null) return true;
        Material mat = block.getType();
        return !mat.isSolid() || mat.isAir();
    }

    /**
     * Comprueba si una ubicación es segura para pararse (suelo sólido debajo, 2 bloques transitables arriba).
     */
    public static boolean isWalkable(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block floor = loc.clone().subtract(0, 0.5, 0).getBlock();
        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1.0, 0).getBlock();
        return floor.getType().isSolid() && isPassable(feet) && isPassable(head);
    }

    /**
     * Aplica una pequeña dispersión horizontal segura a un punto.
     */
    private static Location applySafeJitter(Location origin, double maxOffset) {
        double dx = (random.nextDouble() - 0.5) * (maxOffset * 2.0);
        double dz = (random.nextDouble() - 0.5) * (maxOffset * 2.0);
        return origin.clone().add(dx, 0, dz);
    }
}
