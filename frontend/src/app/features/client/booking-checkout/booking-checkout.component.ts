import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { PublicI18nService } from '../../../core/i18n/public-i18n.service';
import { ClientApiService, PromotionQuote, PublicPlacementDisclosure, ReservationRequest } from '../../../core/services/client-api.service';
import { PropertyPaymentMethodCode } from '../../../core/services/property-payment-configuration.service';
import {
  PropertyPaymentAttempt,
  PropertyPaymentService,
} from '../../../core/services/property-payment.service';
import { PropertyPaymentPanelComponent } from './property-payment-panel.component';
import { AsyncActionCoordinatorService } from '../../../core/services/async-action-coordinator.service';
import { PaymentService } from '../../../core/services/payment.service';

@Component({
  selector: 'app-booking-checkout',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, PropertyPaymentPanelComponent],
  templateUrl: './booking-checkout.component.html',
  styleUrls: ['./booking-checkout.component.css']
})
export class BookingCheckoutComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clientApi = inject(ClientApiService);
  private propertyPaymentService = inject(PropertyPaymentService);
  private paymentService = inject(PaymentService);
  private changeDetector = inject(ChangeDetectorRef);
  private actionCoordinator = inject(AsyncActionCoordinatorService);
  readonly i18n = inject(PublicI18nService);

  roomTypeId: number = 0;
  roomTypeName = '';
  nightlyPrice = 0;
  serverEstimate = 0;
  hotelId = 0;
  
  bookingData: ReservationRequest = {
    roomTypeId: 0,
    checkInDate: '',
    checkOutDate: '',
    guests: 2,
    firstName: '',
    lastName: '',
    phone: '',
    paymentMethod: 'PAY_AT_HOTEL'
    ,quantity: 1
    ,adults: 2
    ,children: 0
    ,specialRequests: ''
  };

  isSubmitting = false;
  bookingSuccess = false;
  errorMessage = '';
  contextError = '';
  reservationDetails: any = null;
  paymentAttempt: PropertyPaymentAttempt | null = null;
  quote: PromotionQuote | null = null;
  quoteLoading = false;
  quoteError = '';
  sponsoredPlacement: PublicPlacementDisclosure | null = null;
  private quoteRequestIdentity = '';
  private paymentIdempotencyKey = '';
  private paymentRequestIdentity = '';
  private reservedPaymentMethod = '';
  private bookingIdempotencyKey = '';

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('roomTypeId');
      if (id) {
        this.roomTypeId = Number(id);
        this.bookingData.roomTypeId = this.roomTypeId;
        this.validateBookingContext();
        this.loadQuote();
      }
    });

    this.route.queryParams.subscribe((params) => {
      if (params['checkIn']) this.bookingData.checkInDate = params['checkIn'];
      if (params['checkOut']) this.bookingData.checkOutDate = params['checkOut'];
      if (params['guests']) this.bookingData.guests = Number(params['guests']) || this.bookingData.guests;
      this.bookingData.adults = Number(params['adultCount']) || this.bookingData.guests;
      this.bookingData.children = Number(params['childCount']) || 0;
      this.bookingData.quantity = Math.max(1, Number(params['quantity']) || Number(params['roomCount']) || 1);
      this.bookingData.guests = (this.bookingData.adults || 0) + (this.bookingData.children || 0);
      this.roomTypeName = params['roomTypeName'] || '';
      this.nightlyPrice = Number(params['nightlyPrice']) || 0;
      this.serverEstimate = Number(params['estimatedTotal']) || 0;
      this.hotelId = Number(params['hotelId']) || 0;
      this.loadPlacementDisclosure();
      this.bookingData.couponCode = params['couponCode'] || undefined;
      this.validateBookingContext();
      this.loadQuote();
    });

    this.prefillUserInfo();
  }

  submitBooking(): void {
    if (this.isSubmitting || !this.bookingContextValid) return;
    this.errorMessage = '';
    if (this.quoteLoading || !this.quote) {
      this.errorMessage = this.quoteError || this.i18n.text('PUBLIC.BOOKING.QUOTE_REQUIRED');
      if (!this.quoteLoading) this.loadQuote();
      return;
    }
    if (this.reservationDetails?.id && !this.paymentAttempt) {
      this.isSubmitting = true;
      this.createPaymentAttempt(this.reservationDetails.id);
      return;
    }
    if (this.bookingData.checkOutDate <= this.bookingData.checkInDate) {
      this.errorMessage = this.i18n.text('PUBLIC.BOOKING.ERROR_CHECKOUT_AFTER_CHECKIN');
      return;
    }
    if (this.bookingData.guests < 1) {
      this.errorMessage = this.i18n.text('PUBLIC.BOOKING.ERROR_GUEST_COUNT');
      return;
    }
    if (!this.bookingData.quantity || this.bookingData.quantity < 1) {
      this.errorMessage = this.i18n.text('PUBLIC.BOOKING.ERROR_ROOM_COUNT');
      return;
    }

    this.isSubmitting = true;
    const bookingKey = this.getBookingIdempotencyKey();
    this.actionCoordinator.run('booking:create', () => this.clientApi.bookRoom(this.bookingData, bookingKey)).subscribe({
      next: (res) => {
        this.reservationDetails = res;
        this.reservedPaymentMethod = this.bookingData.paymentMethod;
        
        if (this.bookingData.paymentMethod !== 'PAY_AT_HOTEL') {
          this.createPaymentAttempt(res.id);
        } else {
          // Pay at hotel: finish immediately
          this.isSubmitting = false;
          this.bookingSuccess = true;
          this.changeDetector.markForCheck();
        }
      },
      error: (err) => {
        console.error('Error submitting booking', err);
        this.isSubmitting = false;
        if (err?.error?.message) {
          this.errorMessage = err.error.message;
          this.changeDetector.markForCheck();
          return;
        }
        if (err?.status === 409) {
          this.errorMessage = this.i18n.text('PUBLIC.BOOKING.ERROR_ROOM_SOLD_OUT');
          this.changeDetector.markForCheck();
          return;
        }
        this.errorMessage = this.i18n.text('PUBLIC.BOOKING.ERROR_BOOKING_GENERIC');
        this.changeDetector.markForCheck();
      }
    });
  }

  get nights(): number {
    if (!this.bookingData.checkInDate || !this.bookingData.checkOutDate) return 0;
    return Math.max(1, Math.round((new Date(this.bookingData.checkOutDate).getTime() - new Date(this.bookingData.checkInDate).getTime()) / 86400000));
  }

  get guestSummary(): string {
    const adults = this.i18n.count('PUBLIC.GUESTS.ADULT_COUNT', this.bookingData.adults || 0);
    const children = this.bookingData.children
      ? `, ${this.i18n.count('PUBLIC.GUESTS.CHILD_COUNT', this.bookingData.children)}`
      : '';
    return `${adults}${children}`;
  }

  get estimatedTotal(): number {
    return this.quote?.finalTotal ?? 0;
  }

  get promotionNames(): string {
    return (this.quote?.appliedPromotions ?? [])
      .map(promotion => this.i18n.dateLocale() === 'en-US'
        ? (promotion.nameEn || promotion.nameVi)
        : promotion.nameVi)
      .join(', ');
  }

  get memberTierLabel(): string {
    const benefit = this.quote?.memberBenefit;
    if (!benefit?.eligible) return '';
    return this.i18n.dateLocale() === 'en-US'
      ? (benefit.tierNameEn || benefit.tierNameVi || '')
      : (benefit.tierNameVi || benefit.tierNameEn || '');
  }

  get sponsoredDisclosure(): string {
    const placement = this.sponsoredPlacement;
    return placement ? (this.i18n.dateLocale() === 'en-US' ? placement.disclosureEn : placement.disclosureVi) : '';
  }

  formatVnd(value: number): string {
    return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)} ₫`;
  }

  goHome() {
    this.router.navigate(['/']);
  }

  goToProfileBookings() {
    this.router.navigate(['/profile'], { queryParams: { tab: 'bookings' } });
  }

  goToSearch(): void {
    this.router.navigate(['/search']);
  }

  onPaymentAttemptChange(attempt: PropertyPaymentAttempt): void {
    this.paymentAttempt = attempt;
  }

  retryPaymentAttempt(): void {
    const reservationId = Number(this.reservationDetails?.id);
    if (this.isSubmitting || !Number.isInteger(reservationId) || reservationId <= 0) return;

    this.errorMessage = '';
    this.isSubmitting = true;
    this.paymentRequestIdentity = '';
    this.paymentIdempotencyKey = '';
    this.createPaymentAttempt(reservationId);
  }

  get bookingContextValid(): boolean {
    return !this.contextError;
  }

  private validateBookingContext(): void {
    const validRoom = Number.isInteger(this.roomTypeId) && this.roomTypeId > 0;
    const validHotel = Number.isInteger(this.hotelId) && this.hotelId > 0;
    const validDates = !!this.bookingData.checkInDate && !!this.bookingData.checkOutDate
      && this.bookingData.checkOutDate > this.bookingData.checkInDate;
    const validName = !!this.roomTypeName.trim();

    this.contextError = validRoom && validHotel && validDates && validName
      ? ''
      : this.i18n.text('PUBLIC.BOOKING.ERROR_INVALID_CONTEXT');
  }

  private loadPlacementDisclosure(): void {
    if (!Number.isInteger(this.hotelId) || this.hotelId <= 0) {
      this.sponsoredPlacement = null;
      return;
    }
    this.clientApi.getHotelById(this.hotelId).subscribe({
      next: hotel => {
        this.sponsoredPlacement = hotel.sponsoredPlacement ?? null;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.sponsoredPlacement = null;
        this.changeDetector.markForCheck();
      },
    });
  }

  private loadQuote(): void {
    this.validateBookingContext();
    if (!this.bookingContextValid) return;
    const identity = [
      this.hotelId,
      this.roomTypeId,
      this.bookingData.checkInDate,
      this.bookingData.checkOutDate,
      this.bookingData.quantity,
      this.bookingData.adults,
      this.bookingData.children,
      this.bookingData.couponCode || '',
    ].join(':');
    if (identity === this.quoteRequestIdentity && (this.quote || this.quoteLoading)) return;
    this.quoteRequestIdentity = identity;
    this.quote = null;
    this.quoteError = '';
    this.quoteLoading = true;
    this.clientApi.getPromotionQuote({
      propertyId: this.hotelId,
      roomTypeId: this.roomTypeId,
      checkInDate: this.bookingData.checkInDate,
      checkOutDate: this.bookingData.checkOutDate,
      quantity: this.bookingData.quantity || 1,
      adultCount: this.bookingData.adults || 1,
      childCount: this.bookingData.children || 0,
      couponCode: this.bookingData.couponCode,
    }).subscribe({
      next: (quote) => {
        this.quote = quote;
        this.serverEstimate = quote.finalTotal;
        this.quoteLoading = false;
        this.changeDetector.markForCheck();
      },
      error: (error) => {
        this.quoteLoading = false;
        this.quoteError = error?.error?.message || this.i18n.text('PUBLIC.BOOKING.QUOTE_ERROR');
        this.changeDetector.markForCheck();
      },
    });
  }

  private prefillUserInfo() {
    const userStr = localStorage.getItem('user');
    if (!userStr) return;

    try {
      const user = JSON.parse(userStr);
      const displayName = user.fullName || user.username || '';
      const parts = displayName.trim().split(' ').filter(Boolean);
      this.bookingData.firstName = parts.length > 1 ? parts.pop() || '' : displayName;
      this.bookingData.lastName = parts.join(' ');
    } catch {
      return;
    }
  }

  private createPaymentAttempt(reservationId: number): void {
    const method = (this.reservedPaymentMethod || this.bookingData.paymentMethod) as PropertyPaymentMethodCode;
    const requestIdentity = `${reservationId}:DEPOSIT:${method}`;
    if (this.paymentRequestIdentity !== requestIdentity) {
      this.paymentRequestIdentity = requestIdentity;
      this.paymentIdempotencyKey = this.newRequestId();
    }

    if (method === 'VNPAY') {
      this.paymentService.createPaymentSession(
        reservationId,
        'VNPAY',
        this.paymentIdempotencyKey,
      ).subscribe({
        next: (session) => {
          this.isSubmitting = false;
          if (!session.url) {
            this.errorMessage = this.i18n.text('PUBLIC.BOOKING.ERROR_PAYMENT_CONNECTION');
            this.changeDetector.markForCheck();
            return;
          }
          window.location.href = session.url;
        },
        error: (err) => {
          console.error('Unable to create VNPAY payment session', err);
          this.isSubmitting = false;
          this.errorMessage = err?.error?.message
            || this.i18n.text('PUBLIC.BOOKING.ERROR_PAYMENT_CONNECTION');
          this.changeDetector.markForCheck();
        },
      });
      return;
    }

    this.propertyPaymentService.createAttempt(
      reservationId,
      { purpose: 'DEPOSIT', method },
      { idempotencyKey: this.paymentIdempotencyKey },
    ).subscribe({
      next: (attempt) => {
        this.paymentAttempt = attempt;
        this.isSubmitting = false;
        this.bookingSuccess = true;
        this.changeDetector.markForCheck();
        if (attempt.redirectUrl) {
          window.location.href = attempt.redirectUrl;
        }
      },
      error: (err) => {
        console.error('Unable to create property payment attempt', err);
        this.isSubmitting = false;
        this.errorMessage = err?.error?.message
          || this.i18n.text('PUBLIC.BOOKING.ERROR_PAYMENT_CONNECTION');
        this.changeDetector.markForCheck();
      },
    });
  }

  private newRequestId(): string {
    const cryptoApi = globalThis.crypto as Crypto | undefined;
    if (cryptoApi?.randomUUID) return cryptoApi.randomUUID();
    return `payment-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  private getBookingIdempotencyKey(): string {
    if (this.bookingIdempotencyKey) return this.bookingIdempotencyKey;
    const identity = [
      this.roomTypeId,
      this.bookingData.checkInDate,
      this.bookingData.checkOutDate,
      this.bookingData.quantity,
      this.bookingData.adults,
      this.bookingData.children,
      this.bookingData.paymentMethod,
      this.bookingData.couponCode || '',
    ].join(':');
    const storageKey = `hotel:booking:idempotency:${identity}`;
    const stored = localStorage.getItem(storageKey);
    if (stored) {
      try {
        const parsed = JSON.parse(stored) as { key?: string; expiresAt?: number };
        if (parsed.key && Number(parsed.expiresAt) > Date.now()) {
          this.bookingIdempotencyKey = parsed.key;
          return this.bookingIdempotencyKey;
        }
      } catch {
        // Replace legacy/plain entries with the bounded shared-tab format.
      }
    }

    this.bookingIdempotencyKey = this.newRequestId();
    localStorage.setItem(storageKey, JSON.stringify({
      key: this.bookingIdempotencyKey,
      expiresAt: Date.now() + 30 * 60 * 1000,
    }));
    return this.bookingIdempotencyKey;
  }
}
