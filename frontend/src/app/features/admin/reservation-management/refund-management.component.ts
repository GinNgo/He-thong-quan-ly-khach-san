import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { finalize } from 'rxjs';
import { PropertyRefundResult, RefundService, RefundStatus } from '@app/core/services/refund.service';

@Component({
  selector: 'app-refund-management',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './refund-management.component.html',
  styleUrls: ['./refund-management.component.css'],
})
export class RefundManagementComponent {
  private readonly refundService = inject(RefundService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  @Input() refunds: PropertyRefundResult[] = [];
  @Output() readonly refundUpdated = new EventEmitter<PropertyRefundResult>();

  approvingId: string | null = null;
  error = '';
  success = '';

  approve(refund: PropertyRefundResult): void {
    if (this.approvingId || !refund.publicId) return;
    this.approvingId = refund.publicId;
    this.error = '';
    this.success = '';
    this.refundService
      .approvePropertyRefund(refund.publicId)
      .pipe(finalize(() => {
        this.approvingId = null;
        this.changeDetector.detectChanges();
      }))
      .subscribe({
        next: (updated) => {
          this.refunds = this.refunds.map((item) => item.publicId === updated.publicId ? updated : item);
          this.success = 'Đã chuyển yêu cầu sang xử lý / Refund sent to provider processing.';
          this.refundUpdated.emit(updated);
          this.changeDetector.detectChanges();
        },
        error: (error) => {
          this.error = error?.error?.message || 'Không thể duyệt yêu cầu / Approval failed.';
          this.changeDetector.detectChanges();
        },
      });
  }

  canApprove(status: RefundStatus): boolean {
    return status === 'REQUESTED' || status === 'PENDING_APPROVAL';
  }

  statusLabel(status: RefundStatus): string {
    return {
      REQUESTED: 'Chờ duyệt / Requested',
      PENDING_APPROVAL: 'Chờ duyệt / Pending approval',
      POLICY_BLOCKED: 'Bị chặn / Policy blocked',
      PENDING_PROVIDER: 'Đang xử lý / Processing',
      SUCCEEDED: 'Đã hoàn / Succeeded',
      FAILED: 'Thất bại / Failed',
      CANCELLED: 'Đã hủy / Cancelled',
    }[status] || status;
  }

  statusTone(status: RefundStatus): string {
    return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'REQUESTED' || status === 'PENDING_APPROVAL' || status === 'PENDING_PROVIDER' || status === 'POLICY_BLOCKED' ? 'warning' : 'muted';
  }
}
