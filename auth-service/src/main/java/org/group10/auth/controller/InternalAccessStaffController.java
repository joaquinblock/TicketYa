package org.group10.auth.controller;

import lombok.RequiredArgsConstructor;
import org.group10.auth.service.AccessAssignmentService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/access-staff")
@RequiredArgsConstructor
public class InternalAccessStaffController {

    private final AccessAssignmentService accessAssignmentService;
}
