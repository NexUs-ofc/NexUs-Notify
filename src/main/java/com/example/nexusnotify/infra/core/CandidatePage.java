package com.example.nexusnotify.infra.core;

import java.util.List;

public record CandidatePage<T>(List<T> content, boolean hasNext) {}
