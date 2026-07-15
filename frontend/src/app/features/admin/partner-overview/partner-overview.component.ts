import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-partner-overview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './partner-overview.component.html',
  styleUrl: './partner-overview.component.css'
})
export class PartnerOverviewComponent implements OnInit {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  title = 'Đối tác & Cơ sở';
  endpoint = 'properties';
  rows: Record<string, unknown>[] = [];
  columns: string[] = [];
  loading = true;
  error = '';

  ngOnInit(): void {
    this.title = this.route.snapshot.data['title'] || this.title;
    this.endpoint = this.route.snapshot.data['endpoint'] || this.endpoint;
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.http.get<Record<string, unknown>[]>(`${environment.apiUrl}/admin/${this.endpoint}`).subscribe({
      next: rows => {
        this.rows = rows;
        this.columns = rows.length ? Object.keys(rows[0]) : [];
        this.loading = false;
      },
      error: error => {
        this.error = error?.error?.message || 'Không thể tải dữ liệu.';
        this.loading = false;
      }
    });
  }

  label(key: string): string {
    return key.replaceAll('_', ' ').replace(/\b\w/g, value => value.toUpperCase());
  }

  display(value: unknown): string {
    if (value === null || value === undefined || value === '') return '-';
    if (typeof value === 'boolean') return value ? 'Có' : 'Không';
    return String(value);
  }
}
