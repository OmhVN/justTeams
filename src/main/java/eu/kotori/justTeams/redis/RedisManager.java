package eu.kotori.justTeams.redis;

import eu.kotori.justTeams.JustTeams;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RedisManager {
   private final JustTeams plugin;
   private JedisPool jedisPool;
   private ExecutorService executorService;
   private TeamMessageSubscriber messageSubscriber;
   private TeamUpdateSubscriber updateSubscriber;
   private volatile boolean enabled = false;
   private volatile boolean connected = false;
   private static final String CHANNEL_TEAM_CHAT = "justteams:chat";
   private static final String CHANNEL_TEAM_UPDATES = "justteams:updates";
   private static final String CHANNEL_TEAM_MESSAGES = "justteams:messages";

   public RedisManager(JustTeams plugin) {
      this.plugin = plugin;
   }

   public void initialize() {
      if (!this.plugin.getConfigManager().isRedisEnabled()) {
         this.plugin.getLogger().info("Redis is disabled in configuration");
         this.enabled = false;
      } else {
         try {
            String host = this.plugin.getConfigManager().getRedisHost();
            int port = this.plugin.getConfigManager().getRedisPort();
            String password = this.plugin.getConfigManager().getRedisPassword();
            boolean useSSL = this.plugin.getConfigManager().isRedisSslEnabled();
            int timeout = this.plugin.getConfigManager().getRedisTimeout();
            this.plugin.getLogger().info("Initializing Redis connection to " + host + ":" + port + "...");
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(2);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setMinEvictableIdleTime(Duration.ofSeconds(60L));
            poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30L));
            poolConfig.setBlockWhenExhausted(true);
            poolConfig.setMaxWait(Duration.ofSeconds(2L));
            if (password != null && !password.isEmpty()) {
               this.jedisPool = new JedisPool(poolConfig, host, port, timeout, password, useSSL);
            } else {
               this.jedisPool = new JedisPool(poolConfig, host, port, timeout, useSSL);
            }

            Jedis jedis = this.jedisPool.getResource();

            try {
               String pong = jedis.ping();
               if (!"PONG".equals(pong)) {
                  throw new JedisException("Unexpected PING response: " + pong);
               }

               this.connected = true;
               this.enabled = true;
               this.plugin.getLogger().info("✓ Redis connection successful! PING returned PONG");
            } catch (Throwable var11) {
               if (jedis != null) {
                  try {
                     jedis.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (jedis != null) {
               jedis.close();
            }

            this.executorService = Executors.newFixedThreadPool(2, (r) -> {
               Thread t = new Thread(r, "JustTeams-Redis-Subscriber");
               t.setDaemon(true);
               return t;
            });
            this.startSubscribers();
            this.plugin.getLogger().info("✓ Redis Pub/Sub initialized successfully");
            this.plugin.getLogger().info("  Channels: justteams:chat, justteams:updates, justteams:messages");
            this.plugin.getLogger().info("  Mode: INSTANT (< 100ms delivery)");
         } catch (Exception e) {
            this.plugin.getLogger().severe("✗ Failed to initialize Redis connection: " + e.getMessage());
            this.plugin.getLogger().severe("  Will fall back to MySQL polling mode");
            this.enabled = false;
            this.connected = false;
            if (this.jedisPool != null) {
               this.jedisPool.close();
               this.jedisPool = null;
            }
         }

      }
   }

   private void startSubscribers() {
      this.messageSubscriber = new TeamMessageSubscriber(this.plugin);
      this.executorService.submit(() -> {
         while(true) {
            if (this.enabled && !Thread.currentThread().isInterrupted()) {
               try {
                  Jedis jedis = this.jedisPool.getResource();

                  try {
                     this.plugin.getLogger().info("Starting Redis message subscriber...");
                     jedis.subscribe((JedisPubSub)this.messageSubscriber, new String[]{"justteams:chat", "justteams:messages"});
                  } catch (Throwable var5) {
                     if (jedis != null) {
                        try {
                           jedis.close();
                        } catch (Throwable var4) {
                           var5.addSuppressed(var4);
                        }
                     }

                     throw var5;
                  }

                  if (jedis != null) {
                     jedis.close();
                  }
                  continue;
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Redis message subscriber disconnected: " + e.getMessage());
                  if (!this.enabled) {
                     continue;
                  }

                  try {
                     Thread.sleep(5000L);
                     continue;
                  } catch (InterruptedException var6) {
                  }
               }
            }

            return;
         }
      });
      this.updateSubscriber = new TeamUpdateSubscriber(this.plugin);
      this.executorService.submit(() -> {
         while(true) {
            if (this.enabled && !Thread.currentThread().isInterrupted()) {
               try {
                  Jedis jedis = this.jedisPool.getResource();

                  try {
                     this.plugin.getLogger().info("Starting Redis update subscriber...");
                     jedis.subscribe((JedisPubSub)this.updateSubscriber, new String[]{"justteams:updates"});
                  } catch (Throwable var5) {
                     if (jedis != null) {
                        try {
                           jedis.close();
                        } catch (Throwable var4) {
                           var5.addSuppressed(var4);
                        }
                     }

                     throw var5;
                  }

                  if (jedis != null) {
                     jedis.close();
                  }
                  continue;
               } catch (Exception e) {
                  this.plugin.getLogger().warning("Redis update subscriber disconnected: " + e.getMessage());
                  if (!this.enabled) {
                     continue;
                  }

                  try {
                     Thread.sleep(5000L);
                     continue;
                  } catch (InterruptedException var6) {
                  }
               }
            }

            return;
         }
      });
   }

   public CompletableFuture<Boolean> publishTeamChat(int teamId, String playerUuid, String playerName, String message) {
      return !this.isAvailable() ? CompletableFuture.completedFuture(false) : CompletableFuture.supplyAsync(() -> {
         try {
            Jedis jedis = this.jedisPool.getResource();

            Boolean var9;
            try {
               String payload = String.format("%d|%s|%s|%s|%d", teamId, playerUuid, playerName, message, System.currentTimeMillis());
               long subscribers = jedis.publish("justteams:chat", payload);
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info("Published team chat to " + subscribers + " servers (Redis)");
               }

               var9 = subscribers > 0L;
            } catch (Throwable var11) {
               if (jedis != null) {
                  try {
                     jedis.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (jedis != null) {
               jedis.close();
            }

            return var9;
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to publish team chat via Redis: " + e.getMessage());
            return false;
         }
      });
   }

   public CompletableFuture<Boolean> publishTeamMessage(int teamId, String playerUuid, String playerName, String message) {
      return !this.isAvailable() ? CompletableFuture.completedFuture(false) : CompletableFuture.supplyAsync(() -> {
         try {
            Jedis jedis = this.jedisPool.getResource();

            Boolean var9;
            try {
               String payload = String.format("%d|%s|%s|%s|%d", teamId, playerUuid, playerName, message, System.currentTimeMillis());
               long subscribers = jedis.publish("justteams:messages", payload);
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info("Published team message to " + subscribers + " servers (Redis)");
               }

               var9 = subscribers > 0L;
            } catch (Throwable var11) {
               if (jedis != null) {
                  try {
                     jedis.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (jedis != null) {
               jedis.close();
            }

            return var9;
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to publish team message via Redis: " + e.getMessage());
            return false;
         }
      });
   }

   public CompletableFuture<Boolean> publishTeamUpdate(int teamId, String updateType, String playerUuid, String data) {
      return !this.isAvailable() ? CompletableFuture.completedFuture(false) : CompletableFuture.supplyAsync(() -> {
         try {
            Jedis jedis = this.jedisPool.getResource();

            Boolean var9;
            try {
               String payload = String.format("%d|%s|%s|%s|%d", teamId, updateType, playerUuid, data, System.currentTimeMillis());
               long subscribers = jedis.publish("justteams:updates", payload);
               if (this.plugin.getConfigManager().isDebugEnabled()) {
                  this.plugin.getLogger().info("Published " + updateType + " to " + subscribers + " servers (Redis)");
               }

               var9 = subscribers > 0L;
            } catch (Throwable var11) {
               if (jedis != null) {
                  try {
                     jedis.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (jedis != null) {
               jedis.close();
            }

            return var9;
         } catch (Exception e) {
            this.plugin.getLogger().warning("Failed to publish team update via Redis: " + e.getMessage());
            return false;
         }
      });
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isAvailable() {
      return this.enabled && this.connected && this.jedisPool != null && !this.jedisPool.isClosed();
   }

   public String getConnectionStatus() {
      if (!this.enabled) {
         return "DISABLED";
      } else if (!this.connected) {
         return "DISCONNECTED";
      } else if (this.jedisPool != null && !this.jedisPool.isClosed()) {
         try {
            Jedis jedis = this.jedisPool.getResource();

            String var2;
            try {
               jedis.ping();
               var2 = "CONNECTED";
            } catch (Throwable var5) {
               if (jedis != null) {
                  try {
                     jedis.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (jedis != null) {
               jedis.close();
            }

            return var2;
         } catch (Exception e) {
            return "ERROR: " + e.getMessage();
         }
      } else {
         return "CLOSED";
      }
   }

   public void shutdown() {
      this.plugin.getLogger().info("Shutting down Redis connection...");
      this.enabled = false;
      this.connected = false;
      if (this.messageSubscriber != null) {
         try {
            this.messageSubscriber.unsubscribe();
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error unsubscribing message subscriber: " + e.getMessage());
         }
      }

      if (this.updateSubscriber != null) {
         try {
            this.updateSubscriber.unsubscribe();
         } catch (Exception e) {
            this.plugin.getLogger().warning("Error unsubscribing update subscriber: " + e.getMessage());
         }
      }

      if (this.executorService != null) {
         this.executorService.shutdownNow();
      }

      if (this.jedisPool != null && !this.jedisPool.isClosed()) {
         this.jedisPool.close();
      }

      this.plugin.getLogger().info("Redis connection closed");
   }

   public String getPoolStats() {
      return this.jedisPool != null && !this.jedisPool.isClosed() ? String.format("Pool: %d active, %d idle, %d waiters", this.jedisPool.getNumActive(), this.jedisPool.getNumIdle(), this.jedisPool.getNumWaiters()) : "Pool: CLOSED";
   }
}
