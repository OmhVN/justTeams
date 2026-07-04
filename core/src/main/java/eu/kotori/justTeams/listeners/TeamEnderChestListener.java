package eu.kotori.justTeams.listeners;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.EnderChestPageHolder;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class TeamEnderChestListener implements Listener {
   private final JustTeams plugin;
   private final ConcurrentHashMap<UUID, Long> lastUpdateTime = new ConcurrentHashMap();
   private static final long UPDATE_COOLDOWN = 100L;
   private static final long SLOT_UPDATE_COOLDOWN = 50L;
   private final Set<Integer> dirtyTeams = Collections.newSetFromMap(new ConcurrentHashMap());
   private static final long FLUSH_PERIOD_TICKS = 200L;

   public TeamEnderChestListener(JustTeams plugin) {
      this.plugin = plugin;
      plugin.getTaskRunner().runAsyncTaskTimer(this::flushDirtyEnderChests, 200L, 200L);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onInventoryOpen(InventoryOpenEvent event) {
      InventoryHolder var3 = event.getInventory().getHolder();
      if (var3 instanceof EnderChestPageHolder pageHolder) {
         Team team = pageHolder.getTeam();
         Player var4 = (Player)event.getPlayer();
         team.addEnderChestViewer(var4.getUniqueId());
         if (this.plugin.getConfigManager().isDebugEnabled()) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = var4.getName();
            var10000.info("Player " + var10001 + " opened team enderchest page " + pageHolder.getPage() + " for team " + team.getName() + " (viewers: " + team.getEnderChestViewers().size() + ")");
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onInventoryClick(InventoryClickEvent event) {
      InventoryHolder var3 = event.getInventory().getHolder();
      if (var3 instanceof EnderChestPageHolder pageHolder) {
         Team team = pageHolder.getTeam();
         Player var6 = (Player)event.getWhoClicked();
         long currentTime = System.currentTimeMillis();
         if (!this.lastUpdateTime.containsKey(var6.getUniqueId()) || currentTime - (Long)this.lastUpdateTime.get(var6.getUniqueId()) >= 50L) {
            this.handleInventoryChange(team, var6, event.getInventory(), "click");
            this.lastUpdateTime.put(var6.getUniqueId(), currentTime);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onInventoryDrag(InventoryDragEvent event) {
      InventoryHolder var3 = event.getInventory().getHolder();
      if (var3 instanceof EnderChestPageHolder pageHolder) {
         Team team = pageHolder.getTeam();
         Player var6 = (Player)event.getWhoClicked();
         long currentTime = System.currentTimeMillis();
         if (!this.lastUpdateTime.containsKey(var6.getUniqueId()) || currentTime - (Long)this.lastUpdateTime.get(var6.getUniqueId()) >= 50L) {
            this.handleInventoryChange(team, var6, event.getInventory(), "drag");
            this.lastUpdateTime.put(var6.getUniqueId(), currentTime);
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      this.lastUpdateTime.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onInventoryClose(InventoryCloseEvent event) {
      InventoryHolder var3 = event.getInventory().getHolder();
      if (var3 instanceof EnderChestPageHolder pageHolder) {
         Team team = pageHolder.getTeam();
         int page = pageHolder.getPage();
         Player var6 = (Player)event.getPlayer();
         team.removeEnderChestViewer(var6.getUniqueId());
         if (this.plugin.getConfigManager().isDebugEnabled()) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = var6.getName();
            var10000.info("Player " + var10001 + " closed team enderchest page " + page + " for team " + team.getName() + " (remaining viewers: " + team.getEnderChestViewers().size() + ")");
         }

         if (!team.hasEnderChestViewers()) {
            this.dirtyTeams.remove(team.getId());
            Inventory enderChest = event.getInventory();
            ItemStack[] snapshot = this.cloneContents(enderChest.getContents());
            team.setEnderChest((Inventory)null);
            this.plugin.getTaskRunner().runAsync(() -> {
               try {
                  this.plugin.getTeamManager().saveEnderChestPageSnapshot(team, page, snapshot);

                  try {
                     this.plugin.getStorageManager().getStorage().releaseEnderChestLock(team.getId());
                  } catch (Exception e) {
                     Logger var12 = this.plugin.getLogger();
                     String var13 = team.getName();
                     var12.warning("Failed to release ender chest lock for team " + var13 + ": " + e.getMessage());
                  } finally {
                     team.unlockEnderChest();
                  }

                  if (this.plugin.getConfigManager().isDebugEnabled()) {
                     this.plugin.getLogger().info("✓ Last viewer closed enderchest page " + page + " for team " + team.getName() + ", saved and released lock");
                  }
               } catch (Exception e) {
                  Logger var10000 = this.plugin.getLogger();
                  String var10001 = var6.getName();
                  var10000.warning("Error saving enderchest page " + page + " on close for " + var10001 + ": " + e.getMessage());
                  e.printStackTrace();
               }

            });
         }
      }
   }

   private void handleInventoryChange(Team team, Player player, Inventory inventory, String changeType) {
      this.dirtyTeams.add(team.getId());
      this.plugin.getTaskRunner().run(() -> {
         if (team.getEnderChest() != null) {
            if (team.hasEnderChestViewers()) {
               this.notifyOtherViewers(team, player, changeType);
            }
         }
      });
   }

   private void flushDirtyEnderChests() {
      if (!this.dirtyTeams.isEmpty()) {
         for(Integer teamId : this.dirtyTeams) {
            Team team = (Team)this.plugin.getTeamManager().getTeamById(teamId).orElse(null);
            if (team == null) {
               this.dirtyTeams.remove(teamId);
            } else if (!team.hasEnderChestViewers()) {
               this.dirtyTeams.remove(teamId);
            } else {
               this.dirtyTeams.remove(teamId);
               this.plugin.getTaskRunner().run(() -> {
                  Inventory enderChest = team.getEnderChest();
                  if (enderChest != null && enderChest.getHolder() instanceof EnderChestPageHolder pageHolder) {
                     int page = pageHolder.getPage();
                     ItemStack[] snapshot = this.cloneContents(enderChest.getContents());
                     this.plugin.getTaskRunner().runAsync(() -> {
                        try {
                           this.plugin.getTeamManager().saveEnderChestPageSnapshot(team, page, snapshot);
                        } catch (Exception e) {
                           Logger var10000 = this.plugin.getLogger();
                           String var10001 = team.getName();
                           var10000.warning("Error flushing enderchest page " + page + " for team " + var10001 + ": " + e.getMessage());
                        }
                     });
                  }
               });
            }
         }
      }
   }

   private ItemStack[] cloneContents(ItemStack[] live) {
      ItemStack[] snapshot = new ItemStack[live.length];
      for(int i = 0; i < live.length; ++i) {
         snapshot[i] = live[i] == null ? null : live[i].clone();
      }
      return snapshot;
   }

   private void notifyOtherViewers(Team team, Player changer, String changeType) {
      for(UUID viewerUuid : team.getEnderChestViewers()) {
         if (!viewerUuid.equals(changer.getUniqueId())) {
            Player viewer = Bukkit.getPlayer(viewerUuid);
            if (viewer != null && viewer.isOnline()) {
               try {
                  this.refreshViewerInventory(viewer, team);
               } catch (Exception e) {
                  Logger var10000 = this.plugin.getLogger();
                  String var10001 = viewer.getName();
                  var10000.warning("Failed to refresh enderchest for viewer " + var10001 + ": " + e.getMessage());
               }
            }
         }
      }
   }

   private void refreshViewerInventory(Player viewer, Team team) {
      if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof EnderChestPageHolder) {
         this.plugin.getTaskRunner().runOnEntity(viewer, () -> {
            try {
               viewer.closeInventory();
               this.plugin.getTaskRunner().runOnEntity(viewer, () -> viewer.openInventory(team.getEnderChest()));
            } catch (Exception e) {
               Logger var10000 = this.plugin.getLogger();
               String var10001 = viewer.getName();
               var10000.warning("Failed to refresh enderchest inventory for " + var10001 + ": " + e.getMessage());
            }
         });
      }
   }
}
