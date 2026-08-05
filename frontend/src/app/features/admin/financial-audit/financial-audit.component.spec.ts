import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { FinancialAuditService } from '../../../core/services/financial-audit.service';
import { FinancialAuditComponent } from './financial-audit.component';

describe('FinancialAuditComponent', () => {
  it('renders redacted references, policy and paginated tenant events', async () => {
    await TestBed.configureTestingModule({ imports: [FinancialAuditComponent], providers: [
      { provide: AuthService, useValue: { getRoles: () => ['PROPERTY_OWNER'] } },
      { provide: FinancialAuditService, useValue: {
        policy: () => of({ appendOnly: true, retentionDays: 2555, exportMaxRows: 10000, redactionPolicy: 'REDACT_SECRETS_AND_PII_HASH_EXTERNAL_IDENTITIES' }),
        search: () => of({ content: [{ id: 1, context: 'PROPERTY_COMMERCE', hotelId: 7, aggregateType: 'PAYMENT', aggregateId: 'PAY-1', actorType: 'USER', actorId: 3, source: 'CALLBACK', previousState: 'PENDING', newState: 'PAID', providerReference: 'sha256:1234abcd', idempotencyReference: 'sha256:5678abcd', correlationId: 'corr-1', metadataJson: '{"email":"[REDACTED]"}', occurredAt: '2026-08-04T10:00:00Z' }], totalElements: 1, totalPages: 1, number: 0, size: 25 }),
        export: () => of(new Blob(['csv'])),
      } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(FinancialAuditComponent); fixture.detectChanges(); await fixture.whenStable();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Append-only'); expect(text).toContain('2555'); expect(text).toContain('PAY-1');
    fixture.componentInstance.toggle(1); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('sha256:1234abcd');
    expect(fixture.nativeElement.textContent).toContain('[REDACTED]');
    expect(fixture.nativeElement.querySelector('select[name="context"]')).toBeNull();
  });
});
