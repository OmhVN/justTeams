package eu.kotori.justTeams.core.quests;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class QuestListener implements Listener {
   private static final long SAME_VICTIM_COOLDOWN_MS = 300000L;
   private static final int MAX_TRACKED_BLOCKS = 100000;
   private final JustTeams plugin;
   private final Map<UUID, Long> distanceAccum = new ConcurrentHashMap();
   private final Map<String, Long> recentPlayerKills = new ConcurrentHashMap();
   private final Set<Long> playerPlacedBlocks = Collections.newSetFromMap(Collections.synchronizedMap(new LinkedHashMap<Long, Boolean>(1024, 0.75F, false) {
      protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
         return this.size() > 100000;
      }
   }));

   public QuestListener(JustTeams plugin) {
      this.plugin = plugin;
   }

   private Team teamOf(Player p) {
      return p == null ? null : this.plugin.getTeamManager().getPlayerTeamCached(p.getUniqueId());
   }

   private static long posKey(Block b) {
      long key = (long)b.getX() & 67108863L | ((long)b.getZ() & 67108863L) << 26 | ((long)(b.getY() + 2048) & 4095L) << 52;
      return key ^ (long)b.getWorld().getUID().hashCode() * -7046029254386353131L;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPlayerKill(PlayerDeathEvent event) {
      Player victim = event.getEntity();
      Player killer = victim.getKiller();
      if (killer != null && !killer.equals(victim)) {
         Team team = this.teamOf(killer);
         if (team != null) {
            if (!team.isMember(victim.getUniqueId())) {
               String var10000 = String.valueOf(killer.getUniqueId());
               String pairKey = var10000 + "|" + String.valueOf(victim.getUniqueId());
               long now = System.currentTimeMillis();
               Long last = (Long)this.recentPlayerKills.get(pairKey);
               if (last == null || now - last >= 300000L) {
                  this.recentPlayerKills.put(pairKey, now);
                  if (this.recentPlayerKills.size() > 5000) {
                     this.recentPlayerKills.values().removeIf((t) -> now - t >= 300000L);
                  }

                  this.plugin.getQuestManager().onEvent(team.getId(), QuestType.KILL_PLAYERS, "ANY", 1L);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMobKill(EntityDeathEvent event) {
      Player killer = event.getEntity().getKiller();
      if (killer != null) {
         if (!(event.getEntity() instanceof Player)) {
            Team team = this.teamOf(killer);
            if (team != null) {
               this.plugin.getQuestManager().onEvent(team.getId(), QuestType.KILL_MOBS, event.getEntityType().name(), 1L);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent event) {
      if (this.plugin.getQuestManager().isEnabled()) {
         if (!this.playerPlacedBlocks.contains(posKey(event.getBlock()))) {
            Team team = this.teamOf(event.getPlayer());
            if (team != null) {
               this.plugin.getQuestManager().onEvent(team.getId(), QuestType.BREAK_BLOCKS, event.getBlock().getType().name(), 1L);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (this.plugin.getQuestManager().isEnabled()) {
         boolean freshSpot = this.playerPlacedBlocks.add(posKey(event.getBlock()));
         if (freshSpot) {
            Team team = this.teamOf(event.getPlayer());
            if (team != null) {
               this.plugin.getQuestManager().onEvent(team.getId(), QuestType.PLACE_BLOCKS, event.getBlock().getType().name(), 1L);
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.distanceAccum.remove(event.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
         Player p = event.getPlayer();
         Team team = this.teamOf(p);
         if (team != null) {
            long accum = (Long)this.distanceAccum.merge(p.getUniqueId(), 1L, Long::sum);
            if (accum >= 10L) {
               this.distanceAccum.put(p.getUniqueId(), 0L);
               this.plugin.getQuestManager().onEvent(team.getId(), QuestType.TRAVEL_DISTANCE, "ANY", accum);
            }

         }
      }
   }
}
