package com.example.multimodel.Service;

import com.example.multimodel.Entities.Report;
import com.example.multimodel.Entities.Result;
import org.springframework.stereotype.Service;

@Service
public class ModelService {
    public Result ModelOutput(Report report)
    {
        return new Result();
    }
}
