package com.project.sb13ootbsteam04.user.controller;

import com.project.sb13ootbsteam04.user.dto.request.UserCreateRequest;
import com.project.sb13ootbsteam04.user.dto.request.UserSearchRequest;
import com.project.sb13ootbsteam04.user.dto.response.UserDto;
import com.project.sb13ootbsteam04.user.dto.response.UserDtoCursorResponse;
import com.project.sb13ootbsteam04.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 회원가입
    @PostMapping
    public ResponseEntity<UserDto> create(
            @Valid @RequestBody UserCreateRequest request
    ) {
        UserDto response = userService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // 계정 목록 조회
    @GetMapping
    public ResponseEntity<UserDtoCursorResponse> findAll(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam Integer limit,
            @RequestParam String sortBy,
            @RequestParam String sortDirection,
            @RequestParam(required = false) String emailLike,
            @RequestParam(required = false) String roleEqual,
            @RequestParam(required = false) Boolean locked
    ) {

        UserSearchRequest request = new UserSearchRequest(
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection,
                emailLike,
                roleEqual,
                locked
        );

        UserDtoCursorResponse response =
                userService.searchUsers(request);

        return ResponseEntity.ok(response);
    }
}
