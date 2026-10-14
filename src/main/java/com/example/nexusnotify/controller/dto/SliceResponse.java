package com.example.nexusnotify.controller.dto;

import java.util.List;

public record SliceResponse<T>(List<T> content, boolean hasNext) {
}
