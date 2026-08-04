import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { finalize } from 'rxjs';

import {
  AvailablePhysicalRoom,
  AvailableRoomContext,
  ReservationService,
} from '@app/core/services/reservation.service';

@Component({
  selector: 'app-physical-room-picker',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './physical-room-picker.component.html',
  styleUrls: ['./physical-room-picker.component.css'],
})
export class PhysicalRoomPickerComponent implements OnChanges {
  private readonly reservationService = inject(ReservationService);
  private requestSequence = 0;

  @Input({ required: true }) reservationId!: number;
  @Input() initialSelectedRoomIds: number[] = [];
  @Output() readonly selectionChange = new EventEmitter<number[]>();

  context: AvailableRoomContext | null = null;
  loading = false;
  error = '';
  selectedRoomIds = new Set<number>();

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['reservationId'] && this.reservationId) this.load();
    if (changes['initialSelectedRoomIds'] && this.context) this.reconcileSelection();
  }

  load(): void {
    if (!this.reservationId) return;
    const requestId = ++this.requestSequence;
    const reservationId = this.reservationId;
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
    if (!this.context) return;
    if (this.selectedRoomIds.has(room.id)) {
      this.selectedRoomIds.delete(room.id);
    } else if (this.selectedRoomIds.size < this.context.requiredQuantity) {
      this.selectedRoomIds.add(room.id);
    }
    this.selectedRoomIds = new Set(this.selectedRoomIds);
    this.emitSelection();
  }

  isSelected(roomId: number): boolean {
    return this.selectedRoomIds.has(roomId);
  }

  get selectedCount(): number {
    return this.selectedRoomIds.size;
  }

  get hasShortage(): boolean {
    return Boolean(this.context && this.context.candidates.length > 0
      && this.context.candidates.length < this.context.requiredQuantity);
  }

  get isEmpty(): boolean {
    return Boolean(this.context && this.context.candidates.length === 0);
  }

  get selectionComplete(): boolean {
    return Boolean(this.context && this.selectedCount === this.context.requiredQuantity);
  }

  trackRoom(_: number, room: AvailablePhysicalRoom): number {
    return room.id;
  }

  private emitSelection(): void {
    this.selectionChange.emit([...this.selectedRoomIds].sort((left, right) => left - right));
  }

  private reconcileSelection(): void {
    if (!this.context) return;
    const candidateIds = new Set(this.context.candidates.map(room => room.id));
    const previous = this.selectedRoomIds.size
      ? [...this.selectedRoomIds]
      : this.initialSelectedRoomIds;
    this.selectedRoomIds = new Set(previous.filter(id => candidateIds.has(id)));
    this.emitSelection();
  }
}
