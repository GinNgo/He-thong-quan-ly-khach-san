import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NEVER, Subject, of, throwError } from 'rxjs';

import { HotelServiceService } from '../../../core/services/hotel-service.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { PermissionService } from '../../../core/services/permission.service';
import { PropertyCheckoutService } from '../../../core/services/property-checkout.service';
import { Reservation, ReservationPage, ReservationService } from '../../../core/services/reservation.service';
import { ReservationManagement } from './reservation-management';

describe('ReservationManagement read journey', () => {
  let fixture: ComponentFixture<ReservationManagement>;
  let component: ReservationManagement;
  let searchReservations: ReturnType<typeof vi.fn>;
  let getReservationById: ReturnType<typeof vi.fn>;

  const reservation: Reservation = {
    id: 801,
    userId: 41,
    userFullName: 'Khách seeded',
    checkInDate: '2026-08-15',
    checkOutDate: '2026-08-17',
    guests: 2,
    totalAmount: 1800000,
    status: 'CONFIRMED',
    paymentMethod: 'VNPAY',
    details: [],
    events: [{
      id: 9,
      eventType: 'RESERVATION_CREATED',
      reason: 'Reservation created from an authoritative server quote.',
      actorType: 'USER',
      occurredAt: '2026-08-04T08:15:00',
    }],
  };

  const page: ReservationPage = {
    content: [reservation], page: 0, size: 10, totalElements: 1, totalPages: 1,
  };

  beforeEach(async () => {
    searchReservations = vi.fn(() => of(page));
    getReservationById = vi.fn(() => of(reservation));

    await TestBed.configureTestingModule({
      imports: [ReservationManagement],
      providers: [
        { provide: ReservationService, useValue: {
          searchReservations,
          getReservationById,
        } },
        { provide: InvoiceService, useValue: {} },
        { provide: HotelServiceService, useValue: { getServices: vi.fn(() => of([])) } },
        { provide: PropertyCheckoutService, useValue: { preview: vi.fn(() => NEVER) } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        { provide: PermissionService, useValue: { hasPermission: vi.fn(() => false) } },
      ],
    }).compileComponents();
  });

  it('keeps the loading state visible until the server page arrives', async () => {
    const response = new Subject<ReservationPage>();
    searchReservations.mockReturnValue(response);
    fixture = TestBed.createComponent(ReservationManagement);
    component = fixture.componentInstance;

    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Đang tải danh sách đặt phòng');

    response.next(page);
    response.complete();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Khách seeded');
    expect(fixture.nativeElement.textContent).toContain('Đã xác nhận');
  });

  it('shows an actionable error state and supports retry', () => {
    searchReservations
      .mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValueOnce(of(page));
    fixture = TestBed.createComponent(ReservationManagement);
    fixture.detectChanges();

    const retry = fixture.nativeElement.querySelector('.reservation-state--error button') as HTMLButtonElement;
    expect(retry).toBeTruthy();
    retry.click();
    fixture.detectChanges();

    expect(searchReservations).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Khách seeded');
  });

  it('shows a stable empty state for a filtered page', () => {
    searchReservations.mockReturnValue(of({ ...page, content: [], totalElements: 0, totalPages: 0 }));
    fixture = TestBed.createComponent(ReservationManagement);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Không có dữ liệu đặt phòng');
  });

  it('loads an authorized detail and renders immutable event history', async () => {
    fixture = TestBed.createComponent(ReservationManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.openReservationDetail(801);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getReservationById).toHaveBeenCalledWith(801);
    expect(document.body.textContent).toContain('Chi tiết đặt phòng');
    expect(document.body.textContent).toContain('Đã tạo đặt phòng');
  });

  it('sends status, query and stable page coordinates to the backend', () => {
    fixture = TestBed.createComponent(ReservationManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
    searchReservations.mockClear();
    component.searchQuery = 'RES-801';
    component.statusFilter = 'CONFIRMED';

    component.loadReservations(true);

    expect(searchReservations).toHaveBeenCalledWith({
      query: 'RES-801', status: 'CONFIRMED', page: 0, size: 10,
    });
  });
});
