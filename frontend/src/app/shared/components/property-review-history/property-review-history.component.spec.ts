import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PropertyReviewHistoryEvent } from '../../../core/services/property.service';
import { PropertyReviewHistoryComponent } from './property-review-history.component';

describe('PropertyReviewHistoryComponent', () => {
  let fixture: ComponentFixture<PropertyReviewHistoryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PropertyReviewHistoryComponent] }).compileComponents();
    fixture = TestBed.createComponent(PropertyReviewHistoryComponent);
  });

  it('renders a safe immutable transition without actor identifiers', () => {
    fixture.componentInstance.events = [historyEvent()];
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Đã phê duyệt');
    expect(text).toContain('Thực hiện bởi quản trị viên');
    expect(text).toContain('Chờ duyệt');
    expect(text).toContain('Hoạt động');
    expect(text).toContain('Hồ sơ hợp lệ.');
    expect(text).not.toContain('actorUserId');
  });

  it('emits retry from an accessible failure state', () => {
    const retry = vi.fn();
    fixture.componentInstance.error = 'Không thể tải lịch sử xét duyệt.';
    fixture.componentInstance.retry.subscribe(retry);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it('handles nullable legacy state snapshots without throwing', () => {
    fixture.componentInstance.events = [{
      ...historyEvent(),
      eventType: 'PROPERTY_SUBMITTED_FOR_APPROVAL',
      beforeState: null,
      afterState: {
        status: 'PENDING_APPROVAL',
        approvalStatus: 'PENDING_APPROVAL',
        operationStatus: null,
        ownershipStatus: null
      }
    }];

    expect(() => fixture.detectChanges()).not.toThrow();
    expect(fixture.nativeElement.textContent).toContain('Đã gửi xét duyệt');
    expect(fixture.nativeElement.textContent).toContain('Không xác định');
  });

  it('treats offset-less backend LocalDateTime values as UTC', () => {
    expect(fixture.componentInstance.formatDate('2026-08-04T10:00:00'))
      .toBe(fixture.componentInstance.formatDate('2026-08-04T10:00:00Z'));
  });
});

function historyEvent(): PropertyReviewHistoryEvent {
  return {
    eventId: 17,
    propertyId: 7,
    eventType: 'PROPERTY_APPROVED' as const,
    actorKind: 'ADMIN' as const,
    note: 'Hồ sơ hợp lệ.',
    beforeState: state('PENDING_APPROVAL', 'PENDING_APPROVAL', 'INACTIVE', 'PENDING'),
    afterState: state('ACTIVE', 'APPROVED', 'ACTIVE', 'ACTIVE'),
    occurredAt: '2026-08-04T10:00:00Z'
  };
}

function state(status: string, approvalStatus: string, operationStatus: string, ownershipStatus: string) {
  return { status, approvalStatus, operationStatus, ownershipStatus };
}
