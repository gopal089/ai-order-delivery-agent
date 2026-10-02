package com.aiorderdeliveryagent.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
class PostgreSqlConnectionTests {
	private static final Set<String> INITIAL_SCHEMA_TABLES = Set.of(
			"users",
			"refresh_tokens",
			"integrations",
			"conversations",
			"messages",
			"orders",
			"shipments",
			"tracking_events",
			"tool_executions",
			"audit_events");

	@Autowired
	private DataSource dataSource;

	@Test
	void connectsToPostgreSqlAndExecutesQuery() throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("SELECT 1")) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
			assertThat(result.next()).isTrue();
			assertThat(result.getInt(1)).isEqualTo(1);
		}
	}

	@Test
	void appliesInitialFlywayMigration() throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("""
						SELECT description, success
						FROM flyway_schema_history
						WHERE version = '1'
						""")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getString("description")).isEqualTo("baseline");
			assertThat(result.getBoolean("success")).isTrue();
			assertThat(result.next()).isFalse();
		}
	}

	@Test
	void appliesTenantOwnedInitialDatabaseSchema() throws Exception {
		Set<String> migratedTables = new HashSet<>();
		Set<String> tenantOwnedTables = new HashSet<>();

		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet tables = statement.executeQuery("""
						SELECT table_name
						FROM information_schema.tables
						WHERE table_schema = 'public'
						  AND table_type = 'BASE TABLE'
						""")) {
			while (tables.next()) {
				migratedTables.add(tables.getString("table_name"));
			}
		}

		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet columns = statement.executeQuery("""
						SELECT table_name
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND column_name = 'tenant_id'
						""")) {
			while (columns.next()) {
				tenantOwnedTables.add(columns.getString("table_name"));
			}
		}

		assertThat(migratedTables).containsAll(INITIAL_SCHEMA_TABLES);
		assertThat(tenantOwnedTables).containsAll(INITIAL_SCHEMA_TABLES);
	}
}
