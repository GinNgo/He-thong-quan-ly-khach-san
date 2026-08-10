import { HttpErrorResponse } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { Reservation, ReservationService } from '../../../core/services/reservation.service';
import { Room, RoomService } from '../../../core/services/room.service';
import { User, UserService } from '../../../core/services/user.service';

@Component({
  selector: 'app-reservation-create',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePickerModule, SelectModule, ButtonModule, InputTextModule, TextareaModule, InputNumberModule],
  templateUrl: './reservation-create.html'
})
export class ReservationCreate implements OnInit {
  reservation: Partial<Reservation> = { paymentMethod: 'CASH', guests: 1, details: [] };
  users: User[] = [];
  rooms: Room[] = [];
  selectedRoomId?: number;
  minCheckInDate = this.startOfToday();
  minCheckOutDate?: Date;
  saving = false;

  paymentMethods = [
    { label: 'Tiền mặt', value: 'CASH' },
    { label: 'Thẻ tín dụng', value: 'CREDIT_CARD' },
    { label: 'Chuyển khoản', value: 'BANK_TRANSFER' }
  ];

  private reservationService = inject(ReservationService);
  private roomService = inject(RoomService);
  private userService = inject(UserService);
  private router = inject(Router);
  private messageService = inject(MessageService);

  ngOnInit() {
    this.userService.getAllUsers().subscribe((data: User[]) => this.users = data);
    this.roomService.getAllRooms().subscribe((data: Room[]) => {
      this.rooms = data.filter((room: Room) => room.status === 'AVAILABLE');
    });
  }

  onCheckInChange(value: Date | string | null | undefined) {
    if (!value) {
      this.reservation.checkOutDate = undefined;
      this.minCheckOutDate = undefined;
      return;
    }
    const nextDay = this.addDays(new Date(value), 1);
    this.minCheckOutDate = nextDay;
    if (!this.reservation.checkOutDate || new Date(this.reservation.checkOutDate) < nextDay) {
      this.reservation.checkOutDate = nextDay as unknown as string;
    }
  }

  get formValid(): boolean {
    if (!this.reservation.userId || !this.selectedRoomId || !this.reservation.checkInDate || !this.reservation.checkOutDate) return false;
    return new Date(this.reservation.checkOutDate) > new Date(this.reservation.checkInDate);
  }

  save() {
    if (!this.formValid) {
      this.messageService.add({ severity: 'warn', summary: 'Cảnh báo', detail: 'Vui lòng điền đầy đủ thông tin bắt buộc.' });
      return;
    }

    const selectedRoom = this.rooms.find(room => room.id === this.selectedRoomId);
    if (!selectedRoom?.roomType?.id) {
      this.messageService.add({ severity: 'error', summary: 'Không thể đặt phòng', detail: 'Phòng chưa có loại phòng hợp lệ.' });
      return;
    }

    const request: Reservation = {
      ...(this.reservation as Reservation),
      roomTypeId: selectedRoom.roomType.id,
      quantity: 1,
      details: [],
      checkInDate: this.toLocalDate(new Date(this.reservation.checkInDate!)),
      checkOutDate: this.toLocalDate(new Date(this.reservation.checkOutDate!))
    };

    this.saving = true;
    this.reservationService.createReservation(request).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Tạo đặt phòng thành công.' });
        this.router.navigate(['/admin/reservations']);
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        const detail = error.error?.message || error.error?.detail || 'Không thể tạo đặt phòng. Vui lòng kiểm tra lại thông tin.';
        this.messageService.add({ severity: 'error', summary: 'Tạo đặt phòng thất bại', detail });
      }
    });
  }

  cancel() {
    this.router.navigate(['/admin/reservations']);
  }

  private startOfToday(): Date {
    const today = new Date();
    return new Date(today.getFullYear(), today.getMonth(), today.getDate());
  }

  private addDays(value: Date, days: number): Date {
    return new Date(value.getFullYear(), value.getMonth(), value.getDate() + days);
  }

  private toLocalDate(value: Date): string {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
