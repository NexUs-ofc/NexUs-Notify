package com.example.nexusnotify.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Document(collection = "nearby_store_rotations")
public class NearbyStoreRotation {

    @Id
    private Integer profileId;

    @Version
    private Long version;

    private long cycle = 1;

    @Field("shown_store_ids")
    private Set<Integer> shownStoreIds = new HashSet<>();

    @Field("last_store_id")
    private Integer lastStoreId;

    @Field("next_eligible_at")
    private Instant nextEligibleAt;

    protected NearbyStoreRotation() {}

    public NearbyStoreRotation(Integer profileId) {
        this.profileId = profileId;
    }

    public boolean canSuggestAt(Instant now) {
        return nextEligibleAt == null || !now.isBefore(nextEligibleAt);
    }

    public long getCycle() {
        return cycle;
    }

    public Set<Integer> getShownStoreIds() {
        return Set.copyOf(shownStoreIds);
    }

    public Integer getLastStoreId() {
        return lastStoreId;
    }

    public Instant getNextEligibleAt() {
        return nextEligibleAt;
    }

    public void registerSuggestion(Integer storeId, long selectedCycle, Instant nextEligibleAt) {
        if (cycle != selectedCycle) {
            shownStoreIds.clear();
        }
        cycle = selectedCycle;
        shownStoreIds.add(storeId);
        lastStoreId = storeId;
        this.nextEligibleAt = nextEligibleAt;
    }
}
