package com.shopee.monolith.modules.recommendation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopee.monolith.BasePostgresRedisIntegrationTest;
import com.shopee.monolith.modules.recommendation.dto.ChatRecommendRequest;
import com.shopee.monolith.modules.recommendation.dto.RecommendationResponse;
import com.shopee.monolith.modules.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RecommendationControllerIT extends BasePostgresRedisIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void homeRecommendationsShouldAllowAnonymousAccess() throws Exception {
        when(recommendationService.homeRecommendations(null, 0, 20))
                .thenReturn(RecommendationResponse.builder()
                        .items(List.of())
                        .degraded(false)
                        .build());

        mockMvc.perform(get("/api/recommendations/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.degraded").value(false));
    }

    @Test
    void chatRecommendationsShouldAllowAnonymousAccess() throws Exception {
        ChatRecommendRequest request = new ChatRecommendRequest("find gaming mouse", null, null, 5);
        when(recommendationService.chatRecommendations(eq(null), any(ChatRecommendRequest.class)))
                .thenReturn(RecommendationResponse.builder()
                        .items(List.of())
                        .degraded(false)
                        .build());

        mockMvc.perform(post("/api/recommendations/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.degraded").value(false));
    }
}
