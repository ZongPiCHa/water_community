package com.watercommunity;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectRankingsTests {
    private DialectJdbc jdbc;
    private ProjectRankingService service;

    @BeforeEach
    void setup() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:rank" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new DialectJdbc(source);
        service = new ProjectRankingService(jdbc);
        jdbc.execute("CREATE SCHEMA water_analytics");
        jdbc.execute("CREATE TABLE water_analytics.heat_score_rule(version BIGINT, status INT)");
        jdbc.execute("""
                CREATE TABLE water_analytics.project_engagement_stats(
                    project_id VARCHAR(64), user_id VARCHAR(64), heat_score BIGINT,
                    like_count BIGINT DEFAULT 1, comment_count BIGINT DEFAULT 2,
                    share_count BIGINT DEFAULT 3, follow_count BIGINT DEFAULT 4, view_page_count BIGINT DEFAULT 5,
                    window_type INT, score_version BIGINT, window_start TIMESTAMP, window_end TIMESTAMP)
                """);
        jdbc.execute("CREATE TABLE projects(id VARCHAR(64) PRIMARY KEY, name VARCHAR(64), image_asset_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE community_users(id VARCHAR(64) PRIMARY KEY, nickname VARCHAR(64), selectable BOOLEAN)");
        jdbc.update("INSERT INTO water_analytics.heat_score_rule VALUES(4,1),(5,0)");
        jdbc.update("INSERT INTO projects VALUES('p1','风景一','a1')");
        jdbc.update("INSERT INTO community_users VALUES('u1','云舒',TRUE)");
    }

    void row(int type, int version, String start, String end, String project, long score) {
        jdbc.update("""
                INSERT INTO water_analytics.project_engagement_stats
                (window_type,score_version,window_start,window_end,project_id,user_id,heat_score)
                VALUES(?,?,?,?,?,'u1',?)
                """, type, version, start, end, project, score);
    }

    @Test
    void latestAvailableWindowActiveVersionTopFiveAndPrecision() {
        for (int type = 1; type <= 4; type++) {
            // A newer inactive-version row and a very hot older row must both be ignored.
            row(type, 5, "2099-01-01 00:00:00", "2099-01-02 00:00:00", "inactive", 999999);
            row(type, 4, "2000-01-01 00:00:00", "2000-01-02 00:00:00", "old", 999999);
            // Past windows and future/unclosed windows are both valid; no NOW() filtering.
            String start = type == 4 ? "2098-01-01 00:00:00" : "2001-01-01 00:00:00";
            String end = type == 4 ? "2098-01-08 00:00:00" : "2001-01-04 00:00:00";
            for (int index = 6; index >= 1; index--) row(type, 4, start, end, "p" + index, 17300);
            row(type, 4, start, end, "low", 17299);
        }
        var result = service.rankings();
        assertThat(result.scoreVersion()).isEqualTo(4);
        assertThat(result.windows()).hasSize(4);
        for (var window : result.windows()) {
            assertThat(window.projects()).extracting(RankedProject::projectId)
                    .containsExactly("p1", "p2", "p3", "p4", "p5");
            var first = window.projects().get(0);
            assertThat(first.heatScore()).isEqualByComparingTo("17.3");
            assertThat(first.projectName()).isEqualTo("风景一");
            assertThat(first.nickname()).isEqualTo("云舒");
            assertThat(first.viewPageCount()).isEqualTo(5);
            assertThat(window.projects().get(1).projectName()).isNull(); // Keep unmatched Top 5 IDs.
        }
        assertThat(result.windows().get(3).projects().get(0).windowStart()).startsWith("2098-01-01");
        assertThat(jdbc.reads).hasSize(5);
        assertThat(jdbc.reads.subList(1, 5)).allSatisfy(sql -> {
            assertThat(sql.split("FORCE INDEX", -1)).hasSize(3);
            assertThat(sql).doesNotContain("NOW(", "SUM(", "GROUP BY");
        });
    }

    @Test
    void emptyWindowDoesNotBorrowAnotherWindowOrVersion() {
        row(1, 4, "2001-01-01 00:00:00", "2001-01-01 01:00:00", "p1", 1);
        row(2, 5, "2001-01-01 00:00:00", "2001-01-02 00:00:00", "p1", 1000);
        var result = service.rankings();
        assertThat(result.windows().get(0).projects().get(0).heatScore()).isEqualByComparingTo("0.001");
        assertThat(result.windows().subList(1, 4)).allSatisfy(window -> assertThat(window.projects()).isEmpty());
        assertThat(jdbc.reads).hasSize(5);
    }

    @Test
    void noActiveRuleReturnsFourEmptyBoards() {
        jdbc.update("UPDATE water_analytics.heat_score_rule SET status=0");
        var result = service.rankings();
        assertThat(result.scoreVersion()).isNull();
        assertThat(result.windows()).hasSize(4).allSatisfy(window -> assertThat(window.projects()).isEmpty());
        assertThat(jdbc.reads).hasSize(1);
    }

    @Test
    void unavailableAnalyticsDoesNotFallBackToLikes() {
        jdbc.execute("DROP TABLE water_analytics.project_engagement_stats");
        assertThatThrownBy(service::rankings).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(503));
    }

    // Exercise the actual selection/join logic on H2; only MySQL-specific hint/date syntax is translated.
    // The unmodified SQL is also checked against the real MySQL instance during integration verification.
    static class DialectJdbc extends JdbcTemplate {
        final List<String> reads = new ArrayList<>();
        DialectJdbc(JdbcDataSource source) { super(source); }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper) {
            reads.add(sql);
            return super.query(sql, mapper);
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            reads.add(sql);
            return super.query(sql.replace("FORCE INDEX (idx_project_rank_lookup)", "")
                    .replace("DATE_FORMAT(window_start, '%Y-%m-%d %H:%i:%s')", "FORMATDATETIME(window_start, 'yyyy-MM-dd HH:mm:ss')")
                    .replace("DATE_FORMAT(window_end, '%Y-%m-%d %H:%i:%s')", "FORMATDATETIME(window_end, 'yyyy-MM-dd HH:mm:ss')"),
                    mapper, args);
        }
    }
}
