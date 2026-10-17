package com.example.nexusnotify.config.properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NearbyStorePropertiesTest {

    @Test
    void acceptsTheAgreedRadiusAndMinimumInterval() {
        NearbyStoreProperties properties = new NearbyStoreProperties(true, 5, 3);

        assertThat(properties.radiusKm()).isEqualTo(5);
        assertThat(properties.intervalDays()).isEqualTo(3);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, 100.01, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidRadii(double radius) {
        assertThatThrownBy(() -> new NearbyStoreProperties(true, radius, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 2})
    void rejectsAnIntervalShorterThanThreeDays(int days) {
        assertThatThrownBy(() -> new NearbyStoreProperties(true, 5, days))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
