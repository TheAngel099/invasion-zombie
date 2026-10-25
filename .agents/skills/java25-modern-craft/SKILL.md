---
name: java25-modern-craft
description: Architectural patterns and best practices for high-performance Paper/Purpur Minecraft plugins leveraging Java 25 features (Records, Pattern Matching, Sealed Types, Item Data Components, Zero-Allocation loops, and Concurrency boundaries).
---

# Java 25 Modern Craft: High-Performance Minecraft Plugin Engineering

This skill defines the technical standards, idioms, and design patterns for engineering high-performance Paper and Purpur plugins on Minecraft 26.2+ utilizing Java 25.

---

## 1. Domain Modeling with Records & Value Objects

### 1.1 Pure Data Transfer Objects (DTOs) & Events
Use Java Records for all immutable state representations, event arguments, configurations, and spatial queries:

```java
public record ArenaBoundary(Location min, Location max) {
    public ArenaBoundary {
        Objects.requireNonNull(min, "min cannot be null");
        Objects.requireNonNull(max, "max cannot be null");
        if (!Objects.equals(min.getWorld(), max.getWorld())) {
            throw new IllegalArgumentException("Boundaries must reside in the same world");
        }
    }

    public boolean contains(Location loc) {
        if (!Objects.equals(loc.getWorld(), min.getWorld())) return false;
        double x = loc.getX(), y = loc.getY(), z = loc.getZ();
        return x >= min.getX() && x <= max.getX()
            && y >= min.getY() && y <= max.getY()
            && z >= min.getZ() && z <= max.getZ();
    }
}
```

### 1.2 Compact Canonical Constructors for Validation
Take full advantage of compact record constructors to guarantee invariant validation without boilerplate field assignments.

### 1.3 Statements Before `super(...)` (Java 22+)
When creating custom Bukkit/Adventure wrappers or events, perform input validation and data pre-processing *prior* to delegating to `super(...)`:

```java
public class MutatorActivatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Mutator mutator;

    public MutatorActivatedEvent(Mutator mutator) {
        Objects.requireNonNull(mutator, "mutator cannot be null");
        super();
        this.mutator = mutator;
    }
    
    // ...
}
```

---

## 2. Sealed Hierarchies & Exhaustive Pattern Matching

### 2.1 State Machines and Minigame Phases
Model finite states with `sealed` interfaces and records or final classes. This guarantees compile-time exhaustiveness in `switch` expressions:

```java
public sealed interface GamePhase permits WaitingPhase, ActivePhase, EndingPhase {}

public record WaitingPhase(int countdownSeconds, int minPlayersNeeded) implements GamePhase {}
public record ActivePhase(int currentRound, int remainingEnemies, Instant startedAt) implements GamePhase {}
public record EndingPhase(UUID winnerUuid, Instant cleanupAt) implements GamePhase {}
```

### 2.2 Exhaustive Switch Expressions
Avoid long `if-else instanceof` chains. Use pattern matching switch expressions with record destructuring:

```java
public Component renderPhaseHud(GamePhase phase) {
    return switch (phase) {
        case WaitingPhase(int sec, int needed) -> 
            Component.text("Esperando jugadores (" + sec + "s) - Faltan: " + needed, NamedTextColor.YELLOW);
            
        case ActivePhase(int round, int enemies, var startedAt) -> 
            Component.text("Ronda " + round + " | Enemigos vivos: " + enemies, NamedTextColor.RED);
            
        case EndingPhase(UUID winner, var cleanupAt) -> 
            Component.text("Partida finalizada. ¡Ganador: " + winner + "!", NamedTextColor.GOLD);
    };
}
```

### 2.3 Pattern Matching on Entities & Events
Safely unpack entity types and behaviors with type patterns and guards (`when`):

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onEntityDamage(EntityDamageByEntityEvent event) {
    switch (event.getDamager()) {
        case Player player when player.hasPermission("zombie.bypass") -> {
            // Bypass logic
        }
        case Projectile projectile when projectile.getShooter() instanceof Player shooter -> {
            processRangedStrike(shooter, event.getEntity(), event.getFinalDamage());
        }
        case Zombie zombie when zombie.getScoreboardTags().contains("boss_minion") -> {
            event.setDamage(event.getDamage() * 1.5);
        }
        default -> {}
    }
}
```

---

## 3. Paper Data Components API (Minecraft 20.5+ / 26.2+)

### 3.1 Eliminating NMS and Legacy PDC where Native Components Exist
Use Paper's native `DataComponentType` API to query and mutate item attributes, tool properties, custom models, and food effects cleanly without NBT/NMS reflection.

```java
public static ItemStack createEliteWeapon() {
    ItemStack sword = ItemStack.of(Material.NETHERITE_SWORD);
    
    sword.setData(DataComponentTypes.UNBREAKABLE, Unbreakable.unbreakable(true));
    sword.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData(10042));
    sword.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
    
    sword.editMeta(meta -> {
        meta.displayName(MiniMessage.miniMessage().deserialize("<gold><bold>Excalibur Astral</bold></gold>"));
    });
    
    return sword;
}
```

### 3.2 PersistentDataContainer (PDC) Best Practices
- Never instantiate `NamespacedKey` inside a loop or event handler. Store them as `public static final NamespacedKey` constants.
- Use structured `PersistentDataType` (e.g., custom `PersistentDataType<byte[], CustomRecord>` via serialization) rather than split unstructured strings.

---

## 4. Zero-Allocation Math & Hot Tick Loop Optimization

### 4.1 Distance Squared vs Distance
Never call `Location#distance()` in entity scans, spatial searches, or projectile raytraces. Always use `Location#distanceSquared()` against squared thresholds:

```java
// BAD: Calls Math.sqrt on every entity in every tick
if (player.getLocation().distance(target.getLocation()) < 8.0) { ... }

// GOOD: Zero Math.sqrt overhead
double maxDistSq = 8.0 * 8.0; // 64.0
if (player.getLocation().distanceSquared(target.getLocation()) <= maxDistSq) { ... }
```

### 4.2 Primitive Collections & Cache Locality
- Prefer fastutil primitive maps (`Int2ObjectOpenHashMap`, `Long2ObjectOpenHashMap` for chunk coordinates `Chunk.getChunkKey()`) over standard `HashMap<Integer, Object>`.
- Avoid stream pipelines (`.stream().filter().map().collect()`) inside methods executed every tick (`1/20s`) across thousands of entities. Use standard indexed `for` loops or iterator loops.

---

## 5. Thread Boundaries & Concurrency

### 5.1 Strict Bukkit World Mutability Boundary
- **Main Thread Only:** Adding entities to worlds, mutating blocks, firing synchronous Bukkit events, modifying player inventories, teleports.
- **Async Execution:** SQL queries, file/JSON loading, network webhooks, complex pathfinding algorithms, heavy string parsing.

### 5.2 Thread Handoff Protocol
When performing async processing, always bridge back to the main thread (or region thread on Folia/Purpur) before applying modifications:

```java
CompletableFuture.supplyAsync(() -> database.loadPlayerData(uuid), asyncExecutor)
    .thenAccept(data -> {
        // Bridge safely to the main server thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                applyDataToPlayer(player, data);
            }
        });
    });
```
