package com.hms.repository;

import com.hms.entity.Appointment;
import com.hms.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    boolean existsByAppointment(Appointment appointment);

    List<Visit> findByStatusOrderByVisitDateAsc(Visit.Status status);

    long countByStatusNot(Visit.Status status);

    @Query("select v.status, count(v) from Visit v group by v.status")
    List<Object[]> countGroupedByStatus();

    /**
     * Active (non-completed) visit counts per department — analogue of
     * admin_dashboard's visits_by_department. Django falls back to
     * "Unassigned" for a null department via `|| "Unassigned"` at the
     * template layer; done here instead since this is a JSON API, not a
     * template.
     */
    @Query("""
            select coalesce(v.department.name, 'Unassigned'), count(v)
            from Visit v
            where v.status <> :excludedStatus
            group by v.department.name
            order by count(v) desc
            """)
    List<Object[]> countActiveGroupedByDepartment(@Param("excludedStatus") Visit.Status excludedStatus);
}
