package eu.kotori.justTeams.core.config;

import eu.kotori.justTeams.JustTeams;
import eu.kotori.justTeams.core.team.Team;
import eu.kotori.justTeams.core.team.TeamUpgradeManager;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class ConfigManager {
   private final JustTeams plugin;
   private FileConfiguration config;
   private volatile boolean bedrockSupportEnabled;
   private volatile String uuidMode;

   public ConfigManager(JustTeams plugin) {
      this.plugin = plugin;
      plugin.saveDefaultConfig();
      this.reloadConfig();
   }

   public void reloadConfig() {
      this.plugin.reloadConfig();
      this.config = this.plugin.getConfig();
      this.bedrockSupportEnabled = this.config.getBoolean("bedrock_support.enabled", true);
      this.uuidMode = this.config.getString("bedrock_support.uuid_mode", "auto");
   }

   public boolean isDebugEnabled() {
      return this.config.getBoolean("settings.debug", false);
   }

   public String getString(String path, String defaultValue) {
      return this.config.getString(path, defaultValue);
   }

   public int getInt(String path, int defaultValue) {
      return this.config.getInt(path, defaultValue);
   }

   public boolean getBoolean(String path, boolean defaultValue) {
      return this.config.getBoolean(path, defaultValue);
   }

   public String getServerIdentifier() {
      return this.config.getString("settings.server-identifier", "survival");
   }

   public String getMainColor() {
      return this.config.getString("settings.main_color", "#4C9DDE");
   }

   public String getAccentColor() {
      return this.config.getString("settings.accent_color", "#4C96D2");
   }

   public String getCurrencyFormat() {
      return this.config.getString("settings.currency_format", "#,##0.00");
   }

   public int getMaxTeamSize() {
      return this.config.getInt("settings.max_team_size", 10);
   }

   public int getMaxTeamSize(Team team) {
      TeamUpgradeManager upgrades = JustTeams.getInstance().getTeamUpgradeManager();
      if (team != null && upgrades != null && upgrades.isEnabled()) {
         return upgrades.getMaxMembers(team.getTier());
      } else {
         if (team != null) {
            Player owner = Bukkit.getPlayer(team.getOwnerUuid());
            if (owner != null) {
               return this.getMaxTeamSize(owner);
            }
         }

         return this.getMaxTeamSize();
      }
   }

   public int getMaxTeamSize(Player player) {
      if (player == null) {
         return this.getMaxTeamSize();
      } else {
         int maxSize = this.getMaxTeamSize();
         if (!this.config.getBoolean("settings.permission_based_team_size", true)) {
            return maxSize;
         } else {
            int[] commonSizes = new int[]{5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200};

            for(int size : commonSizes) {
               if (player.hasPermission("justteams.team.size." + size) && size > maxSize) {
                  maxSize = size;
               }
            }

            for(PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
               if (perm.getValue() && perm.getPermission().startsWith("justteams.team.size.")) {
                  try {
                     String sizeStr = perm.getPermission().substring("justteams.team.size.".length());
                     int size = Integer.parseInt(sizeStr);
                     if (size > maxSize && size <= 1000) {
                        maxSize = size;
                     }
                  } catch (NumberFormatException var8) {
                  }
               }
            }

            return maxSize;
         }
      }
   }

   public double getJoinFee(Player player) {
      if (player == null) {
         return this.config.getDouble("feature_costs.economy.team_join", (double)0.0F);
      } else if (!this.config.getBoolean("feature_costs.permission_based_join_fee.enabled", true)) {
         return this.config.getDouble("feature_costs.economy.team_join", (double)0.0F);
      } else {
         double joinFee = this.config.getDouble("feature_costs.economy.team_join", (double)0.0F);
         double maxFee = this.config.getDouble("feature_costs.permission_based_join_fee.max_fee", (double)10000.0F);
         double[] commonFees = new double[]{(double)10.0F, (double)25.0F, (double)50.0F, (double)100.0F, (double)250.0F, (double)500.0F, (double)1000.0F, (double)2500.0F, (double)5000.0F, (double)10000.0F};

         for(double fee : commonFees) {
            if (player.hasPermission("justteams.joinfee." + (int)fee) && fee > joinFee) {
               joinFee = fee;
            }
         }

         for(PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            if (perm.getValue() && perm.getPermission().startsWith("justteams.joinfee.")) {
               try {
                  String feeStr = perm.getPermission().substring("justteams.joinfee.".length());
                  double fee = Double.parseDouble(feeStr);
                  if (fee > joinFee && fee <= maxFee) {
                     joinFee = fee;
                  }
               } catch (NumberFormatException var12) {
               }
            }
         }

         if (joinFee > maxFee) {
            joinFee = maxFee;
         }

         return joinFee;
      }
   }

   public int getMinNameLength() {
      return this.config.getInt("settings.min_name_length", 3);
   }

   public int getMaxNameLength() {
      return this.config.getInt("settings.max_name_length", 16);
   }

   public int getMaxTagLength() {
      return this.config.getInt("settings.max_tag_length", 6);
   }

   public int getMaxDescriptionLength() {
      return this.config.getInt("settings.max_description_length", 64);
   }

   public boolean getDefaultPvpStatus() {
      return this.config.getBoolean("settings.default_pvp_status", true);
   }

   public boolean isDefaultPublicStatus() {
      return this.config.getBoolean("settings.default_public_status", false);
   }

   public boolean isCrossServerSyncEnabled() {
      return this.config.getBoolean("settings.enable_cross_server_sync", true);
   }

   public boolean isSingleServerMode() {
      return !this.isCrossServerSyncEnabled() || this.config.getBoolean("settings.single_server_mode", false) || this.getServerIdentifier().equals("single-server");
   }

   public int getHeartbeatInterval() {
      return this.config.getInt("settings.sync_optimization.heartbeat_interval", 120);
   }

   public int getCrossServerSyncInterval() {
      return this.config.getInt("settings.sync_optimization.cross_server_sync_interval", 30);
   }

   public int getCriticalSyncInterval() {
      return this.config.getInt("settings.sync_optimization.critical_sync_interval", 5);
   }

   public int getMaxTeamsPerBatch() {
      return this.config.getInt("settings.sync_optimization.max_teams_per_batch", 50);
   }

   public boolean isLazyLoadingEnabled() {
      return this.config.getBoolean("settings.sync_optimization.enable_lazy_loading", true);
   }

   public int getTeamCacheTTL() {
      return this.config.getInt("settings.sync_optimization.team_cache_ttl", 300);
   }

   public boolean isOptimisticLockingEnabled() {
      return this.config.getBoolean("settings.sync_optimization.enable_optimistic_locking", true);
   }

   public int getMaxSyncRetries() {
      return this.config.getInt("settings.sync_optimization.max_sync_retries", 3);
   }

   public int getSyncRetryDelay() {
      return this.config.getInt("settings.sync_optimization.sync_retry_delay", 1000);
   }

   public int getMaxBatchSize() {
      return this.config.getInt("settings.sync_optimization.max_batch_size", 100);
   }

   public boolean isPerformanceMetricsEnabled() {
      return this.config.getBoolean("settings.performance.enable_metrics", false);
   }

   public boolean isSlowQueryLoggingEnabled() {
      return this.config.getBoolean("settings.performance.log_slow_queries", true);
   }

   public int getSlowQueryThreshold() {
      return this.config.getInt("settings.performance.slow_query_threshold", 100);
   }

   public boolean isDetailedSyncLoggingEnabled() {
      return this.config.getBoolean("settings.performance.detailed_sync_logging", false);
   }

   public boolean isConnectionPoolMonitoringEnabled() {
      return this.config.getBoolean("settings.performance.monitor_connection_pool", true);
   }

   public int getConnectionPoolLogInterval() {
      return this.config.getInt("settings.performance.connection_pool_log_interval", 15);
   }

   public boolean isDebugLoggingEnabled() {
      return this.config.getBoolean("settings.performance.enable_debug_logging", false);
   }

   public long getCacheCleanupInterval() {
      return this.config.getLong("settings.sync_optimization.cache_cleanup_interval", 600L);
   }

   public long getGuiUpdateThrottleMs() {
      return this.config.getLong("settings.performance.gui_update_throttle_ms", 100L);
   }

   public int getConnectionPoolMaxSize() {
      return this.config.getInt("storage.connection_pool.max_size", 8);
   }

   public int getConnectionPoolMinIdle() {
      return this.config.getInt("storage.connection_pool.min_idle", 2);
   }

   public long getConnectionPoolConnectionTimeout() {
      return this.config.getLong("storage.connection_pool.connection_timeout", 30000L);
   }

   public long getConnectionPoolIdleTimeout() {
      return this.config.getLong("storage.connection_pool.idle_timeout", 600000L);
   }

   public long getConnectionPoolMaxLifetime() {
      return this.config.getLong("storage.connection_pool.max_lifetime", 1800000L);
   }

   public long getConnectionPoolLeakDetectionThreshold() {
      return this.config.getLong("storage.connection_pool.leak_detection_threshold", 60000L);
   }

   public String getConnectionPoolConnectionTestQuery() {
      return this.config.getString("storage.connection_pool.connection_test_query", "SELECT 1");
   }

   public long getConnectionPoolValidationTimeout() {
      return this.config.getLong("storage.connection_pool.validation_timeout", 5000L);
   }

   public boolean isMySQLEnabled() {
      return this.config.getBoolean("storage.mysql.enabled", false);
   }

   public String getMySQLHost() {
      return this.config.getString("storage.mysql.host", "localhost");
   }

   public int getMySQLPort() {
      return this.config.getInt("storage.mysql.port", 3306);
   }

   public String getMySQLDatabase() {
      return this.config.getString("storage.mysql.database", "donutsmp");
   }

   public String getMySQLUsername() {
      return this.config.getString("storage.mysql.username", "root");
   }

   public String getMySQLPassword() {
      return this.config.getString("storage.mysql.password", "");
   }

   public boolean isMySQLUseSSL() {
      return this.config.getBoolean("storage.mysql.use_ssl", false);
   }

   public boolean isMySQLAllowPublicKeyRetrieval() {
      return this.config.getBoolean("storage.mysql.allow_public_key_retrieval", true);
   }

   public boolean isMySQLUseUnicode() {
      return this.config.getBoolean("storage.mysql.use_unicode", true);
   }

   public String getMySQLCharacterEncoding() {
      return this.config.getString("storage.mysql.character_encoding", "utf8");
   }

   public String getMySQLCollation() {
      return this.config.getString("storage.mysql.collation", "utf8_general_ci");
   }

   public boolean isMySQLAutoReconnect() {
      return this.config.getBoolean("storage.mysql.auto_reconnect", true);
   }

   public boolean isMySQLFailOverReadOnly() {
      return this.config.getBoolean("storage.mysql.fail_over_read_only", false);
   }

   public int getMySQLMaxReconnects() {
      return this.config.getInt("storage.mysql.max_reconnects", 3);
   }

   public int getMySQLConnectionTimeout() {
      return this.config.getInt("storage.mysql.connection_timeout", 30000);
   }

   public int getMySQLSocketTimeout() {
      return this.config.getInt("storage.mysql.socket_timeout", 60000);
   }

   public boolean isRedisEnabled() {
      return this.config.getBoolean("redis.enabled", false);
   }

   public String getRedisHost() {
      return this.config.getString("redis.host", "localhost");
   }

   public int getRedisPort() {
      return this.config.getInt("redis.port", 6379);
   }

   public String getRedisPassword() {
      return this.config.getString("redis.password", "");
   }

   public boolean isRedisSslEnabled() {
      return this.config.getBoolean("redis.use_ssl", false);
   }

   public int getRedisTimeout() {
      return this.config.getInt("redis.timeout", 5000);
   }

   public int getRedisPoolMaxTotal() {
      return this.config.getInt("redis.pool.max_total", 20);
   }

   public int getRedisPoolMaxIdle() {
      return this.config.getInt("redis.pool.max_idle", 10);
   }

   public int getRedisPoolMinIdle() {
      return this.config.getInt("redis.pool.min_idle", 2);
   }

   public boolean isBankEnabled() {
      return this.config.getBoolean("team_bank.enabled", true);
   }

   public double getMaxBankBalance() {
      return this.config.getDouble("team_bank.max_balance", (double)1000000.0F);
   }

   public boolean isHomeEnabled() {
      return this.config.getBoolean("team_home.enabled", true);
   }

   public int getWarmupSeconds() {
      return this.config.getInt("team_home.warmup_seconds", 5);
   }

   public int getHomeCooldownSeconds() {
      return this.config.getInt("team_home.cooldown_seconds", 300);
   }

   public boolean isEnderChestEnabled() {
      return this.config.getBoolean("team_enderchest.enabled", true);
   }

   public int getEnderChestRows() {
      return this.config.getInt("team_enderchest.rows", 3);
   }

   public boolean isSoundsEnabled() {
      return this.config.getBoolean("effects.sounds.enabled", true);
   }

   public boolean isParticlesEnabled() {
      return this.config.getBoolean("effects.particles.enabled", true);
   }

   public double getDouble(String path, double defaultValue) {
      return this.config.getDouble(path, defaultValue);
   }

   public long getLong(String path, long defaultValue) {
      return this.config.getLong(path, defaultValue);
   }

   public List<String> getStringList(String path) {
      return this.config.getStringList(path);
   }

   public boolean contains(String path) {
      return this.config.contains(path);
   }

   public Set<String> getKeys(boolean deep) {
      return this.config.getKeys(deep);
   }

   public ConfigurationSection getConfigurationSection(String path) {
      return this.config.getConfigurationSection(path);
   }

   public int getMaxWarpsPerTeam() {
      return this.config.getInt("team_warps.max_warps", 5);
   }

   public int getMaxInvitesPerTeam() {
      return this.config.getInt("settings.max_invites_per_team", 10);
   }

   public int getInviteExpirationMinutes() {
      return this.config.getInt("settings.invite_expiration_minutes", 30);
   }

   public int getJoinRequestExpirationMinutes() {
      return this.config.getInt("settings.join_request_expiration_minutes", 60);
   }

   public boolean isEconomyEnabled() {
      return this.config.getBoolean("team_bank.enabled", true);
   }

   public double getMinBankTransaction() {
      return this.config.getDouble("team_bank.min_transaction", 0.01);
   }

   public double getMaxBankTransaction() {
      return this.config.getDouble("team_bank.max_transaction", (double)1000000.0F);
   }

   public boolean isTeamPvpEnabled() {
      return this.isFeatureEnabled("team_pvp");
   }

   public int getPvpToggleCooldown() {
      return this.config.getInt("team_pvp.toggle_cooldown", 300);
   }

   public boolean isDisableFlyOnCombat() {
      return this.config.getBoolean("team_pvp.disable_fly_on_combat", false);
   }

   public boolean isTeamHomeEnabled() {
      return this.isFeatureEnabled("team_home");
   }

   public int getHomeWarmupSeconds() {
      return this.config.getInt("team_home.warmup_seconds", 5);
   }

   public boolean isTeamWarpsEnabled() {
      return this.isFeatureEnabled("team_warps");
   }

   public int getWarpCooldownSeconds() {
      return this.config.getInt("team_warps.cooldown_seconds", 10);
   }

   public int getWarpWarmupSeconds() {
      return this.config.getInt("team_warps.warmup_seconds", 5);
   }

   public int getMaxWarpPasswordLength() {
      return this.config.getInt("team_warps.max_password_length", 20);
   }

   public boolean isTeamEnderchestEnabled() {
      return this.isFeatureEnabled("team_enderchest") && this.config.getBoolean("team_enderchest.enabled", true);
   }

   public boolean isTeamAlliesEnabled() {
      return this.isFeatureEnabled("team_allies") && this.config.getBoolean("team_allies.enabled", true);
   }

   public int getMaxAllies() {
      return this.config.getInt("team_allies.max_allies", 10);
   }

   public boolean isAllyRequestToggleAllowed() {
      return this.config.getBoolean("team_allies.allow_request_toggle", true);
   }

   public int getEnderchestRows() {
      return this.config.getInt("team_enderchest.rows", 3);
   }

   public int getEnderchestLockTimeout() {
      return this.config.getInt("team_enderchest.lock_timeout", 300);
   }

   public boolean areSoundsEnabled() {
      return this.config.getBoolean("effects.sounds.enabled", true);
   }

   public boolean areParticlesEnabled() {
      return this.config.getBoolean("effects.particles.enabled", true);
   }

   public String getSuccessSound() {
      return this.config.getString("effects.sounds.success", "ENTITY_PLAYER_LEVELUP");
   }

   public String getErrorSound() {
      return this.config.getString("effects.sounds.error", "ENTITY_VILLAGER_NO");
   }

   public String getTeleportSound() {
      return this.config.getString("effects.sounds.teleport", "ENTITY_ENDERMAN_TELEPORT");
   }

   public String getWarmupParticle() {
      return this.config.getString("effects.particles.teleport_warmup", "PORTAL");
   }

   public String getSuccessParticle() {
      return this.config.getString("effects.particles.teleport_success", "END_ROD");
   }

   public boolean isBroadcastTeamCreatedEnabled() {
      return this.config.getBoolean("broadcasts.team-created", true);
   }

   public boolean isBroadcastTeamDisbandedEnabled() {
      return this.config.getBoolean("broadcasts.team-disbanded", true);
   }

   public boolean isBroadcastPlayerJoinedEnabled() {
      return this.config.getBoolean("broadcasts.player-joined", true);
   }

   public boolean isBroadcastPlayerLeftEnabled() {
      return this.config.getBoolean("broadcasts.player-left", true);
   }

   public boolean isBroadcastPlayerKickedEnabled() {
      return this.config.getBoolean("broadcasts.player-kicked", true);
   }

   public boolean isBroadcastOwnershipTransferredEnabled() {
      return this.config.getBoolean("broadcasts.ownership-transferred", true);
   }

   public boolean isSpamProtectionEnabled() {
      return this.config.getBoolean("security.spam_protection.enabled", true);
   }

   public int getCommandSpamThreshold() {
      return this.config.getInt("security.spam_protection.command_threshold", 5);
   }

   public int getMessageSpamThreshold() {
      return this.config.getInt("security.spam_protection.message_threshold", 3);
   }

   public int getSpamCooldownSeconds() {
      return this.config.getInt("security.spam_protection.cooldown_seconds", 10);
   }

   public boolean isContentFilteringEnabled() {
      return this.config.getBoolean("security.content_filtering.enabled", true);
   }

   public List<String> getBannedWords() {
      return this.config.getStringList("security.content_filtering.banned_words");
   }

   public int getMaxMessageLength() {
      return this.config.getInt("security.content_filtering.max_message_length", 200);
   }

   public boolean isBedrockSupportEnabled() {
      return this.bedrockSupportEnabled;
   }

   public boolean isShowPlatformIndicators() {
      return this.config.getBoolean("bedrock_support.show_platform_indicators", true);
   }

   public boolean isShowGamertags() {
      return this.config.getBoolean("bedrock_support.show_gamertags", true);
   }

   public boolean isFullFeatureSupport() {
      return this.config.getBoolean("bedrock_support.full_feature_support", true);
   }

   public boolean isCrossPlatformTeams() {
      return this.config.getBoolean("bedrock_support.cross_platform_teams", true);
   }

   public String getUuidMode() {
      return this.uuidMode;
   }

   public boolean isFeatureEnabled(String feature) {
      return this.config.getBoolean("features." + feature, true);
   }

   public boolean isTeamCreationEnabled() {
      return this.isFeatureEnabled("team_creation");
   }

   public boolean isTeamDisbandEnabled() {
      return this.isFeatureEnabled("team_disband");
   }

   public boolean isTeamInvitesEnabled() {
      return this.isFeatureEnabled("team_invites");
   }

   public boolean isTeamJoinRequestsEnabled() {
      return this.isFeatureEnabled("team_join_requests");
   }

   public boolean isTeamBlacklistEnabled() {
      return this.isFeatureEnabled("team_blacklist");
   }

   public boolean isTeamTransferEnabled() {
      return this.isFeatureEnabled("team_transfer");
   }

   public boolean isTeamPublicToggleEnabled() {
      return this.isFeatureEnabled("team_public_toggle");
   }

   public boolean isMemberKickEnabled() {
      return this.isFeatureEnabled("member_kick");
   }

   public boolean isMemberPromoteEnabled() {
      return this.isFeatureEnabled("member_promote");
   }

   public boolean isMemberDemoteEnabled() {
      return this.isFeatureEnabled("member_demote");
   }

   public boolean isMemberLeaveEnabled() {
      return this.isFeatureEnabled("member_leave");
   }

   public boolean isTeamInfoEnabled() {
      return this.isFeatureEnabled("team_info");
   }

   public boolean isTeamTagEnabled() {
      return this.isFeatureEnabled("team_tag");
   }

   public boolean isTeamDescriptionEnabled() {
      return this.isFeatureEnabled("team_description");
   }

   public boolean isTeamLeaderboardEnabled() {
      return this.isFeatureEnabled("team_leaderboard");
   }

   public boolean isTeamHomeSetEnabled() {
      return this.isFeatureEnabled("team_home_set");
   }

   public boolean isTeamHomeTeleportEnabled() {
      return this.isFeatureEnabled("team_home_teleport");
   }

   public boolean isTeamWarpSetEnabled() {
      return this.isFeatureEnabled("team_warp_set");
   }

   public boolean isTeamWarpDeleteEnabled() {
      return this.isFeatureEnabled("team_warp_delete");
   }

   public boolean isTeamWarpTeleportEnabled() {
      return this.isFeatureEnabled("team_warp_teleport");
   }

   public boolean isTeamPvpToggleEnabled() {
      return this.isFeatureEnabled("team_pvp_toggle");
   }

   public boolean isTeamBankEnabled() {
      return this.isFeatureEnabled("team_bank");
   }

   public boolean isTeamBankDepositEnabled() {
      return this.isFeatureEnabled("team_bank_deposit");
   }

   public boolean isTeamBankWithdrawEnabled() {
      return this.isFeatureEnabled("team_bank_withdraw");
   }

   public boolean isTeamChatEnabled() {
      return this.isFeatureEnabled("team_chat");
   }

   public boolean isTeamGlowEnabled() {
      return this.isFeatureEnabled("team_glow");
   }

   public boolean isTeamMessageCommandEnabled() {
      return this.isFeatureEnabled("team_message_command");
   }

   public boolean isWorldRestrictionsEnabled() {
      return this.config.getBoolean("world_restrictions.enabled", true);
   }

   public boolean isFeatureDisabledInWorld(String feature, String worldName) {
      if (!this.isWorldRestrictionsEnabled()) {
         return false;
      } else {
         List<String> disabledWorlds = this.config.getStringList("world_restrictions.disabled_worlds." + feature);
         return disabledWorlds.contains(worldName);
      }
   }

   public List<String> getDisabledWorldsForFeature(String feature) {
      return this.config.getStringList("world_restrictions.disabled_worlds." + feature);
   }

   public boolean isSetHomeDisabledInWorld(String worldName) {
      return this.isFeatureDisabledInWorld("sethome", worldName);
   }

   public boolean isSetWarpDisabledInWorld(String worldName) {
      return this.isFeatureDisabledInWorld("setwarp", worldName);
   }

   public boolean isHomeDisabledInWorld(String worldName) {
      return this.isFeatureDisabledInWorld("home", worldName);
   }

   public boolean isWarpDisabledInWorld(String worldName) {
      return this.isFeatureDisabledInWorld("warp", worldName);
   }

   public boolean isFeatureCostsEnabled() {
      return this.config.getBoolean("feature_costs.enabled", true);
   }

   public boolean isEconomyCostsEnabled() {
      return this.isFeatureCostsEnabled() && this.config.getBoolean("feature_costs.economy.enabled", true);
   }

   public boolean isItemCostsEnabled() {
      return this.isFeatureCostsEnabled() && this.config.getBoolean("feature_costs.items.enabled", false);
   }

   public double getFeatureEconomyCost(String feature) {
      return this.config.getDouble("feature_costs.economy." + feature, (double)0.0F);
   }

   public List<String> getFeatureItemCosts(String feature) {
      return this.config.getStringList("feature_costs.items." + feature);
   }

   public boolean shouldConsumeItemsOnUse() {
      return this.config.getBoolean("feature_costs.items.consume_on_use", true);
   }

   public boolean isSpacesInNameAllowed() {
      return this.config.getBoolean("settings.creation.allow_spaces_in_name", true);
   }

   public boolean areFormattingCodesAllowed() {
      return this.config.contains("settings.allow_formatting_codes") ? this.config.getBoolean("settings.allow_formatting_codes", true) : this.config.getBoolean("allow_formatting_codes", true);
   }

   public List<String> getBlockedFormattingCodes() {
      return this.config.contains("settings.blocked_formatting_codes") ? this.config.getStringList("settings.blocked_formatting_codes") : this.config.getStringList("blocked_formatting_codes");
   }

   public boolean isFormattingCodeBlocked(String code) {
      List<String> blockedCodes = this.getBlockedFormattingCodes();
      return blockedCodes.contains(code.toLowerCase());
   }

   public boolean isCreationRequireColor() {
      return this.config.getBoolean("settings.creation.require_color", false);
   }

   public String getCreationDefaultColor() {
      return this.config.getString("settings.creation.default_color", "<white>");
   }

   public boolean isGuiColorSelectionEnabled() {
      return this.config.getBoolean("settings.creation.use_gui_color_selection", false);
   }
}
