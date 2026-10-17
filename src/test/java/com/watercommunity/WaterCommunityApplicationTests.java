package com.watercommunity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WaterCommunityApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CapturingWaterBoxSender sender;

    @Autowired
    private ProjectShareRepository shareRepository;

    @Test
    void seededCommunityCanBeRead() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].id", is("u000000000000001")))
                .andExpect(jsonPath("$[0].nickname", is("云舒")));

        mockMvc.perform(get("/api/projects").header("X-User-Id", "u000000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].name", is("雾中岛")));
    }

    @Test
    void scaledCommunityHidesSimulatedUsersButShowsTheirProjects() throws Exception {
        mockMvc.perform(get("/api/simulation/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", hasSize(10000)))
                .andExpect(jsonPath("$.projects", hasSize(20000)))
                .andExpect(jsonPath("$.users[9999][0]", matchesPattern("^u[0-9]{15}$")));

        mockMvc.perform(get("/api/projects?page=399&size=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(50)))
                .andExpect(jsonPath("$[49].author.avatarUrl", is("/generated/avatars/default-avatar.svg")));

        mockMvc.perform(get("/api/projects?sort=popular&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void projectViewEmitsFrontViewPageEvent() throws Exception {
        sender.events.clear();
        TestTransaction.flagForCommit();
        mockMvc.perform(post("/api/projects/p000000000100001/views").header("X-User-Id", "u000000000100002"))
                .andExpect(status().isNoContent());
        assertThat(sender.events).isEmpty();
        TestTransaction.end();
        TestTransaction.start();
        assertThat(sender.events).hasSize(1);
        assertThat(sender.events.get(0))
                .containsEntry("system", "front")
                .containsEntry("action", "view_page")
                .containsEntry("properties", Map.of(
                        "project_id", "p000000000100001",
                        "author_user_id", "u000000000100001"
                ));
    }

    @Test
    void blankProjectNameGetsUniqueThreeCharacterName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header("X-User-Id", "u000000000000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageAssetId": "a000000000000003",
                                  "name": "",
                                  "description": "自动命名测试"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", matchesPattern("^.{3}$")))
                .andExpect(jsonPath("$.author.id", is("u000000000000001")))
                .andExpect(jsonPath("$.id", matchesPattern("^p[0-9a-z]{15}$")));
    }

    @Test
    void likeCanBeToggledWithoutDuplicates() throws Exception {
        mockMvc.perform(post("/api/projects/p000000000000001/likes").header("X-User-Id", "u000000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.count", is(9)));

        mockMvc.perform(post("/api/projects/p000000000000001/likes").header("X-User-Id", "u000000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.count", is(8)));
    }

    @Test
    void mutationsRequireSelectedUser() throws Exception {
        mockMvc.perform(post("/api/projects/p000000000000001/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("请先选择一个用户")));
    }

    @Test
    void committedActionsEmitWaterBoxEvents() throws Exception {
        sender.events.clear();
        mockMvc.perform(post("/api/projects/p000000000000002/likes")
                        .header("X-User-Id", "u000000000000003")
                        .header("X-Session-Id", "session-test")
                        .header("X-Span-Id", "span-front"))
                .andExpect(status().isOk());
        assertThat(sender.events).isEmpty();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // Undo the committed like so the shared in-memory database keeps its seeded counts.
        TestTransaction.start();
        TestTransaction.flagForCommit();
        mockMvc.perform(post("/api/projects/p000000000000002/likes").header("X-User-Id", "u000000000000003"))
                .andExpect(status().isOk());
        TestTransaction.end();
        TestTransaction.start();

        assertThat(sender.events).hasSize(2);
        Map<String, Object> like = sender.events.get(0);
        assertThat(like.get("timestamp")).isInstanceOf(Long.class);
        assertThat(String.valueOf(like.get("timestamp"))).hasSize(13);
        assertThat(like).containsEntry("session_id", "session-test")
                .containsEntry("pre_span_id", "span-front")
                .containsEntry("system", "backend")
                .containsEntry("theme", "community")
                .containsEntry("user_id", "u000000000000003")
                .containsEntry("team_id", null)
                .containsEntry("env", "test")
                .containsEntry("properties", Map.of(
                        "project_id", "p000000000000002",
                        "author_user_id", "u000000000000002"
                ));
        assertThat((String) like.get("log_id")).isNotBlank();
        assertThat((String) like.get("span_id")).isNotBlank();
        assertThat(List.of(like.get("action"), sender.events.get(1).get("action")))
                .containsExactlyInAnyOrder("like", "unlike");
    }

    @Test
    @SuppressWarnings("unchecked")
    void commentShareAndFollowActionsMatchWaterBoxActionList() throws Exception {
        sender.events.clear();
        String user = "u000000000000003";
        String project = "p000000000000002";
        TestTransaction.flagForCommit();
        String body = mockMvc.perform(post("/api/projects/" + project + "/comments")
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"埋点测试\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String commentId = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(delete("/api/projects/" + project + "/comments/" + commentId).header("X-User-Id", user))
                .andExpect(status().isNoContent());
        String shareBody = mockMvc.perform(post("/api/projects/" + project + "/shares").header("X-User-Id", user))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/users/u000000000000002/follow?projectId=" + project).header("X-User-Id", user))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/u000000000000002/follow?projectId=" + project).header("X-User-Id", user))
                .andExpect(status().isOk());
        TestTransaction.end();

        // Remove the committed share so other tests keep the seeded counts.
        TestTransaction.start();
        TestTransaction.flagForCommit();
        shareRepository.findAll().stream()
                .filter(share -> !share.getId().matches("s\\d{15}"))
                .forEach(shareRepository::delete);
        TestTransaction.end();
        TestTransaction.start();

        // A forbidden delete marks the transaction rollback-only, so check it in the final rolled-back transaction.
        String otherBody = mockMvc.perform(post("/api/projects/" + project + "/comments")
                        .header("X-User-Id", user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"权限测试\"}"))
                .andReturn().getResponse().getContentAsString();
        String otherId = otherBody.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(delete("/api/projects/" + project + "/comments/" + otherId)
                        .header("X-User-Id", "u000000000000005"))
                .andExpect(status().isForbidden());

        assertThat(shareBody).contains("分享已记录");
        assertThat(sender.events).extracting(event -> event.get("action"))
                .containsExactly("comment", "delete_comment", "share", "follow");
        assertThat(sender.events.get(1)).containsEntry("properties", Map.of(
                "project_id", project,
                "comment_id", commentId,
                "author_user_id", "u000000000000002"
        ));
        assertThat(sender.events.get(2)).containsEntry("properties", Map.of(
                "project_id", project,
                "author_user_id", "u000000000000002"
        ));
        assertThat(sender.events.get(3)).containsEntry("properties", Map.of(
                "project_id", project,
                "target_user_id", "u000000000000002"
        ));
        assertThat(sender.events).allSatisfy(event -> {
            assertThat(event.get("timestamp")).isInstanceOf(Long.class);
            assertThat((String) event.get("log_id")).isNotBlank();
            assertThat((String) event.get("user_id")).isNotBlank();
            assertThat(((Map<String, Object>) event.get("properties")).values())
                    .allSatisfy(value -> assertThat(value).isInstanceOf(String.class)
                            .asString().isNotBlank());
        });
        mockMvc.perform(post("/api/users/u000000000000002/follow?projectId=p000000000000001")
                        .header("X-User-Id", user))
                .andExpect(status().isBadRequest());
        assertThat(sender.events).allSatisfy(event -> assertThat(List.of(
                "like", "unlike", "comment", "delete_comment", "share", "follow"
        )).contains((String) event.get("action")));
    }

    @TestConfiguration
    static class TrackingTestConfig {
        @Bean
        CapturingWaterBoxSender capturingWaterBoxSender() {
            return new CapturingWaterBoxSender();
        }
    }

    static class CapturingWaterBoxSender implements WaterBoxSender {
        final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

        @Override
        public void send(Map<String, Object> event) {
            events.add(event);
        }
    }
}
