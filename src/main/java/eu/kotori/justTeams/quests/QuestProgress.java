package eu.kotori.justTeams.quests;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class QuestProgress {
   private final int teamId;
   private final String questId;
   private final AtomicLong progress;
   private final long startedAt;
   private final AtomicBoolean completed;
   private final AtomicBoolean claimed;
   private final AtomicBoolean claimPending = new AtomicBoolean(false);

   public QuestProgress(int teamId, String questId, long progress, long startedAt, boolean completed, boolean claimed) {
      this.teamId = teamId;
      this.questId = questId;
      this.progress = new AtomicLong(Math.max(0L, progress));
      this.startedAt = startedAt > 0L ? startedAt : System.currentTimeMillis();
      this.completed = new AtomicBoolean(completed);
      this.claimed = new AtomicBoolean(claimed);
   }

   public int getTeamId() {
      return this.teamId;
   }

   public String getQuestId() {
      return this.questId;
   }

   public long getProgress() {
      return this.progress.get();
   }

   public long getStartedAt() {
      return this.startedAt;
   }

   public boolean isCompleted() {
      return this.completed.get();
   }

   public boolean isClaimed() {
      return this.claimed.get();
   }

   public long addProgress(long amount) {
      return amount > 0L && !this.completed.get() ? this.progress.addAndGet(amount) : this.progress.get();
   }

   public void setProgress(long value) {
      this.progress.set(Math.max(0L, value));
   }

   public void markCompleted() {
      this.completed.set(true);
   }

   public void markClaimed() {
      this.claimed.set(true);
   }

   public boolean markCompletedIfFirst() {
      return this.completed.compareAndSet(false, true);
   }

   public boolean tryClaim() {
      return this.claimed.compareAndSet(false, true);
   }

   public boolean isClaimPending() {
      return this.claimPending.get();
   }

   public void setClaimPending(boolean pending) {
      this.claimPending.set(pending);
   }
}
