import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { InputNumberModule } from 'primeng/inputnumber';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { RoomService, Room } from '../../../core/services/room.service';
import { UserService, User } from '../../../core/services/user.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-reservation-create',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePickerModule, SelectModule, ButtonModule, InputTextModule, TextareaModule, InputNumberModule],
  templateUrl: './reservation-create.html'
})
export class ReservationCreate implements OnInit {
  reservation: Partial<Reservation> = {
    paymentMethod: 'CASH',
    guests: 1,
    details: []
  };

  users: User[] = [];
  rooms: Room[] = [];
  selectedRoomId?: number;

  paymentMethods = [
    { label: 'Tiền mặt', value: 'CASH' },
    { label: 'Thẻ tín dụng', value: 'CREDIT_CARD' },
    { label: 'Chuyển khoản', value: 'BANK_TRANSFER' }
  ];

  constructor(
    private reservationService: ReservationService,
    private roomService: RoomService,
    private userService: UserService,
    private router: Router
  ) {}

  ngOnInit() {
    this.userService.getAllUsers().subscribe(data => this.users = data);
    this.roomService.getAllRooms().subscribe(data => {
      this.rooms = data.filter(r => r.status === 'AVAILABLE');
    });
  }

  save() {
    if (this.reservation.userId && this.reservation.checkInDate && this.reservation.checkOutDate && this.selectedRoomId) {
      this.reservation.details = [{ roomId: this.selectedRoomId }];
      
      // Formatting dates
      const checkIn = new Date(this.reservation.checkInDate);
      const checkOut = new Date(this.reservation.checkOutDate);
      this.reservation.checkInDate = checkIn.toISOString().split('T')[0];
      this.reservation.checkOutDate = checkOut.toISOString().split('T')[0];

      this.reservationService.createReservation(this.reservation as Reservation).subscribe(() => {
        this.router.navigate(['/admin/reservations']);
      });
    } else {
      alert("Vui lòng điền đầy đủ thông tin bắt buộc.");
    }
  }

  cancel() {
    this.router.navigate(['/admin/reservations']);
  }
}
