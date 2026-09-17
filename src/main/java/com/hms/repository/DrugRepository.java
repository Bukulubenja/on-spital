package com.hms.repository;

import com.hms.entity.Drug;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugRepository extends JpaRepository<Drug, Long> {

    List<Drug> findAllByOrderByNameAsc();
}
