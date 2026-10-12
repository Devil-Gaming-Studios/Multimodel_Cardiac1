package com.example.multimodel.DB;

import com.example.multimodel.Entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("select m from ChatMessage m where m.result.id = :rid order by m.id asc")
    List<ChatMessage> findByResultId(@Param("rid") Integer resultId);

    @Modifying
    @Query("delete from ChatMessage m where m.result.id = :rid")
    void deleteByResultId(@Param("rid") Integer resultId);
}
