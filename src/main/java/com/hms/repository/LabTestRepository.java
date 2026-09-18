package com.hms.repository;

import com.hms.entity.LabTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {

    List<LabTest> findAllByOrderByNameAsc();
}
