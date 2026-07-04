package eu.kotori.justTeams.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import eu.kotori.justTeams.JustTeams;
import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class DiscordWebhookManager {
   private final JustTeams plugin;
   private FileConfiguration webhookConfig;
   private File webhookFile;
   private boolean enabled;
   private String webhookUrl;
   private String username;
   private String avatarUrl;
   private String serverName;
   private boolean rateLimitingEnabled;
   private int maxPerMinute;
   private long minDelayMs;
   private final AtomicInteger requestsThisMinute = new AtomicInteger(0);
   private final AtomicLong lastRequestTime = new AtomicLong(0L);
   private final AtomicLong lastMinuteReset = new AtomicLong(System.currentTimeMillis());
   private boolean retryEnabled;
   private int maxRetryAttempts;
   private long retryDelayMs;
   private boolean showServerIp;
   private boolean showUuids;
   private int connectionTimeout;
   private int readTimeout;
   private boolean logErrors;
   private boolean logSuccess;
   private boolean asyncSending;
   private boolean queueOnRateLimit;
   private int maxQueueSize;
   private final BlockingQueue<WebhookMessage> messageQueue;
   private final ScheduledExecutorService executor;
   private final ExecutorService asyncExecutor;

   public DiscordWebhookManager(JustTeams plugin) {
      this.plugin = plugin;
      this.messageQueue = new LinkedBlockingQueue();
      this.executor = Executors.newScheduledThreadPool(1, (r) -> {
         Thread thread = new Thread(r, "JustTeams-Webhook-Processor");
         thread.setDaemon(true);
         return thread;
      });
      this.asyncExecutor = Executors.newFixedThreadPool(2, (r) -> {
         Thread thread = new Thread(r, "JustTeams-Webhook-Sender");
         thread.setDaemon(true);
         return thread;
      });
      this.loadConfiguration();
      this.startQueueProcessor();
   }

   private void loadConfiguration() {
      this.webhookFile = new File(this.plugin.getDataFolder(), "webhooks.yml");
      if (!this.webhookFile.exists()) {
         this.plugin.saveResource("webhooks.yml", false);
      }

      this.webhookConfig = YamlConfiguration.loadConfiguration(this.webhookFile);
      this.enabled = this.webhookConfig.getBoolean("webhook.enabled", false);
      this.webhookUrl = this.webhookConfig.getString("webhook.url", "");
      this.username = this.webhookConfig.getString("webhook.username", "JustTeams Bot");
      this.avatarUrl = this.webhookConfig.getString("webhook.avatar_url", "");
      this.serverName = this.webhookConfig.getString("webhook.server_name", "Minecraft Server");
      this.rateLimitingEnabled = this.webhookConfig.getBoolean("webhook.rate_limiting.enabled", true);
      this.maxPerMinute = this.webhookConfig.getInt("webhook.rate_limiting.max_per_minute", 20);
      this.minDelayMs = this.webhookConfig.getLong("webhook.rate_limiting.min_delay_ms", 1000L);
      this.retryEnabled = this.webhookConfig.getBoolean("webhook.retry.enabled", true);
      this.maxRetryAttempts = this.webhookConfig.getInt("webhook.retry.max_attempts", 3);
      this.retryDelayMs = this.webhookConfig.getLong("webhook.retry.retry_delay_ms", 2000L);
      this.showServerIp = this.webhookConfig.getBoolean("advanced.show_server_ip", false);
      this.showUuids = this.webhookConfig.getBoolean("advanced.show_uuids", false);
      this.connectionTimeout = this.webhookConfig.getInt("advanced.connection_timeout", 5000);
      this.readTimeout = this.webhookConfig.getInt("advanced.read_timeout", 5000);
      this.logErrors = this.webhookConfig.getBoolean("advanced.log_errors", true);
      this.logSuccess = this.webhookConfig.getBoolean("advanced.log_success", false);
      this.asyncSending = this.webhookConfig.getBoolean("advanced.async_sending", true);
      this.queueOnRateLimit = this.webhookConfig.getBoolean("advanced.queue_on_rate_limit", true);
      this.maxQueueSize = this.webhookConfig.getInt("advanced.max_queue_size", 50);
      if (this.enabled && (this.webhookUrl.isEmpty() || this.webhookUrl.contains("YOUR_WEBHOOK"))) {
         this.plugin.getLogger().warning("[Discord Webhook] Webhook is enabled but URL is not configured!");
         this.plugin.getLogger().warning("[Discord Webhook] Please set your webhook URL in webhooks.yml");
         this.enabled = false;
      }

      if (this.enabled) {
         this.plugin.getLogger().info("[Discord Webhook] Discord webhook notifications enabled");
         this.plugin.getLogger().info("[Discord Webhook] Server: " + this.serverName);
      }

   }

   public void reload() {
      this.loadConfiguration();
      this.plugin.getLogger().info("[Discord Webhook] Configuration reloaded");
   }

   private void startQueueProcessor() {
      this.executor.scheduleAtFixedRate(() -> {
         try {
            this.processQueue();
         } catch (Exception e) {
            if (this.logErrors) {
               this.plugin.getLogger().warning("[Discord Webhook] Error in queue processor: " + e.getMessage());
            }
         }

      }, 1L, 1L, TimeUnit.SECONDS);
   }

   private void processQueue() {
      long now = System.currentTimeMillis();
      if (now - this.lastMinuteReset.get() >= 60000L) {
         this.requestsThisMinute.set(0);
         this.lastMinuteReset.set(now);
      }

      while(!this.messageQueue.isEmpty() && this.canSendNow()) {
         WebhookMessage message = (WebhookMessage)this.messageQueue.poll();
         if (message != null) {
            this.sendWebhookInternal(message);
         }
      }

   }

   private boolean canSendNow() {
      if (!this.rateLimitingEnabled) {
         return true;
      } else {
         long now = System.currentTimeMillis();
         if (this.requestsThisMinute.get() >= this.maxPerMinute) {
            return false;
         } else {
            return now - this.lastRequestTime.get() >= this.minDelayMs;
         }
      }
   }

   public void sendWebhook(String eventType, Map<String, String> placeholders) {
      if (this.enabled) {
         if (this.webhookConfig.getBoolean("events." + eventType + ".enabled", false)) {
            WebhookMessage message = new WebhookMessage(eventType, placeholders);
            if (this.asyncSending) {
               if (this.queueOnRateLimit) {
                  if (this.messageQueue.size() < this.maxQueueSize) {
                     this.messageQueue.offer(message);
                  } else if (this.logErrors) {
                     this.plugin.getLogger().warning("[Discord Webhook] Queue is full, dropping message: " + eventType);
                  }
               } else {
                  this.asyncExecutor.submit(() -> this.sendWebhookInternal(message));
               }
            } else {
               this.sendWebhookInternal(message);
            }

         }
      }
   }

   private void sendWebhookInternal(WebhookMessage message) {
      try {
         if (!this.canSendNow()) {
            if (this.queueOnRateLimit && message.retryCount < this.maxRetryAttempts) {
               this.messageQueue.offer(message);
            }

            return;
         }

         JsonObject payload = this.buildWebhookPayload(message.eventType, message.placeholders);
         boolean success = this.sendToDiscord(payload);
         if (success) {
            this.requestsThisMinute.incrementAndGet();
            this.lastRequestTime.set(System.currentTimeMillis());
            if (this.logSuccess) {
               this.plugin.getLogger().info("[Discord Webhook] Successfully sent: " + message.eventType);
            }
         } else if (this.retryEnabled && message.retryCount < this.maxRetryAttempts) {
            this.executor.schedule(() -> this.messageQueue.offer(message.withRetry()), this.retryDelayMs, TimeUnit.MILLISECONDS);
            if (this.logErrors) {
               this.plugin.getLogger().warning("[Discord Webhook] Failed to send, will retry (" + (message.retryCount + 1) + "/" + this.maxRetryAttempts + "): " + message.eventType);
            }
         }
      } catch (Exception e) {
         if (this.logErrors) {
            this.plugin.getLogger().warning("[Discord Webhook] Error sending webhook: " + e.getMessage());
         }
      }

   }

   private JsonObject buildWebhookPayload(String eventType, Map<String, String> placeholders) {
      JsonObject payload = new JsonObject();
      if (!this.username.isEmpty()) {
         payload.addProperty("username", this.username);
      }

      if (!this.avatarUrl.isEmpty()) {
         payload.addProperty("avatar_url", this.avatarUrl);
      }

      JsonObject embed = new JsonObject();
      ConfigurationSection eventConfig = this.webhookConfig.getConfigurationSection("events." + eventType);
      if (eventConfig == null) {
         return payload;
      } else {
         int color = eventConfig.getInt("color", 5814783);
         embed.addProperty("color", (Number)color);
         String title = eventConfig.getString("title", "Team Event");
         title = this.replacePlaceholders(title, placeholders);
         embed.addProperty("title", title);
         String description = eventConfig.getString("description", "");
         description = this.replacePlaceholders(description, placeholders);
         if (!description.isEmpty()) {
            embed.addProperty("description", description);
         }

         if (eventConfig.getBoolean("show_fields", false)) {
            JsonArray fields = new JsonArray();

            for(Map<?, ?> fieldMap : eventConfig.getMapList("fields")) {
               String fieldName = (String)fieldMap.get("name");
               String fieldValue = (String)fieldMap.get("value");
               boolean inline = fieldMap.containsKey("inline") ? (Boolean)fieldMap.get("inline") : false;
               fieldName = this.replacePlaceholders(fieldName, placeholders);
               fieldValue = this.replacePlaceholders(fieldValue, placeholders);
               JsonObject field = new JsonObject();
               field.addProperty("name", fieldName);
               field.addProperty("value", fieldValue);
               field.addProperty("inline", inline);
               fields.add((JsonElement)field);
            }

            if (fields.size() > 0) {
               embed.add("fields", fields);
            }
         }

         String footer = eventConfig.getString("footer", "");
         footer = this.replacePlaceholders(footer, placeholders);
         if (this.showServerIp) {
            String serverIp = Bukkit.getIp();
            int serverPort = Bukkit.getPort();
            if (!serverIp.isEmpty()) {
               footer = footer + " | " + serverIp + ":" + serverPort;
            }
         }

         if (!footer.isEmpty()) {
            JsonObject footerObj = new JsonObject();
            footerObj.addProperty("text", footer);
            embed.add("footer", footerObj);
         }

         if (eventConfig.getBoolean("timestamp", true)) {
            embed.addProperty("timestamp", Instant.now().toString());
         }

         JsonArray embeds = new JsonArray();
         embeds.add((JsonElement)embed);
         payload.add("embeds", embeds);
         return payload;
      }
   }

   private String replacePlaceholders(String text, Map<String, String> placeholders) {
      Map<String, String> allPlaceholders = new HashMap(placeholders);
      allPlaceholders.putIfAbsent("server", this.serverName);
      allPlaceholders.putIfAbsent("time", Instant.now().toString());

      for(Map.Entry<String, String> entry : allPlaceholders.entrySet()) {
         text = text.replace("{" + (String)entry.getKey() + "}", (CharSequence)entry.getValue());
      }

      return text;
   }

   private boolean sendToDiscord(JsonObject payload) {
      HttpURLConnection connection = null;

      boolean var18;
      try {
         URL url = new URL(this.webhookUrl);
         connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setRequestProperty("User-Agent", "JustTeams-Webhook/1.0");
         connection.setDoOutput(true);
         connection.setConnectTimeout(this.connectionTimeout);
         connection.setReadTimeout(this.readTimeout);
         OutputStream os = connection.getOutputStream();

         try {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
         } catch (Throwable var13) {
            if (os != null) {
               try {
                  os.close();
               } catch (Throwable var12) {
                  var13.addSuppressed(var12);
               }
            }

            throw var13;
         }

         if (os != null) {
            os.close();
         }

         int responseCode = connection.getResponseCode();
         if (responseCode != 204) {
            if (responseCode != 429) {
               if (responseCode < 400) {
                  var18 = true;
                  return var18;
               }

               if (this.logErrors) {
                  this.plugin.getLogger().warning("[Discord Webhook] Discord returned error code: " + responseCode);
               }

               var18 = false;
               return var18;
            }

            if (this.logErrors) {
               this.plugin.getLogger().warning("[Discord Webhook] Rate limited by Discord (429)");
            }

            var18 = false;
            return var18;
         }

         var18 = true;
      } catch (Exception e) {
         if (this.logErrors) {
            this.plugin.getLogger().warning("[Discord Webhook] Failed to send webhook: " + e.getMessage());
         }

         boolean os = false;
         return os;
      } finally {
         if (connection != null) {
            connection.disconnect();
         }

      }

      return var18;
   }

   public void shutdown() {
      this.executor.shutdown();
      this.asyncExecutor.shutdown();

      try {
         if (!this.executor.awaitTermination(5L, TimeUnit.SECONDS)) {
            this.executor.shutdownNow();
         }

         if (!this.asyncExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
            this.asyncExecutor.shutdownNow();
         }
      } catch (InterruptedException var2) {
         this.executor.shutdownNow();
         this.asyncExecutor.shutdownNow();
         Thread.currentThread().interrupt();
      }

      this.plugin.getLogger().info("[Discord Webhook] Webhook manager shut down");
   }

   public int getQueueSize() {
      return this.messageQueue.size();
   }

   public int getRequestsThisMinute() {
      return this.requestsThisMinute.get();
   }

   private static class WebhookMessage {
      final String eventType;
      final Map<String, String> placeholders;
      final int retryCount;
      final long scheduledTime;

      WebhookMessage(String eventType, Map<String, String> placeholders) {
         this(eventType, placeholders, 0, System.currentTimeMillis());
      }

      WebhookMessage(String eventType, Map<String, String> placeholders, int retryCount, long scheduledTime) {
         this.eventType = eventType;
         this.placeholders = placeholders;
         this.retryCount = retryCount;
         this.scheduledTime = scheduledTime;
      }

      WebhookMessage withRetry() {
         return new WebhookMessage(this.eventType, this.placeholders, this.retryCount + 1, System.currentTimeMillis());
      }
   }
}
