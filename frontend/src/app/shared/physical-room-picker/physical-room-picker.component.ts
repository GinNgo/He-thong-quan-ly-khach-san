import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  AvailablePhysicalRoom,
  AvailableRoomContext,
  Reservation,
  ReservationService,
} from '@app/core/services/reservation.service';

@Component({
  selector: 'app-physical-room-picker',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './physical-room-picker.component.html',
  styleUrls: ['./physical-room-picker.component.css'],
})
export class PhysicalRoomPickerComponent implements OnChanges {
  private readonly reservationService = inject(ReservationService);
  private readonly mutationKeys = new Map<string, string>();
  private requestSequence = 0;

  @Input({ required: true }) reservationId!: number;
  @Input() initialSelectedRoomIds: number[] = [];
  @Input() allowMutation = false;
  @Output() readonly selectionChange = new EventEmitter<number[]>();
  @Output() readonly assignmentApplied = new EventEmitter<Reservation>();

  context: AvailableRoomContext | null = null;
  loading = false;
  error = '';
  selectedRoomIds = new Set<number>();
  reason = '';
  mutationBusy = false;
  mutationError = '';
  mutationSuccess = '';
  releaseConfirmation = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['reservationId'] && this.reservationId) this.load(true);
    if (changes['initialSelectedRoomIds'] && this.context) this.reconcileSelection();
  }

  load(resetSelection = false): void {
    if (!this.reservationId) return;
    const requestId = ++this.requestSequence;
    const reservationId = this.reservationId;
    if (resetSelection) this.selectedRoomIds.clear();
    this.loading = true;
    this.error = '';
    this.reservationService.getAvailableRoomContext(reservationId).pipe(
      finalize(() => {
        if (requestId === this.requestSequence) this.loading = false;
      }),
    ).subscribe({
      next: context => {
        if (requestId !== this.requestSequence || context.reservationId !== this.reservationId) return;
        this.context = context;
        this.reconcileSelection();
      },
      error: error => {
        if (requestId !== this.requestSequence || reservationId !== this.reservationId) return;
        this.context = null;
        this.selectedRoomIds.clear();
        this.error = error?.error?.message || 'Không thể tải danh sách phòng vật lý sẵn sàng.';
        this.emitSelection();
      },
    });
  }

  toggle(room: AvailablePhysicalRoom): void {
    if (!this.context || !this.allowMutation || this.mutationBusy) return;
    if (this.selectedRoomIds.has(room.id)) {
      this.selectedRoomIds.delete(room.id);
    } else if (this.selectedRoomIds.size < this.context.requiredQuantity) {
      this.selectedRoomIds.add(room.id);
    }
    this.selectedRoomIds = new Set(this.selectedRoomIds);
    this.mutationError = '';
    this.mutationSuccess = '';
    this.emitSelection();
  }

  applyAssignment(): void {
    if (!this.canApplyAssignment || !this.context) return;
    const roomIds = [...this.selectedRoomIds].sort((left, right) => left - right);
    const normalizedReason = this.reason.trim();
    const operation = this.hasAssignment ? 'reassign' : 'assign';
    const signature = `${operation}:${roomIds.join(',')}:${normalizedReason}`;
    this.runMutation(
      this.reservationService.updateRoomAssignment(
        this.reservationId,
        { roomIds, reason: normalizedReason },
        this.idempotencyKey(signature),
      ),
      this.hasAssignment ? 'Đã gán lại phòng.' : 'Đã gán phòng.',
    );
  }

  releaseAssignment(): void {
    if (!this.canReleaseAssignment || !this.releaseConfirmation) return;
    const normalizedReason = this.reason.trim();
    const signature = `release:${this.currentAssignedIds.join(',')}:${normalizedReason}`;
    this.releaseConfirmation = false;
    this.runMutation(
      this.reservationService.releaseRoomAssignment(
        this.reservationId,
        normalizedReason,
        this.idempotencyKey(signature),
      ),
      'Đã giải phóng phòng.',
    );
  }

  beginRelease(): void {
    if (this.canReleaseAssignment) this.releaseConfirmation = true;
  }

  cancelRelease(): void {
    this.releaseConfirmation = false;
  }

  isSelected(roomId: number): boolean {
    return this.selectedRoomIds.has(roomId);
  }

  isCurrentlyAssigned(roomId: number): boolean {
    return this.currentAssignedIds.includes(roomId);
  }

  get visibleRooms(): AvailablePhysicalRoom[] {
    if (!this.context) return [];
    const rooms = [...(this.context.assignedRooms || []), ...this.context.candidates];
    return [...new Map(rooms.map(room => [room.id, room])).values()].sort((left, right) =>
      left.floor - right.floor || left.roomNumber.localeCompare(right.roomNumber) || left.id - right.id);
  }

  get currentAssignedIds(): number[] {
    return [...(this.context?.assignedRoomIds || [])].sort((left, right) => left - right);
  }

  get selectedCount(): number {
    return this.selectedRoomIds.size;
  }

  get hasAssignment(): boolean {
    return this.currentAssignedIds.length > 0;
  }

  get hasShortage(): boolean {
    return Boolean(this.context && this.visibleRooms.length > 0
      && this.visibleRooms.length < this.context.requiredQuantity);
  }

  get isEmpty(): boolean {
    return Boolean(this.context && this.visibleRooms.length === 0);
  }

  get selectionComplete(): boolean {
    return Boolean(this.context && this.selectedCount === this.context.requiredQuantity);
  }

  get selectionChanged(): boolean {
    return this.currentAssignedIds.join(',') !== [...this.selectedRoomIds]
      .sort((left, right) => left - right)
      .join(',');
  }

  get reasonValid(): boolean {
    const length = this.reason.trim().length;
    return length >= 3 && length <= 500;
  }

  get canApplyAssignment(): boolean {
    return this.allowMutation && !this.mutationBusy && this.selectionComplete
      && this.selectionChanged && this.reasonValid;
  }

  get canReleaseAssignment(): boolean {
    return this.allowMutation && !this.mutationBusy && this.hasAssignment && this.reasonValid;
  }

  trackRoom(_: number, room: AvailablePhysicalRoom): number {
    return room.id;
  }

  private runMutation(request$: ReturnType<ReservationService['updateRoomAssignment']>, success: string): void {
    this.mutationBusy = true;
    this.mutationError = '';
    this.mutationSuccess = '';
    this.releaseConfirmation = false;
    request$.pipe(finalize(() => this.mutationBusy = false)).subscribe({
      next: reservation => {
        this.reason = '';
        this.mutationKeys.clear();
        this.selectedRoomIds.clear();
        this.mutationSuccess = success;
        this.assignmentApplied.emit(reservation);
        this.load(true);
      },
      error: error => {
        const conflict = error?.status === 409
          || ['CONFLICT', 'CONCURRENT_MODIFICATION', 'DATA_CONFLICT'].includes(error?.error?.code);
        this.mutationError = conflict
          ? 'Kho phòng vừa thay đổi. Danh sách đã được làm mới; vui lòng xác nhận lại.'
          : (error?.error?.message || 'Không thể cập nhật phân phòng.');
        if (conflict) this.load(true);
      },
    });
  }

  private emitSelection(): void {
    this.selectionChange.emit([...this.selectedRoomIds].sort((left, right) => left - right));
  }

  private reconcileSelection(): void {
    if (!this.context) return;
    const visibleIds = new Set(this.visibleRooms.map(room => room.id));
    const previous = this.selectedRoomIds.size
      ? [...this.selectedRoomIds]
      : (this.initialSelectedRoomIds.length ? this.initialSelectedRoomIds : this.context.assignedRoomIds);
    this.selectedRoomIds = new Set(previous.filter(id => visibleIds.has(id)));
    this.emitSelection();
  }

  private idempotencyKey(signature: string): string {
    const existing = this.mutationKeys.get(signature);
    if (existing) return existing;
    const generated = globalThis.crypto?.randomUUID?.()
      || `room-assignment-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    this.mutationKeys.set(signature, generated);
    return generated;
  }
}
