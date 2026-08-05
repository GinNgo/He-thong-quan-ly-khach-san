import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { of, throwError } from 'rxjs';

import { InvoiceService } from '../../core/services/invoice.service';
import { ReservationService } from '../../core/services/reservation.service';
import { SubscriptionService } from '../../core/services/subscription.service';
import { PlatformBillingService } from '../../core/services/platform-billing.service';
import { PermissionService } from '../../core/services/permission.service';
import { InvoiceManagement } from './invoice-management/invoice-management';
import { PropertyClaimsComponent } from './property-claims/property-claims.component';
import { PropertyImportsComponent } from './property-imports/property-imports.component';
import { SubscriptionPlansComponent } from './subscription-plans/subscription-plans';

describe('admin route state remediation', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('shows a retryable property-claim load error instead of logging only', async () => {
    await TestBed.configureTestingModule({
      imports: [PropertyClaimsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyClaimsComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    http.expectOne(request => request.url.endsWith('/admin/property-claims'))
      .flush({ message: 'Claim queue unavailable' }, { status: 503, statusText: 'Unavailable' });
    await vi.waitFor(() => expect(fixture.componentInstance.loading).toBe(false));
    fixture.detectChanges();

    expect(fixture.componentInstance.loadError).toBe('Claim queue unavailable');
    expect(fixture.componentInstance.loading).toBe(false);
    fixture.componentInstance.loadClaims();
    http.expectOne(request => request.url.endsWith('/admin/property-claims')).flush({ content: [] });
    http.verify();
  });

  it('shows actionable empty guidance for property-import batches', async () => {
    await TestBed.configureTestingModule({
      imports: [PropertyImportsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyImportsComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    http.expectOne(request => request.url.endsWith('/admin/property-imports')).flush({ content: [] });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(fixture)).toContain('No import batches');
    expect(text(fixture)).toContain('stage the first review batch');
    http.verify();
  });

  it('shows a retryable catalog error on the property-scoped subscription surface', async () => {
    await TestBed.configureTestingModule({
      imports: [SubscriptionPlansComponent],
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        { provide: SubscriptionService, useValue: {
          getPlans: () => throwError(() => ({ error: { message: 'Catalog unavailable' } })),
        } },
        { provide: PlatformBillingService, useValue: {} },
        { provide: PermissionService, useValue: { hasPermission: () => false, isSuperAdmin: () => false } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(SubscriptionPlansComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(fixture)).toContain('Catalog unavailable');
    expect(fixture.nativeElement.querySelectorAll('button')).toHaveLength(1);
  });

  it('shows invoice loading failures and does not render an empty table shell', async () => {
    await TestBed.configureTestingModule({
      imports: [InvoiceManagement],
      providers: [
        provideNoopAnimations(),
        ConfirmationService,
        { provide: ReservationService, useValue: {
          getAllReservations: () => throwError(() => ({ error: { message: 'Reservation ledger unavailable' } })),
        } },
        { provide: InvoiceService, useValue: {
          getInvoiceByReservation: () => of(null),
          generateInvoice: () => of(null),
        } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(InvoiceManagement);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(fixture)).toContain('Reservation ledger unavailable');
    expect(fixture.nativeElement.querySelector('p-table')).toBeNull();
  });
});

function text<T>(fixture: ComponentFixture<T>): string {
  return fixture.nativeElement.textContent.replace(/\s+/g, ' ').trim();
}
