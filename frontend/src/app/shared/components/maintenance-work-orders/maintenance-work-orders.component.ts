import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import {
  MaintenancePriority,
  MaintenanceWorkOrder,
  MaintenanceWorkOrderService,
  MaintenanceWorkOrderStatus,
} from '../../../core/services/maintenance-work-order.service';

@Component({
  selector: 'app-maintenance-work-orders',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './maintenance-work-orders.component.html',
  styleUrl: './maintenance-work-orders.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MaintenanceWorkOrdersComponent implements OnChanges {
  private readonly api = inject(MaintenanceWorkOrderService);
  private readonly cdr = inject(ChangeDetectorRef);

  @Input({ required: true }) propertyId!: number;
  @Input({ required: true }) roomId!: number;
  @Input() roomNumber = '';
  @Input() canCreate = false;
  @Input() canUpdate = false;
  @Input() canCancel = false;
  @Output() closeRequested = new EventEmitter<void>();
  @Output() changed = new EventEmitter<void>();

  orders: MaintenanceWorkOrder[] = [];
  loading = false;
  saving = false;
  transitioningId?: number;
  transitionNotes: Record<number, string> = {};
  error = '';
  form: { reason: string; priority: MaintenancePriority; assigneeUserId?: number; scheduledStart: string; scheduledEnd: string } = this.emptyForm();
  readonly priorities: MaintenancePriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['propertyId'] || changes['roomId']) && this.propertyId && this.roomId) this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.api.getAll(this.propertyId, this.roomId).pipe(finalize(() => {
      this.loading = false;
      this.cdr.markForCheck();
    })).subscribe({
      next: orders => this.orders = [...orders].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || '')),
      error: e => this.error = e?.error?.message || 'Khong the tai danh sach phieu bao tri.',
    });
  }

  create(): void {
    const reason = this.form.reason.trim();
    if (!this.canCreate || this.saving || !reason) {
      if (!reason) this.error = 'Vui long nhap ly do bao tri.';
      return;
    }
    this.saving = true;
    this.error = '';
    this.api.create({
      propertyId: this.propertyId,
      roomId: this.roomId,
      reason,
      priority: this.form.priority,
      assigneeUserId: this.form.assigneeUserId || undefined,
      scheduledStart: this.form.scheduledStart || undefined,
      scheduledEnd: this.form.scheduledEnd || undefined,
    }).pipe(finalize(() => {
      this.saving = false;
      this.cdr.markForCheck();
    })).subscribe({
      next: order => {
        this.orders = [order, ...this.orders];
        this.form = this.emptyForm();
        this.changed.emit();
      },
      error: e => this.error = e?.error?.message || 'Khong the tao phieu bao tri. Kiem tra anh huong dat phong va thu lai.',
    });
  }

  transition(order: MaintenanceWorkOrder, action: 'start' | 'complete' | 'reopen' | 'cancel'): void {
    if (this.transitioningId || !this.canRun(action)) return;
    const note = (this.transitionNotes[order.id] || '').trim();
    if ((action === 'reopen' || action === 'cancel') && !note) {
      this.error = action === 'reopen' ? 'Vui long nhap ly do mo lai.' : 'Vui long nhap ly do huy phieu.';
      return;
    }
    this.transitioningId = order.id;
    this.error = '';
    const request = action === 'start' ? this.api.start(order.id)
      : action === 'complete' ? this.api.complete(order.id, note || undefined)
      : action === 'reopen' ? this.api.reopen(order.id, note)
      : this.api.cancel(order.id, note);
    request.pipe(finalize(() => {
      this.transitioningId = undefined;
      this.cdr.markForCheck();
    })).subscribe({
      next: updated => {
        this.orders = this.orders.map(item => item.id === updated.id ? updated : item);
        delete this.transitionNotes[order.id];
        this.changed.emit();
      },
      error: e => this.error = e?.error?.message || 'Khong the chuyen trang thai phieu bao tri.',
    });
  }

  actions(status: MaintenanceWorkOrderStatus): Array<'start' | 'complete' | 'reopen' | 'cancel'> {
    if (status === 'OPEN') return ['start', 'cancel'];
    if (status === 'IN_PROGRESS') return ['complete', 'cancel'];
    if (status === 'COMPLETED' || status === 'CANCELLED') return ['reopen'];
    return [];
  }

  actionLabel(action: 'start' | 'complete' | 'reopen' | 'cancel'): string {
    return ({ start: 'Bat dau', complete: 'Hoan tat', reopen: 'Mo lai', cancel: 'Huy phieu' })[action];
  }

  canRun(action: 'start' | 'complete' | 'reopen' | 'cancel'): boolean {
    return action === 'cancel' ? this.canCancel : this.canUpdate;
  }

  assignee(order: MaintenanceWorkOrder): string { return order.assigneeUserId ? `Nhan vien #${order.assigneeUserId}` : 'Chua phan cong'; }

  private emptyForm() { return { reason: '', priority: 'NORMAL' as MaintenancePriority, assigneeUserId: undefined, scheduledStart: '', scheduledEnd: '' }; }
}
