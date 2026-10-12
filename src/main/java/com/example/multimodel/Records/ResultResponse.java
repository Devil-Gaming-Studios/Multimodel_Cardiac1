package com.example.multimodel.Records;

import com.example.multimodel.Service.ExplanationService;

import java.util.Map;

public record ResultResponse(Integer id, Byte cath, Double cathProb, Byte lad, Double ladProb,
                             Byte lcx, Double lcxProb, Byte rca, Double rcaProb,
                             Map<String, ExplanationService.TargetExplanation> explanation) {}