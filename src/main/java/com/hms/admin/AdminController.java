package com.hms.admin;

import com.hms.admin.dto.AdminDashboardView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Direct analogue of the ADMIN-only view in hospital/views.py. */
@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/admin/dashboard")
    public AdminDashboardView dashboard() {
        return adminService.dashboard();
    }
}
