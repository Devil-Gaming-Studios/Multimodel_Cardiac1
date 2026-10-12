package com.example.multimodel.Service;

import com.example.multimodel.Entities.ChatMessage;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.DB.ChatMessageRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Stores the chat of each assessment (Result) in the database.
 * A user can only touch chats of results whose Report belongs to them (Result -> Report -> User).
 */
@Service
public class ChatHistoryService {

    private final ChatMessageRepository repo;

    @PersistenceContext
    private EntityManager em;

    public ChatHistoryService(ChatMessageRepository repo) { this.repo = repo; }

    /** The Result with this id, only if it belongs to the user; otherwise 404 (never reveals other users' ids). */
    public Result ownedResult(String username, Integer resultId) {
        return em.createQuery(
                        "select r from Result r where r.id = :id and r.report.user.username = :u", Result.class)
                .setParameter("id", resultId)
                .setParameter("u", username)
                .getResultList().stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "assessment not found"));
    }

    @Transactional
    public void save(Result result, String role, String content) {
        if (result == null || content == null || content.isBlank()) return;
        String c = content.length() > 10000 ? content.substring(0, 10000) : content;
        repo.save(new ChatMessage(result, role, c));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String username, Integer resultId) {
        ownedResult(username, resultId);
        return repo.findByResultId(resultId).stream()
                .<Map<String, Object>>map(m -> Map.of(
                        "role", m.getRole(),
                        "content", m.getContent(),
                        "ts", m.getCreatedAt().toEpochMilli()))
                .toList();
    }

    @Transactional
    public void delete(String username, Integer resultId) {
        ownedResult(username, resultId);
        repo.deleteByResultId(resultId);
    }
}
