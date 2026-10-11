package com.rahbar.controller;

import com.rahbar.service.RccStudentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Admin > Coaching Center Students: personal and family details of students at RCC / coaching centers. */
@RestController
@RequestMapping("/api/admin/rcc-students")
@PreAuthorize("denyAll()")
public class RccStudentController {

    private final RccStudentService rccStudentService;

    public RccStudentController(RccStudentService rccStudentService) {
        this.rccStudentService = rccStudentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RCC_STUDENTS:VIEW')")
    public Map<String, Object> list(@RequestParam(required = false) Long centerId, @RequestParam(required = false) String q,
                                    @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return rccStudentService.list(centerId, q, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RCC_STUDENTS:VIEW')")
    public Map<String, Object> get(@PathVariable Long id) {
        return rccStudentService.get(id);
    }

    /** Body: { name, sex, email, phone, address, fatherName, motherName, fatherProfession, motherProfession,
     *  fatherMobile, motherMobile, averageAnnualSalary, rccCenterId (office only; a lead always uses their own),
     *  refId (office only, on update) }. */
    @PostMapping
    @PreAuthorize("hasAuthority('RCC_STUDENTS:EDIT')")
    public Map<String, String> create(@RequestBody Map<String, Object> body) {
        String name = rccStudentService.create(body);
        return Map.of("message", name + " was added. The office assigns the reference ID after the application.");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('RCC_STUDENTS:EDIT')")
    public Map<String, String> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        rccStudentService.update(id, body);
        return Map.of("message", "Student details saved.");
    }
}
