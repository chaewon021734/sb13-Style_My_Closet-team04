package com.project.sb13ootbsteam04.auth.controller;

import com.project.sb13ootbsteam04.auth.dto.request.TemporaryPasswordRequest;
import com.project.sb13ootbsteam04.auth.service.TemporaryPasswordService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class TemporaryPasswordController {

    private final TemporaryPasswordService temporaryPasswordService;

    @PostMapping("/temporary-password")
    public ResponseEntity<Void> issueTemporaryPassword(
            @Valid @RequestBody TemporaryPasswordRequest request
    ) {

        temporaryPasswordService.issueTemporaryPassword(
                request.email()
        );

        return ResponseEntity.accepted().build();
    }
}
