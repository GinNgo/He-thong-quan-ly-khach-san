import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ClientApiService, ReservationRequest } from '../../../core/services/client-api.service';
import { PaymentService } from '../../../core/services/payment.service';

@Component({
  selector: 'app-booking-checkout',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './booking-checkout.component.html',
  styleUrls: ['./booking-checkout.component.css']
})
export class BookingCheckoutComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clientApi = inject(ClientApiService);
  private paymentService = inject(PaymentService);

  roomTypeId: number = 0;
  roomTypeName = '';
  nightlyPrice = 0;
  serverEstimate = 0;
  hotelId = 0;
  
  bookingData: ReservationRequest = {
    roomTypeId: 0,
    checkInDate: new Date().toISOString().split('T')[0], // Default today
    checkOutDate: new Date(new Date().getTime() + 86400000).toISOString().split('T')[0], // Default tomorrow
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
  reservationDetails: any = null;

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('roomTypeId');
      if (id) {
        this.roomTypeId = Number(id);
        this.bookingData.roomTypeId = this.roomTypeId;
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
    });

    this.prefillUserInfo();
  }

  submitBooking() {
    this.errorMessage = '';
    if (this.bookingData.checkOutDate <= this.bookingData.checkInDate) {
      this.errorMessage = 'Ngày trả phòng phải sau ngày nhận phòng.';
      return;
    }
    if (this.bookingData.guests < 1) {
      this.errorMessage = 'Số khách phải lớn hơn 0.';
      return;
    }
    if (!this.bookingData.quantity || this.bookingData.quantity < 1) {
      this.errorMessage = 'Số lượng phòng phải lớn hơn 0.';
      return;
    }

    this.isSubmitting = true;
    this.clientApi.bookRoom(this.bookingData).subscribe({
      next: (res) => {
        this.reservationDetails = res;
        
        if (this.bookingData.paymentMethod !== 'PAY_AT_HOTEL') {
          // Redirect to Mock Payment Simulator
          this.paymentService.createPaymentUrl(res.id, this.bookingData.paymentMethod, res.totalAmount).subscribe({
            next: (paymentResponse) => {
              window.location.href = paymentResponse.url;
            },
            error: (err) => {
              console.error('Lỗi khi tạo URL thanh toán', err);
              this.isSubmitting = false;
              this.errorMessage = 'Không thể kết nối đến cổng thanh toán.';
            }
          });
        } else {
          // Pay at hotel: finish immediately
          this.isSubmitting = false;
          this.bookingSuccess = true;
        }
      },
      error: (err) => {
        console.error('Error submitting booking', err);
        this.isSubmitting = false;
        if (err?.status === 409) {
          this.errorMessage = 'Số phòng bạn chọn vừa hết. Vui lòng quay lại chọn phòng.';
          return;
        }
        if (err?.error?.message) {
          this.errorMessage = err.error.message;
          return;
        }
        this.errorMessage = 'Có lỗi xảy ra khi đặt phòng. Vui lòng kiểm tra thông tin và thử lại.';
      }
    });
  }

  get nights(): number {
    return Math.max(1, Math.round((new Date(this.bookingData.checkOutDate).getTime() - new Date(this.bookingData.checkInDate).getTime()) / 86400000));
  }

  get estimatedTotal(): number {
    return this.serverEstimate || this.nightlyPrice * this.nights * (this.bookingData.quantity || 1);
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
}
