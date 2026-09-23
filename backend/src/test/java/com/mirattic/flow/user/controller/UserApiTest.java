package com.mirattic.flow.user.controller;

import com.mirattic.flow.auth.dto.LoginRequest;
import com.mirattic.flow.auth.dto.SignupRequest;
import com.mirattic.flow.support.ApiTestSupport;
import com.mirattic.flow.user.dto.WithdrawRequest;
import com.mirattic.flow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴는 DB 까지 가봐야 확인되는 동작이다.
 * 단위 테스트는 리포지토리가 대역이라 영속성 컨텍스트가 없고, 실제로 지워졌는지 알 수 없다.
 */
class UserApiTest extends ApiTestSupport {

    @Autowired private UserRepository userRepository;

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(new SignupRequest(email, "password123", "탈퇴할사람"))));
        return field(bodyOf(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(email, "password123"))))), "accessToken");
    }

    @Test
    @DisplayName("탈퇴하면 DB 의 개인정보 컬럼이 실제로 비워진다")
    void withdrawErasesPersonalData() throws Exception {
        String token = signupAndLogin("bye@test.com");
        long userId = userIdOf(token);

        authed(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WithdrawRequest("password123"))), token)
                .andExpect(status().isNoContent());

        // 204 만 보고 넘어가면 "응답은 성공인데 지워지지 않는" 경우를 놓친다.
        var user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPassword()).isNull();
        assertThat(user.getProviderId()).isNull();
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.isWithdrawn()).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 계정으로는 로그인할 수 없고, 같은 이메일로 다시 가입할 수 있다")
    void withdrawnEmailCanSignupAgain() throws Exception {
        String token = signupAndLogin("rejoin@test.com");
        authed(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WithdrawRequest("password123"))), token)
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("rejoin@test.com", "password123"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest("rejoin@test.com", "password123", "다시가입"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("혼자 관리자인 워크스페이스가 있으면 탈퇴가 막힌다")
    void withdrawBlockedBySoleOwnedWorkspace() throws Exception {
        String token = signupAndLogin("owner@test.com");
        createWorkspace(token);

        authed(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WithdrawRequest("password123"))), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWNER_WORKSPACE_EXISTS"));

        // 막혔으면 아무것도 지워지지 않아야 한다.
        authed(get("/api/users/me"), token).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@test.com"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴되지 않는다")
    void withdrawRequiresPassword() throws Exception {
        String token = signupAndLogin("wrongpw@test.com");

        authed(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
                .content(json(new WithdrawRequest("not-my-password"))), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        authed(get("/api/users/me"), token).andExpect(status().isOk());
    }
}
