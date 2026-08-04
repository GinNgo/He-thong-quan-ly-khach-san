import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  PropertyAccountSubscription,
  PropertySubscriptionUsage,
  SubscriptionPlan,
  SubscriptionService,
} from '../../../core/services/subscription.service';
import { PlatformBillingService } from '../../../core/services/platform-billing.service';
import { ActionCode, FunctionCode, PermissionService } from '../../../core/services/permission.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-subscription-plans',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ButtonModule, CardModule, TagModule],
  templateUrl: './subscription-plans.html',
  styles: [`
    .plan-card{height:100%;display:flex;flex-direction:column}.plan-price{font-size:2rem;font-weight:bold;margin:1rem 0;color:var(--primary-color)}
    .plan-features{flex-grow:1}.feature-item{margin:.5rem 0;display:flex;align-items:center;gap:.5rem}.feature-item i{color:var(--green-500)}
    .state{margin:1rem auto;max-width:900px;padding:1rem;border-radius:.75rem;background:#fff}.error{color:#9f1239;background:#fff1f2}.source{font-size:.85rem;color:#475569}
  `]
})
export class SubscriptionPlansComponent implements OnInit {
  private readonly subscriptions = inject(SubscriptionService);
  private readonly route = inject(ActivatedRoute);
  private readonly platformBilling = inject(PlatformBillingService);
  private readonly permissions = inject(PermissionService);

  plans: SubscriptionPlan[] = [];
  current: PropertyAccountSubscription | null = null;
  usage: PropertySubscriptionUsage | null = null;
  propertyId?: number;
  loadingCatalog = true;
  loadingProperty = false;
  catalogError = '';
  propertyError = '';
  readonly canUpdatePlatformBilling = this.permissions.hasPermission(FunctionCode.PLATFORM_BILLING, ActionCode.UPDATE);
  revokeEditorOpen = false;
  revokeConfirmed = false;
  revokeReason = '';
  revoking = false;
  revokeError = '';
  revokeMessage = '';

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    this.propertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined;
    this.loadPlans();
    this.loadSelectedProperty();
  }

  loadPlans(): void {
    this.loadingCatalog = true;
    this.catalogError = '';
    this.subscriptions.getPlans().subscribe({
      next: plans => { this.plans = plans; this.loadingCatalog = false; },
      error: () => { this.plans = []; this.catalogError = 'Không thể tải catalog gói dịch vụ từ máy chủ.'; this.loadingCatalog = false; }
    });
  }

  loadSelectedProperty(): void {
    if (!this.propertyId) {
      this.current = null;
      this.usage = null;
      return;
    }
    this.loadingProperty = true;
    this.propertyError = '';
    forkJoin({
      current: this.subscriptions.getPropertySubscription(this.propertyId),
      usage: this.subscriptions.getPropertyUsage(this.propertyId),
    }).subscribe({
      next: result => { this.current = result.current; this.usage = result.usage; this.loadingProperty = false; },
      error: () => {
        this.current = null;
        this.usage = null;
        this.propertyError = 'Không thể tải subscription của cơ sở đã chọn. Kiểm tra quyền truy cập hoặc thử lại.';
        this.loadingProperty = false;
      }
    });
  }

  isCurrentPlan(plan: SubscriptionPlan): boolean {
    return Boolean(this.current?.platformAuthoritative && this.current.status === 'ACTIVE' && this.current.planId === plan.id);
  }

  get canRevokeSelectedProperty(): boolean {
    return Boolean(
      this.canUpdatePlatformBilling &&
      this.propertyId &&
      this.current?.platformAuthoritative &&
      this.current.status === 'ACTIVE'
    );
  }

  get revokeReasonValid(): boolean {
    const length = this.revokeReason.trim().length;
    return length >= 10 && length <= 1000;
  }

  revokeSubscription(): void {
    if (!this.propertyId || !this.canRevokeSelectedProperty || !this.revokeConfirmed || !this.revokeReasonValid || this.revoking) return;
    this.revoking = true;
    this.revokeError = '';
    this.revokeMessage = '';
    this.platformBilling.revokeSubscription(this.propertyId, this.revokeReason).subscribe({
      next: result => {
        this.revoking = false;
        this.revokeEditorOpen = false;
        this.revokeConfirmed = false;
        this.revokeReason = '';
        if (result.entitlementStatus === 'EXPIRED') {
          this.revokeMessage = 'Subscription đã hết hạn theo thời điểm hiệu lực. Lịch sử vẫn được giữ lại.';
        } else {
          this.revokeMessage = result.transitioned
            ? 'Subscription đã được thu hồi. Lịch sử vẫn được giữ lại.'
            : 'Subscription đã ở trạng thái thu hồi; không có chuyển trạng thái mới.';
        }
        this.loadSelectedProperty();
      },
      error: error => { this.revoking = false; this.revokeError = this.safeRevokeError(error); }
    });
  }

  featureLimit(limit: number | null): string {
    if (limit === -1) return 'Không giới hạn';
    return typeof limit === 'number' && Number.isInteger(limit) && limit >= 0 ? String(limit) : 'Không khả dụng';
  }

  private safeRevokeError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Không thể thu hồi subscription. Vui lòng thử lại.';
    const body = error.error && typeof error.error === 'object' ? error.error as Record<string, unknown> : {};
    const code = typeof body['code'] === 'string' ? body['code'] : '';
    if (error.status === 403) return 'Bạn không có quyền thu hồi subscription của cơ sở này.';
    if (error.status === 404) return 'Không tìm thấy authoritative subscription lifecycle cho cơ sở đã chọn.';
    if (error.status === 409 || code.includes('LIFECYCLE')) return 'Subscription đã ở trạng thái kết thúc hoặc vừa thay đổi. Hãy tải lại trạng thái mới nhất.';
    if (error.status === 400) return 'Nhập lý do từ 10 đến 1000 ký tự và xác nhận thao tác.';
    return 'Không thể thu hồi subscription. Vui lòng thử lại.';
  }
}
