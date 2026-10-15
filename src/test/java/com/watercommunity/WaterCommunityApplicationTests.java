package com.watercommunity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    void seededCommunityCanBeRead() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].nickname", is("云舒")));

        mockMvc.perform(get("/api/projects").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(16)))
                .andExpect(jsonPath("$[0].name", is("雾中岛")));
    }

    @Test
    void blankProjectNameGetsUniqueThreeCharacterName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageAssetId": 3,
                                  "name": "",
                                  "description": "自动命名测试"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", matchesPattern("^.{3}$")))
                .andExpect(jsonPath("$.author.id", is(1)));
    }

    @Test
    void likeCanBeToggledWithoutDuplicates() throws Exception {
        mockMvc.perform(post("/api/projects/1/likes").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.count", is(9)));

        mockMvc.perform(post("/api/projects/1/likes").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.count", is(8)));
    }

    @Test
    void mutationsRequireSelectedUser() throws Exception {
        mockMvc.perform(post("/api/projects/1/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("请先选择一个用户")));
    }
}
