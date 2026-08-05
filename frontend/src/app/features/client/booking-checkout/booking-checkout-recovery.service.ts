import { Injectable } from '@angular/core';

export type CheckoutPhase = 'RESERVATION_CREATED' | 'PAYMENT_PENDING' | 'PAYMENT_SUCCESS' | 'PAYMENT_FAILED' | 'PAYMENT_EXPIRED';

export interface BookingCheckoutRecoveryState {
  userKey: string;
  roomTypeId: number;
  reservationId: number;
  attemptId: string | null;
  paymentMethod: string;
  phase: CheckoutPhase;
  expiresAt: number;
}

@Injectable({ providedIn: 'root' })
export class BookingCheckoutRecoveryService {
  private readonly storageKey = 'hotel:booking:checkout-recovery';
  private readonly ttlMs = 24 * 60 * 60 * 1000;

  load(roomTypeId: number): BookingCheckoutRecoveryState | null {
    const userKey = this.currentUserKey();
    if (!userKey) return null;
    try {
      const state = JSON.parse(localStorage.getItem(this.storageKey) || 'null') as BookingCheckoutRecoveryState | null;
      if (!state || state.userKey !== userKey || state.roomTypeId !== roomTypeId
          || state.expiresAt <= Date.now() || !Number.isInteger(state.reservationId) || state.reservationId <= 0) {
        this.clear();
        return null;
      }
      return state;
    } catch {
      this.clear();
      return null;
    }
  }

  save(state: Omit<BookingCheckoutRecoveryState, 'userKey' | 'expiresAt'>): void {
    const userKey = this.currentUserKey();
    if (!userKey) return;
    localStorage.setItem(this.storageKey, JSON.stringify({
      ...state,
      userKey,
      expiresAt: Date.now() + this.ttlMs,
    } satisfies BookingCheckoutRecoveryState));
  }

  clear(): void {
    localStorage.removeItem(this.storageKey);
  }

  private currentUserKey(): string | null {
    try {
      const user = JSON.parse(localStorage.getItem('user') || 'null') as { id?: number; username?: string } | null;
      if (Number.isInteger(user?.id) && Number(user?.id) > 0) return `id:${user!.id}`;
      if (user?.username?.trim()) return `username:${user.username.trim().toLowerCase()}`;
      return null;
    } catch {
      return null;
    }
  }
}
