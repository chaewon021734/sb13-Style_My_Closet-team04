package com.project.sb13ootbsteam04.user.dto.request;

import java.util.UUID;

public record UserSearchRequest(
        String cursor,
        UUID idAfter,
        Integer limit,
        String sortBy,
        String sortDirection,
        String emailLike,
        String roleEqual,
        Boolean locked
) {
}
