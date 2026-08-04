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
import { PlatformBillingService, PlatformPlanVersion } from '../../../core/services/platform-billing.service';
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
    .admin-form{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;text-align:left}.admin-form label{display:grid;gap:6px}.admin-form .wide{grid-column:1/-1}.version-list{display:grid;gap:12px}.version-row{display:grid;gap:8px;padding:14px;border:1px solid #d8e2ee;border-radius:12px;text-align:left}.actions{display:flex;flex-wrap:wrap;gap:8px}
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
  readonly canAdministerVersions = this.permissions.isSuperAdmin() && this.canUpdatePlatformBilling;
  revokeEditorOpen = false;
  revokeConfirmed = false;
  revokeReason = '';
  revoking = false;
  revokeError = '';
  revokeMessage = '';
  planVersions: PlatformPlanVersion[] = [];
  versionsLoading = false;
  versionsError = '';
  versionMessage = '';
  versionBusy = '';
  pendingVersionAction: { version: PlatformPlanVersion; action: 'activate' | 'deactivate' } | null = null;
  versionActionReason = '';
  versionForm = this.emptyVersionForm();

  ngOnInit(): void {
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    this.propertyId = Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined;
    this.loadPlans();
    this.loadSelectedProperty();
    if (this.canAdministerVersions) this.loadPlanVersions();
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
        if (result.contractStatus === 'EXPIRED') {
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

  loadPlanVersions(): void {
    if (!this.canAdministerVersions) return;
    this.versionsLoading = true;
    this.versionsError = '';
    this.platformBilling.getPlanVersions().subscribe({
      next: versions => { this.planVersions = versions; this.versionsLoading = false; },
      error: () => { this.planVersions = []; this.versionsError = 'Không thể tải lịch sử version gói dịch vụ.'; this.versionsLoading = false; }
    });
  }

  useVersionAsBasis(version: PlatformPlanVersion): void {
    if (!this.canAdministerVersions || this.versionBusy) return;
    this.versionForm = {
      familyCode: version.familyCode,
      nameVi: version.nameVi,
      nameEn: version.nameEn,
      billingType: version.billingType,
      price: version.price,
      lifetime: version.lifetime,
      durationValue: version.durationValue ?? 1,
      durationUnit: version.durationUnit,
      featuresText: version.features.map(feature => `${feature.code}=${feature.limit}`).join('\n'),
    };
    this.versionMessage = `Đang tạo version mới dựa trên ${version.versionCode}; version cũ vẫn bất biến.`;
  }

  createPlanVersion(): void {
    if (!this.canAdministerVersions || this.versionBusy) return;
    const request = this.validVersionRequest();
    if (!request) return;
    this.versionBusy = 'create';
    this.versionsError = '';
    this.platformBilling.createPlanVersion(request, this.newIdempotencyKey('plan-version')).subscribe({
      next: version => {
        this.versionBusy = '';
        this.versionForm = this.emptyVersionForm();
        this.versionMessage = `${version.versionCode} đã được tạo ở trạng thái INACTIVE. Contract/version cũ không thay đổi.`;
        this.loadPlanVersions();
      },
      error: error => { this.versionBusy = ''; this.versionsError = this.safePlanAdminError(error); }
    });
  }

  requestVersionAction(version: PlatformPlanVersion, action: 'activate' | 'deactivate'): void {
    if (!this.canAdministerVersions || this.versionBusy) return;
    if (action === 'activate' && (version.status !== 'INACTIVE' || Boolean(version.deactivatedAt))) return;
    if (action === 'deactivate' && version.status !== 'ACTIVE') return;
    this.pendingVersionAction = { version, action };
    this.versionActionReason = '';
  }

  confirmVersionAction(): void {
    const pending = this.pendingVersionAction;
    if (!pending || !this.canAdministerVersions || this.versionBusy) return;
    const reason = this.versionActionReason.trim();
    if (pending.action === 'deactivate' && (reason.length < 10 || reason.length > 1000)) {
      this.versionsError = 'Nhập lý do deactivate từ 10 đến 1000 ký tự.';
      return;
    }
    this.versionBusy = `${pending.action}-${pending.version.id}`;
    this.versionsError = '';
    const request = pending.action === 'activate'
      ? this.platformBilling.activatePlanVersion(pending.version.id, this.newIdempotencyKey('plan-activate'))
      : this.platformBilling.deactivatePlanVersion(pending.version.id, reason, this.newIdempotencyKey('plan-deactivate'));
    request.subscribe({
      next: version => {
        this.versionBusy = '';
        this.pendingVersionAction = null;
        this.versionActionReason = '';
        this.versionMessage = `${version.versionCode} hiện ở trạng thái ${version.status}. Existing order snapshots/contracts vẫn bất biến.`;
        this.loadPlanVersions();
        this.loadPlans();
      },
      error: error => { this.versionBusy = ''; this.versionsError = this.safePlanAdminError(error); }
    });
  }

  private validVersionRequest() {
    const form = this.versionForm;
    const familyCode = form.familyCode.trim().toUpperCase();
    const nameVi = form.nameVi.trim();
    const nameEn = form.nameEn.trim();
    const features = form.featuresText.split(/\r?\n/).map(line => line.trim()).filter(Boolean).map(line => {
      const [rawCode, rawLimit, ...extra] = line.split('=');
      return { code: rawCode?.trim().toUpperCase() ?? '', limit: Number(rawLimit), valid: extra.length === 0 && rawLimit !== undefined };
    });
    const keysValid = /^[A-Z0-9_]{2,50}$/.test(familyCode) && features.every(feature => feature.valid && /^[A-Z0-9_]{2,50}$/.test(feature.code));
    const featureKeysUnique = new Set(features.map(feature => feature.code)).size === features.length;
    const limitsValid = features.every(feature => Number.isInteger(feature.limit) && (feature.limit === -1 || feature.limit >= 0));
    const priceValid = Number.isInteger(form.price) && form.price > 0;
    const durationValid = form.lifetime
      ? form.billingType === 'ONCE' && form.durationUnit === 'LIFETIME'
      : ((form.billingType === 'MONTHLY' && form.durationUnit === 'MONTH') || (form.billingType === 'YEARLY' && form.durationUnit === 'YEAR'))
        && Number.isInteger(form.durationValue) && form.durationValue >= 1 && form.durationValue <= 120;
    if (!keysValid || !featureKeysUnique || !limitsValid || !priceValid || !durationValid || !nameVi || nameVi.length > 255 || !nameEn || nameEn.length > 255 || features.length === 0) {
      this.versionsError = 'Kiểm tra mã duy nhất, tên, giá VND nguyên dương, billing/duration và feature theo dạng CODE=-1 hoặc CODE=giới_hạn.';
      return null;
    }
    return { familyCode, nameVi, nameEn, billingType: form.billingType, price: form.price, durationValue: form.lifetime ? null : form.durationValue, durationUnit: form.durationUnit, features: features.map(({ code, limit }) => ({ code, limit })) };
  }

  private emptyVersionForm() {
    return { familyCode: '', nameVi: '', nameEn: '', billingType: 'MONTHLY' as 'MONTHLY' | 'YEARLY' | 'ONCE', price: 0, lifetime: false, durationValue: 1, durationUnit: 'MONTH' as 'DAY' | 'MONTH' | 'YEAR' | 'LIFETIME', featuresText: '' };
  }

  private newIdempotencyKey(prefix: string): string {
    const randomUuid = globalThis.crypto?.randomUUID?.();
    return randomUuid ? `${prefix}-${randomUuid}` : `${prefix}-${Date.now()}`;
  }

  private safePlanAdminError(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) return 'Không thể cập nhật version gói. Vui lòng thử lại.';
    if (error.status === 403) return 'Bạn không có quyền quản trị version gói dịch vụ.';
    if (error.status === 404) return 'Version gói không còn tồn tại hoặc không thể truy cập.';
    if (error.status === 409) return 'Version đã thay đổi hoặc idempotency/activation bị xung đột. Tải lại trước khi thử tiếp.';
    if (error.status === 400) return 'Dữ liệu version không hợp lệ. Kiểm tra giá, duration, mã và giới hạn feature.';
    return 'Không thể cập nhật version gói. Vui lòng thử lại.';
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
