import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import {
  PropertyReviewHistoryEvent,
  PropertyReviewState
} from '../../../core/services/property.service';

interface StateChange {
  label: string;
  before: string;
  after: string;
}

@Component({
  selector: 'app-property-review-history',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="history-panel" aria-label="Lịch sử xét duyệt cơ sở">
      @if (loading) {
        <div class="history-state" role="status" aria-live="polite">
          <i class="pi pi-spinner pi-spin" aria-hidden="true"></i>
          <span>Đang tải lịch sử...</span>
        </div>
      } @else if (error) {
        <div class="history-state history-state--error" role="alert">
          <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          <span>{{ error }}</span>
          <button type="button" (click)="retry.emit()">Thử lại</button>
        </div>
      } @else if (events.length === 0) {
        <div class="history-state history-state--empty">
          <i class="pi pi-history" aria-hidden="true"></i>
          <span>Chưa có sự kiện xét duyệt nào được ghi nhận.</span>
        </div>
      } @else {
        <ol class="history-timeline">
          @for (event of events; track event.eventId) {
            <li class="history-event" [attr.data-event-id]="event.eventId">
              <span class="history-marker" aria-hidden="true"></span>
              <article>
                <header>
                  <div>
                    <strong>{{ eventLabel(event.eventType) }}</strong>
                    <span class="history-actor">{{ actorLabel(event.actorKind) }}</span>
                  </div>
                  <time [attr.datetime]="event.occurredAt">{{ formatDate(event.occurredAt) }}</time>
                </header>

                @if (stateChanges(event).length) {
                  <dl class="history-state-changes">
                    @for (change of stateChanges(event); track change.label) {
                      <div>
                        <dt>{{ change.label }}</dt>
                        <dd>
                          <span>{{ stateLabel(change.before) }}</span>
                          <i class="pi pi-arrow-right" aria-hidden="true"></i>
                          <strong>{{ stateLabel(change.after) }}</strong>
                        </dd>
                      </div>
                    }
                  </dl>
                }

                @if (event.note) {
                  <p class="history-note"><strong>Ghi chú:</strong> {{ event.note }}</p>
                }
              </article>
            </li>
          }
        </ol>
      }
    </section>
  `,
  styles: [`
    :host{display:block}.history-panel{min-height:96px}.history-state{display:flex;align-items:center;justify-content:center;gap:10px;min-height:120px;padding:24px;text-align:center;color:#526070;background:#f7f9fc;border:1px dashed #cbd5e1;border-radius:14px}.history-state--error{flex-wrap:wrap;color:#9f1239;background:#fff1f2;border-color:#fecdd3}.history-state button{min-height:38px;padding:0 14px;border:0;border-radius:10px;background:#173f6b;color:#fff;font-weight:800;cursor:pointer}.history-timeline{display:grid;gap:0;margin:0;padding:0;list-style:none}.history-event{position:relative;display:grid;grid-template-columns:18px 1fr;gap:12px;padding-bottom:18px}.history-event:not(:last-child)::before{content:'';position:absolute;left:7px;top:16px;bottom:0;width:2px;background:#dbe4ef}.history-marker{position:relative;z-index:1;width:16px;height:16px;margin-top:4px;border:4px solid #dbeafe;border-radius:50%;background:#175bb5}.history-event article{padding:16px;border:1px solid #dbe4ef;border-radius:14px;background:#fff;box-shadow:0 8px 24px rgba(15,23,42,.05)}.history-event header{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.history-event header>div{display:grid;gap:4px}.history-event time,.history-actor{font-size:12px;color:#64748b}.history-state-changes{display:grid;gap:7px;margin:14px 0 0}.history-state-changes div{display:flex;justify-content:space-between;gap:14px;padding-top:7px;border-top:1px solid #eef2f7}.history-state-changes dt{color:#64748b}.history-state-changes dd{display:flex;align-items:center;gap:8px;margin:0;text-align:right}.history-state-changes i{font-size:11px;color:#94a3b8}.history-note{margin:14px 0 0;padding:12px;border-radius:10px;background:#f8fafc;color:#334155;line-height:1.55}@media(max-width:600px){.history-event header,.history-state-changes div{flex-direction:column}.history-state-changes dd{text-align:left}.history-event article{padding:14px}}
  `]
})
export class PropertyReviewHistoryComponent {
  @Input() events: PropertyReviewHistoryEvent[] = [];
  @Input() loading = false;
  @Input() error = '';
  @Output() readonly retry = new EventEmitter<void>();

  eventLabel(eventType: string): string {
    const normalized = this.normalize(eventType);
    return ({
      SUBMITTED: 'Đã gửi xét duyệt',
      PROPERTY_SUBMITTED: 'Đã gửi xét duyệt',
      PROPERTY_SUBMITTED_FOR_APPROVAL: 'Đã gửi xét duyệt',
      APPROVED: 'Đã phê duyệt',
      PROPERTY_APPROVED: 'Đã phê duyệt',
      REJECTED: 'Đã từ chối',
      PROPERTY_REJECTED: 'Đã từ chối',
      SUSPENDED: 'Đã tạm ngừng',
      PROPERTY_SUSPENDED: 'Đã tạm ngừng',
      REACTIVATED: 'Đã kích hoạt lại',
      PROPERTY_REACTIVATED: 'Đã kích hoạt lại',
      CLOSED: 'Đã đóng cơ sở',
      PROPERTY_CLOSED: 'Đã đóng cơ sở'
    } as Record<string, string>)[normalized] ?? this.humanize(normalized);
  }

  actorLabel(actorKind: string): string {
    const normalized = this.normalize(actorKind);
    return ({
      OWNER: 'Thực hiện bởi chủ cơ sở',
      ADMIN: 'Thực hiện bởi quản trị viên',
      SUPER_ADMIN: 'Thực hiện bởi quản trị hệ thống',
      SYSTEM: 'Thực hiện tự động bởi hệ thống'
    } as Record<string, string>)[normalized] ?? 'Thực hiện bởi tác nhân được ủy quyền';
  }

  stateChanges(event: PropertyReviewHistoryEvent): StateChange[] {
    const fields: Array<{ key: keyof PropertyReviewState; label: string }> = [
      { key: 'status', label: 'Trạng thái' },
      { key: 'approvalStatus', label: 'Phê duyệt' },
      { key: 'operationStatus', label: 'Vận hành' },
      { key: 'ownershipStatus', label: 'Quyền sở hữu' }
    ];
    return fields.flatMap(field => {
      const before = event.beforeState?.[field.key] ?? null;
      const after = event.afterState?.[field.key] ?? null;
      if (before === after || (before === null && after === null)) return [];
      return [{
        label: field.label,
        before: before ?? 'UNKNOWN',
        after: after ?? 'UNKNOWN'
      }];
    });
  }

  stateLabel(status: string | null | undefined): string {
    const normalized = this.normalize(status);
    return ({
      ACTIVE: 'Hoạt động',
      APPROVED: 'Đã duyệt',
      CLOSED: 'Đã đóng',
      DRAFT: 'Bản nháp',
      INACTIVE: 'Chưa hoạt động',
      PENDING: 'Chờ xử lý',
      PENDING_APPROVAL: 'Chờ duyệt',
      REJECTED: 'Từ chối',
      SUSPENDED: 'Tạm ngừng',
      UNKNOWN: 'Không xác định'
    } as Record<string, string>)[normalized] ?? this.humanize(normalized);
  }

  formatDate(value: string): string {
    const timestamp = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value) ? value : `${value}Z`;
    const date = new Date(timestamp);
    return Number.isNaN(date.getTime())
      ? 'Thời điểm không xác định'
      : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
  }

  private normalize(value: string | null | undefined): string {
    return String(value ?? '').trim().toUpperCase().replaceAll(' ', '_');
  }

  private humanize(value: string): string {
    const normalized = value.replaceAll('_', ' ').toLocaleLowerCase('vi-VN');
    return normalized ? normalized.charAt(0).toLocaleUpperCase('vi-VN') + normalized.slice(1) : 'Sự kiện';
  }
}
