package com.example.multimodel.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer Report_id;

    Byte age;
    Short weight;
    Short length;
    Byte sex;
    Double BMI;
    Byte DM;
    Byte HTN;
    Byte current_smoker;
    Byte ex_smoker;
    Byte FH;
    Byte Obesity;
    Byte CRF;
    Byte CVA;
    Byte Airway_disease;
    Byte Thyroid_disease;
    Byte CHF;
    Byte DLP;
    Short BP;
    Short PR;
    Byte Edema;
    Byte Weak_Peripheral_Pulse;
    Byte Lung_rales;
    Byte Systolic_Murmur;
    Byte Diastolic_Murmur;
    Byte Typical_Chest_Pain;
    Byte Dyspnea;
    Byte Function_class;
    Byte Atypical;
    Byte Nonanginal;
    Byte Exertional_CP;
    Byte LowTH_Ang;
    Byte Q_Wave;
    Byte St_Elevation;
    Byte St_Depression;
    Byte Tinversion;
    Byte LVH;
    Byte BBB;
    Short FBS;
    Double CR;
    Short TG;
    Short LDL;
    Double HDL;
    Short BUN;
    Short ESR;
    Double HB;
    Double K;
    Short Na;
    Integer WBC;
    Byte Lymph;
    Byte Neut;
    Short PLT;
    Byte EF_TTE;
    Byte Region_RWMA;
    Byte VHD;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @OneToOne(cascade = CascadeType.ALL)
    private Result result;
}