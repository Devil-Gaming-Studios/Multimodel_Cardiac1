package com.example.multimodel.DB;

import com.example.multimodel.Entities.Report;
import com.example.multimodel.Entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepo extends JpaRepository<Report, Integer> {
    Report findByUser(User user);
}
