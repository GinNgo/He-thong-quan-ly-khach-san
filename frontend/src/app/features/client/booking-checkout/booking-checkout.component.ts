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
  
  bookingData: ReservationRequest = {
    roomTypeId: 0,
    checkInDate: new Date().toISOString().split('T')[0], // Default today
    checkOutDate: new Date(new Date().getTime() + 86400000).toISOString().split('T')[0], // Default tomorrow
    guests: 2,
    firstName: '',
    lastName: '',
    phone: '',
    paymentMethod: 'PAY_AT_HOTEL'
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
        if (err?.error?.message) {
          this.errorMessage = err.error.message;
          return;
        }
        this.errorMessage = 'Có lỗi xảy ra khi đặt phòng. Vui lòng kiểm tra thông tin và thử lại.';
      }
    });
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
