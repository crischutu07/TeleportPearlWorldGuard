package dev.crischutu07.TeleportPearlWorldGuard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EnderPearlFlagsListener implements Listener {

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
  public void onEnderPearlTeleport(PlayerTeleportEvent event) {
    if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;

    Location from = event.getFrom();
    Location to = event.getTo();
    if (to == null) return;

    var container = WorldGuard.getInstance().getPlatform().getRegionContainer();


    RegionManager fromManager = container.get(BukkitAdapter.adapt(from.getWorld()));
    RegionManager toManager = container.get(BukkitAdapter.adapt(to.getWorld()));
    if (fromManager == null || toManager == null) return;

    BlockVector3 fromVec = BukkitAdapter.asBlockVector(from);
    BlockVector3 toVec = BukkitAdapter.asBlockVector(to);

    // enderpearl-entry: flag lives on destination regions.
    // Triggered when fromVec is OUTSIDE a destination region (player is entering it).
    StateFlag.State entryRuling = resolveRuling(
      toManager,
      Main.ENDERPEARL_ENTRY,
      toVec,    // inspect regions at destination
      fromVec   // crossing check: fromVec must be outside that region
    );

    // enderpearl-exit: flag lives on origin regions.
    // Triggered when toVec is OUTSIDE an origin region (player is leaving it).
    StateFlag.State exitRuling = resolveRuling(
      fromManager,
      Main.ENDERPEARL_EXIT,
      fromVec,  // inspect regions at origin
      toVec     // crossing check: toVec must be outside that region
    );

    // DENY from either flag wins unconditionally (security-first).
    // ALLOW from either flag overrides WorldGuard's default block.
    // If both null: leave WorldGuard's own ruling untouched.
    if (entryRuling == StateFlag.State.DENY || exitRuling == StateFlag.State.DENY) {
      event.setCancelled(true);
    } else if (entryRuling == StateFlag.State.ALLOW || exitRuling == StateFlag.State.ALLOW) {
      event.setCancelled(false);
    }
  }

  /**
   * Collects regions at {@code regionVec} that have {@code flag} explicitly set,
   * but only if {@code crossVec} lies OUTSIDE that region's bounds - confirming
   * that the player is actually crossing the region boundary, not moving within it.
   *
   * <p>Priority resolution: the highest-priority region tier wins.
   * Within the same tier, DENY beats ALLOW.</p>
   *
   * @return ALLOW or DENY if any relevant region has the flag set; null otherwise.
   */
  private StateFlag.State resolveRuling(
    RegionManager manager,
    StateFlag flag,
    BlockVector3 regionVec,
    BlockVector3 crossVec
  ) {
    List<ProtectedRegion> relevant = new ArrayList<>();

    for (ProtectedRegion region : manager.getApplicableRegions(regionVec)) {
      if (region.getFlag(flag) == null) continue;

      if (region.contains(crossVec)) continue;

      relevant.add(region);
    }

    if (relevant.isEmpty()) return null;

    relevant.sort(Comparator.comparingInt(ProtectedRegion::getPriority).reversed());
    return resolveByPriority(relevant, flag);
  }

  /**
   * Among the top-priority tier of regions, DENY beats ALLOW.
   * Lower-priority regions are ignored once the top tier is established.
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