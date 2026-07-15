import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CheckboxModule } from 'primeng/checkbox';
import { SliderModule } from 'primeng/slider';

export interface FilterState {
  minPrice: number;
  maxPrice: number;
  propertyTypes: string[];
  starRatings: number[];
  minReviewScore: number | null;
  amenityIds: number[];
}

@Component({
  selector: 'app-search-filter-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, SliderModule, CheckboxModule],
  template: `
    <aside class="filter-panel" aria-label="Bộ lọc kết quả tìm kiếm">
      <header class="filter-header">
        <div><span class="eyebrow">Tinh chỉnh kết quả</span><h2>Bộ lọc</h2></div>
        <button type="button" class="text-action" (click)="clearAll()">Xóa tất cả</button>
      </header>

      <section class="filter-group">
        <div class="group-heading"><h3>Khoảng giá mỗi đêm</h3><span>VND</span></div>
        <p-slider [(ngModel)]="priceRange" [range]="true" [min]="0" [max]="10000000"
          [step]="100000" ariaLabel="Khoảng giá phòng"></p-slider>
        <div class="price-values">
          <span>{{ formatVnd(priceRange[0]) }}</span>
          <span>{{ priceRange[1] >= 10000000 ? '10.000.000 ₫ trở lên' : formatVnd(priceRange[1]) }}</span>
        </div>
      </section>

      <section class="filter-group">
        <h3>Loại cơ sở</h3>
        <label *ngFor="let type of propertyTypeOptions" class="check-row" [for]="'type-' + type.value">
          <p-checkbox [value]="type.value" [(ngModel)]="selectedPropertyTypes"
            [inputId]="'type-' + type.value"></p-checkbox>
          <span>{{ type.label }}</span>
        </label>
      </section>

      <section class="filter-group">
        <h3>Hạng sao</h3>
        <label *ngFor="let star of [5,4,3,2,1]" class="check-row" [for]="'star-' + star">
          <p-checkbox [value]="star" [(ngModel)]="selectedStars" [inputId]="'star-' + star"></p-checkbox>
          <span>{{ star }} sao</span><span class="stars" aria-hidden="true">★</span>
        </label>
      </section>

      <section class="filter-group">
        <h3>Điểm đánh giá</h3>
        <label *ngFor="let score of reviewOptions" class="radio-row">
          <input type="radio" name="review-score" [value]="score.value" [(ngModel)]="selectedReviewScore">
          <span><strong>{{ score.value }}+</strong> {{ score.label }}</span>
        </label>
        <button *ngIf="selectedReviewScore" type="button" class="text-action compact" (click)="selectedReviewScore = null">
          Bỏ lọc điểm đánh giá
        </button>
      </section>

      <div class="filter-actions">
        <button type="button" class="apply-button" (click)="applyFilters()">Áp dụng bộ lọc</button>
      </div>
    </aside>
  `,
  styles: [`
    .filter-panel{background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:20px;box-shadow:0 4px 18px rgba(15,23,42,.05)}
    .filter-header,.group-heading,.price-values{display:flex;align-items:center;justify-content:space-between;gap:12px}
    .filter-header{padding-bottom:18px}.filter-header h2{font-size:20px;margin:2px 0 0;color:#0f172a}.eyebrow{font-size:11px;text-transform:uppercase;color:#64748b;font-weight:700}
    .filter-group{padding:19px 0;border-top:1px solid #edf2f7}.filter-group h3{font-size:15px;margin:0 0 14px;color:#172033}.group-heading h3{margin:0}.group-heading span{font-size:11px;color:#64748b}
    .price-values{font-size:12px;color:#475569;margin-top:12px}.check-row,.radio-row{display:flex;align-items:center;gap:10px;min-height:34px;font-size:14px;color:#334155;cursor:pointer}.stars{color:#d59b00;margin-left:auto}
    .radio-row input{width:17px;height:17px;accent-color:#1769e0}.text-action{border:0;background:transparent;color:#1769e0;font-weight:700;cursor:pointer;padding:4px}.text-action.compact{font-size:12px;margin-top:8px;padding-left:0}
    .filter-actions{position:sticky;bottom:0;background:#fff;padding-top:12px;border-top:1px solid #edf2f7}.apply-button{width:100%;height:44px;border:0;border-radius:6px;background:#1769e0;color:#fff;font-weight:700;cursor:pointer}.apply-button:hover{background:#0f58c7}
    :host ::ng-deep .p-slider{margin:20px 7px 0}:host ::ng-deep .p-slider-range{background:#1769e0}:host ::ng-deep .p-slider-handle{border-color:#1769e0}
  `]
})
export class SearchFilterSidebarComponent implements OnChanges {
  @Input() initialState: Partial<FilterState> = {};
  @Output() filtersChanged = new EventEmitter<FilterState>();

  priceRange = [0, 10000000];
  selectedPropertyTypes: string[] = [];
  selectedStars: number[] = [];
  selectedReviewScore: number | null = null;

  readonly propertyTypeOptions = [
    { label: 'Khách sạn', value: 'HOTEL' }, { label: 'Khu nghỉ dưỡng', value: 'RESORT' },
    { label: 'Căn hộ', value: 'APARTMENT' }, { label: 'Biệt thự', value: 'VILLA' },
    { label: 'Homestay', value: 'HOMESTAY' }, { label: 'Nhà nghỉ', value: 'MOTEL' },
    { label: 'Nhà khách', value: 'GUEST_HOUSE' }, { label: 'Hostel', value: 'HOSTEL' }
  ];
  readonly reviewOptions = [
    { value: 9, label: 'Tuyệt hảo' }, { value: 8, label: 'Rất tốt' },
    { value: 7, label: 'Tốt' }, { value: 6, label: 'Dễ chịu' }
  ];

  ngOnChanges(): void {
    this.priceRange = [this.initialState.minPrice ?? 0, this.initialState.maxPrice ?? 10000000];
    this.selectedPropertyTypes = [...(this.initialState.propertyTypes || [])];
    this.selectedStars = [...(this.initialState.starRatings || [])];
    this.selectedReviewScore = this.initialState.minReviewScore ?? null;
  }

  applyFilters(): void {
    this.filtersChanged.emit({
      minPrice: Math.max(0, Number(this.priceRange[0]) || 0),
      maxPrice: Math.max(this.priceRange[0], Number(this.priceRange[1]) || 10000000),
      propertyTypes: [...this.selectedPropertyTypes], starRatings: [...this.selectedStars],
      minReviewScore: this.selectedReviewScore, amenityIds: []
    });
  }

  clearAll(): void {
    this.priceRange = [0, 10000000]; this.selectedPropertyTypes = []; this.selectedStars = [];
    this.selectedReviewScore = null; this.applyFilters();
  }

  formatVnd(value: number): string {
    return `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 }).format(value || 0)} ₫`;
  }
}
