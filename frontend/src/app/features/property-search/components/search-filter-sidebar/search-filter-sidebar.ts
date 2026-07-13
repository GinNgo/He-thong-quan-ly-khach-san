import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SliderModule } from 'primeng/slider';
import { CheckboxModule } from 'primeng/checkbox';
import { ButtonModule } from 'primeng/button';

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
  imports: [CommonModule, FormsModule, SliderModule, CheckboxModule, ButtonModule],
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-4 sticky top-[90px]">
      <div class="flex items-center justify-between mb-4 pb-4 border-b border-gray-100">
        <h3 class="font-bold text-lg text-gray-800">Bộ lọc</h3>
        <button pButton type="button" label="Xóa tất cả" class="p-button-text p-button-sm p-button-secondary" (click)="clearAll()"></button>
      </div>

      <!-- Price Range -->
      <div class="mb-6">
        <h4 class="font-semibold text-gray-700 mb-3">Khoảng giá (1 đêm)</h4>
        <p-slider [(ngModel)]="priceRange" [range]="true" [min]="0" [max]="10000000" [step]="100000" (onSlideEnd)="onFilterChange()"></p-slider>
        <div class="flex justify-between mt-2 text-sm text-gray-600 font-medium">
          <span>{{ priceRange[0] | currency:'VND':'symbol':'1.0-0' }}</span>
          <span>{{ priceRange[1] >= 10000000 ? '10,000,000+ đ' : (priceRange[1] | currency:'VND':'symbol':'1.0-0') }}</span>
        </div>
      </div>

      <!-- Property Type -->
      <div class="mb-6">
        <h4 class="font-semibold text-gray-700 mb-3">Loại cơ sở</h4>
        <div class="flex flex-col gap-2">
          <div *ngFor="let type of propertyTypeOptions" class="flex items-center">
            <p-checkbox [value]="type.value" [(ngModel)]="selectedPropertyTypes" [inputId]="type.value" (onChange)="onFilterChange()"></p-checkbox>
            <label [for]="type.value" class="ml-2 text-gray-600 cursor-pointer text-sm">{{ type.label }}</label>
          </div>
        </div>
      </div>

      <!-- Star Rating -->
      <div class="mb-6">
        <h4 class="font-semibold text-gray-700 mb-3">Hạng sao</h4>
        <div class="flex flex-col gap-2">
          <div *ngFor="let star of [5,4,3,2,1]" class="flex items-center">
            <p-checkbox [value]="star" [(ngModel)]="selectedStars" [inputId]="'star' + star" (onChange)="onFilterChange()"></p-checkbox>
            <label [for]="'star' + star" class="ml-2 text-gray-600 cursor-pointer text-sm flex items-center">
              {{ star }} <i class="pi pi-star-fill text-accent-gold ml-1 text-xs"></i>
            </label>
          </div>
        </div>
      </div>

    </div>
  `
})
export class SearchFilterSidebarComponent {
  @Input() initialState: Partial<FilterState> = {};
  @Output() filtersChanged = new EventEmitter<FilterState>();

  priceRange: number[] = [0, 10000000];
  selectedPropertyTypes: string[] = [];
  selectedStars: number[] = [];
  
  propertyTypeOptions = [
    { label: 'Khách sạn (Hotel)', value: 'HOTEL' },
    { label: 'Khu nghỉ dưỡng (Resort)', value: 'RESORT' },
    { label: 'Căn hộ (Apartment)', value: 'APARTMENT' },
    { label: 'Biệt thự (Villa)', value: 'VILLA' },
    { label: 'Nhà khách (Homestay)', value: 'HOMESTAY' },
  ];

  ngOnChanges() {
    if (this.initialState) {
      this.priceRange = [
        this.initialState.minPrice || 0,
        this.initialState.maxPrice || 10000000
      ];
      this.selectedPropertyTypes = this.initialState.propertyTypes || [];
      this.selectedStars = this.initialState.starRatings || [];
    }
  }

  onFilterChange() {
    this.filtersChanged.emit({
      minPrice: this.priceRange[0],
      maxPrice: this.priceRange[1],
      propertyTypes: this.selectedPropertyTypes,
      starRatings: this.selectedStars,
      minReviewScore: null,
      amenityIds: []
    });
  }

  clearAll() {
    this.priceRange = [0, 10000000];
    this.selectedPropertyTypes = [];
    this.selectedStars = [];
    this.onFilterChange();
  }
}
