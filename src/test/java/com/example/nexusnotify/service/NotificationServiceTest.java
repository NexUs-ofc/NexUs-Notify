package com.example.nexusnotify.service;

import com.example.nexusnotify.controller.dto.NotificationResponse;
import com.example.nexusnotify.controller.dto.SliceResponse;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.exception.NotFoundException;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T14:00:00Z");
    private NotificationRepository repository;
    private NotificationService service;
    private Notification notification;
    private ObjectId id;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        id = new ObjectId();
        notification = new Notification(12, NotificationType.PRODUCT_EXPIRING, "45", "expiry:one-day",
                "Produto vencendo", "O leite vence amanha.");
        ReflectionTestUtils.setField(notification, "id", id);
    }

    @Test
    void listsTheProfileNotificationsWithTheirReadStatusAndNextSlice() {
        Instant readAt = NOW.minusSeconds(60);
        notification.markAsRead(readAt);
        Pageable pageable = PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "id"));
        when(repository.findByProfileIdOrderByCreatedAtDesc(12, pageable))
                .thenReturn(new SliceImpl<>(List.of(notification), pageable, true));

        SliceResponse<NotificationResponse> response = service.list(12, 2, 10);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(id.toHexString());
            assertThat(item.type()).isEqualTo(NotificationType.PRODUCT_EXPIRING);
            assertThat(item.sourceId()).isEqualTo("45");
            assertThat(item.title()).isEqualTo(notification.getTitle());
            assertThat(item.message()).isEqualTo(notification.getMessage());
            assertThat(item.createdAt()).isEqualTo(notification.getCreatedAt());
            assertThat(item.readAt()).isEqualTo(readAt);
            assertThat(item.read()).isTrue();
        });
        verify(repository).findByProfileIdOrderByCreatedAtDesc(12, pageable);
    }

    @Test
    void returnsAnEmptyLastSlice() {
        when(repository.findByProfileIdOrderByCreatedAtDesc(eq(12), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of()));

        SliceResponse<NotificationResponse> response = service.list(12, 0, 20);

        assertThat(response.content()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void rejectsInvalidPaginationBeforeQueryingMongo(int page, int size) {
        assertThrows(IllegalArgumentException.class, () -> service.list(12, page, size));
        verifyNoInteractions(repository);
    }

    @Test
    void updatesOnlyTheReadTimeOfTheProfileNotification() {
        when(repository.markAsRead(id, 12, NOW)).thenReturn(1L);

        service.markAsRead(12, id.toHexString());

        verify(repository).markAsRead(id, 12, NOW);
        verify(repository, never()).findByIdAndProfileId(any(ObjectId.class), any(Integer.class));
        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void acceptsAnAlreadyReadNotificationWithoutSavingItAgain() {
        notification.markAsRead(NOW.minusSeconds(60));
        when(repository.findByIdAndProfileId(id, 12)).thenReturn(Optional.of(notification));

        service.markAsRead(12, id.toHexString());

        assertThat(notification.getReadAt()).isEqualTo(NOW.minusSeconds(60));
        verify(repository).markAsRead(id, 12, NOW);
        verify(repository, never()).save(any(Notification.class));
    }

    @Test
    void rejectsANotificationThatDoesNotBelongToTheProfile() {
        when(repository.findByIdAndProfileId(id, 13)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.markAsRead(13, id.toHexString()));

        verify(repository).markAsRead(id, 13, NOW);
        verify(repository).findByIdAndProfileId(id, 13);
        verify(repository, never()).findById(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "", "123", "zzzzzzzzzzzzzzzzzzzzzzzz"})
    void rejectsInvalidIdsBeforeQueryingMongo(String invalidId) {
        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(12, invalidId));
        verifyNoInteractions(repository);
    }
}
