package eu.kotori.justTeams.gui;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.util.CancellableTask;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIUpdateThrottle {
   private final JustTeams plugin;
   private final Map<UUID, CancellableTask> pendingUpdates = new ConcurrentHashMap();
   private final long throttleMs;

   public GUIUpdateThrottle(JustTeams plugin) {
      this.plugin = plugin;
      this.throttleMs = plugin.getConfigManager().getGuiUpdateThrottleMs();
   }

   public void scheduleUpdate(UUID playerUuid, Runnable updateTask) {
      if (playerUuid != null && updateTask != null) {
         CancellableTask existingTask = (CancellableTask)this.pendingUpdates.get(playerUuid);
         if (existingTask != null) {
            existingTask.cancel();
         }

         CancellableTask newTask = this.plugin.getTaskRunner().runLater(() -> {
            try {
               updateTask.run();
            } catch (Exception e) {
               this.plugin.getLogger().warning("Error executing GUI update task: " + e.getMessage());
            } finally {
               this.pendingUpdates.remove(playerUuid);
            }

         }, this.throttleMs / 50L);
         this.pendingUpdates.put(playerUuid, newTask);
      } else {
         this.plugin.getLogger().warning("GUIUpdateThrottle: Cannot schedule update with null parameters");
      }
   }

   public void cancelPendingUpdate(UUID playerUuid) {
      if (playerUuid != null) {
         CancellableTask task = (CancellableTask)this.pendingUpdates.remove(playerUuid);
         if (task != null) {
            task.cancel();
         }

      }
   }

   public void cleanup() {
      for(CancellableTask task : this.pendingUpdates.values()) {
         if (task != null) {
            task.cancel();
         }
      }

      this.pendingUpdates.clear();
   }

   public int getPendingUpdateCount() {
      return this.pendingUpdates.size();
   }
}
