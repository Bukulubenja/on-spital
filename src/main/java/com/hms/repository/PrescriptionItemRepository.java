package com.hms.repository;

import com.hms.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {
}
