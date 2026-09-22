package com.mirattic.flow.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** BaseTimeEntity 의 @CreatedDate / @LastModifiedDate 를 켜는 설정. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
