package com.example.multimodel.DB;

import com.example.multimodel.Entities.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

    @Query("select a from Assessment a where a.result.id = :rid")
    Optional<Assessment> findByResultId(@Param("rid") Integer resultId);

    /** Every assessment of one user, oldest first, with its Result loaded (no extra queries per row). */
    @Query("select a from Assessment a join fetch a.result r " +
           "where r.report.user.username = :u order by a.createdAt asc, a.id asc")
    List<Assessment> findAllForUser(@Param("u") String username);
}
