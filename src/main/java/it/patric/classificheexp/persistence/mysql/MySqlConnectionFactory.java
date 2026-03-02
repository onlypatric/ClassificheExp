package it.patric.classificheexp.persistence.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.patric.classificheexp.config.PluginConfig;

import javax.sql.DataSource;
import java.util.Objects;

public final class MySqlConnectionFactory implements AutoCloseable {

    private final HikariDataSource dataSource;

    public MySqlConnectionFactory(PluginConfig.MySqlConfig config) {
        Objects.requireNonNull(config, "mysql config cannot be null");

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(buildJdbcUrl(config));
            hikariConfig.setUsername(config.username());
            hikariConfig.setPassword(config.password());
            hikariConfig.setPoolName("ClassificheExp-MySQL");
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setConnectionTimeout(10_000);
            hikariConfig.setValidationTimeout(5_000);
            hikariConfig.setInitializationFailTimeout(1);

            this.dataSource = new HikariDataSource(hikariConfig);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to initialize MySQL connection pool", ex);
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static String buildJdbcUrl(PluginConfig.MySqlConfig config) {
        return "jdbc:mysql://"
                + config.host() + ":" + config.port() + "/" + config.database()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }
}
