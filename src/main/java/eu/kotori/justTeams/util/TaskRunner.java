package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

public class TaskRunner {
   private final JustTeams plugin;
   private final boolean isFolia;
   private final boolean isPaper;
   private final Map<UUID, CancellableTask> activeTasks = new ConcurrentHashMap();
   private final ExecutorService asyncExecutor;
   private final AtomicInteger asyncThreadCounter = new AtomicInteger();

   public TaskRunner(JustTeams plugin) {
      this.plugin = plugin;
      int cores = Runtime.getRuntime().availableProcessors();
      int configured = plugin.getConfig().getInt("settings.async_pool_size", 0);
      int asyncThreads = configured > 0 ? configured : Math.max(4, Math.min(cores, 16));
      this.asyncExecutor = Executors.newFixedThreadPool(asyncThreads, (r) -> {
         Thread t = new Thread(r, "JustTeams-Async-" + this.asyncThreadCounter.incrementAndGet());
         t.setDaemon(true);
         return t;
      });
      String serverName = plugin.getServer().getName();
      String serverNameLower = serverName.toLowerCase();

      boolean threadedRegions;
      try {
         Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
         threadedRegions = true;
      } catch (ClassNotFoundException var9) {
         threadedRegions = false;
      }

      this.isFolia = threadedRegions || serverName.equals("Folia") || serverNameLower.contains("folia") || serverNameLower.equals("canvas") || serverNameLower.equals("petal") || serverNameLower.equals("leaf") || serverNameLower.contains("luminol");
      this.isPaper = serverName.equals("Paper") || serverNameLower.contains("paper") || serverName.equals("Purpur") || serverName.equals("Airplane") || serverName.equals("Pufferfish") || serverNameLower.contains("universespigot") || serverNameLower.equals("plazma") || serverNameLower.equals("mirai") || serverNameLower.contains("luminol");
   }

   public void run(Runnable task) {
      if (this.isFolia) {
         this.plugin.getServer().getGlobalRegionScheduler().run(this.plugin, (scheduledTask) -> task.run());
      } else {
         this.plugin.getServer().getScheduler().runTask(this.plugin, task);
      }

   }

   public void runAsync(Runnable task) {
      if (task != null) {
         this.runAsyncInternal(task);
      }
   }

   private void runAsyncInternal(Runnable task) {
      ExecutorService exec = this.asyncExecutor;
      if (exec != null && !exec.isShutdown()) {
         exec.execute(() -> {
            try {
               task.run();
            } catch (Throwable e) {
               this.plugin.getLogger().severe("Error in async task: " + e.getMessage());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  e.printStackTrace();
               }
            }

         });
      }
   }

   public void shutdownAsyncExecutor() {
      if (this.asyncExecutor != null) {
         this.asyncExecutor.shutdown();

         try {
            if (!this.asyncExecutor.awaitTermination(10L, TimeUnit.SECONDS)) {
               this.asyncExecutor.shutdownNow();
            }
         } catch (InterruptedException var2) {
            this.asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
         }

      }
   }

   public void runAtLocation(Location location, Runnable task) {
      if (this.isFolia) {
         this.plugin.getServer().getRegionScheduler().run(this.plugin, location, (scheduledTask) -> task.run());
      } else {
         this.run(task);
      }

   }

   public void runOnEntity(Entity entity, Runnable task) {
      if (this.isFolia) {
         entity.getScheduler().run(this.plugin, (scheduledTask) -> task.run(), (Runnable)null);
      } else {
         this.run(task);
      }

   }

   public void runOnEntity(Entity entity, Runnable task, Runnable retired) {
      if (this.isFolia) {
         entity.getScheduler().run(this.plugin, (scheduledTask) -> task.run(), retired);
      } else {
         if (!entity.isValid() && retired != null) {
            retired.run();
            return;
         }

         this.run(task);
      }

   }

   public CancellableTask runEntityTaskLater(Entity entity, Runnable task, long delay) {
      if (this.isFolia) {
         long foliaDelay = Math.max(1L, delay);
         ScheduledTask scheduledTask = entity.getScheduler().runDelayed(this.plugin, (scheduledTask1) -> task.run(), (Runnable)null, foliaDelay);
         Objects.requireNonNull(scheduledTask);
         return scheduledTask::cancel;
      } else {
         BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, task, delay);
         Objects.requireNonNull(bukkitTask);
         return bukkitTask::cancel;
      }
   }

   public CancellableTask runEntityTaskTimer(Entity entity, Runnable task, long delay, long period) {
      if (this.isFolia) {
         long foliaDelay = Math.max(1L, delay);
         long foliaPeriod = Math.max(1L, period);
         ScheduledTask scheduledTask = entity.getScheduler().runAtFixedRate(this.plugin, (scheduledTask1) -> task.run(), (Runnable)null, foliaDelay, foliaPeriod);
         Objects.requireNonNull(scheduledTask);
         return scheduledTask::cancel;
      } else {
         BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, task, delay, period);
         Objects.requireNonNull(bukkitTask);
         return bukkitTask::cancel;
      }
   }

   public CancellableTask runTimer(Runnable task, long delay, long period) {
      return this.runTaskTimer(task, delay, period);
   }

   public CancellableTask runLater(Runnable task, long delay) {
      return this.runTaskLater(task, delay);
   }

   public CancellableTask runTaskLater(Runnable task, long delay) {
      if (this.isFolia) {
         long foliaDelay = Math.max(1L, delay);
         ScheduledTask scheduledTask = this.plugin.getServer().getGlobalRegionScheduler().runDelayed(this.plugin, (scheduledTask1) -> task.run(), foliaDelay);
         Objects.requireNonNull(scheduledTask);
         return scheduledTask::cancel;
      } else {
         BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskLater(this.plugin, task, delay);
         Objects.requireNonNull(bukkitTask);
         return bukkitTask::cancel;
      }
   }

   public CancellableTask runTaskTimer(Runnable task, long delay, long period) {
      if (this.isFolia) {
         long foliaDelay = Math.max(1L, delay);
         long foliaPeriod = Math.max(1L, period);
         ScheduledTask scheduledTask = this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(this.plugin, (scheduledTask1) -> task.run(), foliaDelay, foliaPeriod);
         Objects.requireNonNull(scheduledTask);
         return scheduledTask::cancel;
      } else {
         BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, task, delay, period);
         Objects.requireNonNull(bukkitTask);
         return bukkitTask::cancel;
      }
   }

   public CancellableTask runAsyncTaskLater(Runnable task, long delay) {
      if (this.isFolia) {
         long delayMs = Math.max(50L, delay * 50L);
         ScheduledTask scheduledTask = this.plugin.getServer().getAsyncScheduler().runDelayed(this.plugin, (scheduledTask1) -> task.run(), delayMs, TimeUnit.MILLISECONDS);
         Objects.requireNonNull(scheduledTask);
         return scheduledTask::cancel;
      } else {
         BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskLaterAsynchronously(this.plugin, task, delay);
         Objects.requireNonNull(bukkitTask);
         return bukkitTask::cancel;
      }
   }

   public CancellableTask runAsyncTaskTimer(Runnable task, long delay, long period) {
      CancellableTask handle;
      if (this.isFolia) {
         long delayMs = Math.max(50L, delay * 50L);
         long periodMs = Math.max(50L, period * 50L);
         ScheduledTask scheduledTask = this.plugin.getServer().getAsyncScheduler().runAtFixedRate(this.plugin, (scheduledTask1) -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
         Objects.requireNonNull(scheduledTask);
         handle = scheduledTask::cancel;
      } else {
         BukkitTask bukkitTask = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, task, delay, period);
         Objects.requireNonNull(bukkitTask);
         handle = bukkitTask::cancel;
      }

      this.activeTasks.put(UUID.randomUUID(), handle);
      return handle;
   }

   public void addActiveTask(UUID taskId, CancellableTask task) {
      this.activeTasks.put(taskId, task);
   }

   public void removeActiveTask(UUID taskId) {
      CancellableTask task = (CancellableTask)this.activeTasks.remove(taskId);
      if (task != null) {
         task.cancel();
      }

   }

   public void cancelAllTasks() {
      this.activeTasks.values().forEach(CancellableTask::cancel);
      this.activeTasks.clear();
   }

   public boolean hasActiveTask(UUID taskId) {
      return this.activeTasks.containsKey(taskId);
   }

   public boolean isFolia() {
      return this.isFolia;
   }

   public boolean isPaper() {
      return this.isPaper;
   }

   public int getActiveTaskCount() {
      return this.activeTasks.size();
   }

   public void runAsyncWithCatch(Runnable task, String taskName) {
      if (task == null) {
         this.plugin.getLogger().warning("Attempted to run null task: " + taskName);
      } else {
         this.runAsync(() -> {
            try {
               long startTime = System.currentTimeMillis();
               task.run();
               long duration = System.currentTimeMillis() - startTime;
               if (duration > 100L && this.plugin.getConfigManager().isSlowQueryLoggingEnabled()) {
                  this.plugin.getLogger().warning("Slow async task '" + taskName + "' took " + duration + "ms");
               }
            } catch (Exception e) {
               this.plugin.getLogger().severe("Error in async task '" + taskName + "': " + e.getMessage());
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  e.printStackTrace();
               }
            }

         });
      }
   }
}
