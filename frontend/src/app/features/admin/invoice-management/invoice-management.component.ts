import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';
import { InvoiceService } from '@app/core/services/invoice.service';

@Component({
  standalone: true,
  imports: [SharedModule],
  selector: 'app-invoice-management',
  templateUrl: './invoice-management.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./invoice-management.component.css'],
})
export class InvoiceManagementComponent implements OnInit {
  invoices: any[] = [];
  displayModal: boolean = false;
  selectedInvoice: any = null;

  constructor(private invoiceService: InvoiceService) {}

  ngOnInit(): void {
    this.invoiceService.getAllInvoices().subscribe({
      next: (data) => {
        // Map backend data to frontend format if necessary
        this.invoices = data.map(inv => ({
          invoiceCode: inv.invoiceCode,
          customerName: inv.reservation?.user?.fullName || 'N/A',
          issueDate: inv.issueDate,
          totalAmount: new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(inv.totalAmount || 0),
          status: inv.status === 'PAID' || inv.status === 'Paid' ? 'Paid' : 'Pending',
          raw: inv
        }));
      },
      error: (err) => {
        console.error('Lỗi khi tải danh sách hóa đơn:', err);
      }
    });
  }

  showPreview(invoice: any) {
    this.selectedInvoice = invoice;
    this.displayModal = true;
  }

  downloadPDF() {
    const data = document.getElementById('invoice-pdf-content');
    if (data) {
      html2canvas(data, { scale: 2 }).then((canvas) => {
        const imgWidth = 208;
        const pageHeight = 295;
        const imgHeight = (canvas.height * imgWidth) / canvas.width;

        const contentDataURL = canvas.toDataURL('image/png');
        const pdf = new jsPDF('p', 'mm', 'a4');
        pdf.addImage(contentDataURL, 'PNG', 0, 0, imgWidth, imgHeight);
        pdf.save(`${this.selectedInvoice.invoiceCode}.pdf`);
      });
    }
  }
}
