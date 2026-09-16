package com.hms.repository;

import com.hms.entity.Prescription;
import com.hms.entity.PrescriptionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItem, Long> {

    List<PrescriptionItem> findByPrescription(Prescription prescription);

    boolean existsByPrescriptionAndDispensedFalse(Prescription prescription);
}
