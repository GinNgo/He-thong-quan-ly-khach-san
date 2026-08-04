import { BookingCheckoutRecoveryService } from './booking-checkout-recovery.service';

describe('BookingCheckoutRecoveryService', () => {
  const service = new BookingCheckoutRecoveryService();

  beforeEach(() => localStorage.clear());

  it('restores only the same user and room type', () => {
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'customer' }));
    service.save({
      roomTypeId: 11,
      reservationId: 91,
      attemptId: 'attempt-1',
      paymentMethod: 'MOMO',
      phase: 'PAYMENT_PENDING',
    });

    expect(service.load(11)?.reservationId).toBe(91);
    expect(service.load(12)).toBeNull();
  });

  it('rejects state after the signed-in user changes', () => {
    localStorage.setItem('user', JSON.stringify({ id: 7 }));
    service.save({
      roomTypeId: 11,
      reservationId: 91,
      attemptId: null,
      paymentMethod: 'PAY_AT_HOTEL',
      phase: 'RESERVATION_CREATED',
    });
    localStorage.setItem('user', JSON.stringify({ id: 8 }));

    expect(service.load(11)).toBeNull();
  });
});
