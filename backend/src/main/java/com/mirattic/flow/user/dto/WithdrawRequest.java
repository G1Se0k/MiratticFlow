package com.mirattic.flow.user.dto;

/**
 * 탈퇴 요청.
 * 비밀번호로 가입한 회원만 값을 보낸다 (소셜 회원은 비밀번호가 없으므로 null).
 */
public record WithdrawRequest(String password) {
}
