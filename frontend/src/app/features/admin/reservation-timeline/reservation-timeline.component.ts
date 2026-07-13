import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { RoomService } from '../../../core/services/room.service';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-reservation-timeline',
  standalone: true,
  imports: [CommonModule, ButtonModule, DatePickerModule, FormsModule, CardModule],
  templateUrl: './reservation-timeline.component.html'
})
export class ReservationTimelineComponent implements OnInit {
  dates: Date[] = [];
  rooms: any[] = [];
  reservations: Reservation[] = [];
  startDate: Date = new Date();
  
  constructor(
    private reservationService: ReservationService,
    private roomService: RoomService
  ) {}

  ngOnInit() {
    this.generateDates();
    this.loadData();
  }

  generateDates() {
    this.dates = [];
    const current = new Date(this.startDate);
    current.setHours(0,0,0,0);
    for (let i = 0; i < 14; i++) {
      const d = new Date(current);
      d.setDate(current.getDate() + i);
      this.dates.push(d);
    }
  }

  onStartDateChange() {
    this.generateDates();
  }

  loadData() {
    this.roomService.getAllRooms().subscribe(rooms => {
      this.rooms = rooms;
    });

    this.reservationService.getAllReservations().subscribe(res => {
      this.reservations = res;
    });
  }

  getReservationForCell(roomId: number, date: Date): Reservation | null {
    const dStr = date.toISOString().split('T')[0];
    return this.reservations.find(r => {
      const checkIn = new Date(r.checkInDate!).toISOString().split('T')[0];
      const checkOut = new Date(r.checkOutDate!).toISOString().split('T')[0];
      
      // If room matches
      const isRoomMatch = r.details?.some((d: any) => d.roomId === roomId) || false;
      
      // If date is within checkin and checkout (exclusive checkout if hotel logic, but let's say inclusive for visual)
      return isRoomMatch && dStr >= checkIn && dStr < checkOut;
    }) || null;
  }

  getSeverityClass(status: string | undefined): string {
    if (!status) return 'bg-gray-300 text-gray-800';
    switch (status) {
      case 'CONFIRMED': return 'bg-green-500 text-white';
      case 'PENDING': return 'bg-yellow-500 text-white';
      case 'CHECKED_IN': return 'bg-blue-500 text-white';
      case 'CHECKED_OUT': return 'bg-gray-500 text-white';
      case 'CANCELLED': return 'bg-red-500 text-white';
      default: return 'bg-gray-300 text-gray-800';
    }
  }

  isCheckInDate(res: Reservation, date: Date): boolean {
    const checkIn = new Date(res.checkInDate!).toISOString().split('T')[0];
    const dStr = date.toISOString().split('T')[0];
    return checkIn === dStr;
  }

  getDuration(res: Reservation): number {
    const start = new Date(res.checkInDate!).getTime();
    const end = new Date(res.checkOutDate!).getTime();
    const diff = Math.max(1, Math.round((end - start) / (1000 * 60 * 60 * 24)));
    return diff;
  }
}
