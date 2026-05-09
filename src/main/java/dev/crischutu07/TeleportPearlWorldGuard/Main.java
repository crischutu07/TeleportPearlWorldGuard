package dev.crischutu07.TeleportPearlWorldGuard;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

  // Default false = DENY. Flag must be explicitly set to ALLOW to permit crossing.
  public static StateFlag ENDERPEARL_ENTRY;
  public static StateFlag ENDERPEARL_EXIT;

  @Override
  public void onLoad() {
    // Must register flags in onLoad(). WorldGuard freezes its registry during
    // its own onEnable() - registering there throws IllegalStateException.
    FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
    ENDERPEARL_ENTRY = registerFlag(registry, "enderpearl-entry");
    ENDERPEARL_EXIT = registerFlag(registry, "enderpearl-exit");
  }

  private StateFlag registerFlag(FlagRegistry registry, String name) {
    try {
      StateFlag flag = new StateFlag(name, false);
      registry.register(flag);
      getLogger().info("Registered WorldGuard flag: " + name);
      return flag;
    } catch (FlagConflictException e) {
      var existing = registry.get(name);
      if (existing instanceof StateFlag sf) {
        getLogger().warning("Flag '" + name + "' already registered by another plugin - reusing it.");
        return sf;
      }
      getLogger().severe("Flag '" + name + "' exists but is not a StateFlag. Cannot use it.");
      return null;
    }
  }

  @Override
  public void onEnable() {
    if (ENDERPEARL_ENTRY == null || ENDERPEARL_EXIT == null) {
      getLogger().severe("One or more flags failed to register. Disabling plugin.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
      getLogger().severe("WorldGuard not found. Disabling plugin.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    getServer().getPluginManager().registerEvents(new EnderPearlFlagsListener(), this);
    getLogger().info("EnderpearlGuard enabled.");
  }
}
