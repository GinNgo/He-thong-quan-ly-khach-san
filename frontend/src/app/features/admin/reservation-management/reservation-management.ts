import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-reservation-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule, CardModule],
  templateUrl: './reservation-management.html'
})
export class ReservationManagement implements OnInit {
  reservations: Reservation[] = [];

  constructor(private reservationService: ReservationService, private router: Router) {}

  ngOnInit() {
    this.loadReservations();
  }

  loadReservations() {
    this.reservationService.getAllReservations().subscribe(data => {
      this.reservations = data;
    });
  }

  getSeverity(status: string | undefined): "success" | "secondary" | "info" | "warn" | "danger" | "contrast" | undefined {
    if (!status) return 'info';
    switch (status) {
      case 'CONFIRMED': return 'success';
      case 'PENDING': return 'warn';
      case 'CHECKED_IN': return 'info';
      case 'CHECKED_OUT': return 'secondary';
      case 'CANCELLED': return 'danger';
      default: return 'info';
    }
  }

  updateStatus(id: number | undefined, status: string) {
    if (id) {
      this.reservationService.updateReservationStatus(id, status).subscribe(() => {
        this.loadReservations();
      });
    }
  }

  createNew() {
    this.router.navigate(['/admin/reservations/create']);
  }
}
