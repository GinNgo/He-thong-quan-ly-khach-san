import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { CheckInReadiness, Reservation, ReservationService } from '../../core/services/reservation.service';

@Component({
  selector: 'app-check-in-readiness',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './check-in-readiness.component.html',
  styleUrls: ['./check-in-readiness.component.css'],
})
export class CheckInReadinessComponent implements OnChanges {
  @Input({ required: true }) reservationId!: number;
  @Input() canAssignRooms = false;
  @Output() checkedIn = new EventEmitter<Reservation>();
  @Output() assignmentRequested = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  readiness: CheckInReadiness | null = null;
  loading = false;
  submitting = false;
  error = '';
  confirmed = false;
  requiresReconfirmation = false;
  private requestVersion = 0;
  private idempotencyKey = '';
  private readonly translate = inject(TranslateService, { optional: true });

  constructor(private readonly reservations: ReservationService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['reservationId'] && this.reservationId) this.refresh();
  }

  refresh(): void {
    const version = ++this.requestVersion;
    this.loading = true;
    this.error = '';
    this.confirmed = false;
    this.requiresReconfirmation = false;
    this.reservations.getCheckInReadiness(this.reservationId)
      .pipe(finalize(() => { if (version === this.requestVersion) this.loading = false; }))
      .subscribe({
        next: readiness => {
          if (version !== this.requestVersion) return;
          this.readiness = readiness;
          this.idempotencyKey = this.newKey();
        },
        error: () => {
          if (version !== this.requestVersion) return;
          this.readiness = null;
          this.error = this.copy('loadError');
        },
      });
  }

  confirmCheckIn(): void {
    if (!this.readiness?.ready || !this.confirmed || this.loading || this.submitting) return;
    this.submitting = true;
    this.error = '';
    this.reservations.checkIn(this.reservationId, this.idempotencyKey)
      .pipe(finalize(() => this.submitting = false))
      .subscribe({
        next: reservation => this.checkedIn.emit(reservation),
        error: (error: HttpErrorResponse) => {
          if (error.status === 409) {
            this.requiresReconfirmation = true;
            this.refreshAfterConflict();
            return;
          }
          this.error = this.copy('submitError');
        },
      });
  }

  hasBlocker(code: string): boolean {
    return Boolean(this.readiness?.blockers.some(blocker => blocker.code === code));
  }

  blockerText(code: string, fallback: string): string {
    return this.copy(`blocker.${code}`, fallback);
  }

  assignedRoomLabels(state: CheckInReadiness): string {
    return state.assignedRooms.length
      ? state.assignedRooms.map(room => room.roomNumber).join(', ')
      : '—';
  }

  copy(key: string, fallback?: string): string {
    const language = this.translate?.getCurrentLang() === 'en' ? 'en' : 'vi';
    return COPY[language][key] || fallback || key;
  }

  private refreshAfterConflict(): void {
    const version = ++this.requestVersion;
    this.loading = true;
    this.confirmed = false;
    this.reservations.getCheckInReadiness(this.reservationId)
      .pipe(finalize(() => { if (version === this.requestVersion) this.loading = false; }))
      .subscribe({
        next: readiness => {
          if (version !== this.requestVersion) return;
          this.readiness = readiness;
          this.idempotencyKey = this.newKey();
          this.error = this.copy('conflict');
        },
        error: () => { if (version === this.requestVersion) this.error = this.copy('loadError'); },
      });
  }

  private newKey(): string {
    return `check-in-${this.reservationId}-${crypto.randomUUID()}`;
  }
}

const COPY: Record<'vi' | 'en', Record<string, string>> = {
  vi: {
    title: 'Sẵn sàng nhận phòng', close: 'Đóng', retry: 'Tải lại', loading: 'Đang kiểm tra điều kiện nhận phòng',
    loadError: 'Không thể tải điều kiện nhận phòng. Vui lòng thử lại.', submitError: 'Không thể nhận phòng. Hãy tải lại điều kiện và thử lại.',
    conflict: 'Dữ liệu vừa thay đổi. Hãy kiểm tra lại và xác nhận lần nữa.', assigned: 'Phòng đã gán', window: 'Khung giờ nhận phòng',
    confirmLabel: 'Tôi đã kiểm tra khách và phòng được gán', confirm: 'Xác nhận nhận phòng', assign: 'Gán phòng ngay', done: 'Đặt phòng này đã được nhận phòng.',
    'blocker.INVALID_RESERVATION_STATUS': 'Trạng thái đặt phòng chưa cho phép nhận phòng.',
    'blocker.ARRIVAL_WINDOW_NOT_OPEN': 'Chưa đến khung giờ nhận phòng.', 'blocker.STAY_WINDOW_CLOSED': 'Thời gian lưu trú đã kết thúc.',
    'blocker.MISSING_ROOM_ASSIGNMENT': 'Chưa gán đủ phòng vật lý.', 'blocker.ASSIGNMENT_PROPERTY_MISMATCH': 'Phòng được gán không thuộc cơ sở này.',
    'blocker.ROOM_NOT_READY': 'Có phòng chưa sạch hoặc đang bảo trì.',
  },
  en: {
    title: 'Check-in readiness', close: 'Close', retry: 'Refresh', loading: 'Checking authoritative readiness',
    loadError: 'Unable to load check-in readiness. Please retry.', submitError: 'Unable to check in. Refresh readiness and retry.',
    conflict: 'The reservation changed. Review the refreshed readiness and confirm again.', assigned: 'Assigned rooms', window: 'Check-in window',
    confirmLabel: 'I verified the guest and assigned rooms', confirm: 'Confirm check-in', assign: 'Assign rooms', done: 'This reservation is already checked in.',
    'blocker.INVALID_RESERVATION_STATUS': 'The reservation status does not allow check-in.',
    'blocker.ARRIVAL_WINDOW_NOT_OPEN': 'The approved arrival window is not open.', 'blocker.STAY_WINDOW_CLOSED': 'The stay window has closed.',
    'blocker.MISSING_ROOM_ASSIGNMENT': 'The required physical rooms are not assigned.', 'blocker.ASSIGNMENT_PROPERTY_MISMATCH': 'An assigned room belongs to another property.',
    'blocker.ROOM_NOT_READY': 'An assigned room is not clean or operational.',
  },
};
