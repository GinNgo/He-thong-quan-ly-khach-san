import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import {
  CustomerNotification,
  CustomerNotificationService,
  NotificationChannel,
  NotificationEventClass,
  NotificationPreferenceGroup,
} from '../../../core/services/customer-notification.service';

@Component({
  selector: 'app-customer-notifications',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './customer-notifications.component.html',
  styleUrl: './customer-notifications.component.css',
})
export class CustomerNotificationsComponent implements OnInit, OnDestroy {
  private readonly notificationsApi = inject(CustomerNotificationService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  notifications: CustomerNotification[] = [];
  unreadCount = 0;
  page = 0;
  totalPages = 0;
  loading = true;
  errorMessage = '';
  archivedView = false;
  retentionDays = 365;
  settingsOpen = false;
  settingsLoading = false;
  settingsSaving = false;
  settingsMessage = '';
  settingsError = '';
  preferences: NotificationPreferenceGroup[] = [];

  ngOnInit(): void {
    this.notificationsApi.notifications$
      .pipe(takeUntil(this.destroy$))
      .subscribe(notification => {
        if (this.archivedView) return;
        if (this.notifications.some(item => item.id === notification.id)) return;
        this.notifications = [notification, ...this.notifications];
        if (!notification.isRead) this.unreadCount += 1;
        this.changeDetector.markForCheck();
      });
    this.notificationsApi.reconciliation$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.reconcilePersistedState());
    this.notificationsApi.connect();
    this.loadPage(0);
  }

  loadPage(page: number): void {
    this.loading = true;
    this.errorMessage = '';
    this.notificationsApi.getInbox(page, 20, this.archivedView)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.applyPage(result);
          this.loading = false;
          this.changeDetector.markForCheck();
        },
        error: () => {
          this.errorMessage = 'Khong the tai thong bao. Vui long thu lai.';
          this.loading = false;
          this.changeDetector.markForCheck();
        },
      });
  }

  markAsRead(notification: CustomerNotification): void {
    if (notification.isRead) return;
    this.notificationsApi.markAsRead(notification.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(updated => {
        this.notifications = this.notifications.map(item => item.id === updated.id ? updated : item);
        this.unreadCount = Math.max(0, this.unreadCount - 1);
        this.changeDetector.markForCheck();
      });
  }

  showHistory(archived: boolean): void {
    if (this.archivedView === archived && !this.errorMessage) return;
    this.archivedView = archived;
    this.loadPage(0);
  }

  archive(notification: CustomerNotification): void {
    this.notificationsApi.archive(notification.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadPage(this.page));
  }

  restore(notification: CustomerNotification): void {
    this.notificationsApi.restore(notification.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => this.loadPage(this.page));
  }

  toggleSettings(): void {
    this.settingsOpen = !this.settingsOpen;
    if (this.settingsOpen && this.preferences.length === 0) {
      this.loadPreferences();
    }
  }

  setPreference(
    eventClass: NotificationEventClass,
    channel: NotificationChannel,
    enabled: boolean,
  ): void {
    this.preferences = this.preferences.map(group => group.eventClass !== eventClass ? group : ({
      ...group,
      channels: group.channels.map(option => option.channel !== channel || option.locked
        ? option
        : { ...option, enabled }),
    }));
    this.settingsMessage = '';
  }

  savePreferences(): void {
    const updates = this.preferences.flatMap(group => group.channels.map(option => ({
      eventClass: group.eventClass,
      channel: option.channel,
      enabled: option.enabled,
    })));
    this.settingsSaving = true;
    this.settingsError = '';
    this.notificationsApi.updatePreferences(updates)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: preferences => {
          this.preferences = preferences;
          this.settingsSaving = false;
          this.settingsMessage = 'Da luu tuy chon thong bao.';
          this.changeDetector.markForCheck();
        },
        error: () => {
          this.settingsSaving = false;
          this.settingsError = 'Khong the luu tuy chon. Vui long thu lai.';
          this.changeDetector.markForCheck();
        },
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private reconcilePersistedState(): void {
    this.notificationsApi.getInbox(this.page, 20, this.archivedView)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.applyPage(result);
          this.errorMessage = '';
          this.changeDetector.markForCheck();
        },
        error: () => {
          // Keep the last persisted view; the next reconnect retries reconciliation.
        },
      });
  }

  private applyPage(result: {
    content: CustomerNotification[];
    unreadCount: number;
    number: number;
    totalPages: number;
    retentionDays?: number;
  }): void {
    this.notifications = result.content;
    this.unreadCount = result.unreadCount;
    this.page = result.number;
    this.totalPages = result.totalPages;
    this.retentionDays = result.retentionDays ?? this.retentionDays;
  }

  private loadPreferences(): void {
    this.settingsLoading = true;
    this.settingsError = '';
    this.notificationsApi.getPreferences()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: preferences => {
          this.preferences = preferences;
          this.settingsLoading = false;
          this.changeDetector.markForCheck();
        },
        error: () => {
          this.settingsLoading = false;
          this.settingsError = 'Khong the tai tuy chon thong bao.';
          this.changeDetector.markForCheck();
        },
      });
  }
}
