package eu.kotori.justTeams.util;

import eu.kotori.justTeams.JustTeams;
import java.util.logging.Logger;

public class DebugLogger {
   private final JustTeams plugin;
   private final Logger logger;
   private boolean debugEnabled;

   public DebugLogger(JustTeams plugin) {
      this.plugin = plugin;
      this.logger = plugin.getLogger();
      this.reload();
   }

   public void reload() {
      this.debugEnabled = this.plugin.getConfigManager().isDebugEnabled();
   }

   public void log(String message) {
      if (this.debugEnabled) {
         this.logger.info("[DEBUG] " + message);
      }

   }
}
