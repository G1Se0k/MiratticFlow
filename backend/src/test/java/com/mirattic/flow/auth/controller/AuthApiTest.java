package com.mirattic.flow.auth.controller;

import tools.jackson.databind.ObjectMapper;
import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.ReissueRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 회원가입 → 로그인 → 보호된 API 호출까지 필터 체인을 포함해 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("가입한 사용자는 로그인 후 받은 토큰으로 /api/users/me 를 조회할 수 있다")
    void signupLoginAndMe() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("flow@test.com", "password123", "장기석"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("flow@test.com"));

        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("flow@test.com", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("장기석"));
    }

    @Test
    @DisplayName("재발급에 쓴 refresh token 은 폐기되어 두 번 쓸 수 없다")
    void refreshTokenIsRotated() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest("rotate@test.com", "password123", "회전"))));
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("rotate@test.com", "password123"))))
                .andReturn().getResponse().getContentAsString();
        String oldRefreshToken = objectMapper.readTree(login).get("refreshToken").asText();

        String reissued = mockMvc.perform(post("/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReissueRequest(oldRefreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(reissued).get("refreshToken").asText()).isNotEqualTo(oldRefreshToken);

        mockMvc.perform(post("/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReissueRequest(oldRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃하면 그 refresh token 으로 더 이상 재발급할 수 없다")
    void logout() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest("logout@test.com", "password123", "로그아웃"))));
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("logout@test.com", "password123"))))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(login).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReissueRequest(refreshToken))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReissueRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("토큰 없이 보호된 API 를 호출하면 통일된 포맷의 401 이 온다")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("잘못된 형식의 이메일은 400 과 함께 어떤 필드가 틀렸는지 알려준다")
    void signupValidation() throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("not-an-email", "123", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors").isNotEmpty());
    }
}
