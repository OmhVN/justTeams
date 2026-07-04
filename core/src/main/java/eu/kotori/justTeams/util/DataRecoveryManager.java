package eu.kotori.justTeams.core.util;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.storage.IDataStorage;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamPlayer;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DataRecoveryManager {
   private final JustTeams plugin;
   private final IDataStorage storage;
   private final Map<Integer, TeamSnapshot> teamSnapshots = new ConcurrentHashMap();
   private final Map<Integer, Instant> lastSaveTimestamps = new ConcurrentHashMap();
   private final File backupDirectory;
   private boolean autoSaveEnabled = true;
   private final Set<Integer> changedTeams = ConcurrentHashMap.newKeySet();

   public DataRecoveryManager(JustTeams plugin) {
      this.plugin = plugin;
      this.storage = plugin.getStorageManager().getStorage();
      this.backupDirectory = new File(plugin.getDataFolder(), "backups");
      if (!this.backupDirectory.exists()) {
         this.backupDirectory.mkdirs();
      }

      this.startAutoSaveTask();
      this.startBackupTask();
   }

   private void startAutoSaveTask() {
      this.plugin.getTaskRunner().runAsyncTaskTimer(() -> {
         if (this.autoSaveEnabled) {
            this.performAutoSave();
         }

      }, 6000L, 6000L);
   }

   private void startBackupTask() {
      this.plugin.getTaskRunner().runAsyncTaskTimer(() -> {
         if (this.autoSaveEnabled && this.plugin.getConfigManager().isDebugEnabled()) {
            this.createBackupSnapshot();
         }

      }, 36000L, 36000L);
   }

   public void performAutoSave() {
      if (!this.changedTeams.isEmpty()) {
         this.plugin.getLogger().info("Auto-save starting for " + this.changedTeams.size() + " modified teams...");
         int savedCount = 0;
         int errorCount = 0;
         Set<Integer> teamsToSave = new HashSet(this.changedTeams);
         this.changedTeams.clear();

         for(int teamId : teamsToSave) {
            try {
               Optional<Team> teamOpt = this.storage.findTeamById(teamId);
               if (teamOpt.isPresent()) {
                  Team team = (Team)teamOpt.get();
                  this.saveTeamData(team);
                  ++savedCount;
                  this.teamSnapshots.put(teamId, new TeamSnapshot(team));
                  this.lastSaveTimestamps.put(teamId, Instant.now());
               }
            } catch (Exception e) {
               ++errorCount;
               this.plugin.getLogger().severe("Error auto-saving team " + teamId + ": " + e.getMessage());
               e.printStackTrace();
            }
         }

         this.plugin.getLogger().info("Auto-save completed: " + savedCount + " saved, " + errorCount + " errors");
      }
   }

   private void saveTeamData(Team team) {
      for(TeamPlayer member : team.getMembers()) {
         try {
            this.storage.updateMemberPermissions(team.getId(), member.getPlayerUuid(), member.canWithdraw(), member.canUseEnderChest(), member.canSetHome(), member.canUseHome());
            this.storage.updateMemberEditingPermissions(team.getId(), member.getPlayerUuid(), member.canEditMembers(), member.canEditCoOwners(), member.canKickMembers(), member.canPromoteMembers(), member.canDemoteMembers());
         } catch (Exception e) {
            Logger var10000 = this.plugin.getLogger();
            String var10001 = String.valueOf(member.getPlayerUuid());
            var10000.severe("Error saving member " + var10001 + " in team " + team.getName() + ": " + e.getMessage());
         }
      }

      try {
         this.storage.setPvpStatus(team.getId(), team.isPvpEnabled());
         this.storage.setPublicStatus(team.getId(), team.isPublic());
         this.storage.updateTeamBalance(team.getId(), team.getBalance());
         this.storage.updateTeamStats(team.getId(), team.getKills(), team.getDeaths());
      } catch (Exception e) {
         Logger var7 = this.plugin.getLogger();
         String var8 = team.getName();
         var7.severe("Error saving team settings for " + var8 + ": " + e.getMessage());
      }

   }

   public void markTeamChanged(int teamId) {
      this.changedTeams.add(teamId);
   }

   public void createBackupSnapshot() {
      try {
         String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
         File backupFile = new File(this.backupDirectory, "teams_backup_" + timestamp + ".log");
         this.plugin.getLogger().fine("Creating backup snapshot: " + backupFile.getName());
         FileWriter writer = new FileWriter(backupFile);

         try {
            writer.write("=".repeat(80) + "\n");
            writer.write("DonutTeams Data Backup\n");
            writer.write("Timestamp: " + timestamp + "\n");
            writer.write("=".repeat(80) + "\n\n");
            List<Team> allTeams = this.storage.getAllTeams();
            writer.write("Total Teams: " + allTeams.size() + "\n\n");

            for(Team team : allTeams) {
               writer.write("-".repeat(80) + "\n");
               writer.write("Team ID: " + team.getId() + "\n");
               writer.write("Team Name: " + team.getName() + "\n");
               writer.write("Team Tag: " + team.getTag() + "\n");
               writer.write("Owner: " + String.valueOf(team.getOwnerUuid()) + "\n");
               writer.write("Member Count: " + team.getMembers().size() + "\n");
               Object[] var10002 = new Object[]{team.getBalance()};
               writer.write("Balance: $" + String.format("%.2f", var10002) + "\n");
               writer.write("PvP Enabled: " + team.isPvpEnabled() + "\n");
               writer.write("Public: " + team.isPublic() + "\n");
               writer.write("Kills: " + team.getKills() + "\n");
               writer.write("Deaths: " + team.getDeaths() + "\n");
               writer.write("\nMembers:\n");

               for(TeamPlayer member : team.getMembers()) {
                  String var10001 = String.valueOf(member.getPlayerUuid());
                  writer.write("  - " + var10001 + " (" + String.valueOf(member.getRole()) + ")\n");
                  writer.write("    Permissions: ");
                  writer.write("withdraw=" + member.canWithdraw() + ", ");
                  writer.write("enderchest=" + member.canUseEnderChest() + ", ");
                  writer.write("sethome=" + member.canSetHome() + ", ");
                  writer.write("usehome=" + member.canUseHome() + "\n");
               }

               List<IDataStorage.TeamWarp> warps = this.storage.getTeamWarps(team.getId());
               if (!warps.isEmpty()) {
                  writer.write("\nWarps (" + warps.size() + "):\n");

                  for(IDataStorage.TeamWarp warp : warps) {
                     String var15 = warp.name();
                     writer.write("  - " + var15 + " @ " + warp.serverName() + "\n");
                     var15 = warp.location();
                     writer.write("    Location: " + var15 + "\n");
                     writer.write("    Password Protected: " + (warp.password() != null) + "\n");
                  }
               }

               writer.write("\n");
            }

            writer.write("=".repeat(80) + "\n");
            writer.write("Backup completed successfully\n");
         } catch (Throwable var11) {
            try {
               writer.close();
            } catch (Throwable var10) {
               var11.addSuppressed(var10);
            }

            throw var11;
         }

         writer.close();
         this.plugin.getLogger().fine("Backup snapshot created successfully: " + backupFile.getName());
         this.cleanOldBackups();
      } catch (IOException e) {
         this.plugin.getLogger().severe("Error creating backup snapshot: " + e.getMessage());
         e.printStackTrace();
      }

   }

   private void cleanOldBackups() {
      File[] backupFiles = this.backupDirectory.listFiles((dir, name) -> name.startsWith("teams_backup_") && name.endsWith(".log"));
      if (backupFiles != null && backupFiles.length > 10) {
         Arrays.sort(backupFiles, Comparator.comparingLong(File::lastModified).reversed());

         for(int i = 10; i < backupFiles.length; ++i) {
            if (backupFiles[i].delete()) {
               this.plugin.getLogger().fine("Deleted old backup: " + backupFiles[i].getName());
            }
         }

      }
   }

   public ValidationReport validateTeamData(Team team) {
      ValidationReport report = new ValidationReport(team.getId(), team.getName());
      if (team.getOwnerUuid() == null) {
         report.addError("Team has no owner");
      }

      if (team.getMembers().isEmpty()) {
         report.addError("Team has no members");
      }

      boolean ownerInMembers = team.getMembers().stream().anyMatch((m) -> m.getPlayerUuid().equals(team.getOwnerUuid()));
      if (!ownerInMembers) {
         report.addError("Owner not found in members list");
      }

      for(TeamPlayer member : team.getMembers()) {
         if (member.getRole() == null) {
            report.addWarning("Member " + String.valueOf(member.getPlayerUuid()) + " has null role");
         }
      }

      if (team.getBalance() < (double)0.0F) {
         report.addWarning("Team has negative balance: " + team.getBalance());
      }

      return report;
   }

   public Map<Integer, ValidationReport> validateAllTeams() {
      Map<Integer, ValidationReport> reports = new HashMap();

      for(Team team : this.storage.getAllTeams()) {
         ValidationReport report = this.validateTeamData(team);
         if (!report.isValid()) {
            reports.put(team.getId(), report);
         }
      }

      return reports;
   }

   public void forceSaveTeam(Team team) {
      try {
         this.saveTeamData(team);
         this.teamSnapshots.put(team.getId(), new TeamSnapshot(team));
         this.lastSaveTimestamps.put(team.getId(), Instant.now());
         this.plugin.getLogger().info("Force saved team: " + team.getName());
      } catch (Exception e) {
         Logger var10000 = this.plugin.getLogger();
         String var10001 = team.getName();
         var10000.severe("Error force saving team " + var10001 + ": " + e.getMessage());
         e.printStackTrace();
      }

   }

   public Optional<Instant> getLastSaveTime(int teamId) {
      return Optional.ofNullable((Instant)this.lastSaveTimestamps.get(teamId));
   }

   public void setAutoSaveEnabled(boolean enabled) {
      this.autoSaveEnabled = enabled;
      this.plugin.getLogger().info("Auto-save " + (enabled ? "enabled" : "disabled"));
   }

   private static class TeamSnapshot {
      final int memberCount;
      final int warpCount;
      final double balance;
      final Instant timestamp;

      TeamSnapshot(Team team) {
         this.memberCount = team.getMembers().size();
         this.warpCount = 0;
         this.balance = team.getBalance();
         this.timestamp = Instant.now();
      }
   }

   public static class ValidationReport {
      private final int teamId;
      private final String teamName;
      private final List<String> errors = new ArrayList();
      private final List<String> warnings = new ArrayList();

      public ValidationReport(int teamId, String teamName) {
         this.teamId = teamId;
         this.teamName = teamName;
      }

      public void addError(String error) {
         this.errors.add(error);
      }

      public void addWarning(String warning) {
         this.warnings.add(warning);
      }

      public boolean isValid() {
         return this.errors.isEmpty();
      }

      public int getTeamId() {
         return this.teamId;
      }

      public String getTeamName() {
         return this.teamName;
      }

      public List<String> getErrors() {
         return new ArrayList(this.errors);
      }

      public List<String> getWarnings() {
         return new ArrayList(this.warnings);
      }

      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("Validation Report for Team: ").append(this.teamName).append(" (ID: ").append(this.teamId).append(")\n");
         if (this.errors.isEmpty() && this.warnings.isEmpty()) {
            sb.append("  ✓ All checks passed\n");
         }

         if (!this.errors.isEmpty()) {
            sb.append("  ERRORS:\n");

            for(String error : this.errors) {
               sb.append("    ✗ ").append(error).append("\n");
            }
         }

         if (!this.warnings.isEmpty()) {
            sb.append("  WARNINGS:\n");

            for(String warning : this.warnings) {
               sb.append("    ⚠ ").append(warning).append("\n");
            }
         }

         return sb.toString();
      }
   }
}
