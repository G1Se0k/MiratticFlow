package com.mirattic.flow.user.service;

/** 이 계정은 탈퇴한 적이 있고, 이번 로그인은 그 탈퇴보다 앞선 것이다 — 비밀번호를 다시 입력한 로그인이 필요하다. */
public class FreshLoginRequired extends RuntimeException {

    public FreshLoginRequired() {
        super("login older than the last withdrawal");
    }
}
