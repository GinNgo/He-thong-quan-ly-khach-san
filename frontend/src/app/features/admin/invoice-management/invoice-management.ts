import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { DialogModule } from 'primeng/dialog';
import { ReservationService, Reservation } from '../../../core/services/reservation.service';
import { InvoiceService, Invoice } from '../../../core/services/invoice.service';
import { CardModule } from 'primeng/card';
import { ConfirmationService } from 'primeng/api';
import { FeedbackStateComponent } from '../../../shared/components/feedback-state/feedback-state.component';

@Component({
  selector: 'app-invoice-management',
  standalone: true,
  imports: [CommonModule, TableModule, ButtonModule, TagModule, DialogModule, CardModule, FeedbackStateComponent],
  templateUrl: './invoice-management.html'
})
export class InvoiceManagement implements OnInit {
  reservations: Reservation[] = [];
  displayInvoiceDialog = false;
  currentInvoice?: Invoice;
  currentReservation?: Reservation;
  loading = false;
  loadError = '';
  invoiceError = '';
  invoiceLoadingId: number | null = null;

  constructor(
    private reservationService: ReservationService,
    private invoiceService: InvoiceService,
    private confirmationService: ConfirmationService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.loadReservations();
  }

  loadReservations() {
    this.loading = true;
    this.loadError = '';
    this.reservationService.getAllReservations().subscribe({
      next: data => {
        this.reservations = data.filter(r => r.status === 'CHECKED_OUT' || r.status === 'CHECKED_IN');
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: error => {
        this.loading = false;
        this.loadError = error?.error?.message || 'Unable to load invoice-ready reservations.';
        this.cdr.markForCheck();
      }
    });
  }

  showInvoice(res: Reservation) {
    this.currentReservation = res;
    if (res.id) {
      this.invoiceError = '';
      this.invoiceLoadingId = res.id;
      this.invoiceService.getInvoiceByReservation(res.id).subscribe({
        next: (invoice) => {
          this.invoiceLoadingId = null;
          this.currentInvoice = invoice;
          this.displayInvoiceDialog = true;
          this.cdr.markForCheck();
        },
        error: () => {
          this.invoiceLoadingId = null;
          this.confirmationService.confirm({
            message: 'Chưa có hóa đơn cho Booking này. Tạo hóa đơn mới?',
            header: 'Xác nhận',
            icon: 'pi pi-exclamation-triangle',
            accept: () => {
              this.invoiceLoadingId = res.id!;
              this.invoiceService.generateInvoice(res.id!).subscribe({
                next: newInvoice => {
                  this.invoiceLoadingId = null;
                  this.currentInvoice = newInvoice;
                  this.displayInvoiceDialog = true;
                  this.cdr.markForCheck();
                },
                error: error => {
                  this.invoiceLoadingId = null;
                  this.invoiceError = error?.error?.message || 'Unable to generate the invoice. Retry from the reservation list.';
                  this.cdr.markForCheck();
                }
              });
            }
          });
        }
      });
    }
  }

  printInvoice() {
    window.print();
  }
}
