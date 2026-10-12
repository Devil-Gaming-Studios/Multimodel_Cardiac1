package com.example.multimodel.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Says which patient a Result belongs to and when it was made. This is what lets us build a
 * per-patient timeline: all Assessments of one user with the same patientLabel, oldest first.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "assessment")
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false, unique = true)
    private Result result;

    @Column(name = "patient_label", nullable = false, length = 200)
    private String patientLabel = "";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Assessment(Result result, String patientLabel) {
        this.result = result;
        this.patientLabel = patientLabel == null ? "" : patientLabel;
    }
}
