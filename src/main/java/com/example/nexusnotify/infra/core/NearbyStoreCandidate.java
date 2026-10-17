package com.example.nexusnotify.infra.core;

import java.util.List;

public record NearbyStoreCandidate(Integer profileId, List<NearbyStoreOption> stores) {}
