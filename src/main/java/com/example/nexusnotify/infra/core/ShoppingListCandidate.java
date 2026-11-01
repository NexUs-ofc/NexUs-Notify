package com.example.nexusnotify.infra.core;

import java.time.LocalDate;

public record ShoppingListCandidate(
        Integer profileId,
        String shoppingListId,
        String title,
        LocalDate date,
        boolean completed
) {}
