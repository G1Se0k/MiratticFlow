package com.mirattic.flow.issue.dto;

import com.mirattic.flow.issue.entity.IssueStatus;

/** group by 결과를 Object[] 대신 이 모양으로 바로 받는다 (JPQL 생성자 표현식). */
public record StatusCount(IssueStatus status, long count) {
}
