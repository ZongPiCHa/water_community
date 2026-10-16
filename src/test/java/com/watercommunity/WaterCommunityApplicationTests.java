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

    @Test
    void seededCommunityCanBeRead() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].id", is("u000000000000001")))
                .andExpect(jsonPath("$[0].nickname", is("云舒")));

        mockMvc.perform(get("/api/projects").header("X-User-Id", "u000000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[0].name", is("雾中岛")));
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
