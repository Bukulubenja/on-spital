package com.hms.repository;

import com.hms.entity.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Not tenant-scoped — Hospital is the tenant boundary itself, mirroring Django. */
public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    Optional<Hospital> findBySubdomainAndActiveTrue(String subdomain);
}
