import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import jsPDF from 'jspdf';
import html2canvas from 'html2canvas';

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

  constructor() {}

  ngOnInit(): void {
    // Mock data based on the screen.png template
    this.invoices = [
      {
        invoiceCode: 'INV-2024-001',
        customerName: 'John Smith',
        issueDate: '2024-06-15',
        totalAmount: '$450.00',
        status: 'Paid',
      },
      {
        invoiceCode: 'INV-2024-002',
        customerName: 'Emily Davis',
        issueDate: '2024-06-16',
        totalAmount: '$1,200.50',
        status: 'Pending',
      },
      {
        invoiceCode: 'INV-2024-003',
        customerName: 'Michael Brown',
        issueDate: '2024-06-17',
        totalAmount: '$890.75',
        status: 'Paid',
      },
    ];
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
