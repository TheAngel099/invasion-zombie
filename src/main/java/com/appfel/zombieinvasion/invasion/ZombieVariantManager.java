package com.appfel.zombieinvasion.invasion;

import com.appfel.zombieinvasion.ZombieInvasionPlugin;
import com.appfel.zombieinvasion.utils.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestor centralizado y de alto rendimiento para variantes de zombies en Java 25.
 * Controla la carga de mobs.yml, la asignación ponderada de variantes,
 * el ticker unificado de habilidades, la IA asimétrica táctica y la mitigación absoluta de fuego amigo.
 */
public class ZombieVariantManager {

    private final ZombieInvasionPlugin plugin;
    private final Map<ZombieVariantType, ZombieVariant> variants = new EnumMap<>(ZombieVariantType.class);
    private final Map<ZombieVariantType, ZombieVariantConfig> configs = new EnumMap<>(ZombieVariantType.class);

    private boolean variantsEnabled = true;
    private boolean tacticalTargeting = true;
    private BukkitTask tickerTask;
    private long tickCounter = 0;

    private final List<AcidPuddle> activePuddles = new ArrayList<>();

    public ZombieVariantManager(ZombieInvasionPlugin plugin) {
        this.plugin = plugin;
        registerAllVariants();
    }

    private void registerAllVariants() {
        variants.put(ZombieVariantType.TANK, new TankZombie());
        variants.put(ZombieVariantType.SPEED, new SpeedZombie());
        variants.put(ZombieVariantType.ENDER, new EnderZombie());
        variants.put(ZombieVariantType.EXPLOSIVE, new ExplosiveZombie());
        variants.put(ZombieVariantType.FROST, new FrostZombie());
        variants.put(ZombieVariantType.SPITTER, new ToxicSpitterZombie());
        variants.put(ZombieVariantType.NECROMANCER, new NecromancerZombie());
        variants.put(ZombieVariantType.BERSERKER, new BerserkerZombie());
        variants.put(ZombieVariantType.VORTEX, new VortexZombie());
        variants.put(ZombieVariantType.PHANTOM, new PhantomZombie());
        variants.put(ZombieVariantType.DEFAULT, new DefaultZombie());
    }

    public boolean isTacticalTargetingEnabled() {
        return variantsEnabled && tacticalTargeting;
    }

    /**
     * Carga o recarga la configuración de variantes desde mobs.yml.
     */
    public void loadConfig() {
        configs.clear();
        FileConfiguration mobsCfg = plugin.getConfigManager() != null ? plugin.getConfigManager().getMobsConfig() : null;

        if (mobsCfg != null && mobsCfg.contains("variants")) {
            variantsEnabled = mobsCfg.getBoolean("variants.enabled", true);
            tacticalTargeting = mobsCfg.getBoolean("variants.tactical_targeting", true);
            ConfigurationSection typesSec = mobsCfg.getConfigurationSection("variants.types");

            for (ZombieVariantType type : ZombieVariantType.values()) {
                if (typesSec != null && typesSec.contains(type.name())) {
                    ConfigurationSection sec = typesSec.getConfigurationSection(type.name());
                    if (sec != null) {
                        boolean enabled = sec.getBoolean("enabled", true);
                        int weight = sec.getInt("weight", ZombieVariantConfig.createDefault(type).weight());
                        double hpMult = sec.getDouble("health_multiplier", 1.0);
                        double speedMult = sec.getDouble("speed_multiplier", 1.0);
                        String name = sec.getString("name", null);

                        Map<String, Object> extra = new HashMap<>();
                        for (String key : sec.getKeys(false)) {
                            extra.put(key, sec.get(key));
                        }

                        configs.put(type, new ZombieVariantConfig(type, enabled, weight, hpMult, speedMult, name, extra));
                        continue;
                    }
                }
                configs.put(type, ZombieVariantConfig.createDefault(type));
            }
        } else {
            variantsEnabled = true;
            tacticalTargeting = true;
            for (ZombieVariantType type : ZombieVariantType.values()) {
                configs.put(type, ZombieVariantConfig.createDefault(type));
            }
        }
    }

    /**
     * Asigna una variante de forma ponderada a un Zombie recién spawneado.
     */
    public void assignVariant(Zombie zombie, int round, Map<?, ?> mobMap) {
        if (!variantsEnabled) {
            zombie.getPersistentDataContainer().set(ZombieVariant.getKey(plugin), PersistentDataType.STRING, "DEFAULT");
            applyNametag(zombie, mobMap);
            return;
        }

        ZombieVariantType selectedType = selectWeightedVariant(round);
        zombie.getPersistentDataContainer().set(ZombieVariant.getKey(plugin), PersistentDataType.STRING, selectedType.name());

        ZombieVariant variant = variants.getOrDefault(selectedType, variants.get(ZombieVariantType.DEFAULT));
        ZombieVariantConfig config = configs.getOrDefault(selectedType, ZombieVariantConfig.createDefault(selectedType));

        variant.onSpawn(zombie, config);

        // Si es DEFAULT, se permite usar un nametag personalizado de la ronda si existe
        if (selectedType == ZombieVariantType.DEFAULT) {
            applyNametag(zombie, mobMap);
        }
    }

    private void applyNametag(Zombie zombie, Map<?, ?> mobMap) {
        if (mobMap != null && mobMap.containsKey("nametags")) {
            Object tagsObj = mobMap.get("nametags");
            if (tagsObj instanceof List<?> tags && !tags.isEmpty()) {
                Object tag = tags.get(ThreadLocalRandom.current().nextInt(tags.size()));
                zombie.customName(ColorUtil.parse(String.valueOf(tag)));
                zombie.setCustomNameVisible(true);
            }
        }
    }

    private ZombieVariantType selectWeightedVariant(int round) {
        int totalWeight = 0;
        List<ZombieVariantConfig> activePool = new ArrayList<>();

        for (ZombieVariantConfig cfg : configs.values()) {
            if (cfg.enabled() && cfg.weight() > 0) {
                activePool.add(cfg);
                totalWeight += cfg.weight();
            }
        }

        if (totalWeight <= 0 || activePool.isEmpty()) {
            return ZombieVariantType.DEFAULT;
        }

        int rand = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;
        for (ZombieVariantConfig cfg : activePool) {
            current += cfg.weight();
            if (rand < current) {
                return cfg.type();
            }
        }

        return ZombieVariantType.DEFAULT;
    }

    /**
     * Asigna de inmediato el objetivo táctico inicial a un zombie recién generado en la arena.
     */
    public void assignInitialTarget(Zombie zombie, ArenaData arena) {
        if (!isTacticalTargetingEnabled() || zombie == null || !zombie.isValid()) return;

        List<Player> participants = getAliveParticipants(arena);
        if (participants.isEmpty()) return;

        String varTypeStr = zombie.getPersistentDataContainer().get(ZombieVariant.getKey(plugin), PersistentDataType.STRING);
        ZombieVariantType type = ZombieVariantType.fromString(varTypeStr);
        ZombieVariant variant = variants.getOrDefault(type, variants.get(ZombieVariantType.DEFAULT));

        TacticalTargetContext context = createContext(arena);
        Player target = variant.selectTacticalTarget(zombie, arena, participants, Collections.emptyMap(), context);
        if (target != null) {
            zombie.setTarget(target);
        }
    }

    public List<Player> getAliveParticipants(ArenaData arena) {
        List<Player> participants = new ArrayList<>();
        for (UUID pid : plugin.getInvasionManager().getParticipants()) {
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline() && plugin.getInvasionManager().isAliveParticipant(p)) {
                if (arena == null || arena.isInside(p.getLocation())) {
                    participants.add(p);
                }
            }
        }
        return participants;
    }

    private TacticalTargetContext createContext(ArenaData arena) {
        return new TacticalTargetContext(
            plugin,
            plugin.getInvasionManager().getPlayerRoundKills(),
            plugin.getInvasionManager().getReviveManager() != null 
                ? plugin.getInvasionManager().getReviveManager().getActiveBeacons() 
                : Collections.emptyMap(),
            arena != null ? arena.toLocation() : null
        );
    }

    /**
     * Inicia el bucle de ejecución de habilidades, efectos y coordinación táctica de objetivos.
     * Tasa de ejecución: cada 10 ticks (0.5 segundos) para alta respuesta con impacto mínimo en CPU.
     */
    public void startTicker(ArenaData arena) {
        cleanupTicker();
        tickCounter = 0;

        tickerTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!plugin.getInvasionManager().isInvasionActive()) {
                        cancel();
                        return;
                    }

                    tickCounter++;
                    List<Player> participants = getAliveParticipants(arena);
                    if (participants.isEmpty()) return;

                    // 1. Recuento rápido de atacantes actuales por jugador (zero-allocation)
                    Map<UUID, Integer> targetCounts = new HashMap<>(participants.size());
                    for (Player p : participants) {
                        targetCounts.put(p.getUniqueId(), 0);
                    }

                    List<UUID> activeIds = new ArrayList<>(plugin.getInvasionManager().getActiveZombies());
                    List<Zombie> aliveZombies = new ArrayList<>(activeIds.size());

                    for (UUID id : activeIds) {
                        Entity ent = Bukkit.getEntity(id);
                        if (ent instanceof Zombie zombie && zombie.isValid() && !zombie.isDead()) {
                            aliveZombies.add(zombie);
                            if (zombie.getTarget() instanceof Player tp && targetCounts.containsKey(tp.getUniqueId())) {
                                targetCounts.put(tp.getUniqueId(), targetCounts.get(tp.getUniqueId()) + 1);
                            }
                        }
                    }

                    TacticalTargetContext context = createContext(arena);

                    // 2. Tick de zombies y coordinación asimétrica de objetivos tácticos
                    for (Zombie zombie : aliveZombies) {
                        String varTypeStr = zombie.getPersistentDataContainer().get(ZombieVariant.getKey(plugin), PersistentDataType.STRING);
                        ZombieVariantType type = ZombieVariantType.fromString(varTypeStr);
                        ZombieVariant variant = variants.getOrDefault(type, variants.get(ZombieVariantType.DEFAULT));
                        ZombieVariantConfig config = configs.getOrDefault(type, ZombieVariantConfig.createDefault(type));

                        if (tacticalTargeting) {
                            LivingEntity curTarget = zombie.getTarget();
                            boolean needsTarget = curTarget == null || !curTarget.isValid() || curTarget.isDead();
                            
                            if (curTarget instanceof Player tp) {
                                if (!plugin.getInvasionManager().isAliveParticipant(tp) ||
                                    (arena != null && !arena.isInside(tp.getLocation()))) {
                                    needsTarget = true;
                                }
                            }

                            // Reevaluación escalonada (cada ~2 segundos por zombie según hash para cero picos de lag)
                            boolean periodicRetarget = ((tickCounter + (long) zombie.getEntityId()) % 4L == 0L);
                            if (needsTarget || periodicRetarget) {
                                Player tacticalTarget = variant.selectTacticalTarget(zombie, arena, participants, targetCounts, context);
                                if (tacticalTarget != null && !tacticalTarget.equals(curTarget)) {
                                    if (curTarget instanceof Player prev && targetCounts.containsKey(prev.getUniqueId())) {
                                        targetCounts.put(prev.getUniqueId(), Math.max(0, targetCounts.get(prev.getUniqueId()) - 1));
                                    }
                                    zombie.setTarget(tacticalTarget);
                                    targetCounts.put(tacticalTarget.getUniqueId(), targetCounts.get(tacticalTarget.getUniqueId()) + 1);
                                }
                            }
                        }

                        if (variant != null) {
                            variant.onTick(zombie, arena, participants, tickCounter, config);
                        }
                    }

                    // 3. Tick de charcos ácidos
                    tickAcidPuddles(participants);

                } catch (Throwable t) {
                    if (plugin.getLogManager() != null) {
                        plugin.getLogManager().recordError("ZombieVariantManager.ticker", "Error en tick de variantes", t);
                    }
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void tickAcidPuddles(List<Player> participants) {
        if (activePuddles.isEmpty()) return;

        Iterator<AcidPuddle> it = activePuddles.iterator();
        while (it.hasNext()) {
            AcidPuddle puddle = it.next();
            if (tickCounter >= puddle.expireTick || puddle.center.getWorld() == null) {
                it.remove();
                continue;
            }

            puddle.center.getWorld().spawnParticle(Particle.ITEM_SLIME, puddle.center, 12, puddle.radius * 0.7, 0.1, puddle.radius * 0.7, 0.02);

            for (Player p : participants) {
                if (p.getWorld().equals(puddle.center.getWorld())) {
                    if (p.getLocation().distanceSquared(puddle.center) <= (puddle.radius * puddle.radius)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 0, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
                    }
                }
            }
        }
    }

    public void createAcidPuddle(Location loc, double radius, int durationTicks) {
        if (loc == null || loc.getWorld() == null) return;
        activePuddles.add(new AcidPuddle(loc.clone(), tickCounter + (durationTicks / 10), radius));
        loc.getWorld().spawnParticle(Particle.ITEM_SLIME, loc, 25, radius * 0.8, 0.2, radius * 0.8, 0.05);
    }

    public void handleDamageReceived(Zombie zombie, EntityDamageByEntityEvent event) {
        String varTypeStr = zombie.getPersistentDataContainer().get(ZombieVariant.getKey(plugin), PersistentDataType.STRING);
        if (varTypeStr != null) {
            ZombieVariantType type = ZombieVariantType.fromString(varTypeStr);
            ZombieVariant variant = variants.get(type);
            ZombieVariantConfig config = configs.getOrDefault(type, ZombieVariantConfig.createDefault(type));
            if (variant != null) {
                variant.onDamageReceived(zombie, event, config);
            }
        }
    }

    public void handleAttack(Zombie zombie, Player target, EntityDamageByEntityEvent event) {
        String varTypeStr = zombie.getPersistentDataContainer().get(ZombieVariant.getKey(plugin), PersistentDataType.STRING);
        if (varTypeStr != null) {
            ZombieVariantType type = ZombieVariantType.fromString(varTypeStr);
            ZombieVariant variant = variants.get(type);
            ZombieVariantConfig config = configs.getOrDefault(type, ZombieVariantConfig.createDefault(type));
            if (variant != null) {
                variant.onAttack(zombie, target, event, config);
            }
        }
    }

    public void handleDeath(Zombie zombie, EntityDeathEvent event) {
        String varTypeStr = zombie.getPersistentDataContainer().get(ZombieVariant.getKey(plugin), PersistentDataType.STRING);
        if (varTypeStr != null) {
            ZombieVariantType type = ZombieVariantType.fromString(varTypeStr);
            ZombieVariant variant = variants.get(type);
            ZombieVariantConfig config = configs.getOrDefault(type, ZombieVariantConfig.createDefault(type));
            if (variant != null) {
                variant.onDeath(zombie, event, config);
            }
        }
    }

    public void cleanup() {
        cleanupTicker();
        activePuddles.clear();
    }

    private void cleanupTicker() {
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    public ZombieVariant getVariant(ZombieVariantType type) {
        return variants.get(type);
    }

    public ZombieVariantConfig getConfig(ZombieVariantType type) {
        return configs.get(type);
    }

    private record AcidPuddle(Location center, long expireTick, double radius) {}
}
