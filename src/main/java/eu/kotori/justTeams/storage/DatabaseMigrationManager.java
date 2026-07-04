package eu.kotori.justTeams.storage;

import eu.kotori.justTeams.JustTeams;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseMigrationManager {
   private final JustTeams plugin;
   private final DatabaseStorage databaseStorage;
   private static final int CURRENT_SCHEMA_VERSION = 5;

   public DatabaseMigrationManager(JustTeams plugin, DatabaseStorage databaseStorage) {
      this.plugin = plugin;
      this.databaseStorage = databaseStorage;
   }

   public boolean performMigration() {
      try {
         this.plugin.getLogger().info("Starting database migration process...");
         this.initializeSchemaVersion();
         int currentVersion = this.getCurrentSchemaVersion();
         this.plugin.getLogger().info("Current database schema version: " + currentVersion);
         if (currentVersion < 5) {
            this.plugin.getLogger().info("Database schema is outdated. Running migrations from version " + currentVersion + " to 5");
            if (!this.runMigrations(currentVersion)) {
               this.plugin.getLogger().severe("Database migration failed!");
               return false;
            }
         } else {
            this.plugin.getLogger().info("Database schema is up to date.");
         }

         if (!this.validateDatabaseIntegrity()) {
            this.plugin.getLogger().warning("Database integrity validation found issues, attempting to fix...");
            if (!this.repairDatabase()) {
               this.plugin.getLogger().severe("Database repair failed!");
               return false;
            }
         }

         this.plugin.getLogger().info("Database migration completed successfully!");
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Database migration failed with exception: " + e.getMessage(), e);
         return false;
      }
   }

   private void initializeSchemaVersion() throws SQLException {
      Connection conn = this.databaseStorage.getConnection();

      try {
         String createTableSQL = "CREATE TABLE IF NOT EXISTS schema_version (version INT PRIMARY KEY, applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, description VARCHAR(255))";
         Statement stmt = conn.createStatement();

         try {
            stmt.execute(createTableSQL);
         } catch (Throwable var16) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var14) {
                  var16.addSuppressed(var14);
               }
            }

            throw var16;
         }

         if (stmt != null) {
            stmt.close();
         }

         String checkVersionSQL = "SELECT COUNT(*) FROM schema_version";
         stmt = conn.createStatement();

         try {
            ResultSet rs = stmt.executeQuery(checkVersionSQL);

            try {
               if (rs.next() && rs.getInt(1) == 0) {
                  String insertVersionSQL = "INSERT INTO schema_version (version, description) VALUES (1, 'Initial schema version')";
                  Statement insertStmt = conn.createStatement();

                  try {
                     insertStmt.execute(insertVersionSQL);
                  } catch (Throwable var15) {
                     if (insertStmt != null) {
                        try {
                           insertStmt.close();
                        } catch (Throwable var13) {
                           var15.addSuppressed(var13);
                        }
                     }

                     throw var15;
                  }

                  if (insertStmt != null) {
                     insertStmt.close();
                  }
               }
            } catch (Throwable var17) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var12) {
                     var17.addSuppressed(var12);
                  }
               }

               throw var17;
            }

            if (rs != null) {
               rs.close();
            }
         } catch (Throwable var18) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var11) {
                  var18.addSuppressed(var11);
               }
            }

            throw var18;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var19) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var10) {
               var19.addSuppressed(var10);
            }
         }

         throw var19;
      }

      if (conn != null) {
         conn.close();
      }

   }

   private int getCurrentSchemaVersion() {
      try {
         Connection conn = this.databaseStorage.getConnection();

         int var5;
         label130: {
            label118: {
               byte var14;
               try {
                  label126: {
                     Statement stmt;
                     label120: {
                        if (conn == null || conn.isClosed()) {
                           this.plugin.getLogger().warning("Database connection is null or closed");
                           var14 = 1;
                           break label126;
                        }

                        String sql = "SELECT MAX(version) FROM schema_version";
                        stmt = conn.createStatement();

                        try {
                           label121: {
                              ResultSet rs = stmt.executeQuery(sql);

                              label102: {
                                 try {
                                    if (rs.next()) {
                                       var5 = rs.getInt(1);
                                       break label102;
                                    }
                                 } catch (Throwable var10) {
                                    if (rs != null) {
                                       try {
                                          rs.close();
                                       } catch (Throwable var9) {
                                          var10.addSuppressed(var9);
                                       }
                                    }

                                    throw var10;
                                 }

                                 if (rs != null) {
                                    rs.close();
                                 }
                                 break label121;
                              }

                              if (rs != null) {
                                 rs.close();
                              }
                              break label120;
                           }
                        } catch (Throwable var11) {
                           if (stmt != null) {
                              try {
                                 stmt.close();
                              } catch (Throwable var8) {
                                 var11.addSuppressed(var8);
                              }
                           }

                           throw var11;
                        }

                        if (stmt != null) {
                           stmt.close();
                        }
                        break label118;
                     }

                     if (stmt != null) {
                        stmt.close();
                     }
                     break label130;
                  }
               } catch (Throwable var12) {
                  if (conn != null) {
                     try {
                        conn.close();
                     } catch (Throwable var7) {
                        var12.addSuppressed(var7);
                     }
                  }

                  throw var12;
               }

               if (conn != null) {
                  conn.close();
               }

               return var14;
            }

            if (conn != null) {
               conn.close();
            }

            return 1;
         }

         if (conn != null) {
            conn.close();
         }

         return var5;
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Could not get schema version, assuming version 1: " + e.getMessage());
         return 1;
      }
   }

   private boolean runMigrations(int fromVersion) {
      try {
         for(Migration migration : this.getMigrations()) {
            if (migration.getVersion() > fromVersion && migration.getVersion() <= 5) {
               Logger var10000 = this.plugin.getLogger();
               int var10001 = migration.getVersion();
               var10000.info("Running migration " + var10001 + ": " + migration.getDescription());
               if (!migration.execute(this.databaseStorage)) {
                  this.plugin.getLogger().severe("Migration " + migration.getVersion() + " failed!");
                  return false;
               }

               this.recordMigration(migration.getVersion(), migration.getDescription());
            }
         }

         return true;
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Migration execution failed: " + e.getMessage(), e);
         return false;
      }
   }

   private void recordMigration(int version, String description) throws SQLException {
      Connection conn = this.databaseStorage.getConnection();

      try {
         String sql = "INSERT INTO schema_version (version, description) VALUES (?, ?)";
         PreparedStatement stmt = conn.prepareStatement(sql);

         try {
            stmt.setInt(1, version);
            stmt.setString(2, description);
            stmt.executeUpdate();
         } catch (Throwable var10) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (stmt != null) {
            stmt.close();
         }
      } catch (Throwable var11) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var8) {
               var11.addSuppressed(var8);
            }
         }

         throw var11;
      }

      if (conn != null) {
         conn.close();
      }

   }

   private List<Migration> getMigrations() {
      List<Migration> migrations = new ArrayList();
      migrations.add(new Migration(2, "Add member permission columns", this::migration2_AddMemberPermissions));
      migrations.add(new Migration(3, "Add blacklist table and fix column issues", this::migration3_AddBlacklistAndFixColumns));
      migrations.add(new Migration(4, "Add cross-server tables and missing features", this::migration4_AddCrossServerTables));
      migrations.add(new Migration(5, "Add tier column to teams and can_promote_to_co_owner to members", this::migration5_AddTierAndPromoteCoOwnerPermission));
      return migrations;
   }

   private boolean migration5_AddTierAndPromoteCoOwnerPermission(DatabaseStorage storage) {
      try {
         if (!this.hasColumn("donut_teams", "tier")) {
            this.addColumnSafely("donut_teams", "tier", "INT DEFAULT 1");
         }

         if (!this.hasColumn("donut_team_members", "can_promote_to_co_owner")) {
            this.addColumnSafely("donut_team_members", "can_promote_to_co_owner", "BOOLEAN DEFAULT FALSE");
         }

         return true;
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Migration 5 failed: " + e.getMessage(), e);
         return false;
      }
   }

   private boolean migration2_AddMemberPermissions(DatabaseStorage storage) {
      try {
         String[] columns = new String[]{"can_edit_members", "can_edit_co_owners", "can_kick_members", "can_promote_members", "can_demote_members"};

         for(String column : columns) {
            if (!this.hasColumn("donut_team_members", column)) {
               this.addColumnSafely("donut_team_members", column, "BOOLEAN DEFAULT false");
            }
         }

         this.updateExistingMemberPermissions();
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Migration 2 failed: " + e.getMessage(), e);
         return false;
      }
   }

   private boolean migration3_AddBlacklistAndFixColumns(DatabaseStorage storage) {
      try {
         this.createBlacklistTable();
         String[] teamColumns = new String[]{"is_public"};

         for(String column : teamColumns) {
            if (!this.hasColumn("donut_teams", column)) {
               this.addColumnSafely("donut_teams", column, "BOOLEAN DEFAULT false");
            }
         }

         return true;
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Migration 3 failed: " + e.getMessage(), e);
         return false;
      }
   }

   private boolean migration4_AddCrossServerTables(DatabaseStorage storage) {
      try {
         this.createCrossServerTables();
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Migration 4 failed: " + e.getMessage(), e);
         return false;
      }
   }

   private void createCrossServerTables() throws SQLException {
      Connection conn = this.databaseStorage.getConnection();

      try {
         String createUpdatesTable = "CREATE TABLE IF NOT EXISTS donut_cross_server_updates (id INT AUTO_INCREMENT PRIMARY KEY, team_id INT NOT NULL, update_type VARCHAR(50) NOT NULL, player_uuid VARCHAR(36) NOT NULL, server_name VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
         Statement stmt = conn.createStatement();

         try {
            stmt.execute(createUpdatesTable);
         } catch (Throwable var26) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var19) {
                  var26.addSuppressed(var19);
               }
            }

            throw var26;
         }

         if (stmt != null) {
            stmt.close();
         }

         String createMessagesTable = "CREATE TABLE IF NOT EXISTS donut_cross_server_messages (id INT AUTO_INCREMENT PRIMARY KEY, team_id INT NOT NULL, player_uuid VARCHAR(36) NOT NULL, message TEXT NOT NULL, server_name VARCHAR(64) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
         stmt = conn.createStatement();

         try {
            stmt.execute(createMessagesTable);
         } catch (Throwable var25) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var18) {
                  var25.addSuppressed(var18);
               }
            }

            throw var25;
         }

         if (stmt != null) {
            stmt.close();
         }

         String createHomesTable = "CREATE TABLE IF NOT EXISTS donut_team_homes (team_id INT PRIMARY KEY, location VARCHAR(255) NOT NULL, server_name VARCHAR(64) NOT NULL, FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
         stmt = conn.createStatement();

         try {
            stmt.execute(createHomesTable);
         } catch (Throwable var24) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var17) {
                  var24.addSuppressed(var17);
               }
            }

            throw var24;
         }

         if (stmt != null) {
            stmt.close();
         }

         String createWarpsTable = "CREATE TABLE IF NOT EXISTS donut_team_warps (id INT AUTO_INCREMENT PRIMARY KEY, team_id INT NOT NULL, warp_name VARCHAR(32) NOT NULL, location VARCHAR(255) NOT NULL, server_name VARCHAR(64) NOT NULL, password VARCHAR(64), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY team_warp (team_id, warp_name), FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
         stmt = conn.createStatement();

         try {
            stmt.execute(createWarpsTable);
         } catch (Throwable var23) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var16) {
                  var23.addSuppressed(var16);
               }
            }

            throw var23;
         }

         if (stmt != null) {
            stmt.close();
         }

         String createJoinRequestsTable = "CREATE TABLE IF NOT EXISTS donut_join_requests (id INT AUTO_INCREMENT PRIMARY KEY, team_id INT NOT NULL, player_uuid VARCHAR(36) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY team_player (team_id, player_uuid), FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
         stmt = conn.createStatement();

         try {
            stmt.execute(createJoinRequestsTable);
         } catch (Throwable var22) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var15) {
                  var22.addSuppressed(var15);
               }
            }

            throw var22;
         }

         if (stmt != null) {
            stmt.close();
         }

         String createEnderChestLocksTable = "CREATE TABLE IF NOT EXISTS donut_ender_chest_locks (team_id INT PRIMARY KEY, server_identifier VARCHAR(255) NOT NULL, lock_time TIMESTAMP NOT NULL, FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
         stmt = conn.createStatement();

         try {
            stmt.execute(createEnderChestLocksTable);
         } catch (Throwable var21) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var14) {
                  var21.addSuppressed(var14);
               }
            }

            throw var21;
         }

         if (stmt != null) {
            stmt.close();
         }

         String createTeamLocksTable = "CREATE TABLE IF NOT EXISTS donut_team_locks (team_id INT PRIMARY KEY, server_identifier VARCHAR(255) NOT NULL, lock_time TIMESTAMP NOT NULL, FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
         stmt = conn.createStatement();

         try {
            stmt.execute(createTeamLocksTable);
         } catch (Throwable var20) {
            if (stmt != null) {
               try {
                  stmt.close();
               } catch (Throwable var13) {
                  var20.addSuppressed(var13);
               }
            }

            throw var20;
         }

         if (stmt != null) {
            stmt.close();
         }

         this.plugin.getLogger().info("Cross-server tables created/verified successfully.");
      } catch (Throwable var27) {
         if (conn != null) {
            try {
               conn.close();
            } catch (Throwable var12) {
               var27.addSuppressed(var12);
            }
         }

         throw var27;
      }

      if (conn != null) {
         conn.close();
      }

   }

   private void addColumnSafely(String tableName, String columnName, String columnDefinition) throws SQLException {
      try {
         Connection conn = this.databaseStorage.getConnection();

         try {
            String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
            Statement stmt = conn.createStatement();

            try {
               stmt.execute(sql);
               this.plugin.getLogger().info("Added column " + columnName + " to " + tableName);
            } catch (Throwable var11) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var12) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var9) {
                  var12.addSuppressed(var9);
               }
            }

            throw var12;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         if (e.getErrorCode() != 42121 && e.getErrorCode() != 1060 && !"42S21".equals(e.getSQLState()) && !e.getMessage().toLowerCase().contains("already exists") && !e.getMessage().toLowerCase().contains("duplicate column")) {
            throw e;
         }

         this.plugin.getLogger().info("Column " + columnName + " already exists in " + tableName + ", skipping.");
      }

   }

   private boolean hasColumn(String tableName, String columnName) throws SQLException {
      Connection conn = this.databaseStorage.getConnection();

      boolean var6;
      label90: {
         try {
            DatabaseMetaData md = conn.getMetaData();
            ResultSet rs = md.getColumns((String)null, (String)null, tableName, columnName);

            label92: {
               try {
                  if (!rs.next()) {
                     break label92;
                  }

                  var6 = true;
               } catch (Throwable var12) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var10) {
                        var12.addSuppressed(var10);
                     }
                  }

                  throw var12;
               }

               if (rs != null) {
                  rs.close();
               }
               break label90;
            }

            if (rs != null) {
               rs.close();
            }

            rs = md.getColumns((String)null, (String)null, tableName.toUpperCase(), columnName.toUpperCase());

            try {
               var6 = rs.next();
            } catch (Throwable var11) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var9) {
                     var11.addSuppressed(var9);
                  }
               }

               throw var11;
            }

            if (rs != null) {
               rs.close();
            }
         } catch (Throwable var13) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var8) {
                  var13.addSuppressed(var8);
               }
            }

            throw var13;
         }

         if (conn != null) {
            conn.close();
         }

         return var6;
      }

      if (conn != null) {
         conn.close();
      }

      return var6;
   }

   private void updateExistingMemberPermissions() {
      try {
         Connection conn = this.databaseStorage.getConnection();

         try {
            String sql = "UPDATE donut_team_members SET can_edit_members = false, can_edit_co_owners = false, can_kick_members = false, can_promote_members = false, can_demote_members = false WHERE can_edit_members IS NULL OR can_edit_co_owners IS NULL";
            Statement stmt = conn.createStatement();

            try {
               int updated = stmt.executeUpdate(sql);
               if (updated > 0) {
                  this.plugin.getLogger().info("Updated " + updated + " member permission records with default values.");
               }
            } catch (Throwable var8) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var9) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var6) {
                  var9.addSuppressed(var6);
               }
            }

            throw var9;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Could not update existing member permissions: " + e.getMessage());
      }

   }

   private void createBlacklistTable() {
      try {
         Connection conn = this.databaseStorage.getConnection();

         try {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS donut_team_blacklist (id INT AUTO_INCREMENT PRIMARY KEY, team_id INT NOT NULL, player_uuid VARCHAR(36) NOT NULL, player_name VARCHAR(16) NOT NULL, reason TEXT, blacklisted_by_uuid VARCHAR(36) NOT NULL, blacklisted_by_name VARCHAR(16) NOT NULL, blacklisted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(team_id, player_uuid), FOREIGN KEY (team_id) REFERENCES donut_teams(id) ON DELETE CASCADE)";
            Statement stmt = conn.createStatement();

            try {
               stmt.execute(createTableSQL);
               this.plugin.getLogger().info("Blacklist table created/verified successfully.");
            } catch (Throwable var8) {
               if (stmt != null) {
                  try {
                     stmt.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (stmt != null) {
               stmt.close();
            }
         } catch (Throwable var9) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var6) {
                  var9.addSuppressed(var6);
               }
            }

            throw var9;
         }

         if (conn != null) {
            conn.close();
         }
      } catch (SQLException e) {
         this.plugin.getLogger().warning("Could not create blacklist table: " + e.getMessage());
      }

   }

   private boolean validateDatabaseIntegrity() {
      try {
         this.plugin.getLogger().info("Validating database integrity...");
         String[] requiredTables = new String[]{"donut_teams", "donut_team_members", "donut_team_homes", "donut_team_warps", "donut_join_requests", "donut_servers", "donut_pending_teleports", "donut_ender_chest_locks", "donut_cross_server_updates", "donut_cross_server_messages", "donut_team_blacklist", "donut_team_locks", "schema_version"};

         for(String table : requiredTables) {
            if (!this.tableExists(table)) {
               this.plugin.getLogger().warning("Required table " + table + " is missing!");
               return false;
            }
         }

         if (!this.validateTableColumns()) {
            return false;
         } else {
            this.plugin.getLogger().info("Database integrity validation passed.");
            return true;
         }
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.WARNING, "Database integrity validation failed: " + e.getMessage(), e);
         return false;
      }
   }

   private boolean tableExists(String tableName) throws SQLException {
      Connection conn = this.databaseStorage.getConnection();

      boolean var5;
      label90: {
         try {
            DatabaseMetaData md = conn.getMetaData();
            ResultSet rs = md.getTables((String)null, (String)null, tableName, (String[])null);

            label92: {
               try {
                  if (!rs.next()) {
                     break label92;
                  }

                  var5 = true;
               } catch (Throwable var11) {
                  if (rs != null) {
                     try {
                        rs.close();
                     } catch (Throwable var9) {
                        var11.addSuppressed(var9);
                     }
                  }

                  throw var11;
               }

               if (rs != null) {
                  rs.close();
               }
               break label90;
            }

            if (rs != null) {
               rs.close();
            }

            rs = md.getTables((String)null, (String)null, tableName.toUpperCase(), (String[])null);

            try {
               var5 = rs.next();
            } catch (Throwable var10) {
               if (rs != null) {
                  try {
                     rs.close();
                  } catch (Throwable var8) {
                     var10.addSuppressed(var8);
                  }
               }

               throw var10;
            }

            if (rs != null) {
               rs.close();
            }
         } catch (Throwable var12) {
            if (conn != null) {
               try {
                  conn.close();
               } catch (Throwable var7) {
                  var12.addSuppressed(var7);
               }
            }

            throw var12;
         }

         if (conn != null) {
            conn.close();
         }

         return var5;
      }

      if (conn != null) {
         conn.close();
      }

      return var5;
   }

   private boolean validateTableColumns() throws SQLException {
      String[] teamColumns = new String[]{"id", "name", "tag", "owner_uuid", "pvp_enabled", "is_public", "balance", "kills", "deaths"};

      for(String column : teamColumns) {
         if (!this.hasColumn("donut_teams", column)) {
            this.plugin.getLogger().warning("Required column " + column + " is missing from donut_teams table!");
            return false;
         }
      }

      String[] memberColumns = new String[]{"player_uuid", "team_id", "role", "join_date", "can_withdraw", "can_use_enderchest", "can_set_home", "can_use_home"};

      for(String column : memberColumns) {
         if (!this.hasColumn("donut_team_members", column)) {
            this.plugin.getLogger().warning("Required column " + column + " is missing from donut_team_members table!");
            return false;
         }
      }

      return true;
   }

   private boolean repairDatabase() {
      try {
         this.plugin.getLogger().info("Attempting to repair database...");
         if (!this.tableExists("donut_teams")) {
            this.plugin.getLogger().info("Recreating donut_teams table...");
         }

         if (!this.hasColumn("donut_teams", "is_public")) {
            this.plugin.getLogger().info("Adding missing is_public column...");
            this.addColumnSafely("donut_teams", "is_public", "BOOLEAN DEFAULT false");
         }

         this.plugin.getLogger().info("Database repair completed.");
         return true;
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Database repair failed: " + e.getMessage(), e);
         return false;
      }
   }

   private static class Migration {
      private final int version;
      private final String description;
      private final MigrationExecutor executor;

      public Migration(int version, String description, MigrationExecutor executor) {
         this.version = version;
         this.description = description;
         this.executor = executor;
      }

      public int getVersion() {
         return this.version;
      }

      public String getDescription() {
         return this.description;
      }

      public boolean execute(DatabaseStorage storage) throws Exception {
         return this.executor.execute(storage);
      }
   }

   private interface MigrationExecutor {
      boolean execute(DatabaseStorage var1) throws Exception;
   }
}
