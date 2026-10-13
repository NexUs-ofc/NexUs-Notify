package com.example.nexusnotify.service;

import com.example.nexusnotify.domain.Notification;
import com.example.nexusnotify.domain.NotificationType;
import com.example.nexusnotify.infra.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWriterTest {

    private static final String ALERT_KEY = "expiry:2026-10-15:days:3";
    private NotificationRepository repository;
    private NotificationWriter writer;
    private Notification notification;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        writer = new NotificationWriter(repository);
        notification = new Notification(12, NotificationType.PRODUCT_EXPIRING, "45", ALERT_KEY,
                "Produto próximo do vencimento", "O produto Leite vence em 3 dias.");
    }

    @Test
    void insertsANewReminder() {
        assertTrue(writer.saveIfAbsent(notification));
        verify(repository).existsByProfileIdAndTypeAndSourceIdAndAlertKey(
                12, NotificationType.PRODUCT_EXPIRING, "45", ALERT_KEY);
        verify(repository).insert(notification);
    }

    @Test
    void skipsAnExistingReminder() {
        when(repository.existsByProfileIdAndTypeAndSourceIdAndAlertKey(
                12, NotificationType.PRODUCT_EXPIRING, "45", ALERT_KEY)).thenReturn(true);

        assertFalse(writer.saveIfAbsent(notification));
        verify(repository, never()).insert(any(Notification.class));
    }

    @Test
    void acceptsTheSameReminderInsertedByAnotherExecution() {
        when(repository.existsByProfileIdAndTypeAndSourceIdAndAlertKey(
                12, NotificationType.PRODUCT_EXPIRING, "45", ALERT_KEY)).thenReturn(false, true);
        when(repository.insert(notification)).thenThrow(new DuplicateKeyException("duplicate"));

        assertFalse(writer.saveIfAbsent(notification));
        verify(repository, times(2)).existsByProfileIdAndTypeAndSourceIdAndAlertKey(
                12, NotificationType.PRODUCT_EXPIRING, "45", ALERT_KEY);
    }

    @Test
    void propagatesAnUnrelatedDuplicateError() {
        DuplicateKeyException exception = new DuplicateKeyException("another unique index");
        when(repository.insert(notification)).thenThrow(exception);

        assertSame(exception, assertThrows(DuplicateKeyException.class, () -> writer.saveIfAbsent(notification)));
    }

    @Test
    void propagatesDatabaseFailures() {
        DataAccessResourceFailureException exception = new DataAccessResourceFailureException("unavailable");
        when(repository.insert(notification)).thenThrow(exception);

        assertSame(exception, assertThrows(DataAccessResourceFailureException.class,
                () -> writer.saveIfAbsent(notification)));
    }

    @Test
    void considersTheProfileAndSourceWhenCheckingDuplicates() {
        when(repository.existsByProfileIdAndTypeAndSourceIdAndAlertKey(
                12, NotificationType.PRODUCT_EXPIRING, "45", ALERT_KEY)).thenReturn(true);
        Notification otherProfile = new Notification(13, NotificationType.PRODUCT_EXPIRING, "45", ALERT_KEY,
                "Produto próximo do vencimento", "O produto Leite vence em 3 dias.");
        Notification otherProduct = new Notification(12, NotificationType.PRODUCT_EXPIRING, "46", ALERT_KEY,
                "Produto próximo do vencimento", "O produto Leite vence em 3 dias.");

        assertFalse(writer.saveIfAbsent(notification));
        assertTrue(writer.saveIfAbsent(otherProfile));
        assertTrue(writer.saveIfAbsent(otherProduct));

        verify(repository).insert(otherProfile);
        verify(repository).insert(otherProduct);
    }
}
