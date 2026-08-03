import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { finalize, switchMap, tap } from 'rxjs';

import {
  PartnerOverallStatus,
  PartnerPropertyStatus,
  PartnerPropertyStatusRow,
  PartnerRegistrationStatusResponse,
  PartnerRegistrationStatusService,
} from './partner-registration-status.service';

const STATUS_LABELS: Record<PartnerPropertyStatus, string> = {
  DRAFT: 'Bản nháp',
  PENDING: 'Đang chờ duyệt',
  APPROVED: 'Đã phê duyệt',
  REJECTED: 'Bị từ chối',
  SUSPENDED: 'Đang tạm ngưng',
  CANCELLED: 'Đã hủy',
};

const STATUS_GUIDANCE: Record<PartnerPropertyStatus, string> = {
  DRAFT: 'Hồ sơ chưa được gửi xét duyệt. Dữ liệu cơ sở vẫn được lưu an toàn.',
  PENDING: 'LuxeStay đang xem xét hồ sơ. Bạn chưa thể truy cập các chức năng vận hành.',
  APPROVED: 'Hồ sơ đã được phê duyệt. Quyền quản lý chỉ khả dụng khi cơ sở và quyền sở hữu đều đang hoạt động.',
  REJECTED: 'Hồ sơ chưa đáp ứng yêu cầu. Vui lòng xem lý do nếu được cung cấp.',
  SUSPENDED: 'Cơ sở hoặc quyền quản lý đang bị tạm ngưng. Không thể tiếp tục vận hành lúc này.',
  CANCELLED: 'Hồ sơ này đã kết thúc và không còn trong quy trình xét duyệt.',
};

@Component({
  selector: 'app-partner-registration-status',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <main class="status-page">
      <section *ngIf="loading" class="state-panel" role="status" aria-live="polite">
        <span class="state-icon pi pi-spin pi-spinner" aria-hidden="true"></span>
        <h1>Đang kiểm tra hồ sơ</h1>
        <p>LuxeStay đang tải trạng thái mới nhất của từng cơ sở.</p>
      </section>

      <section *ngIf="!loading && error" class="state-panel error-state" role="alert">
        <span class="state-icon pi pi-exclamation-triangle" aria-hidden="true"></span>
        <h1>Không thể tải trạng thái</h1>
        <p>{{ error }}</p>
        <button type="button" (click)="load()">Thử lại</button>
      </section>

      <section *ngIf="!loading && !error && properties.length === 0" class="state-panel empty-state">
        <span class="state-icon pi pi-building" aria-hidden="true"></span>
        <h1>Chưa có hồ sơ đối tác</h1>
        <p>Bạn có thể tạo hồ sơ cơ sở đầu tiên để bắt đầu quy trình xét duyệt.</p>
        <a routerLink="/partner/register">Đăng chỗ nghỉ</a>
      </section>

      <section *ngIf="!loading && !error && properties.length > 0" class="status-content">
        <header class="status-header">
          <div>
            <p class="eyebrow">Hồ sơ đối tác</p>
            <h1>{{ overallHeading }}</h1>
            <p>{{ propertyCount }} cơ sở được trả về từ hệ thống.</p>
          </div>
          <span class="overall-badge" [attr.data-status]="overallStatus">{{ overallLabel }}</span>
        </header>

        <div class="property-grid">
          <article *ngFor="let property of properties; trackBy: trackProperty" class="property-card">
            <header>
              <div>
                <p class="property-id">Cơ sở #{{ property.propertyId }}</p>
                <h2>{{ property.propertyName }}</h2>
              </div>
              <span class="status-badge" [attr.data-status]="property.status">
                {{ statusLabel(property.status) }}
              </span>
            </header>

            <p class="guidance">{{ guidance(property.status) }}</p>
            <dl>
              <div><dt>Phê duyệt</dt><dd>{{ property.approvalStatus }}</dd></div>
              <div><dt>Vận hành</dt><dd>{{ property.operationStatus }}</dd></div>
              <div><dt>Quyền sở hữu</dt><dd>{{ property.ownershipStatus }}</dd></div>
            </dl>

            <div *ngIf="property.rejectionReason as reason" class="rejection-reason" role="note">
              <strong>Lý do từ chối</strong>
              <span>{{ reason }}</span>
            </div>

            <a *ngIf="isManagementReady(property)" class="management-link" routerLink="/management/dashboard">
              Đi đến trang quản lý
            </a>

            <button
              *ngIf="canSubmitForReview(property)"
              type="button"
              class="submit-review"
              [disabled]="isSubmitting(property.propertyId)"
              (click)="submitForReview(property)">
              <span *ngIf="!isSubmitting(property.propertyId)">Gửi xét duyệt</span>
              <span *ngIf="isSubmitting(property.propertyId)">Đang gửi...</span>
            </button>

            <div *ngIf="submissionErrors[property.propertyId] as submissionError" class="submission-error" role="alert">
              {{ submissionError }}
            </div>
          </article>
        </div>
      </section>
    </main>
  `,
  styles: [`
    :host{display:block}.status-page{min-height:calc(100vh - 72px);padding:42px 20px 64px;background:radial-gradient(circle at 10% 5%,#dbeafe 0,transparent 28%),linear-gradient(180deg,#f8fafc,#eef2f7);color:#172033}.state-panel{width:min(680px,100%);margin:8vh auto 0;padding:44px;text-align:center;background:#fff;border:1px solid #dbe4ef;border-radius:22px;box-shadow:0 22px 60px rgba(15,23,42,.09)}.state-icon{display:grid;place-items:center;width:64px;height:64px;margin:0 auto 18px;border-radius:20px;background:#eaf2ff;color:#175bb5;font-size:26px}.error-state .state-icon{background:#fff1f2;color:#be123c}.state-panel h1,.status-header h1{margin:0 0 10px;font-size:clamp(26px,4vw,38px);line-height:1.12}.state-panel p,.status-header p{color:#64748b;line-height:1.65}.state-panel a,.state-panel button,.management-link,.submit-review{display:inline-flex;min-height:44px;align-items:center;padding:0 18px;border:0;border-radius:12px;background:#123f73;color:#fff;text-decoration:none;font-weight:800;cursor:pointer}.submit-review{align-self:flex-start;margin-top:auto;background:#9a4d00}.submit-review:disabled{cursor:wait;opacity:.68}.submission-error{padding:12px 14px;border-radius:12px;background:#fff1f2;color:#9f1239;font-weight:700}.status-content{width:min(1080px,100%);margin:0 auto}.status-header{display:flex;align-items:flex-end;justify-content:space-between;gap:24px;margin-bottom:24px}.status-header p{margin:0}.eyebrow{font-size:12px;font-weight:900;letter-spacing:.14em;text-transform:uppercase;color:#1764bd!important}.overall-badge,.status-badge{display:inline-flex;align-items:center;border-radius:999px;padding:8px 12px;font-size:12px;font-weight:900;white-space:nowrap;background:#e2e8f0;color:#334155}.property-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:18px}.property-card{display:flex;flex-direction:column;gap:18px;padding:24px;background:rgba(255,255,255,.96);border:1px solid #dbe4ef;border-radius:18px;box-shadow:0 14px 34px rgba(15,23,42,.07)}.property-card>header{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.property-card h2{margin:3px 0 0;font-size:20px;line-height:1.3}.property-id{margin:0;color:#64748b;font-size:12px;font-weight:800}.guidance{margin:0;color:#475569;line-height:1.6}.property-card dl{display:grid;gap:8px;margin:0}.property-card dl div{display:flex;justify-content:space-between;gap:16px;padding:9px 0;border-bottom:1px solid #eef2f7}.property-card dt{color:#64748b}.property-card dd{margin:0;font-weight:800}.rejection-reason{display:grid;gap:5px;padding:14px;border-radius:12px;background:#fff1f2;color:#9f1239}.management-link{align-self:flex-start;margin-top:auto}.status-badge[data-status='APPROVED'],.overall-badge[data-status='APPROVED']{background:#dcfce7;color:#166534}.status-badge[data-status='PENDING']{background:#fef3c7;color:#92400e}.status-badge[data-status='DRAFT']{background:#e0f2fe;color:#075985}.status-badge[data-status='REJECTED']{background:#ffe4e6;color:#9f1239}.status-badge[data-status='SUSPENDED']{background:#ffedd5;color:#9a3412}.status-badge[data-status='CANCELLED']{background:#e2e8f0;color:#475569}.overall-badge[data-status='MIXED']{background:#ede9fe;color:#5b21b6}@media(max-width:680px){.status-page{padding:28px 14px 48px}.state-panel{padding:32px 20px}.status-header{align-items:flex-start;flex-direction:column}.property-grid{grid-template-columns:1fr}.property-card{padding:20px}}
  `]
})
export class PartnerRegistrationStatusComponent implements OnInit {
  private readonly statusService = inject(PartnerRegistrationStatusService);
  private readonly changeDetector = inject(ChangeDetectorRef);

  loading = true;
  error = '';
  response: PartnerRegistrationStatusResponse | null = null;
  readonly submittingPropertyIds = new Set<number>();
  readonly submittedPropertyIds = new Set<number>();
  readonly submissionErrors: Record<number, string> = {};

  ngOnInit(): void {
    this.load();
  }

  get properties(): PartnerPropertyStatusRow[] {
    return this.response?.properties ?? [];
  }

  get propertyCount(): number {
    return this.response?.propertyCount ?? 0;
  }

  get overallStatus(): PartnerOverallStatus {
    return this.response?.overallStatus ?? 'NONE';
  }

  get overallLabel(): string {
    if (this.overallStatus === 'NONE') return 'Chưa có hồ sơ';
    if (this.overallStatus === 'MIXED') return 'Nhiều trạng thái';
    return this.statusLabel(this.overallStatus);
  }

  get overallHeading(): string {
    return this.overallStatus === 'MIXED'
      ? 'Các cơ sở đang ở nhiều trạng thái'
      : 'Trạng thái hồ sơ của bạn';
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.statusService.load().subscribe({
      next: response => {
        this.response = response;
        this.loading = false;
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.error = 'Vui lòng kiểm tra kết nối và thử lại.';
        this.loading = false;
        this.changeDetector.detectChanges();
      }
    });
  }

  statusLabel(status: PartnerPropertyStatus): string {
    return STATUS_LABELS[status];
  }

  guidance(status: PartnerPropertyStatus): string {
    return STATUS_GUIDANCE[status];
  }

  isManagementReady(property: PartnerPropertyStatusRow): boolean {
    return property.status === 'APPROVED'
      && property.operationStatus === 'ACTIVE'
      && property.ownershipStatus === 'ACTIVE';
  }

  canSubmitForReview(property: PartnerPropertyStatusRow): boolean {
    return property.approvalStatus === 'DRAFT'
      && property.operationStatus === 'INACTIVE'
      && property.ownershipStatus === 'PENDING'
      && !this.submittedPropertyIds.has(property.propertyId);
  }

  isSubmitting(propertyId: number): boolean {
    return this.submittingPropertyIds.has(propertyId);
  }

  submitForReview(property: PartnerPropertyStatusRow): void {
    if (!this.canSubmitForReview(property) || this.isSubmitting(property.propertyId)) return;

    const propertyId = property.propertyId;
    let submissionCompleted = false;
    this.submittingPropertyIds.add(propertyId);
    delete this.submissionErrors[propertyId];

    this.statusService.submitForReview(propertyId).pipe(
      tap(() => {
        submissionCompleted = true;
        this.submittedPropertyIds.add(propertyId);
      }),
      switchMap(() => this.statusService.load()),
      finalize(() => {
        this.submittingPropertyIds.delete(propertyId);
        this.changeDetector.detectChanges();
      })
    ).subscribe({
      next: response => {
        this.response = response;
        this.submittedPropertyIds.delete(propertyId);
      },
      error: () => {
        this.submissionErrors[propertyId] = submissionCompleted
          ? 'Yêu cầu đã được gửi nhưng chưa thể làm mới trạng thái. Vui lòng tải lại trang.'
          : 'Không thể gửi cơ sở này để xét duyệt. Vui lòng thử lại.';
      }
    });
  }

  trackProperty(_index: number, property: PartnerPropertyStatusRow): number {
    return property.propertyId;
  }
}
