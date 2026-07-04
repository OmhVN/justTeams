package eu.kotori.justTeams.core.team;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

class PacketEventsGlowHandler implements PacketListener {
   private final GlowManager glowManager;

   PacketEventsGlowHandler(GlowManager glowManager) {
      this.glowManager = glowManager;
   }

   void register() {
      PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
   }

   public void onPacketSend(PacketSendEvent event) {
      if (this.glowManager.isActive()) {
         if (event.getPacketType() == Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(event);
            int entityId = metadataPacket.getEntityId();
            Player receiver = (Player)event.getPlayer();
            UUID targetUuid = this.glowManager.getPlayerUuidByEntityId(entityId);
            Player target = targetUuid != null ? Bukkit.getPlayer(targetUuid) : null;
            if (target != null && !target.getUniqueId().equals(receiver.getUniqueId())) {
               Map<UUID, Map<UUID, ChatColor>> glowingCache = this.glowManager.getGlowingCache();
               if (glowingCache.containsKey(receiver.getUniqueId()) && ((Map)glowingCache.get(receiver.getUniqueId())).containsKey(target.getUniqueId())) {
                  for(EntityData<?> data : metadataPacket.getEntityMetadata()) {
                     if (data.getIndex() == 0 && data.getType() == EntityDataTypes.BYTE) {
                        byte status = (Byte)data.getValue();
                        status = (byte)(status | 64);
                        ((EntityData)data).setValue(status);
                        break;
                     }
                  }
               }

            }
         } else {
            if (event.getPacketType() == Server.SPAWN_PLAYER) {
               WrapperPlayServerSpawnPlayer spawnPacket = new WrapperPlayServerSpawnPlayer(event);
               Player receiver = (Player)event.getPlayer();
               if (receiver == null) {
                  return;
               }

               int spawnedEntityId = spawnPacket.getEntityId();
               this.glowManager.getPlugin().getTaskRunner().runAsyncTaskLater(() -> {
                  if (receiver.isOnline()) {
                     UUID spawnedUuid = this.glowManager.getPlayerUuidByEntityId(spawnedEntityId);
                     Player spawnedPlayer = spawnedUuid != null ? Bukkit.getPlayer(spawnedUuid) : null;
                     if (spawnedPlayer != null && !spawnedPlayer.getUniqueId().equals(receiver.getUniqueId())) {
                        Map<UUID, Map<UUID, ChatColor>> glowingCache = this.glowManager.getGlowingCache();
                        if (glowingCache.containsKey(receiver.getUniqueId())) {
                           ((Map)glowingCache.get(receiver.getUniqueId())).remove(spawnedPlayer.getUniqueId());
                        }

                        this.glowManager.getPlugin().getTeamManager().getPlayerTeamAsync(spawnedPlayer.getUniqueId()).thenAccept((team) -> this.glowManager.refreshGlowForReceiver(spawnedPlayer, receiver, team));
                     }

                  }
               }, 5L);
            }

         }
      }
   }
}
