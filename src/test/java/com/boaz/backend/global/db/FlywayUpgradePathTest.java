package com.boaz.backend.global.db;

import com.boaz.backend.support.TestcontainersBase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * prod 첫 배포와 같은 경로(테이블은 있고 flyway_schema_history는 없는 DB → baseline → V2 이상 적용)를 검증한다.
 *
 * <p>다른 테스트는 빈 DB에 V1부터 실행하는 경로만 지난다. 그 경로로는 baseline 처리와
 * 기존 행이 있는 상태에서의 ALTER를 확인할 수 없어서 이 테스트를 따로 둔다.
 * Spring 컨텍스트 없이 공용 MySQL 컨테이너 안에 전용 DB 두 개를 만들어 쓴다.
 */
@DisplayName("Flyway 업그레이드 경로 (prod 첫 배포: baseline 후 V2 이상 적용)")
class FlywayUpgradePathTest extends TestcontainersBase {

    private static final String FRESH_DB = "flyway_fresh";
    private static final String UPGRADE_DB = "flyway_upgrade";
    private static final String V1_SCRIPT = "db/migration/V1__init.sql";

    @BeforeEach
    void recreateDatabases() {
        JdbcTemplate server = new JdbcTemplate(rootDataSource(""));
        for (String db : List.of(FRESH_DB, UPGRADE_DB)) {
            server.execute("DROP DATABASE IF EXISTS `" + db + "`");
            server.execute("CREATE DATABASE `" + db + "`");
        }
    }

    @Test
    @DisplayName("TC-001 V1 테이블에 기존 행이 있는 DB에서 V1은 baseline으로만 기록되고 V2 이상이 적용된다. 기존 행은 그대로다")
    void baselineThenMigrateKeepsExistingRows() throws Exception {
        DataSource ds = rootDataSource(UPGRADE_DB);
        applyV1WithoutFlyway(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        insertAdmin(jdbc, "super1", "SUPER", "대표진");
        insertAdmin(jdbc, "team1", "TEAM", "서비스운영팀");
        insertAdmin(jdbc, "team2", "TEAM", "기획팀");

        Flyway flyway = baselineFlyway(ds);
        MigrateResult result = flyway.migrate();

        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied[0].getType()).isEqualTo(CoreMigrationType.BASELINE);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(result.migrationsExecuted).isEqualTo(applied.length - 1);
        assertThat(result.success).isTrue();
        assertThat(flyway.info().pending()).isEmpty();

        assertThat(jdbc.queryForList("SELECT username, role, team_name FROM admin ORDER BY username"))
                .extracting(r -> r.get("username") + "/" + r.get("role") + "/" + r.get("team_name"))
                .containsExactly("super1/SUPER/대표진", "team1/TEAM/서비스운영팀", "team2/TEAM/기획팀");
    }

    @Test
    @DisplayName("TC-002 V2.1 적용 뒤 새 role 값(MASTER, HOST)과 새 team_name 값(그룹리더)을 저장할 수 있다")
    void newEnumValuesAreStorableAfterMigration() throws Exception {
        DataSource ds = rootDataSource(UPGRADE_DB);
        applyV1WithoutFlyway(ds);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        insertAdmin(jdbc, "lead", "TEAM", "서비스운영팀");

        baselineFlyway(ds).migrate();

        jdbc.update("UPDATE admin SET role = 'MASTER' WHERE username = 'lead'");
        insertAdmin(jdbc, "host1", "HOST", "그룹리더");
        assertThat(jdbc.queryForList("SELECT role FROM admin ORDER BY username", String.class))
                .containsExactly("HOST", "MASTER");
    }

    @Test
    @DisplayName("TC-003 baseline 경로로 만든 스키마는 빈 DB에 V1부터 실행한 스키마와 같다")
    void baselinePathSchemaEqualsFreshPathSchema() throws Exception {
        DataSource fresh = rootDataSource(FRESH_DB);
        Flyway.configure().dataSource(fresh).locations("classpath:db/migration").load().migrate();

        DataSource upgrade = rootDataSource(UPGRADE_DB);
        applyV1WithoutFlyway(upgrade);
        baselineFlyway(upgrade).migrate();

        assertThat(schemaOf(upgrade)).isEqualTo(schemaOf(fresh));
    }

    /** prod 첫 배포 설정(application-prod.yml)과 같은 baseline 설정. */
    private static Flyway baselineFlyway(DataSource ds) {
        return Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
    }

    /** prod처럼 V1 테이블만 있고 flyway_schema_history는 없는 상태를 만든다. */
    private static void applyV1WithoutFlyway(DataSource ds) throws Exception {
        try (Connection conn = ds.getConnection()) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource(V1_SCRIPT));
        }
    }

    private static void insertAdmin(JdbcTemplate jdbc, String username, String role, String teamName) {
        jdbc.update("INSERT INTO admin (username, password, name, role, team_name, term, track) "
                        + "VALUES (?, 'pw', ?, ?, ?, 25, 'ALL')",
                username, username, role, teamName);
    }

    /** 테이블별 SHOW CREATE TABLE. AUTO_INCREMENT 현재값과 Flyway 기록 테이블은 비교에서 뺀다. */
    private static Map<String, String> schemaOf(DataSource ds) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        Map<String, String> schema = new TreeMap<>();
        for (String table : jdbc.queryForList("SHOW TABLES", String.class)) {
            if (table.equals("flyway_schema_history")) {
                continue;
            }
            String ddl = jdbc.queryForMap("SHOW CREATE TABLE `" + table + "`").get("Create Table").toString();
            schema.put(table, ddl.replaceAll(" AUTO_INCREMENT=\\d+", ""));
        }
        return schema;
    }

    /** DB를 만들고 지워야 해서 root로 접속한다. Testcontainers MySQL은 root 비밀번호를 사용자 비밀번호와 같게 둔다. */
    private static DataSource rootDataSource(String database) {
        String url = "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
        return new DriverManagerDataSource(url, "root", MYSQL.getPassword());
    }
}
