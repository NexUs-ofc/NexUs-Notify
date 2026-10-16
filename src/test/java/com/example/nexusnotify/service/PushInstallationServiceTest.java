package com.example.nexusnotify.service;

import com.example.nexusnotify.controller.dto.PushInstallationResponse;
import com.example.nexusnotify.domain.PushInstallation;
import com.example.nexusnotify.domain.PushInstallationStatus;
import com.example.nexusnotify.infra.repository.PushInstallationRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushInstallationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private PushInstallationRepository repository;
    private PushInstallationService service;

    @BeforeEach
    void setUp() {
        repository = mock(PushInstallationRepository.class);
        service = new PushInstallationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsTheInternalIdAndUsesTheServerClock() {
        ObjectId id = new ObjectId();
        when(repository.register(12, "fid-one", NOW)).thenReturn(new PushInstallation(id, 12, "fid-one",
                PushInstallationStatus.ACTIVE, NOW, NOW, NOW, null, null));

        PushInstallationResponse response = service.register(12, "fid-one");

        assertThat(response.id()).isEqualTo(id.toHexString());
        assertThat(response.status()).isEqualTo(PushInstallationStatus.ACTIVE);
        verify(repository).register(12, "fid-one", NOW);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " fid-one", "fid-one ", "fid\none", "fid.with.dot", "fid/token", "fid:token"})
    void rejectsInvalidFids(String fid) {
        assertThatThrownBy(() -> service.register(12, fid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Firebase Installation ID invalido");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsOversizedFids() {
        assertThatThrownBy(() -> service.register(12, "a".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "123", "zzzzzzzzzzzzzzzzzzzzzzzz"})
    void rejectsInvalidInternalIds(String id) {
        assertThatThrownBy(() -> service.revoke(12, id)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Id do cadastro de instalacao invalido");
        verifyNoInteractions(repository);
    }

    @Test
    void revokesUsingTheInternalIdAndAuthenticatedProfile() {
        ObjectId id = new ObjectId();

        service.revoke(12, id.toHexString());

        verify(repository).revoke(id, 12, NOW);
    }

    @Test
    void propagatesDatabaseFailures() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("unavailable");
        when(repository.register(12, "fid-one", NOW)).thenThrow(failure);

        assertThatThrownBy(() -> service.register(12, "fid-one")).isSameAs(failure);
    }
}
