package com.mirattic.flow.workspace.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 초대 코드를 만든다.
 *
 * SecureRandom 을 쓰는 이유: Math.random() 이나 UUID 앞자리를 자르는 방식은 예측이 가능하다.
 * 초대 코드를 맞히면 남의 워크스페이스에 들어갈 수 있으므로 암호학적 난수여야 한다.
 */
@Component
public class InviteCodeGenerator {

    /** 사람이 받아 적는 값이라 혼동되는 0/O, 1/I/L 을 뺐다. */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 링크용. URL 에만 쓰이므로 길게 잡는다. */
    public String linkCode() {
        return random(22);
    }

    /** 상시 코드용. 사람이 입력하므로 짧게, 4자씩 끊어 읽기 쉽게 만든다. */
    public String joinCode() {
        return random(4) + "-" + random(4);
    }

    private String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
