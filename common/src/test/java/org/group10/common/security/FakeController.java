package org.group10.common.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
class FakeController {

    record ThingRequest(@NotBlank String name) {}

    @GetMapping("/api/things")
    String publicThings() {
        return "public";
    }

    @PostMapping("/api/things")
    @RequiresPermission(Permission.EVENT_MANAGE)
    String createThing(@Valid @RequestBody ThingRequest request) {
        return "created";
    }

    @PostMapping("/api/things/suspend")
    @RequiresPermission({Permission.EVENT_ADMIN, Permission.EVENT_MANAGE})
    Map<String, Object> suspendThing(Caller caller) {
        return Map.of("userId", caller.userId(), "role", caller.role(),
                "permissions", caller.permissions(),
                "organizationId", caller.organizationId() == null ? "none" : caller.organizationId());
    }

    @GetMapping("/api/things/{id}")
    String publicThingSeenBy(Optional<Caller> caller) {
        return caller.map(c -> "user " + c.userId()).orElse("anonymous");
    }

    @PostMapping("/internal/things")
    @RequiresPermission(Permission.EVENT_ADMIN)
    String internalThing() {
        return "internal";
    }
}
