import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { InvoiceService, Invoice } from '../../../core/services/invoice.service';
import { CardModule } from 'primeng/card';

@Component({
  selector: 'app-invoice-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule, DialogModule, CardModule],
  templateUrl: './invoice-management.html'
})
export class InvoiceManagement implements OnInit {
  reservations: Reservation[] = [];
  displayInvoiceDialog = false;
  currentInvoice?: Invoice;
  currentReservation?: Reservation;

  constructor(
    private reservationService: ReservationService,
    private invoiceService: InvoiceService
  ) {}

  ngOnInit() {
    this.loadReservations();
  }

  loadReservations() {
    this.reservationService.getAllReservations().subscribe(data => {
      // Only show completed/checked-out reservations for billing
      this.reservations = data.filter(r => r.status === 'CHECKED_OUT' || r.status === 'CHECKED_IN');
    });
  }

  showInvoice(res: Reservation) {
    this.currentReservation = res;
    if (res.id) {
      this.invoiceService.getInvoiceByReservation(res.id).subscribe({
        next: (invoice) => {
          this.currentInvoice = invoice;
          this.displayInvoiceDialog = true;
        },
        error: () => {
          // If invoice doesn't exist, prompt to generate
          if (confirm('Chưa có hóa đơn cho Booking này. Tạo hóa đơn mới?')) {
            this.invoiceService.generateInvoice(res.id!).subscribe(newInvoice => {
              this.currentInvoice = newInvoice;
              this.displayInvoiceDialog = true;
            });
          }
        }
      });
    }
  }

  printInvoice() {
    window.print();
  }
}
