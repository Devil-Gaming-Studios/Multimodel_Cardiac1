package com.example.multimodel.Service;

import com.example.multimodel.Entities.Assessment;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.DB.AssessmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Serial comparison: remembers which patient each result belongs to and returns every visit with its probabilities. */
@Service
public class TimelineService {

    private final AssessmentRepository repo;
    private final ChatHistoryService owner;      // reuses its "is this Result mine?" check

    public TimelineService(AssessmentRepository repo, ChatHistoryService owner) {
        this.repo = repo;
        this.owner = owner;
    }

    /** Attach a patient label to a result. Safe to call twice (it just updates the label). */
    @Transactional
    public void register(String username, Integer resultId, String label) {
        Result r = owner.ownedResult(username, resultId);               // 404 if it is not this user's
        String clean = label == null ? "" : label.trim();
        if (clean.length() > 200) clean = clean.substring(0, 200);
        Assessment a = repo.findByResultId(resultId).orElseGet(() -> new Assessment(r, ""));
        a.setPatientLabel(clean);
        repo.save(a);
    }

    /** All visits of the user, oldest first: resultId, patient label, time, and the four probabilities. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> timeline(String username) {
        return repo.findAllForUser(username).stream().<Map<String, Object>>map(a -> {
            Result r = a.getResult();
            Map<String, Double> probs = new HashMap<>();             // HashMap: a probability may be null
            probs.put("CAD", r.getCath_prob());
            probs.put("LAD", r.getLAD_prob());
            probs.put("LCX", r.getLCX_prob());
            probs.put("RCA", r.getRCA_prob());
            return Map.of(
                    "resultId", r.getId(),
                    "label", a.getPatientLabel(),
                    "ts", a.getCreatedAt().toEpochMilli(),
                    "probs", probs);
        }).toList();
    }
}
