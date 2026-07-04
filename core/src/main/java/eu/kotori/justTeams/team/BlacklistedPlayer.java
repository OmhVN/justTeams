package eu.kotori.justTeams.core.team;

import java.time.Instant;
import java.util.UUID;

public class BlacklistedPlayer {
   private final UUID playerUuid;
   private final String playerName;
   private final String reason;
   private final UUID blacklistedByUuid;
   private final String blacklistedByName;
   private final Instant blacklistedAt;

   public BlacklistedPlayer(UUID playerUuid, String playerName, String reason, UUID blacklistedByUuid, String blacklistedByName, Instant blacklistedAt) {
      this.playerUuid = playerUuid;
      this.playerName = playerName;
      this.reason = reason;
      this.blacklistedByUuid = blacklistedByUuid;
      this.blacklistedByName = blacklistedByName;
      this.blacklistedAt = blacklistedAt;
   }

   public UUID getPlayerUuid() {
      return this.playerUuid;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public String getReason() {
      return this.reason;
   }

   public UUID getBlacklistedByUuid() {
      return this.blacklistedByUuid;
   }

   public String getBlacklistedByName() {
      return this.blacklistedByName;
   }

   public Instant getBlacklistedAt() {
      return this.blacklistedAt;
   }
}
