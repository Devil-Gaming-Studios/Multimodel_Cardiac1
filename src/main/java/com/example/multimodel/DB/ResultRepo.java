package com.example.multimodel.DB;

import com.example.multimodel.Entities.Report;
import com.example.multimodel.Entities.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResultRepo extends JpaRepository<Result,Integer> {
    public Result findByReport(Report report);
}
