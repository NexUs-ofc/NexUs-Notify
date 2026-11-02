package com.example.nexusnotify.service;

import com.example.nexusnotify.controller.dto.NotificationResponse;
import com.example.nexusnotify.controller.dto.SliceResponse;
import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationService service;
    private Notification notification;
    private ObjectId id;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository);
        id = new ObjectId();
        notification = new Notification(12, NotificationType.PRODUCT_EXPIRING, "45", "expiry:one-day",
                "Produto vencendo", "O leite vence amanha.");
        ReflectionTestUtils.setField(notification, "id", id);
    }

    @Test
    void listsTheProfileNotificationsAndNextSlice() {
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
}
