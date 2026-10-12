package com.example.multimodel.DB;

import com.example.multimodel.Entities.Report;
import com.example.multimodel.Entities.Result;
import com.example.multimodel.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResultRepo extends JpaRepository<Result,Integer> {
    public Result findByReport(Report report);

    @Query("select r from Result r where r.report.user = :user order by r.Id asc")
    List<Result> findByUserOldestFirst(@Param("user") User user);
}
