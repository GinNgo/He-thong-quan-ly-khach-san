import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ClientApiService, ReservationRequest } from '../../../core/services/client-api.service';

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
  reservationDetails: any = null;

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const id = params.get('roomTypeId');
      if (id) {
        this.roomTypeId = Number(id);
        this.bookingData.roomTypeId = this.roomTypeId;
      }
    });
  }

  submitBooking() {
    this.isSubmitting = true;
    this.clientApi.bookRoom(this.bookingData).subscribe({
      next: (res) => {
        this.isSubmitting = false;
        this.bookingSuccess = true;
        this.reservationDetails = res;
      },
      error: (err) => {
        console.error('Error submitting booking', err);
        this.isSubmitting = false;
        alert('Có lỗi xảy ra khi đặt phòng. Vui lòng thử lại.');
      }
    });
  }

  goHome() {
    this.router.navigate(['/']);
  }
}
