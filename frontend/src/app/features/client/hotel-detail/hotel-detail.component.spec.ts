import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ElementRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgForm } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { AuthService } from '../../../core/services/auth';
import { ClientApiService } from '../../../core/services/client-api.service';
import { OperationalPolicyService } from '../../../core/services/operational-policy.service';
import { PropertyClaimResponse, PropertyClaimService } from '../../../core/services/property-claim.service';
import { HotelDetailComponent } from './hotel-detail.component';

describe('HotelDetailComponent', () => {
  let fixture: ComponentFixture<HotelDetailComponent>;
  let component: HotelDetailComponent;
  let params$: Subject<ParamMap>;
  let api: { getHotelById: ReturnType<typeof vi.fn>; getRoomTypesByHotel: ReturnType<typeof vi.fn> };
  let claims: { submit: ReturnType<typeof vi.fn> };
  let auth: { isLoggedIn: ReturnType<typeof vi.fn> };
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    params$ = new Subject<ParamMap>();
    api = {
      getHotelById: vi.fn(() => throwError(() => ({ status: 404 }))),
      getRoomTypesByHotel: vi.fn(() => of([]))
    };
    claims = { submit: vi.fn() };
    auth = { isLoggedIn: vi.fn(() => true) };

    await TestBed.configureTestingModule({
      imports: [HotelDetailComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: auth },
        { provide: ClientApiService, useValue: api },
        { provide: PropertyClaimService, useValue: claims },
        { provide: OperationalPolicyService, useValue: { current: vi.fn(() => of(null)) } },
        { provide: ActivatedRoute, useValue: { queryParams: of({}), paramMap: params$, snapshot: { fragment: null } } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(HotelDetailComponent);
    component = fixture.componentInstance;
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => vi.restoreAllMocks());

  it('renders a recoverable state for an invalid route parameter', () => {
    params$.next(convertToParamMap({ id: 'not-a-number' }));
    fixture.detectChanges();

    expect(component.pageError).toContain('không hợp lệ');
    expect(api.getHotelById).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Tìm chỗ nghỉ khác');
  });

  it('renders a not-found recovery state when the API returns 404', () => {
    params$.next(convertToParamMap({ id: '999999' }));
    fixture.detectChanges();

    expect(api.getHotelById).toHaveBeenCalledWith(999999);
    expect(component.pageError).toContain('Không tìm thấy chỗ nghỉ này');
    expect(fixture.nativeElement.textContent).toContain('Chuyến đi vẫn có thể tiếp tục');
  });

  it('hides stale property details when the public room catalog becomes unavailable', () => {
    api.getHotelById.mockReturnValue(of({ id: 44, name: 'Stale property' }));
    api.getRoomTypesByHotel.mockReturnValue(throwError(() => ({ status: 404 })));

    params$.next(convertToParamMap({ id: '44' }));
    fixture.detectChanges();

    expect(api.getRoomTypesByHotel).toHaveBeenCalledWith(44, undefined, undefined, 2);
    expect(component.hotel).toBeNull();
    expect(component.roomTypes).toEqual([]);
    expect(component.pageError).toContain('Không tìm thấy chỗ nghỉ này');
  });

  it('redirects an unauthenticated claimant before opening or resetting the modal', () => {
    auth.isLoggedIn.mockReturnValue(false);
    component.hotel = importedProperty();
    component.claimForm.verificationData = 'leave-this-value-alone';

    component.openClaimModal();

    expect(component.showClaimModal).toBe(false);
    expect(component.claimForm.verificationData).toBe('leave-this-value-alone');
    expect(claims.submit).not.toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/hotel/17' }
    });
  });

  it('trims the typed claim payload and blocks duplicate submissions while pending', () => {
    const response$ = new Subject<PropertyClaimResponse>();
    claims.submit.mockReturnValue(response$);
    component.hotel = importedProperty();
    component.openClaimModal();
    fixture.detectChanges();
    component.claimForm = {
      verificationMethod: 'EMAIL',
      verificationData: '  owner@example.com  ',
      note: '  Company mailbox  '
    };

    component.submitClaim(formStub());
    component.submitClaim(formStub());

    expect(claims.submit).toHaveBeenCalledTimes(1);
    expect(claims.submit).toHaveBeenCalledWith(17, {
      verificationMethod: 'EMAIL',
      verificationData: 'owner@example.com',
      note: 'Company mailbox'
    });
    expect(component.claimSubmitting).toBe(true);

    response$.next({ id: 81, status: 'PENDING' } as PropertyClaimResponse);
    response$.complete();
    fixture.detectChanges();

    expect(component.claimSubmitting).toBe(false);
    expect(component.claimSubmittedPropertyId).toBe(17);
    expect(component.claimRequestSuccess).toContain('Yêu cầu đã được gửi');
  });

  it('rejects oversized verification data before calling the API', () => {
    component.hotel = importedProperty();
    component.openClaimModal();
    component.claimForm.verificationData = 'x'.repeat(1001);

    component.submitClaim(formStub());

    expect(claims.submit).not.toHaveBeenCalled();
    expect(component.claimRequestError).toContain('1000 ký tự');
  });

  it('renders a safe accessible Retry-After message instead of using an alert', () => {
    const browserAlert = vi.spyOn(window, 'alert').mockImplementation(() => undefined);
    claims.submit.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 429,
      headers: new HttpHeaders({ 'Retry-After': '90' }),
      error: { message: 'Limiter bucket and account internals' }
    })));
    component.hotel = importedProperty();
    component.openClaimModal();
    fixture.detectChanges();
    component.claimForm.verificationData = 'license-123';

    component.submitClaim(formStub());
    fixture.detectChanges();

    expect(component.claimRequestError).toContain('2 phút');
    expect(component.claimRequestError).not.toContain('Limiter');
    expect(browserAlert).not.toHaveBeenCalled();
  });

  it('shows stable conflict feedback after a concurrent duplicate and allows a deliberate retry', () => {
    const retryResponse$ = new Subject<PropertyClaimResponse>();
    claims.submit
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({
        status: 409,
        error: {
          code: 'PROPERTY_CLAIM_CONFLICT',
          message: 'Unique constraint UX_PROPERTY_CLAIM_PENDING exposed requester 42'
        }
      })))
      .mockReturnValueOnce(retryResponse$);
    component.hotel = importedProperty();
    component.openClaimModal();
    component.claimForm.verificationData = 'license-123';

    component.submitClaim(formStub());

    expect(component.claimSubmitting).toBe(false);
    expect(component.claimSubmitted).toBe(false);
    expect(component.claimRequestError).toContain('đồng thời');
    expect(component.claimRequestError).not.toContain('UX_PROPERTY_CLAIM_PENDING');

    component.submitClaim(formStub());
    component.submitClaim(formStub());

    expect(claims.submit).toHaveBeenCalledTimes(2);
    expect(component.claimSubmitting).toBe(true);
    retryResponse$.next({ id: 82, status: 'PENDING' } as PropertyClaimResponse);
    retryResponse$.complete();
  });

  it('redirects safely when the claim session expires during submission', () => {
    claims.submit.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 401,
      error: { message: 'Token internals must stay hidden' }
    })));
    component.hotel = importedProperty();
    component.openClaimModal();
    component.claimForm.verificationData = 'license-123';

    component.submitClaim(formStub());

    expect(component.showClaimModal).toBe(false);
    expect(component.claimForm.verificationData).toBe('');
    expect(component.claimRequestError).not.toContain('Token internals');
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/hotel/17' }
    });
  });

  it('traps focus, closes on Escape and restores focus to the claim CTA', () => {
    const cta = document.createElement('button');
    const dialog = document.createElement('form');
    const close = document.createElement('button');
    const method = document.createElement('select');
    const verification = document.createElement('input');
    const submit = document.createElement('button');
    close.type = 'button';
    method.dataset['claimInitialFocus'] = '';
    submit.type = 'submit';
    dialog.append(close, method, verification, submit);
    document.body.append(cta, dialog);
    const privateComponent = component as unknown as {
      claimDialog: ElementRef<HTMLElement>;
      claimCta: ElementRef<HTMLElement>;
    };
    privateComponent.claimDialog = new ElementRef(dialog);
    privateComponent.claimCta = new ElementRef(cta);
    component.hotel = importedProperty();

    cta.focus();
    component.openClaimModal({ currentTarget: cta } as unknown as Event);
    component.ngAfterViewChecked();

    expect(document.activeElement).toBe(method);

    submit.focus();
    component.handleClaimDialogKeydown(new KeyboardEvent('keydown', { key: 'Tab' }));
    expect(document.activeElement).toBe(close);

    close.focus();
    component.handleClaimDialogKeydown(new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true }));
    expect(document.activeElement).toBe(submit);

    component.handleClaimDialogKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(component.showClaimModal).toBe(false);
    expect(document.activeElement).toBe(cta);
    cta.remove();
    dialog.remove();
  });

  it('does not close the claim modal on Escape while submitting', () => {
    component.hotel = importedProperty();
    component.openClaimModal();
    component.claimSubmitting = true;

    component.handleClaimDialogKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(component.showClaimModal).toBe(true);
  });
});

function formStub(): NgForm {
  return { control: { markAllAsTouched: vi.fn() } } as unknown as NgForm;
}

function importedProperty() {
  return {
    id: 17,
    name: 'Imported Hotel',
    addressLine: '1 Test Street',
    starRating: 0,
    latitude: 0,
    longitude: 0,
    approvalStatus: 'IMPORTED_PENDING_REVIEW'
  };
}
