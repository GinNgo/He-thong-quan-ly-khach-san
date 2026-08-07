import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';
import {
  PlatformBillingService,
  PlatformCatalogPlan,
  PlatformSubscriptionEntitlement,
  PlatformOrder,
  PlatformPolicyAvailability,
  PlatformSubscriptionHistoryItem,
} from '../../../core/services/platform-billing.service';
import { PlatformPaymentPanelComponent } from './platform-payment-panel.component';

@Component({
  selector: 'app-subscription-billing',
  standalone: true,
  imports: [CommonModule, FeedbackStateComponent, PlatformPaymentPanelComponent],
  templateUrl: './subscription-billing.component.html',
  styleUrls: ['./subscription-billing.component.css'],
})
export class SubscriptionBillingComponent implements OnInit {
  private readonly platformBilling = inject(PlatformBillingService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);

  plans: PlatformCatalogPlan[] = [];
  currentEntitlement: PlatformSubscriptionEntitlement | null = null;
  policyAvailability: PlatformPolicyAvailability | null = null;
  latestOrder: PlatformOrder | null = null;
  history: PlatformSubscriptionHistoryItem[] = [];
  activePropertyId?: number;
  isLoading = true;
  plansError = '';
  subscriptionError = '';
  policyError = '';
  orderError = '';
  historyError = '';
  exportingHistory = false;
  creatingOrderFor?: number;
  private loadingPlans = true;
  private loadingSubscription = true;
  loadingPolicy = true;

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    this.activePropertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined;
    this.loadPlans();
    this.loadEntitlement();
    this.loadPolicyAvailability();
    this.loadHistory();
  }

  loadPlans(): void {
    this.loadingPlans = true;
    this.plansError = '';
    this.updateLoadingState();
    this.platformBilling.getCatalog().subscribe({
      next: (data) => {
        this.plans = data;
        this.loadingPlans = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.plansError = err?.error?.message || 'Không thể tải danh mục gói phần mềm từ máy chủ.';
        this.loadingPlans = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
    });
  }

  loadEntitlement(): void {
    this.loadingSubscription = true;
    this.subscriptionError = '';
    this.updateLoadingState();
    if (!this.activePropertyId) {
      this.currentEntitlement = null;
      this.subscriptionError = 'Hãy chọn cơ sở đang quản lý để xem gói hiện tại.';
      this.loadingSubscription = false;
      this.updateLoadingState();
      return;
    }
    this.platformBilling.getEntitlement(this.activePropertyId).subscribe({
      next: (data) => {
        this.currentEntitlement = data;
        this.loadingSubscription = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.subscriptionError = err?.error?.message || 'Không thể tải quyền lợi gói của cơ sở đã chọn.';
        this.loadingSubscription = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
    });
  }

  loadPolicyAvailability(): void {
    this.loadingPolicy = true;
    this.policyError = '';
    this.updateLoadingState();
    this.platformBilling.getPolicyAvailability().subscribe({
      next: (data) => {
        this.policyAvailability = data;
        this.loadingPolicy = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.policyError = err?.error?.message || 'Không thể tải trạng thái chính sách gói.';
        this.loadingPolicy = false;
        this.updateLoadingState();
        this.cdr.markForCheck();
      },
    });
  }

  loadHistory(): void {
    this.historyError = '';
    if (!this.activePropertyId) {
      this.history = [];
      return;
    }
    this.platformBilling.getHistory(this.activePropertyId).subscribe({
      next: history => { this.history = history; this.cdr.markForCheck(); },
      error: () => { this.history = []; this.historyError = 'Unable to load historical subscription events for this property.'; this.cdr.markForCheck(); }
    });
  }

  exportHistory(): void {
    if (!this.activePropertyId || this.exportingHistory) return;
    this.exportingHistory = true;
    this.historyError = '';
    this.platformBilling.exportHistory(this.activePropertyId).subscribe({
      next: response => {
        const blob = response.body;
        if (!blob) {
          this.historyError = 'The subscription history export was empty.';
          this.exportingHistory = false;
          return;
        }
        const disposition = response.headers.get('Content-Disposition') ?? '';
        const filename = /filename="?([^";]+)"?/i.exec(disposition)?.[1] ?? `subscription-history-${this.activePropertyId}.csv`;
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);
        this.exportingHistory = false;
      },
      error: () => { this.historyError = 'Unable to export subscription history for this property.'; this.exportingHistory = false; }
    });
  }

  createOrder(plan: PlatformCatalogPlan): void {
    if (!this.activePropertyId || this.creatingOrderFor) {
      this.orderError = 'Hãy chọn cơ sở trước khi tạo đơn mua gói.';
      return;
    }
    this.orderError = '';
    this.creatingOrderFor = plan.id;
    const idempotencyKey = this.newIdempotencyKey('platform-order');
    const currentPlanId = this.currentEntitlement?.planId ?? undefined;
    const request = currentPlanId === plan.id
      ? this.platformBilling.createRenewalOrder(this.activePropertyId, idempotencyKey)
      : currentPlanId
        ? this.platformBilling.createUpgradeOrder(this.activePropertyId, plan.id, idempotencyKey)
        : this.platformBilling.createPurchaseOrder(this.activePropertyId, plan.id, idempotencyKey);
    request.subscribe({
      next: (order) => {
        this.latestOrder = order;
        this.creatingOrderFor = undefined;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.orderError = err?.error?.message || 'Không thể tạo đơn mua gói phần mềm.';
        this.creatingOrderFor = undefined;
        this.cdr.markForCheck();
      },
    });
  }

  planName(plan: PlatformCatalogPlan): string {
    return plan.nameVi || plan.nameEn || plan.code;
  }

  featureLimit(limit: number): string {
    return limit === -1 ? 'Không giới hạn' : String(limit);
  }

  orderActionLabel(plan: PlatformCatalogPlan): string {
    if (this.creatingOrderFor === plan.id) return 'Đang tạo đơn an toàn...';
    if (this.currentEntitlement?.planId === plan.id) return 'Gia hạn gói này';
    return this.currentEntitlement?.planId ? 'Nâng cấp lên gói này' : 'Mua gói này';
  }

  statusLabel(status: string): string {
    return ({ ACTIVE: 'Đang hoạt động', NONE: 'Chưa có gói', CREATED: 'Mới tạo', PENDING_PAYMENT: 'Chờ thanh toán', PAID: 'Đã thanh toán', APPLIED: 'Đã kích hoạt', FAILED: 'Thất bại', CANCELLED: 'Đã hủy', EXPIRED: 'Hết hạn' } as Record<string, string>)[status] || status;
  }

  billingPeriodLabel(plan: PlatformCatalogPlan): string {
    if (plan.isLifetime) return 'trọn đời';
    return ({ MONTHLY: 'tháng', QUARTERLY: 'quý', YEARLY: 'năm' } as Record<string, string>)[plan.billingType] || plan.billingType;
  }

  sourceLabel(source: string): string {
    return ({ PLATFORM: 'Hệ thống thanh toán', LEGACY_PROJECTION: 'Dữ liệu chuyển đổi', NONE: 'Chưa có' } as Record<string, string>)[source] || source;
  }

  updateLatestOrder(order: PlatformOrder): void {
    this.latestOrder = order;
  }

  private updateLoadingState(): void {
    this.isLoading = this.loadingPlans || this.loadingSubscription || this.loadingPolicy;
  }

  private newIdempotencyKey(prefix: string): string {
    const randomUuid = globalThis.crypto?.randomUUID?.();
    return randomUuid ? `${prefix}-${randomUuid}` : `${prefix}-${Date.now()}`;
  }

}
