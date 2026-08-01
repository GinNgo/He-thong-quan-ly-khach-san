import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';
import {
  PlatformRefundResult,
  RefundService,
  RefundStatus,
} from '@app/core/services/refund.service';

@Component({
  selector: 'app-platform-refunds',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './platform-refunds.component.html',
  styleUrls: ['./platform-refunds.component.css'],
})
export class PlatformRefundsComponent implements OnInit {
  private readonly refundService = inject(RefundService);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);

  transactionPublicId = '';
  amount: number | null = null;
  reason = '';
  provider = 'SIMULATOR';
  refunds: PlatformRefundResult[] = [];
  loading = false;
  error = '';
  success = '';

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.transactionPublicId = params.get('transactionId') || '';
    const refundId = params.get('refundId');
    if (refundId) this.refresh(refundId);
  }

  requestRefund(): void {
    this.error = '';
    this.success = '';
    if (!this.transactionPublicId || !this.amount || this.amount <= 0 || !Number.isInteger(this.amount)) {
      this.error = 'Cần mã giao dịch và số tiền VND nguyên dương / Transaction and positive integer amount are required.';
      return;
    }
    if (!this.reason.trim()) {
      this.error = 'Vui lòng nhập lý do / Please provide a reason.';
      return;
    }
    this.loading = true;
    this.refundService
      .requestPlatformRefund(
        this.transactionPublicId,
        { amount: this.amount, reason: this.reason.trim() },
        { idempotencyKey: this.requestId() },
      )
      .pipe(finalize(() => this.finishLoading()))
      .subscribe({
        next: (refund) => {
          this.upsert(refund);
          this.success = refund.policyAvailable
            ? 'Đã tạo yêu cầu, chờ duyệt / Request created and awaiting approval.'
            : 'Đã ghi nhận nhưng bị chặn do chưa có policy / Recorded but blocked because policy is not configured.';
          this.amount = null;
          this.reason = '';
          this.changeDetector.detectChanges();
        },
        error: (error) => {
          this.error = error?.error?.message || 'Không thể tạo platform refund / Platform refund failed.';
          this.changeDetector.detectChanges();
        },
      });
  }

  approve(refund: PlatformRefundResult): void {
    if (!refund.policyAvailable || refund.status !== 'REQUESTED' && refund.status !== 'PENDING_APPROVAL') return;
    this.loading = true;
    this.error = '';
    this.refundService
      .approvePlatformRefund(refund.publicId)
      .pipe(finalize(() => this.finishLoading()))
      .subscribe({
        next: (updated) => {
          this.upsert(updated);
          this.success = 'Đã duyệt theo policy / Approved under the configured policy.';
          this.changeDetector.detectChanges();
        },
        error: (error) => {
          this.error = error?.error?.message || 'Không thể duyệt platform refund / Approval failed.';
          this.changeDetector.detectChanges();
        },
      });
  }

  createAttempt(refund: PlatformRefundResult): void {
    if (!refund.policyAvailable || refund.status !== 'PENDING_PROVIDER') return;
    this.loading = true;
    this.error = '';
    this.refundService
      .createPlatformRefundAttempt(refund.publicId, { provider: this.provider })
      .pipe(finalize(() => this.finishLoading()))
      .subscribe({
        next: () => {
          this.success = 'Đã tạo attempt provider / Provider attempt created.';
          this.changeDetector.detectChanges();
        },
        error: (error) => {
          this.error = error?.error?.message || 'Provider chưa sẵn sàng / Provider unavailable.';
          this.changeDetector.detectChanges();
        },
      });
  }

  refresh(refundId: string): void {
    this.loading = true;
    this.refundService
      .getPlatformRefund(refundId)
      .pipe(finalize(() => this.finishLoading()))
      .subscribe({
        next: (refund) => {
          this.upsert(refund);
          this.changeDetector.detectChanges();
        },
        error: (error) => {
          this.error = error?.error?.message || 'Không thể tải refund / Refund status unavailable.';
          this.changeDetector.detectChanges();
        },
      });
  }

  statusLabel(status: RefundStatus): string {
    return {
      REQUESTED: 'Chờ duyệt / Requested',
      PENDING_APPROVAL: 'Chờ duyệt / Pending approval',
      POLICY_BLOCKED: 'Bị chặn / Policy blocked',
      PENDING_PROVIDER: 'Chờ provider / Pending provider',
      SUCCEEDED: 'Đã hoàn / Succeeded',
      FAILED: 'Thất bại / Failed',
      CANCELLED: 'Đã hủy / Cancelled',
    }[status] || status;
  }

  statusTone(status: RefundStatus): string {
    return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : status === 'POLICY_BLOCKED' || status === 'REQUESTED' || status === 'PENDING_APPROVAL' || status === 'PENDING_PROVIDER' ? 'warning' : 'muted';
  }

  private upsert(refund: PlatformRefundResult): void {
    this.refunds = [refund, ...this.refunds.filter((item) => item.publicId !== refund.publicId)];
  }

  private finishLoading(): void {
    this.loading = false;
    this.changeDetector.detectChanges();
  }

  private requestId(): string {
    return globalThis.crypto?.randomUUID?.() || `platform-refund-${Date.now()}`;
  }
}
