# TeleportPearlWorldGuard
worldguard plugin for managing pearl landing entry (useful for prevent players to teleport to unwanted region)

# how to install
- you need a bukkit server running version 1.18+ (preferably, use papermc)
- worldguard and its dependency (worldedit or fawe) installed
- navigate to the release page and grab the latest release build
- put it on the `plugins/` directory

# uhh how does this work..?
this plugin only works if you're in a defined region and not `__global__`

## them flags >:3
there's 2 flags called `enderpearl-entry` and `enderpearl-exit`

`enderpearl-entry` manages the pearl that's about to land on its region, if disable then any pearls lands on it, then it will not teleport the player assosiated with that pearl.

`enderpearl-exit` does the opposite, it manages any players that they throw a pearl in order to exit the region, if this option is disabled then that player cannot exit the region once the pearl is landed outside the region.

# license
this repo is licensed under MIT license, you can modify this source code commerically or open source if u wish :3
