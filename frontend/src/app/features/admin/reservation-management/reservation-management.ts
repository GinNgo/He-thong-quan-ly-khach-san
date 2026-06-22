import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { PaymentService } from '../../../core/services/payment.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { Router } from '@angular/router';
import { CardModule } from 'primeng/card';
import { MessageService } from 'primeng/api';
import { ToastModule } from 'primeng/toast';

@Component({
  selector: 'app-reservation-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule, CardModule, ToastModule],
  providers: [MessageService],
  templateUrl: './reservation-management.html'
})
export class ReservationManagement implements OnInit {
  reservations: Reservation[] = [];

  constructor(
    private reservationService: ReservationService, 
    private paymentService: PaymentService,
    private invoiceService: InvoiceService,
    private messageService: MessageService,
    private router: Router
  ) {}

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

  processPayment(res: Reservation) {
    if (!res.id || !res.totalAmount) return;
    this.paymentService.processPayment({
      reservationId: res.id,
      amount: res.totalAmount,
      paymentMethod: 'CASH'
    }).subscribe({
      next: (data) => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã thanh toán Booking RES-' + res.id });
        this.loadReservations();
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Thanh toán thất bại' });
      }
    });
  }

  generateInvoice(resId: number | undefined) {
    if (!resId) return;
    this.invoiceService.generateInvoice(resId).subscribe({
      next: (data) => {
        this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã xuất hóa đơn ' + data.invoiceCode });
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Chưa có thanh toán để xuất hóa đơn' });
      }
    });
  }
}
