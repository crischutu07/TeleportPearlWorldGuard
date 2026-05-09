package dev.crischutu07.TeleportPearlWorldGuard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class EnderPearlFlagsListener implements Listener {

  /**
   * PRIMARY DENY handler.
   *
   * Fires when the pearl physically hits a surface - before any teleport
   * processing starts. This is more reliable than PlayerTeleportEvent in
   * CraftBukkit 1.21.x, where cancelling the teleport event after the
   * pipeline has partially committed does not reliably prevent movement.
   *
   * Also the correct place to resolve the cross-world case: if the player
   * threw a pearl in World A and moved to World B before it landed, we catch
   * that here using the live shooter location vs. pearl landing location.
   */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onEnderPearlHit(ProjectileHitEvent event) {
    if (!(event.getEntity() instanceof EnderPearl pearl)) return;
    if (!(pearl.getShooter() instanceof Player player)) return;

    Location shooterLoc = player.getLocation();
    Location pearlLoc   = pearl.getLocation();

    var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    RegionManager shooterManager = container.get(BukkitAdapter.adapt(shooterLoc.getWorld()));
    RegionManager pearlManager   = container.get(BukkitAdapter.adapt(pearlLoc.getWorld()));
    if (shooterManager == null || pearlManager == null) return;

    BlockVector3 shooterVec = BukkitAdapter.asBlockVector(shooterLoc);
    BlockVector3 pearlVec   = BukkitAdapter.asBlockVector(pearlLoc);

    // Different worlds means coordinates must never be compared geometrically.
    // A World B coordinate is always "outside" any World A region and vice versa.
    boolean sameWorld = shooterLoc.getWorld().equals(pearlLoc.getWorld());

    // enderpearl-entry: flag on destination (pearl landing) regions.
    // Applies when shooter is coming from outside that region.
    StateFlag.State entryRuling = resolveRuling(
      pearlManager,
      Main.ENDERPEARL_ENTRY,
      pearlVec,
      shooterVec,
      sameWorld
    );

    // enderpearl-exit: flag on origin (shooter's current) regions.
    // Skipped entirely for cross-world throws: the player already left that
    // world through other means so the exit flag is inapplicable.
    StateFlag.State exitRuling = null;
    if (sameWorld) {
      exitRuling = resolveRuling(
        shooterManager,
        Main.ENDERPEARL_EXIT,
        shooterVec,
        pearlVec,
        true
      );
    }

    if (entryRuling == StateFlag.State.DENY || exitRuling == StateFlag.State.DENY) {
      event.setCancelled(true);
      // Explicitly remove the pearl so it does not re-trigger this
      // event on the next tick by bouncing against the same surface.
      pearl.remove();
    }
  }

  /**
   * SECONDARY handler - runs after WorldGuard (NORMAL priority).
   *
   * Sole responsibility: override WorldGuard's block when our flags say ALLOW.
   * Also acts as a safety net for any DENY case that somehow slipped past the
   * hit event (e.g. a server implementation that fires ProjectileHitEvent late).
   *
   * ignoreCancelled = false so we can both re-allow and re-deny.
   */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void onEnderPearlTeleport(PlayerTeleportEvent event) {
    if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

    Location from = event.getFrom();
    Location to   = event.getTo();
    if (to == null) return;

    var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    RegionManager fromManager = container.get(BukkitAdapter.adapt(from.getWorld()));
    RegionManager toManager   = container.get(BukkitAdapter.adapt(to.getWorld()));
    if (fromManager == null || toManager == null) return;

    BlockVector3 fromVec = BukkitAdapter.asBlockVector(from);
    BlockVector3 toVec   = BukkitAdapter.asBlockVector(to);

    boolean sameWorld = from.getWorld().equals(to.getWorld());

    StateFlag.State entryRuling = resolveRuling(
      toManager,
      Main.ENDERPEARL_ENTRY,
      toVec,
      fromVec,
      sameWorld
    );

    StateFlag.State exitRuling = null;
    if (sameWorld) {
      exitRuling = resolveRuling(
        fromManager,
        Main.ENDERPEARL_EXIT,
        fromVec,
        toVec,
        true
      );
    }

    // DENY safety net - re-apply in case ProjectileHitEvent was missed.
    if (entryRuling == StateFlag.State.DENY || exitRuling == StateFlag.State.DENY) {
      event.setCancelled(true);
      // ALLOW override - unblock what WorldGuard denied.
    } else if (entryRuling == StateFlag.State.ALLOW || exitRuling == StateFlag.State.ALLOW) {
      event.setCancelled(false);
    }
    // Both null: leave WorldGuard's own ruling untouched.
  }

  /**
   * Collects regions at {@code regionVec} where {@code flag} is explicitly set
   * AND a boundary is actually being crossed ({@code crossVec} lies outside the
   * region bounds).
   *
   * <p>{@code sameWorld} MUST be false when {@code regionVec} and {@code crossVec}
   * originate from different worlds. Calling {@code region.contains()} with a
   * coordinate from a foreign world is a purely geometric comparison with no world
   * awareness - it can spuriously return true and silently skip the flag. When
   * {@code sameWorld} is false, crossVec is unconditionally treated as outside.</p>
   *
   * @return ALLOW, DENY, or null if no relevant region has the flag set.
   */
  private StateFlag.State resolveRuling(
    RegionManager manager,
    StateFlag flag,
    BlockVector3 regionVec,
    BlockVector3 crossVec,
    boolean sameWorld
  ) {
    List<ProtectedRegion> relevant = new ArrayList<>();

    for (ProtectedRegion region : manager.getApplicableRegions(regionVec)) {
      if (region.getFlag(flag) == null) continue;

      // Only do the contains() check when both vectors share the same world.
      // Cross-world vectors must never be compared geometrically.
      if (sameWorld && region.contains(crossVec)) continue;

      relevant.add(region);
    }

    if (relevant.isEmpty()) return null;

    relevant.sort(Comparator.comparingInt(ProtectedRegion::getPriority).reversed());
    return resolveByPriority(relevant, flag);
  }

  /**
   * Within the top-priority region tier, DENY beats ALLOW.
   * Lower-priority tiers are ignored once the top tier is determined.
   */
  private StateFlag.State resolveByPriority(List<ProtectedRegion> sortedRegions, StateFlag flag) {
    int topPriority = sortedRegions.get(0).getPriority();
    StateFlag.State result = StateFlag.State.ALLOW;

    for (ProtectedRegion region : sortedRegions) {
      if (region.getPriority() < topPriority) break;
      if (region.getFlag(flag) == StateFlag.State.DENY) {
        result = StateFlag.State.DENY;
      }
    }

    return result;
  }
}