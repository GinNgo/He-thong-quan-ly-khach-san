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
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { FormsModule } from '@angular/forms';
import { HotelServiceService, HotelServiceDTO } from '../../../core/services/hotel-service.service';

@Component({
  selector: 'app-reservation-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule, CardModule, ToastModule, DialogModule, SelectModule, InputNumberModule, FormsModule],
  providers: [MessageService],
  templateUrl: './reservation-management.html'
})
export class ReservationManagement implements OnInit {
  reservations: Reservation[] = [];
  services: HotelServiceDTO[] = [];
  
  showAddServiceDialog = false;
  selectedReservationId: number | null = null;
  newServiceItem = { serviceId: 0, quantity: 1 };

  constructor(
    private reservationService: ReservationService, 
    private paymentService: PaymentService,
    private invoiceService: InvoiceService,
    private hotelServiceService: HotelServiceService,
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
    this.hotelServiceService.getServices().subscribe(data => {
      this.services = data;
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

  viewTimeline() {
    this.router.navigate(['/admin/reservations/timeline']);
  }

  openAddServiceDialog(res: Reservation) {
    if (!res.id) return;
    this.selectedReservationId = res.id;
    this.newServiceItem = { serviceId: 0, quantity: 1 };
    this.showAddServiceDialog = true;
  }

  submitAddService() {
    if (!this.selectedReservationId || !this.newServiceItem.serviceId || this.newServiceItem.quantity < 1) {
      this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Vui lòng chọn dịch vụ và số lượng hợp lệ' });
      return;
    }
    
    this.reservationService.addExtraService(this.selectedReservationId, this.newServiceItem.serviceId, this.newServiceItem.quantity)
      .subscribe({
        next: (res) => {
          this.messageService.add({ severity: 'success', summary: 'Thành công', detail: 'Đã thêm dịch vụ' });
          this.showAddServiceDialog = false;
          this.loadReservations();
        },
        error: (err) => {
          this.messageService.add({ severity: 'error', summary: 'Lỗi', detail: 'Thêm dịch vụ thất bại' });
        }
      });
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
