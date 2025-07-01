package org.fourz.RVNKLore.data.service;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for database connection providers
 */
public interface ConnectionProvider {
    Connection getConnection() throws SQLException;
    void close() throws SQLException;
    boolean isValid() throws SQLException;
}