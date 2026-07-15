import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { environment } from '../../../../environments/environment';

interface CustomerInvoice { id: number; invoiceCode: string; reservationId: number; issueDate: string; totalAmount: number; status: string; }

@Component({
  selector: 'app-my-invoices', standalone: true, imports: [CommonModule, RouterModule],
  template: `
    <main class="invoice-page"><header><div><h1>Hóa đơn của tôi</h1><p>Các hóa đơn phát sinh từ đặt phòng của tài khoản này.</p></div><a routerLink="/profile" [queryParams]="{tab:'bookings'}">Xem chuyến đi</a></header>
      <div *ngIf="loading" class="state"><i class="pi pi-spin pi-spinner"></i><span>Đang tải hóa đơn</span></div>
      <div *ngIf="error" class="state error"><i class="pi pi-exclamation-circle"></i><span>{{ error }}</span><button (click)="load()">Thử lại</button></div>
      <div *ngIf="!loading && !error && !invoices.length" class="state"><i class="pi pi-file"></i><strong>Chưa có hóa đơn</strong><span>Hóa đơn sẽ xuất hiện sau khi giao dịch đủ điều kiện.</span></div>
      <section *ngIf="!loading && !error && invoices.length" class="invoice-list">
        <article *ngFor="let invoice of invoices; trackBy: trackInvoice"><div><small>Mã hóa đơn</small><strong>{{ invoice.invoiceCode }}</strong></div><div><small>Ngày phát hành</small><span>{{ invoice.issueDate | date:'dd/MM/yyyy' }}</span></div><div><small>Tổng tiền</small><strong>{{ invoice.totalAmount | currency:'VND':'symbol':'1.0-0' }}</strong></div><span class="status">{{ statusLabel(invoice.status) }}</span></article>
      </section>
    </main>`,
  styles: [`
    .invoice-page{max-width:1100px;margin:auto;padding:36px 20px 70px}.invoice-page>header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:24px}h1{margin:0;color:#0f172a;font-size:30px}p{margin:7px 0 0;color:#64748b}header a,button{border:0;background:#1d4ed8;color:#fff;padding:12px 16px;text-decoration:none;font-weight:700;cursor:pointer}.invoice-list{display:grid;gap:12px}.invoice-list article{display:grid;grid-template-columns:1.3fr 1fr 1fr auto;align-items:center;gap:20px;background:#fff;border:1px solid #e2e8f0;padding:20px}.invoice-list article div{display:flex;flex-direction:column;gap:5px}.invoice-list small{color:#64748b}.status{background:#ecfdf5;color:#047857;padding:7px 10px;font-weight:700;font-size:12px}.state{min-height:260px;background:#fff;border:1px solid #e2e8f0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;color:#64748b}.state i{font-size:30px}.state.error{color:#b91c1c}@media(max-width:700px){.invoice-page>header{align-items:flex-start;flex-direction:column}.invoice-list article{grid-template-columns:1fr 1fr}.status{justify-self:start}}
  `]
})
export class MyInvoicesComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly changeDetector = inject(ChangeDetectorRef);
  invoices: CustomerInvoice[] = []; loading = true; error = '';
  ngOnInit(): void { this.load(); }
  load(): void { this.loading = true; this.error = ''; this.http.get<CustomerInvoice[]>(`${environment.apiUrl}/invoices/my`).subscribe({ next: data => { this.invoices = data; this.loading = false; this.changeDetector.detectChanges(); }, error: () => { this.error = 'Không thể tải hóa đơn.'; this.loading = false; this.changeDetector.detectChanges(); } }); }
  trackInvoice(_: number, invoice: CustomerInvoice): number { return invoice.id; }
  statusLabel(status: string): string { return ({PAID:'Đã thanh toán',PENDING:'Chờ thanh toán',CANCELLED:'Đã hủy'} as Record<string,string>)[status] || status; }
}
