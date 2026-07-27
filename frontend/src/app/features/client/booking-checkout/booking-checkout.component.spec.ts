import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { ClientApiService } from '../../../core/services/client-api.service';
import { PaymentService } from '../../../core/services/payment.service';
import { BookingCheckoutComponent } from './booking-checkout.component';

describe('BookingCheckoutComponent', () => {
  let fixture: ComponentFixture<BookingCheckoutComponent>;
  let component: BookingCheckoutComponent;
  let reservation$: Subject<any>;
  let queryParams$: Subject<Record<string, string>>;
  let clientApi: { bookRoom: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    reservation$ = new Subject<any>();
    queryParams$ = new Subject<Record<string, string>>();
    clientApi = { bookRoom: vi.fn(() => reservation$) };

    await TestBed.configureTestingModule({
      imports: [BookingCheckoutComponent],
      providers: [
        { provide: ClientApiService, useValue: clientApi },
        { provide: PaymentService, useValue: { createPaymentUrl: vi.fn() } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ roomTypeId: '1' })),
            queryParams: queryParams$
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BookingCheckoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('blocks checkout when booking context is missing', () => {
    expect(component.bookingContextValid).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Phiên đặt phòng không hợp lệ');

    component.submitBooking();
    expect(clientApi.bookRoom).not.toHaveBeenCalled();
  });

  it('accepts the complete room-selection context after route parameters resolve', () => {
    queryParams$.next({
      checkIn: '2026-08-10',
      checkOut: '2026-08-12',
      adultCount: '2',
      childCount: '0',
      quantity: '1',
      hotelId: '10',
      roomTypeName: 'Deluxe',
      nightlyPrice: '500000',
      estimatedTotal: '1000000'
    });

    expect(component.bookingContextValid).toBe(true);
  });

  it('submits a valid booking only once while the request is pending', () => {
    component.roomTypeId = 1;
    component.hotelId = 10;
    component.roomTypeName = 'Deluxe';
    component.nightlyPrice = 500000;
    component.contextError = '';
    component.bookingData = {
      roomTypeId: 1,
      checkInDate: '2026-08-10',
      checkOutDate: '2026-08-12',
      guests: 2,
      adults: 2,
      children: 0,
      quantity: 1,
      firstName: 'An',
      lastName: 'Nguyen',
      phone: '0900000000',
      paymentMethod: 'PAY_AT_HOTEL',
      specialRequests: ''
    };

    component.submitBooking();
    component.submitBooking();

    expect(clientApi.bookRoom).toHaveBeenCalledTimes(1);
    expect(component.isSubmitting).toBe(true);

    reservation$.next({ id: 77 });
    reservation$.complete();
    expect(component.bookingSuccess).toBe(true);
  });
});
