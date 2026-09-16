package com.hms.repository;

import com.hms.entity.BillableService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillableServiceRepository extends JpaRepository<BillableService, Long> {
}
