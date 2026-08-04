import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth';
import { FinancialAuditEvent, FinancialAuditFilters, FinancialAuditPolicy, FinancialAuditService } from '../../../core/services/financial-audit.service';

@Component({ selector: 'app-financial-audit', standalone: true, imports: [CommonModule, FormsModule], templateUrl: './financial-audit.component.html', styleUrl: './financial-audit.component.css', changeDetection: ChangeDetectionStrategy.OnPush })
export class FinancialAuditComponent implements OnInit {
  private service = inject(FinancialAuditService); private auth = inject(AuthService); private cdr = inject(ChangeDetectorRef);
  events: FinancialAuditEvent[] = []; policy?: FinancialAuditPolicy; filters: FinancialAuditFilters = {}; loading = true; error = ''; page = 0; totalPages = 0; totalElements = 0; expanded?: number; exporting = false;
  get systemAdmin(): boolean { return this.auth.getRoles().some(role => role === 'SUPER_ADMIN' || role === 'ROLE_SUPER_ADMIN'); }
  ngOnInit(): void { this.service.policy().subscribe({ next: policy => { this.policy = policy; this.cdr.markForCheck(); } }); this.load(); }
  load(page = 0): void { this.loading = true; this.error = ''; this.page = page; this.service.search({ ...this.filters, page, size: 25 }).subscribe({ next: result => { this.events = result.content; this.totalPages = result.totalPages; this.totalElements = result.totalElements; this.loading = false; this.cdr.markForCheck(); }, error: () => { this.error = 'Khong the tai nhat ky tai chinh.'; this.loading = false; this.cdr.markForCheck(); } }); }
  apply(): void { this.load(0); } toggle(id: number): void { this.expanded = this.expanded === id ? undefined : id; }
  exportCsv(): void { this.exporting = true; this.service.export(this.filters).subscribe({ next: blob => { const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = 'financial-audit.csv'; anchor.click(); URL.revokeObjectURL(url); this.exporting = false; this.cdr.markForCheck(); }, error: () => { this.exporting = false; this.error = 'Khong the xuat nhat ky tai chinh.'; this.cdr.markForCheck(); } }); }
  metadata(value: string): string { try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; } }
}
