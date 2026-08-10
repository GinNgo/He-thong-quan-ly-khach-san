import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { DatePickerModule } from 'primeng/datepicker';
import { Reservation, ReservationService } from '../../../core/services/reservation.service';
import { Room, RoomService } from '../../../core/services/room.service';

type TimelineStatus = 'ALL' | 'PENDING' | 'CONFIRMED' | 'CHECKED_IN' | 'CHECKED_OUT';

interface StatusOption {
  value: TimelineStatus;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-reservation-timeline',
  standalone: true,
  imports: [CommonModule, DatePickerModule, FormsModule, RouterLink],
  templateUrl: './reservation-timeline.component.html',
  styleUrls: ['./reservation-timeline.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReservationTimelineComponent implements OnInit {
  private readonly reservationService = inject(ReservationService);
  private readonly roomService = inject(RoomService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  readonly statusOptions: StatusOption[] = [
    { value: 'ALL', label: 'Tất cả', icon: 'pi pi-th-large' },
    { value: 'PENDING', label: 'Chờ xác nhận', icon: 'pi pi-clock' },
    { value: 'CONFIRMED', label: 'Đã xác nhận', icon: 'pi pi-check-circle' },
    { value: 'CHECKED_IN', label: 'Đang lưu trú', icon: 'pi pi-sign-in' },
    { value: 'CHECKED_OUT', label: 'Đã trả phòng', icon: 'pi pi-sign-out' },
  ];

  dates: Date[] = [];
  rooms: Room[] = [];
  totalRoomCount = 0;
  reservations: Reservation[] = [];
  startDate = this.startOfDay(new Date());
  endDate = this.addDays(this.startDate, 13);
  visibleDays = 14;
  selectedStatus: TimelineStatus = 'ALL';
  roomQuery = '';
  loading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.generateDates();
    this.loadData();
  }

  get filteredRooms(): Room[] {
    const query = this.roomQuery.trim().toLocaleLowerCase('vi');
    const matchingRoomIds = new Set(this.activeReservations
      .filter((reservation) => this.overlapsVisibleRange(reservation))
      .flatMap((reservation) => reservation.details?.map((detail) => detail.roomId) ?? []));
    return this.rooms.filter((room) => {
      if (this.selectedStatus !== 'ALL' && (!room.id || !matchingRoomIds.has(room.id))) return false;
      if (!query) return true;
      // Room endpoints have historically returned both nested and flattened room-type fields.
      const roomRecord = room as Room & { roomTypeNameVi?: string; roomTypeCode?: string };
      const searchableText = [
        room.roomNumber,
        room.floor,
        room.roomType?.nameVi,
        room.roomType?.code,
        roomRecord.roomTypeNameVi,
        roomRecord.roomTypeCode,
      ]
        .filter((value) => value !== null && value !== undefined)
        .join(' ')
        .toLocaleLowerCase('vi');
      return searchableText.includes(query);
    }).slice(0, 300);
  }

  get activeReservations(): Reservation[] {
    return this.reservations.filter((reservation) => this.matchesStatus(reservation));
  }

  get occupiedRoomCount(): number {
    const ids = new Set<number>();
    for (const reservation of this.activeReservations) {
      if (!this.overlapsVisibleRange(reservation)) continue;
      reservation.details?.forEach((detail) => ids.add(detail.roomId));
    }
    return ids.size;
  }

  get occupancyRate(): number {
    if (!this.rooms.length || !this.dates.length) return 0;
    let occupiedCells = 0;
    for (const room of this.rooms) {
      for (const date of this.dates) {
        if (this.getReservationForCell(room.id, date)) occupiedCells++;
      }
    }
    return Math.round((occupiedCells / (this.rooms.length * this.dates.length)) * 100);
  }

  loadData(): void {
    this.loading = true;
    this.errorMessage = '';
    forkJoin({
      rooms: this.roomService.getAllRooms(),
      reservations: this.reservationService.getAllReservations(),
    })
      .pipe(finalize(() => {
        this.loading = false;
        this.changeDetector.markForCheck();
      }))
      .subscribe({
        next: ({ rooms, reservations }) => {
          this.totalRoomCount = rooms.length;
          this.reservations = reservations.filter((reservation) => reservation.status !== 'CANCELLED');
          const bookedRoomIds = new Set(this.reservations.flatMap((reservation) =>
            reservation.details?.map((detail) => detail.roomId) ?? []));
          const sortedRooms = [...rooms].sort((a, b) => {
            const bookingPriority = Number(bookedRoomIds.has(b.id ?? -1)) - Number(bookedRoomIds.has(a.id ?? -1));
            return bookingPriority || a.roomNumber.localeCompare(b.roomNumber, 'vi', { numeric: true });
          });
          this.rooms = sortedRooms;
        },
        error: (error) => {
          this.errorMessage = error?.error?.message || 'Không thể tải lịch phòng. Vui lòng thử lại.';
        },
      });
  }

  onStartDateChange(): void {
    this.startDate = this.startOfDay(this.startDate || new Date());
    if (!this.endDate || this.endDate < this.startDate) {
      this.endDate = this.addDays(this.startDate, Math.max(this.visibleDays - 1, 0));
    }
    this.syncRangeFromDates();
  }

  onEndDateChange(): void {
    this.endDate = this.startOfDay(this.endDate || this.startDate);
    if (this.endDate < this.startDate) this.endDate = new Date(this.startDate);
    this.syncRangeFromDates();
  }

  private syncRangeFromDates(): void {
    const millisecondsPerDay = 86_400_000;
    const requestedDays = Math.floor((this.endDate.getTime() - this.startDate.getTime()) / millisecondsPerDay) + 1;
    this.visibleDays = Math.min(Math.max(requestedDays, 1), 31);
    this.endDate = this.addDays(this.startDate, this.visibleDays - 1);
    this.generateDates();
  }

  setVisibleDays(days: number): void {
    this.visibleDays = days;
    this.endDate = this.addDays(this.startDate, days - 1);
    this.generateDates();
  }

  moveRange(offset: number): void {
    const next = new Date(this.startDate);
    next.setDate(next.getDate() + offset);
    this.startDate = this.startOfDay(next);
    this.endDate = this.addDays(this.startDate, this.visibleDays - 1);
    this.generateDates();
  }

  goToToday(): void {
    this.startDate = this.startOfDay(new Date());
    this.endDate = this.addDays(this.startDate, this.visibleDays - 1);
    this.generateDates();
  }

  selectStatus(status: TimelineStatus): void {
    this.selectedStatus = status;
  }

  getReservationForCell(roomId: number | undefined, date: Date): Reservation | null {
    if (!roomId) return null;
    const day = this.dateKey(date);
    return this.activeReservations.find((reservation) => {
      const assigned = reservation.details?.some((detail) => detail.roomId === roomId) ?? false;
      return assigned && day >= reservation.checkInDate && day < reservation.checkOutDate;
    }) ?? null;
  }

  statusLabel(status?: string): string {
    return {
      PENDING: 'Chờ xác nhận',
      PENDING_PAYMENT: 'Chờ thanh toán',
      CONFIRMED: 'Đã xác nhận',
      CHECKED_IN: 'Đang lưu trú',
      CHECKED_OUT: 'Đã trả phòng',
    }[status ?? ''] ?? 'Không xác định';
  }

  statusClass(status?: string): string {
    return {
      PENDING: 'status-pending',
      PENDING_PAYMENT: 'status-pending',
      CONFIRMED: 'status-confirmed',
      CHECKED_IN: 'status-checked-in',
      CHECKED_OUT: 'status-checked-out',
    }[status ?? ''] ?? 'status-neutral';
  }

  guestName(reservation: Reservation): string {
    return reservation.userFullName || reservation.username || `Khách #${reservation.userId}`;
  }

  isToday(date: Date): boolean {
    return this.dateKey(date) === this.dateKey(new Date());
  }

  isWeekend(date: Date): boolean {
    return date.getDay() === 0 || date.getDay() === 6;
  }

  readonly trackDate = (_: number, date: Date): string => this.dateKey(date);

  trackRoom(_: number, room: Room): number | string {
    return room.id ?? room.roomNumber;
  }

  private generateDates(): void {
    const first = this.startOfDay(this.startDate);
    this.dates = Array.from({ length: this.visibleDays }, (_, index) => {
      const date = new Date(first);
      date.setDate(first.getDate() + index);
      return date;
    });
  }

  private matchesStatus(reservation: Reservation): boolean {
    if (this.selectedStatus === 'ALL') return true;
    if (this.selectedStatus === 'PENDING') {
      return reservation.status === 'PENDING' || reservation.status === 'PENDING_PAYMENT';
    }
    return reservation.status === this.selectedStatus;
  }

  private overlapsVisibleRange(reservation: Reservation): boolean {
    const rangeStart = this.dateKey(this.dates[0]);
    const rangeEnd = this.dateKey(this.dates[this.dates.length - 1]);
    return reservation.checkInDate <= rangeEnd && reservation.checkOutDate > rangeStart;
  }

  private dateKey(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private startOfDay(date: Date): Date {
    const result = new Date(date);
    result.setHours(0, 0, 0, 0);
    return result;
  }

  private addDays(date: Date, days: number): Date {
    const result = new Date(date);
    result.setDate(result.getDate() + days);
    return this.startOfDay(result);
  }
}
