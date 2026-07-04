package eu.kotori.justTeams.team;

import eu.kotori.justTeams.JustTeams;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class TeamUpgradeManager {
   private static final int MAX_TIER_HARD_CAP = 10;
   private static final int MAX_MEMBERS_HARD_CAP = 100;
   private static final int MAX_DAMAGE_BONUS_PERCENT = 15;
   private final JustTeams plugin;

   public TeamUpgradeManager(JustTeams plugin) {
      this.plugin = plugin;
   }

   public boolean isEnabled() {
      return this.plugin.getConfig().getBoolean("team_upgrades.enabled", true);
   }

   public int getMaxTier() {
      ConfigurationSection tiers = this.plugin.getConfig().getConfigurationSection("team_upgrades.tiers");
      if (tiers == null) {
         return 1;
      } else {
         int highest = 1;

         for(String key : tiers.getKeys(false)) {
            try {
               int n = Integer.parseInt(key);
               if (n > highest) {
                  highest = n;
               }
            } catch (NumberFormatException var6) {
            }
         }

         return Math.min(highest, 10);
      }
   }

   public int clampTier(int tier) {
      return tier < 1 ? 1 : Math.min(tier, this.getMaxTier());
   }

   private ConfigurationSection getTierSection(int tier) {
      FileConfiguration var10000 = this.plugin.getConfig();
      int var10001 = this.clampTier(tier);
      return var10000.getConfigurationSection("team_upgrades.tiers." + var10001);
   }

   public int getMaxMembers(int tier) {
      ConfigurationSection s = this.getTierSection(tier);
      int configDefault = this.plugin.getConfig().getInt("settings.max_team_size", 10);
      int value = s != null ? s.getInt("max_members", configDefault) : configDefault;
      if (value < 1) {
         value = 1;
      }

      return Math.min(value, 100);
   }

   public int getEnderChestRows(int tier) {
      ConfigurationSection s = this.getTierSection(tier);
      int configDefault = this.plugin.getConfigManager().getEnderChestRows();
      int value = s != null ? s.getInt("enderchest_rows", configDefault) : configDefault;
      if (value < 1) {
         value = 1;
      }

      if (value > 6) {
         value = 6;
      }

      return value;
   }

   public double getDamageBonusMultiplier(int tier) {
      ConfigurationSection s = this.getTierSection(tier);
      int percent = s != null ? s.getInt("damage_bonus_percent", 0) : 0;
      if (percent < 0) {
         percent = 0;
      }

      if (percent > 15) {
         percent = 15;
      }

      return (double)1.0F + (double)percent / (double)100.0F;
   }

   public int getDamageBonusPercent(int tier) {
      return (int)Math.round((this.getDamageBonusMultiplier(tier) - (double)1.0F) * (double)100.0F);
   }

   public int getHomeCooldownReductionPercent(int tier) {
      ConfigurationSection s = this.getTierSection(tier);
      int percent = s != null ? s.getInt("home_cooldown_reduction_percent", 0) : 0;
      if (percent < 0) {
         percent = 0;
      }

      if (percent > 75) {
         percent = 75;
      }

      return percent;
   }

   public double getUpgradeCost(int fromTier) {
      int targetTier = fromTier + 1;
      if (targetTier > this.getMaxTier()) {
         return (double)-1.0F;
      } else {
         ConfigurationSection s = this.getTierSection(targetTier);
         double cost = s != null ? s.getDouble("cost", (double)0.0F) : (double)0.0F;
         return Math.max(cost, (double)0.0F);
      }
   }

   public boolean canUpgrade(int currentTier) {
      return this.isEnabled() && currentTier < this.getMaxTier();
   }
}
