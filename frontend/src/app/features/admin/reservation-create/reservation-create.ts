import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ManagementApiService, ManagedProperty } from '../../../core/services/management-api.service';
import { ReservationService, StaffBookingContext, StaffBookingQuote, StaffBookingQuoteRequest } from '../../../core/services/reservation.service';

@Component({ selector: 'app-reservation-create', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './reservation-create.html', styleUrls: ['./reservation-create.css'] })
export class ReservationCreate implements OnInit {
  private readonly reservations = inject(ReservationService);
  private readonly management = inject(ManagementApiService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);

  properties: ManagedProperty[] = [];
  context: StaffBookingContext | null = null;
  customerQuery = '';
  loading = true;
  contextLoading = false;
  quoting = false;
  creating = false;
  error = '';
  quote: StaffBookingQuote | null = null;
  form: Partial<StaffBookingQuoteRequest> = { quantity: 1, adults: 1, children: 0, paymentMethod: 'CASH' };
  private quoteKey = '';
  private createKey = '';

  ngOnInit(): void {
    this.management.context().pipe(finalize(() => this.loading = false)).subscribe({
      next: value => {
        this.properties = value.properties.filter(property => property.operational !== false);
        const hotelId = value.activePropertyId ?? this.properties[0]?.id;
        if (hotelId) { this.form.hotelId = hotelId; this.loadContext(); }
      },
      error: () => this.error = 'Không thể tải cơ sở được phân quyền.',
    });
  }

  loadContext(): void {
    if (!this.form.hotelId) return;
    this.contextLoading = true; this.error = ''; this.invalidateQuote();
    this.reservations.getStaffBookingContext(this.form.hotelId, this.customerQuery)
      .pipe(finalize(() => this.contextLoading = false)).subscribe({
        next: context => this.context = context,
        error: () => { this.context = null; this.error = 'Không thể tải khách hàng hoặc loại phòng cho cơ sở này.'; },
      });
  }

  searchCustomers(): void { if (this.customerQuery.trim().length >= 2) this.loadContext(); }
  invalidateQuote(): void { this.quote = null; this.quoteKey = ''; this.createKey = ''; }

  requestQuote(): void {
    if (!this.validRequest || this.quoting) return;
    this.quoting = true; this.error = '';
    this.quoteKey ||= this.key('quote');
    this.reservations.createStaffBookingQuote(this.form as StaffBookingQuoteRequest, this.quoteKey)
      .pipe(finalize(() => this.quoting = false)).subscribe({
        next: quote => { this.quote = quote; this.createKey = this.key('create'); },
        error: error => { this.quote = null; this.quoteKey = ''; this.error = error?.error?.message || 'Không thể tạo báo giá xác thực.'; },
      });
  }

  createBooking(): void {
    if (!this.quote || this.creating) return;
    this.creating = true; this.error = '';
    this.reservations.createStaffBooking(this.quote.quoteId, this.createKey)
      .pipe(finalize(() => this.creating = false)).subscribe({
        next: reservation => {
          this.messages.add({ severity: 'success', summary: 'Đã tạo đặt phòng', detail: `RES-${reservation.id} đang chờ thanh toán và chưa gán phòng vật lý.` });
          this.router.navigate(['/admin/reservations']);
        },
        error: error => {
          this.error = error?.status === 409 ? 'Giá, chính sách hoặc tồn phòng đã thay đổi. Hãy lấy báo giá mới.' : (error?.error?.message || 'Không thể tạo đặt phòng.');
          if (error?.status === 409) this.invalidateQuote();
        },
      });
  }

  cancel(): void { this.router.navigate(['/admin/reservations']); }
  get validRequest(): boolean { const f = this.form; return Boolean(f.hotelId && f.customerId && f.roomTypeId && f.checkInDate && f.checkOutDate && (f.quantity || 0) > 0 && (f.adults || 0) > 0 && (f.children || 0) >= 0 && f.paymentMethod); }
  private key(operation: string): string { return `staff-booking-${operation}-${crypto.randomUUID()}`; }
}
