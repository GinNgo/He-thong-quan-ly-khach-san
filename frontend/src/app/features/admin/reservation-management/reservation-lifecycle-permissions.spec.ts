import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NEVER, of } from 'rxjs';
import { Reservation, ReservationService } from '../../../core/services/reservation.service';
import { PaymentService } from '../../../core/services/payment.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { HotelServiceService } from '../../../core/services/hotel-service.service';
import { PermissionService, ActionCode, FunctionCode } from '../../../core/services/permission.service';
import { PropertyCheckoutService } from '../../../core/services/property-checkout.service';
import { Router } from '@angular/router';
import { ReservationManagement } from './reservation-management';

describe('ReservationManagement lifecycle permissions', () => {
  let fixture: ComponentFixture<ReservationManagement>;
  let component: ReservationManagement;
  let reservationService: {
    searchReservations: ReturnType<typeof vi.fn>;
    getReservationById: ReturnType<typeof vi.fn>;
    checkIn: ReturnType<typeof vi.fn>;
    cancelOperational: ReturnType<typeof vi.fn>;
    markNoShow: ReturnType<typeof vi.fn>;
    updateReservationStatus: ReturnType<typeof vi.fn>;
    getAvailableRoomContext: ReturnType<typeof vi.fn>;
    updateRoomAssignment: ReturnType<typeof vi.fn>;
    releaseRoomAssignment: ReturnType<typeof vi.fn>;
  };

  const reservation: Reservation = {
    id: 55,
    userId: 8,
    username: 'guest',
    checkInDate: '2026-08-02',
    checkOutDate: '2026-08-03',
    guests: 2,
    totalAmount: 500000,
    status: 'CONFIRMED',
    paymentMethod: 'MOMO',
    details: [],
  };

  beforeEach(async () => {
    reservationService = {
      searchReservations: vi.fn(() => of({
        content: [reservation], page: 0, size: 10, totalElements: 1, totalPages: 1,
      })),
      getReservationById: vi.fn(() => of(reservation)),
      checkIn: vi.fn(() => of(reservation)),
      cancelOperational: vi.fn(() => of(reservation)),
      markNoShow: vi.fn(() => of(reservation)),
      updateReservationStatus: vi.fn(() => of(reservation)),
      getAvailableRoomContext: vi.fn(() => of({
        reservationId: 55,
        hotelId: 3,
        roomTypeId: 7,
        roomTypeName: 'Deluxe',
        checkInDate: '2026-08-02',
        checkOutDate: '2026-08-03',
        requiredQuantity: 1,
        assignedRooms: [],
        assignedRoomIds: [],
        candidates: [],
      })),
      updateRoomAssignment: vi.fn(() => of(reservation)),
      releaseRoomAssignment: vi.fn(() => of(reservation)),
    };

    await TestBed.configureTestingModule({
      imports: [ReservationManagement],
      providers: [
        { provide: ReservationService, useValue: reservationService },
        { provide: PaymentService, useValue: {} },
        { provide: InvoiceService, useValue: {} },
        { provide: HotelServiceService, useValue: { getServices: vi.fn(() => of([])) } },
        { provide: PropertyCheckoutService, useValue: { preview: vi.fn(() => NEVER) } },
        { provide: Router, useValue: { navigate: vi.fn() } },
        {
          provide: PermissionService,
          useValue: {
            hasPermission: vi.fn((functionCode: string, actionCode: number) =>
              (functionCode === FunctionCode.RESERVATION && actionCode === ActionCode.UPDATE) ||
              (functionCode === FunctionCode.RESERVATION_AMEND && actionCode === ActionCode.UPDATE) ||
              (functionCode === FunctionCode.RESERVATION_ASSIGNMENT &&
                (actionCode === ActionCode.VIEW || actionCode === ActionCode.UPDATE)) ||
              (functionCode === FunctionCode.HOTEL_SERVICE && actionCode === ActionCode.VIEW) ||
              (functionCode === FunctionCode.CHECKIN && actionCode === ActionCode.UPDATE) ||
              (functionCode === FunctionCode.RESERVATION_CANCEL && actionCode === ActionCode.UPDATE) ||
              (functionCode === FunctionCode.RESERVATION_NO_SHOW && actionCode === ActionCode.UPDATE)),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReservationManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows dedicated actions and invokes their dedicated client commands', () => {
    expect(fixture.nativeElement.querySelector('[data-action="check-in"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action="no-show"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action="cancel-operational"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action="amend-reservation"]')).not.toBeNull();

    component.checkIn(55);
    component.markNoShow(55);
    component.cancelOperational(55);

    expect(reservationService.checkIn).toHaveBeenCalledWith(55);
    expect(reservationService.markNoShow).toHaveBeenCalledWith(55);
    expect(reservationService.cancelOperational).toHaveBeenCalledWith(55);
  });

  it('opens the assignment workflow only with the dedicated view and update masks', () => {
    component.openRoomPicker(reservation);
    fixture.detectChanges();

    expect(component.showRoomPickerDialog).toBe(true);
    expect(component.canViewRoomAssignments).toBe(true);
    expect(component.canManageRoomAssignments).toBe(true);
    expect(fixture.nativeElement.querySelector('app-physical-room-picker')).not.toBeNull();
  });

  it('keeps assignment mutation hidden for a view-only operator', () => {
    const permissionService = TestBed.inject(PermissionService) as unknown as { hasPermission: ReturnType<typeof vi.fn> };
    permissionService.hasPermission.mockImplementation((functionCode: string, actionCode: number) =>
      functionCode === FunctionCode.RESERVATION_ASSIGNMENT && actionCode === ActionCode.VIEW);

    fixture = TestBed.createComponent(ReservationManagement);
    component = fixture.componentInstance;
    fixture.detectChanges();
    component.openRoomPicker(reservation);
    fixture.detectChanges();

    expect(component.canViewRoomAssignments).toBe(true);
    expect(component.canManageRoomAssignments).toBe(false);
    expect(fixture.nativeElement.querySelector('.assignment-command')).toBeNull();
  });

  it('does not render lifecycle controls when the dedicated masks are absent', async () => {
    const permissionService = TestBed.inject(PermissionService) as unknown as { hasPermission: ReturnType<typeof vi.fn> };
    permissionService.hasPermission.mockReturnValue(false);

    fixture = TestBed.createComponent(ReservationManagement);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-action="check-in"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action="no-show"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action="cancel-operational"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-action="amend-reservation"]')).toBeNull();
  });

  it('does not treat the generic reservation update mask as amendment authority', () => {
    const permissionService = TestBed.inject(PermissionService) as unknown as { hasPermission: ReturnType<typeof vi.fn> };
    permissionService.hasPermission.mockImplementation((functionCode: string, actionCode: number) =>
      functionCode === FunctionCode.RESERVATION && actionCode === ActionCode.UPDATE);

    fixture = TestBed.createComponent(ReservationManagement);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-action="amend-reservation"]')).toBeNull();
  });

  it('routes in-stay service work to the authoritative folio workspace', () => {
    component.openCheckoutWorkspace({ ...reservation, status: 'CHECKED_IN' });

    expect(component.selectedReservationId).toBe(55);
    expect(component.showCheckoutDialog).toBe(true);
    expect('openAddServiceDialog' in component).toBe(false);
    expect('submitAddService' in component).toBe(false);
  });
});
