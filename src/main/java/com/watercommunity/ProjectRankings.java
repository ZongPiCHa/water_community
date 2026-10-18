package com.watercommunity;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record RankedProject(
        String projectId, String userId, BigDecimal heatScore,
        long likeCount, long commentCount, long shareCount, long followCount, long viewPageCount,
        String windowStart, String windowEnd, LocalDateTime updatedAt,
        String projectName, String nickname, String avatarUrl, String imageUrl
) {}

record RankingWindow(int windowType, String label, LocalDateTime updatedAt, List<RankedProject> projects) {}

record RankingsResponse(Long scoreVersion, List<RankingWindow> windows) {}

@Service
class ProjectRankingService {
    static final String RULE_SQL = """
            SELECT version FROM water_analytics.heat_score_rule WHERE status = 1 LIMIT 1
            """;

    // LIMIT is inside the derived table: missing community metadata must never change the Top 100.
    // Metadata joins add no extra round trips and never aggregate or recalculate analytics scores.
    static final String RANK_SQL = """
            SELECT ranked.*,
                   p.name AS project_name, u.nickname,
                   CASE WHEN u.id IS NULL THEN NULL
                        WHEN u.selectable THEN CONCAT('/generated/avatars/optimized/avatar-', u.id, '.jpg')
                        ELSE '/generated/avatars/default-avatar.svg' END AS avatar_url,
                   CASE WHEN p.image_asset_id IS NULL THEN NULL
                        ELSE CONCAT('/generated/landscapes/optimized/landscape-', p.image_asset_id, '.jpg')
                        END AS image_url
            FROM (
                SELECT project_id, user_id, heat_score,
                       like_count, comment_count, share_count, follow_count, view_page_count, updated_at,
                       DATE_FORMAT(window_start, '%Y-%m-%d %H:%i:%s') AS window_start,
                       DATE_FORMAT(window_end, '%Y-%m-%d %H:%i:%s') AS window_end
                FROM water_analytics.project_engagement_stats
                FORCE INDEX (idx_project_rank_lookup)
                WHERE window_type = ? AND score_version = ?
                  AND window_start = (
                      SELECT window_start
                      FROM water_analytics.project_engagement_stats
                      FORCE INDEX (idx_project_rank_lookup)
                      WHERE window_type = ? AND score_version = ?
                      ORDER BY window_start DESC LIMIT 1
                  )
                ORDER BY heat_score DESC, project_id ASC LIMIT 100
            ) ranked
            LEFT JOIN projects p ON p.id = ranked.project_id
            LEFT JOIN community_users u ON u.id = ranked.user_id
            ORDER BY ranked.heat_score DESC, ranked.project_id ASC
            """;

    private final JdbcTemplate jdbc;

    ProjectRankingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    RankingsResponse rankings() {
        try {
            var versions = jdbc.query(RULE_SQL, (rs, row) -> rs.getLong("version"));
            Long version = versions.isEmpty() ? null : versions.get(0);
            var windows = new ArrayList<RankingWindow>();
            var labels = List.of("小时榜", "天榜", "三天榜", "周榜");
            for (int type = 1; type <= 4; type++) {
                List<RankedProject> projects = version == null ? List.of() : jdbc.query(
                        RANK_SQL,
                        (rs, row) -> new RankedProject(
                                rs.getString("project_id"), rs.getString("user_id"),
                                rs.getBigDecimal("heat_score").movePointLeft(3),
                                rs.getLong("like_count"), rs.getLong("comment_count"),
                                rs.getLong("share_count"), rs.getLong("follow_count"), rs.getLong("view_page_count"),
                                rs.getString("window_start"), rs.getString("window_end"),
                                rs.getObject("updated_at", LocalDateTime.class),
                                rs.getString("project_name"), rs.getString("nickname"),
                                rs.getString("avatar_url"), rs.getString("image_url")),
                        type, version, type, version);
                LocalDateTime updatedAt = projects.stream().map(RankedProject::updatedAt)
                        .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
                windows.add(new RankingWindow(type, labels.get(type - 1), updatedAt, projects));
            }
            return new RankingsResponse(version, windows);
        } catch (DataAccessException exception) {
            org.slf4j.LoggerFactory.getLogger(ProjectRankingService.class)
                    .warn("Unable to read analytics rankings", exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "热度榜暂时无法读取，请稍后重试");
        }
    }
}

@RestController
class ProjectRankingController {
    private final ProjectRankingService rankings;

    ProjectRankingController(ProjectRankingService rankings) {
        this.rankings = rankings;
    }

    @GetMapping("/api/project-rankings")
    RankingsResponse rankings() {
        return rankings.rankings();
    }
}
