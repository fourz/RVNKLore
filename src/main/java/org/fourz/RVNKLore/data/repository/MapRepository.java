package org.fourz.RVNKLore.data.repository;

import org.fourz.RVNKLore.RVNKLore;
import org.fourz.RVNKLore.data.DatabaseConnection;
import org.fourz.RVNKLore.lore.map.LoreMap;
import org.fourz.RVNKLore.lore.map.MapSubtype;
import org.fourz.rvnkcore.util.log.LogManager;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository implementation for lore_map cross-server map storage.
 * Uses HikariCP connection pool with async CompletableFuture pattern.
 */
public class MapRepository implements IMapRepository {
    private final LogManager logger;
    private final DatabaseConnection dbConnection;

    public MapRepository(RVNKLore plugin, DatabaseConnection dbConnection) {
        this.dbConnection = dbConnection;
        this.logger = LogManager.getInstance(plugin, "MapRepository");
    }

    private String t(String baseName) {
        return dbConnection.table(baseName);
    }

    @Override
    public CompletableFuture<Optional<LoreMap>> save(LoreMap map) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO " + t("lore_map") +
                    " (lore_entry_id, map_subtype, center_x, center_z, scale, world_name, dimension, pixel_data, created_by)" +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, map.getLoreEntryId());
                stmt.setString(2, map.getSubtype() != null ? map.getSubtype().name() : MapSubtype.ATLAS.name());
                stmt.setInt(3, map.getCenterX());
                stmt.setInt(4, map.getCenterZ());
                stmt.setInt(5, map.getScale());
                stmt.setString(6, map.getWorldName());
                stmt.setString(7, map.getDimension());
                stmt.setString(8, map.getPixelData());
                stmt.setString(9, map.getCreatedBy());

                int affected = stmt.executeUpdate();
                if (affected > 0) {
                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (keys.next()) {
                            map.setId(keys.getInt(1));
                        }
                    }
                    map.setCreatedAt(Instant.now());
                    return Optional.of(map);
                }
            } catch (SQLException e) {
                logger.error("Failed to save lore map", e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<Optional<LoreMap>> findById(int mapId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM " + t("lore_map") + " WHERE id = ?";
            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mapId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return Optional.of(mapFromRow(rs));
                }
            } catch (SQLException e) {
                logger.error("Failed to find lore map by id: " + mapId, e);
            }
            return Optional.empty();
        });
    }

    @Override
    public CompletableFuture<List<LoreMap>> findByEntryId(String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            List<LoreMap> results = new ArrayList<>();
            String sql = "SELECT * FROM " + t("lore_map") + " WHERE lore_entry_id = ? ORDER BY id ASC";
            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entryId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) results.add(mapFromRow(rs));
                }
            } catch (SQLException e) {
                logger.error("Failed to find lore maps for entry: " + entryId, e);
            }
            return results;
        });
    }

    @Override
    public CompletableFuture<List<LoreMap>> findBySubtype(MapSubtype subtype) {
        return CompletableFuture.supplyAsync(() -> {
            List<LoreMap> results = new ArrayList<>();
            String sql = "SELECT * FROM " + t("lore_map") + " WHERE map_subtype = ? ORDER BY id ASC";
            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, subtype.name());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) results.add(mapFromRow(rs));
                }
            } catch (SQLException e) {
                logger.error("Failed to find lore maps by subtype: " + subtype, e);
            }
            return results;
        });
    }

    @Override
    public CompletableFuture<Boolean> updatePixelData(int mapId, String base64PixelData) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE " + t("lore_map") + " SET pixel_data = ? WHERE id = ?";
            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, base64PixelData);
                stmt.setInt(2, mapId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to update pixel data for map: " + mapId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteById(int mapId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("lore_map") + " WHERE id = ?";
            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, mapId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                logger.error("Failed to delete lore map: " + mapId, e);
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> deleteByEntryId(String entryId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM " + t("lore_map") + " WHERE lore_entry_id = ?";
            try (Connection conn = dbConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, entryId);
                return stmt.executeUpdate() >= 0;
            } catch (SQLException e) {
                logger.error("Failed to delete lore maps for entry: " + entryId, e);
                return false;
            }
        });
    }

    private LoreMap mapFromRow(ResultSet rs) throws SQLException {
        LoreMap map = new LoreMap();
        map.setId(rs.getInt("id"));
        map.setLoreEntryId(rs.getString("lore_entry_id"));
        map.setSubtype(MapSubtype.fromString(rs.getString("map_subtype")));
        map.setCenterX(rs.getInt("center_x"));
        map.setCenterZ(rs.getInt("center_z"));
        map.setScale((byte) rs.getInt("scale"));
        map.setWorldName(rs.getString("world_name"));
        map.setDimension(rs.getString("dimension"));
        map.setPixelData(rs.getString("pixel_data"));
        map.setCreatedBy(rs.getString("created_by"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) map.setCreatedAt(ts.toInstant());
        return map;
    }
}
