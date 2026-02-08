package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.data.model.LoreLocation;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for lore_location spatial data.
 * Uses HikariCP connection pool with async CompletableFuture pattern.
 */
public class LocationRepository implements ILocationRepository {
    @SuppressWarnings("unused")
    private final RVNKLore plugin;
    private final LogManager logger;
    private final DatabaseConnection dbConnection;

    public LocationRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.plugin = plugin;
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "LocationRepository");
    }

    private String t(String baseName) {
        return dbConnection.table(baseName);
    }

    @Override
    public CompletableFuture<LoreLocation> save(LoreLocation location) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + t("lore_location") +
                    " (entry_id, world, x, y, z, location_type, label) VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, location.getEntryId());
                stmt.setString(2, location.getWorld());
                stmt.setDouble(3, location.getX());
                stmt.setDouble(4, location.getY());
                stmt.setDouble(5, location.getZ());
                stmt.setString(6, location.getLocationType() != null ? location.getLocationType() : "PRIMARY");
                stmt.setString(7, location.getLabel());

                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            location.setId(keys.getInt(1));
                        }
                    }
                    location.setCreatedAt(Instant.now());
                    return location;
                }
            } catch (SQLException e) {
                logger.error("Failed to save lore location for entry: " + location.getEntryId(), e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<LoreLocation>> findByEntryId(String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            List<LoreLocation> locations = new ArrayList<>();
            String sql = "SELECT * FROM " + t("lore_location") +
                    " WHERE entry_id = ? ORDER BY location_type, id";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, entryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        locations.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to find locations for entry: " + entryId, e);
            }
            return locations;
        });
    }

    @Override
    public CompletableFuture<LoreLocation> findPrimaryByEntryId(String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_location") +
                    " WHERE entry_id = ? AND location_type = 'PRIMARY' LIMIT 1";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, entryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to find primary location for entry: " + entryId, e);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<LoreLocation>> findNearby(String world, double x, double z, double radius) {
        return CompletableFuture.supplyAsync(() -> {
            List<LoreLocation> locations = new ArrayList<>();
            // Bounding box query, then filter by actual distance in Java
            String sql = "SELECT * FROM " + t("lore_location") +
                    " WHERE world = ? AND x BETWEEN ? AND ? AND z BETWEEN ? AND ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, world);
                stmt.setDouble(2, x - radius);
                stmt.setDouble(3, x + radius);
                stmt.setDouble(4, z - radius);
                stmt.setDouble(5, z + radius);

                double radiusSq = radius * radius;
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        LoreLocation loc = mapRow(rs);
                        // Euclidean distance filter (2D, ignoring Y)
                        double dx = loc.getX() - x;
                        double dz = loc.getZ() - z;
                        if (dx * dx + dz * dz <= radiusSq) {
                            locations.add(loc);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Failed to find nearby locations at " + world + " " + x + "," + z, e);
            }
            return locations;
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteByEntryId(String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("lore_location") + " WHERE entry_id = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, entryId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to delete locations for entry: " + entryId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteById(int locationId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("lore_location") + " WHERE id = ?";

            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setInt(1, locationId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to delete location: " + locationId, e);
                return false;
            }
        });
    }

    private LoreLocation mapRow(ResultSet rs) throws SQLException {
        LoreLocation loc = new LoreLocation();
        loc.setId(rs.getInt("id"));
        loc.setEntryId(rs.getString("entry_id"));
        loc.setWorld(rs.getString("world"));
        loc.setX(rs.getDouble("x"));
        loc.setY(rs.getDouble("y"));
        loc.setZ(rs.getDouble("z"));
        loc.setLocationType(rs.getString("location_type"));
        loc.setLabel(rs.getString("label"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            loc.setCreatedAt(ts.toInstant());
        }
        return loc;
    }
}
