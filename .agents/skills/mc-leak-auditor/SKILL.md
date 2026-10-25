---
name: mc-leak-auditor
description: Audit protocol, diagnostic checklist, and prevention patterns for memory leaks, task leaks, entity orphans, and thread-safety violations in Minecraft Paper/Purpur plugins.
---

# Minecraft Plugin Leak Auditor (`mc-leak-auditor`)

This skill provides a comprehensive diagnostic methodology and prevention standard to audit, detect, and eliminate memory leaks, task leaks, entity orphans, and concurrency pitfalls in Paper and Purpur plugins.

---

## 1. The Cardinal Sins of Minecraft Plugin Leaks

### 1.1 Storing `Player` or `Entity` Instances
**The Problem:**
Holding an `org.bukkit.entity.Player` reference in a static field, singleton, cache, or long-lived collection retains the entire player object graph upon disconnect:
- Their client connection (`PlayerConnection`)
- Loaded player chunks (`ChunkProvider`)
- Inventory, attributes, and permissions
- Entire `WorldServer` references preventing GC

**Audit Standard:**
- **NEVER** use `Player`, `Entity`, or `CraftPlayer` as map keys or collection elements in long-lived state.
- **ALWAYS** store `UUID` or `EntityId` (primitive `int`).
- Resolve dynamically at call time: `Bukkit.getPlayer(uuid)` with `null` checks.

```java
// SEVERE LEAK:
public class CombatManager {
    private final Map<Player, CombatState> activeCombat = new HashMap<>(); // LEAK!
}

// COMPLIANT:
public class CombatManager {
    private final Map<UUID, CombatState> activeCombat = new ConcurrentHashMap<>();

    public void removePlayer(UUID uuid) {
        this.activeCombat.remove(uuid);
    }
}
```

---

### 1.2 Orphaned `BukkitTask` & Scheduler Leaks
**The Problem:**
Periodic tasks (`runTaskTimer`) running infinitely even after minigames finish or after `/reload` / `plugin.onDisable()`. If the task lambda closes over an instance variable, the entire enclosing system cannot be garbage collected.

**Audit Standard:**
1. Save every scheduled `BukkitTask` reference in a manager or state container.
2. Explicitly cancel tasks when their lifecycle completes (`task.cancel()`).
3. In `JavaPlugin#onDisable()`, guarantee that `Bukkit.getScheduler().cancelTasks(this)` or managed tasks are cancelled.

```java
public class MutatorTask implements Runnable {
    private BukkitTask taskHandle;

    public void start(Plugin plugin) {
        this.taskHandle = Bukkit.getScheduler().runTaskTimer(plugin, this, 20L, 20L);
    }

    public void stop() {
        if (this.taskHandle != null && !this.taskHandle.isCancelled()) {
            this.taskHandle.cancel();
            this.taskHandle = null;
        }
    }
}
```

---

### 1.3 Stale Dynamic Event Listeners
**The Problem:**
Temporary match handlers or custom GUI menus that register listeners (`registerEvents(listener, plugin)`) on creation but never unregister them when closed or finished. The `HandlerList` retains them forever.

**Audit Standard:**
- When registering listeners dynamically for temporary sessions:
```java
public void destroy() {
    HandlerList.unregisterAll(this.temporaryListener);
}
```

---

### 1.4 Chunk & BlockState Retention
**The Problem:**
Holding references to `org.bukkit.Chunk` or `org.bukkit.block.BlockState` in memory. If a chunk unloads while a plugin holds its reference, the chunk will remain pinned in memory (preventing unloading) or cause corrupted state when reloaded.

**Audit Standard:**
- Never retain `Chunk` or `TileState` objects across ticks.
- Store primitive coordinates or custom records:
```java
public record BlockCoordinate(UUID worldUid, int x, int y, int z) {
    public static BlockCoordinate from(Block block) {
        return new BlockCoordinate(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    public Block toBlock() {
        World world = Bukkit.getWorld(worldUid);
        return world != null ? world.getBlockAt(x, y, z) : null;
    }
}
```

---

### 1.5 Lingering Custom Entities & Display Orphans
**The Problem:**
Spawned indicator markers (Text Displays, Interaction entities, ArmorStands, temporary minions) that remain frozen in the world if the server shuts down or the minigame aborts abnormally.

**Audit Standard:**
1. Mark transient entities as non-persistent when spawned:
   ```java
   entity.setPersistent(false); // Does not save to disk across world unloads/restarts
   ```
2. Tag custom entities with a PDC key or scoreboard tag:
   ```java
   entity.getPersistentDataContainer().set(PluginKeys.TEMPORARY_ENTITY, PersistentDataType.BOOLEAN, true);
   ```
3. Purge all spawned entities cleanly during `onDisable()` or state transitions.

---

### 1.6 Database Connection & Stream Leaks
**The Problem:**
Unclosed `Connection`, `PreparedStatement`, or `ResultSet` in database operations, exhausting the connection pool and leading to starvation or thread deadlocks.

**Audit Standard:**
Always use `try-with-resources` blocks:

```java
public Optional<PlayerData> fetch(UUID uuid) {
    String sql = "SELECT * FROM player_stats WHERE uuid = ?";
    try (Connection conn = dataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
        ps.setString(1, uuid.toString());
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(mapRecord(rs));
            }
        }
    } catch (SQLException e) {
        logger.error("Failed to query stats for " + uuid, e);
    }
    return Optional.empty();
}
```

---

## 2. Automated Audit Checklist (Fast Static Scan)

Run these inspection patterns across the codebase to identify leak vulnerabilities:

| Risk Item | Regex / Inspection Query | Desired Result |
| :--- | :--- | :--- |
| **Player in Field** | `private (final )?Map<Player,` or `Set<Player>` or `List<Player>` | **0 occurrences**. Must use `UUID` or `WeakReference<Player>`. |
| **Static Bukkit State** | `public static (Player\|World\|Location\|Entity)` | **0 occurrences**. Static references to Bukkit world objects cause full leak. |
| **Uncancelled RunTask** | `runTaskTimer(?!.*cancel)` | Verify that task handle is saved and cancelled in `onDisable` or stop routine. |
| **BlockState Storing** | `Map<.*, BlockState>` or `List<Chunk>` | Replace with block coordinate records or chunk keys. |
| **PDC Key Thrashing** | `new NamespacedKey(` inside methods | Should be `public static final NamespacedKey` constants. |
| **Unbounded Maps** | Cache maps without invalidation or cleanup on `PlayerQuitEvent` | Check that every player-keyed map has an eviction hook on `PlayerQuitEvent`. |

---

## 3. Mandatory `onDisable()` Lifecycle Verification

Every production Paper/Purpur plugin must implement a strict `onDisable()` cleanup routine:

```java
@Override
public void onDisable() {
    // 1. Cancel all ongoing scheduler tasks
    Bukkit.getScheduler().cancelTasks(this);

    // 2. Unregister all event listeners
    HandlerList.unregisterAll(this);

    // 3. Clean up active games / sessions / temporary entities
    if (this.gameManager != null) {
        this.gameManager.shutdownAndCleanupEntities();
    }

    // 4. Flush caches and close database pools
    if (this.databaseManager != null) {
        this.databaseManager.closePool();
    }

    // 5. Clear all in-memory collections
    this.playerCache.clear();
}
```
