package com.example.multimodel.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One chat message (user question or AI answer) belonging to one assessment (Result). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private Result result;

    @Column(name = "msg_role", nullable = false, length = 20)      // "user" or "assistant"
    private String role;

    @Column(name = "msg_content", nullable = false, length = 10000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public ChatMessage(Result result, String role, String content) {
        this.result = result;
        this.role = role;
        this.content = content;
    }
}
