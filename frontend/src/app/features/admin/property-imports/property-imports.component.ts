import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-property-imports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container mt-4">
      <h2>Automated Property Import</h2>
      
      <div class="card mb-4">
        <div class="card-body">
          <h5 class="card-title">Search & Stage</h5>
          <div class="row g-3">
            <div class="col-md-3">
              <label>Provider</label>
              <select class="form-select" [(ngModel)]="provider">
                <option value="NOMINATIM">Nominatim (OSM)</option>
              </select>
            </div>
            <div class="col-md-3">
              <label>Keyword</label>
              <input type="text" class="form-control" [(ngModel)]="searchKeyword" placeholder="e.g. hotel in hanoi">
            </div>
            <div class="col-md-2">
              <label>Max Results</label>
              <input type="number" class="form-control" [(ngModel)]="maxResults">
            </div>
            <div class="col-md-2 d-flex align-items-end">
              <button class="btn btn-primary w-100" (click)="searchAndStage()" [disabled]="loading">
                {{ loading ? 'Searching...' : 'Search' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-body">
          <h5 class="card-title">Import Batches</h5>
          <table class="table table-striped mt-3">
            <thead>
              <tr>
                <th>ID</th>
                <th>Keyword</th>
                <th>Found</th>
                <th>New</th>
                <th>Dup</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let batch of batches">
                <td>{{ batch.id }}</td>
                <td>{{ batch.searchKeyword }}</td>
                <td>{{ batch.totalFound }}</td>
                <td><span class="badge bg-success">{{ batch.totalNew }}</span></td>
                <td><span class="badge bg-warning">{{ batch.totalDuplicate }}</span></td>
                <td>{{ batch.status }}</td>
                <td>
                  <button class="btn btn-sm btn-info me-2" (click)="viewItems(batch.id)">View Items</button>
                  <button class="btn btn-sm btn-success" *ngIf="batch.status === 'PREVIEW_READY'" (click)="importBatch(batch.id)">Import</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="modal d-block" tabindex="-1" *ngIf="showItemsModal">
        <div class="modal-dialog modal-xl">
          <div class="modal-content">
            <div class="modal-header">
              <h5 class="modal-title">Batch Items</h5>
              <button type="button" class="btn-close" (click)="showItemsModal = false"></button>
            </div>
            <div class="modal-body" style="max-height: 70vh; overflow-y: auto;">
              <table class="table table-sm table-bordered">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Address</th>
                    <th>Duplicate Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let item of selectedBatchItems" [ngClass]="{'table-warning': item.duplicateStatus !== 'NEW'}">
                    <td>{{ item.rawName }}</td>
                    <td>{{ item.rawAddress }}</td>
                    <td>{{ item.duplicateStatus }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  `
})
export class PropertyImportsComponent implements OnInit {
  provider = 'NOMINATIM';
  searchKeyword = '';
  maxResults = 50;
  loading = false;
  
  batches: any[] = [];
  selectedBatchItems: any[] = [];
  showItemsModal = false;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadBatches();
  }

  loadBatches() {
    this.http.get<any>(`${environment.apiUrl}/admin/property-imports`).subscribe({
      next: (res) => {
        this.batches = res.content || res;
      },
      error: (err) => console.error(err)
    });
  }

  searchAndStage() {
    this.loading = true;
    const body = {
      keyword: this.searchKeyword,
      maxResults: this.maxResults
    };
    this.http.post(`${environment.apiUrl}/admin/property-imports/search?provider=${this.provider}`, body).subscribe({
      next: () => {
        this.loading = false;
        this.loadBatches();
      },
      error: (err) => {
        this.loading = false;
        console.error(err);
      }
    });
  }

  viewItems(batchId: number) {
    this.http.get<any>(`${environment.apiUrl}/admin/property-imports/${batchId}/items`).subscribe({
      next: (res) => {
        this.selectedBatchItems = res.content || res;
        this.showItemsModal = true;
      },
      error: (err) => console.error(err)
    });
  }

  importBatch(batchId: number) {
    if (confirm('Are you sure you want to import valid properties from this batch?')) {
      this.http.post(`${environment.apiUrl}/admin/property-imports/${batchId}/import`, {}).subscribe({
        next: () => {
          alert('Import successful!');
          this.loadBatches();
        },
        error: (err) => console.error(err)
      });
    }
  }
}
