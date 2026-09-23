package com.mirattic.flow.chat.repository;

import com.mirattic.flow.chat.entity.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 최신 메시지부터 거꾸로 읽는다. before 가 null 이면 가장 최근부터.
     * offset 페이징을 쓰지 않는 이유는 대화 중에 새 메시지가 들어오면
     * 경계가 밀려 같은 메시지를 두 번 받거나 건너뛰기 때문이다.
     */
    @Query("""
            select m from ChatMessage m
            left join fetch m.sender
            where m.topic.id = :topicId
              and (:before is null or m.id < :before)
            order by m.id desc""")
    List<ChatMessage> findPage(@Param("topicId") Long topicId, @Param("before") Long before, Limit limit);

    @Modifying(clearAutomatically = true)
    @Query("delete from ChatMessage m where m.topic.issue.id = :issueId")
    void deleteByIssueId(@Param("issueId") Long issueId);

    @Modifying
    @Query("delete from ChatMessage m where m.topic.id = :topicId")
    void deleteByTopicId(@Param("topicId") Long topicId);

    @Modifying(clearAutomatically = true)
    @Query("delete from ChatMessage m where m.topic.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    @Modifying(clearAutomatically = true)
    @Query("delete from ChatMessage m where m.topic.project.workspace.id = :workspaceId")
    void deleteByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
