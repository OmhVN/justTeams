package eu.kotori.justTeams.core.quests;

import java.util.List;

public final class Quest {
   private final String id;
   private final String displayName;
   private final List<String> description;
   private final QuestType type;
   private final String target;
   private final long required;
   private final long rewardPoints;
   private final double rewardMoney;
   private final int durationSeconds;
   private final String iconMaterial;

   public Quest(String id, String displayName, List<String> description, QuestType type, String target, long required, long rewardPoints, double rewardMoney, int durationSeconds, String iconMaterial) {
      this.id = id;
      this.displayName = displayName;
      this.description = description == null ? List.of() : List.copyOf(description);
      this.type = type;
      this.target = target == null ? "" : target.toUpperCase();
      this.required = Math.max(1L, required);
      this.rewardPoints = Math.max(0L, rewardPoints);
      this.rewardMoney = Math.max((double)0.0F, rewardMoney);
      this.durationSeconds = Math.max(0, durationSeconds);
      this.iconMaterial = iconMaterial == null ? "PAPER" : iconMaterial;
   }

   public String getId() {
      return this.id;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public List<String> getDescription() {
      return this.description;
   }

   public QuestType getType() {
      return this.type;
   }

   public String getTarget() {
      return this.target;
   }

   public long getRequired() {
      return this.required;
   }

   public long getRewardPoints() {
      return this.rewardPoints;
   }

   public double getRewardMoney() {
      return this.rewardMoney;
   }

   public int getDurationSeconds() {
      return this.durationSeconds;
   }

   public String getIconMaterial() {
      return this.iconMaterial;
   }

   public boolean isExpired(QuestProgress progress) {
      if (this.durationSeconds > 0 && progress != null) {
         long elapsedMs = System.currentTimeMillis() - progress.getStartedAt();
         return elapsedMs >= (long)this.durationSeconds * 1000L;
      } else {
         return false;
      }
   }

   public boolean matchesTarget(String value) {
      if (this.target != null && !this.target.isEmpty() && !this.target.equals("ANY") && !this.target.equals("*")) {
         return value == null ? false : this.target.equalsIgnoreCase(value);
      } else {
         return true;
      }
   }
}
