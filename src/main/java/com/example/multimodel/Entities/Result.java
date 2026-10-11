package com.example.multimodel.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Result {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer Id;

    Byte LAD;
    Byte LCX;
    Byte RCA;
    Byte Cath;

    Double LAD_prob;
    Double LCX_prob;
    Double RCA_prob;
    Double Cath_prob;

    @OneToOne
    @JoinColumn(name = "Report_ID")
    private Report report;

}
