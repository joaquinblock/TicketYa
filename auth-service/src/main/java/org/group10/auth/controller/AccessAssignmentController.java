package org.group10.auth.controller;

import lombok.RequiredArgsConstructor;
import org.group10.auth.service.AccessAssignmentService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AccessAssignmentController {

    private final AccessAssignmentService accessAssignmentService;
}
