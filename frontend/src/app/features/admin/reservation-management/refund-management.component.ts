import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize, switchMap } from 'rxjs';
import { PropertyRefundResult, RefundService, RefundStatus } from '@app/core/services/refund.service';
import { ManagedProperty, ManagementApiService } from '@app/core/services/management-api.service';

@Component({
  selector: 'app-refund-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './refund-management.component.html',
  styleUrls: ['./refund-management.component.css'],
})
export class RefundManagementComponent implements OnInit {
  private readonly refundService = inject(RefundService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);

  @Input() refunds: PropertyRefundResult[] = [];
  @Output() readonly refundUpdated = new EventEmitter<PropertyRefundResult>();

  approvingId: string | null = null;
  dispatchingId: string | null = null;
  properties: ManagedProperty[] = [];
  selectedPropertyId: number | null = null;
  loading = false;
  error = '';
  success = '';

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    const requestedPropertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined;
    this.loading = true;
    this.managementApi.context(requestedPropertyId).subscribe({
      next: (context) => {
        this.properties = context.properties ?? [];
        this.selectedPropertyId = context.activePropertyId ?? this.properties[0]?.id ?? null;
        this.loadRefunds();
      },
      error: (error) => {
        this.loading = false;
        this.error = error?.error?.message || 'Không thể tải danh sách cơ sở.';
        this.changeDetector.detectChanges();
      },
    });
  }

  loadRefunds(): void {
    if (!this.selectedPropertyId) {
      this.loading = false;
      this.refunds = [];
      this.error = 'Hãy chọn cơ sở để xem yêu cầu hoàn tiền.';
      return;
    }
    this.loading = true;
    this.error = '';
    this.refundService.listPropertyRefunds(this.selectedPropertyId)
      .pipe(finalize(() => {
        this.loading = false;
        this.changeDetector.detectChanges();
      }))
      .subscribe({
        next: (refunds) => { this.refunds = refunds; },
        error: (error) => {
          this.error = error?.error?.message || 'Không thể tải yêu cầu hoàn tiền.';
        },
      });
  }

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
          this.success = 'Đã duyệt yêu cầu. Hãy gửi sang cổng thanh toán để tiếp tục.';
          this.refundUpdated.emit(updated);
          this.changeDetector.detectChanges();
        },
        error: (error) => {
          this.error = error?.error?.message || 'Không thể duyệt yêu cầu hoàn tiền.';
          this.changeDetector.detectChanges();
        },
      });
  }

  dispatch(refund: PropertyRefundResult): void {
    if (this.dispatchingId || refund.status !== 'PENDING_PROVIDER') return;
    const provider = refund.provider || 'SIMULATOR';
    const environment = refund.environment || 'SIMULATOR';
    this.dispatchingId = refund.publicId;
    this.error = '';
    this.success = '';
    this.refundService.createPropertyRefundAttempt(refund.publicId, { provider, environment })
      .pipe(switchMap((attempt) => environment === 'SIMULATOR'
        ? this.refundService.confirmPropertySimulatorRefund(refund.publicId)
        : [attempt]))
      .pipe(finalize(() => {
        this.dispatchingId = null;
        this.changeDetector.detectChanges();
      }))
      .subscribe({
        next: () => {
          this.success = provider === 'SIMULATOR'
            ? 'Đã hoàn tiền mô phỏng và ghi nhận callback có chữ ký.'
            : `Đã gửi yêu cầu sang ${provider}; đang chờ callback xác nhận.`;
          this.loadRefunds();
        },
        error: (error) => {
          this.error = error?.error?.message
            || `Chưa thể gửi hoàn tiền sang ${provider}. Kiểm tra adapter và tài khoản sandbox.`;
        },
      });
  }

  canApprove(status: RefundStatus): boolean {
    return status === 'REQUESTED' || status === 'PENDING_APPROVAL';
  }

  statusLabel(status: RefundStatus): string {
    return {
      REQUESTED: 'Chờ duyệt',
      PENDING_APPROVAL: 'Chờ duyệt',
      POLICY_BLOCKED: 'Bị chặn theo chính sách',
      PENDING_PROVIDER: 'Đang chờ cổng thanh toán',
      SUCCEEDED: 'Đã hoàn tiền',
      FAILED: 'Thất bại',
      CANCELLED: 'Đã hủy',
    }[status] || status;
  }

  statusTone(status: RefundStatus): string {
    return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'REQUESTED' || status === 'PENDING_APPROVAL' || status === 'PENDING_PROVIDER' || status === 'POLICY_BLOCKED' ? 'warning' : 'muted';
  }
}
