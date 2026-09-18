package com.hms.repository;

import com.hms.entity.BillableService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillableServiceRepository extends JpaRepository<BillableService, Long> {

    List<BillableService> findAllByOrderByNameAsc();
}
