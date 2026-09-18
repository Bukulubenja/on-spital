package com.hms.reception.dto;

import com.hms.entity.Department;

public record DepartmentSummary(Long id, String name) {

    public static DepartmentSummary from(Department department) {
        return new DepartmentSummary(department.getId(), department.getName());
    }
}
