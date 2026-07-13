import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PopoverModule } from 'primeng/popover';
import { DatePickerModule } from 'primeng/datepicker';
import { HomeSearchStateService } from '../../services/home-search-state.service';

@Component({
  selector: 'app-date-range-selector',
  standalone: true,
  imports: [CommonModule, FormsModule, PopoverModule, DatePickerModule],
  template: `
    <div class="flex w-full h-full divide-x divide-gray-200">
      <!-- Check-in -->
      <div class="flex-1 min-w-0 h-full bg-transparent relative cursor-pointer group transition-colors" (click)="dateOp.toggle($event)">
        <div class="flex items-center h-full px-3">
          <i class="pi pi-calendar text-primary mr-2 text-xl flex-shrink-0"></i>
          <div class="flex flex-col justify-center min-w-0">
            <span class="hidden lg:block text-[14px] font-bold truncate" [ngClass]="checkInDate ? 'text-gray-900' : 'text-gray-400'">{{ formatDisplayDateFull(checkInDate) }}</span>
            <span class="hidden md:block lg:hidden text-[14px] font-bold truncate" [ngClass]="checkInDate ? 'text-gray-900' : 'text-gray-400'">{{ formatDisplayDateShort(checkInDate) }}</span>
            <span class="block md:hidden text-[14px] font-bold truncate" [ngClass]="checkInDate ? 'text-gray-900' : 'text-gray-400'">{{ formatDisplayDateTiny(checkInDate) }}</span>
            <span class="text-[12px] text-gray-500 truncate">{{ formatDisplayDayOfWeek(checkInDate) }}</span>
          </div>
        </div>
      </div>

      <!-- Check-out -->
      <div class="flex-1 min-w-0 h-full bg-transparent relative cursor-pointer group transition-colors" (click)="dateOp.toggle($event)">
        <div class="flex items-center h-full px-3">
          <i class="pi pi-calendar text-primary mr-2 text-xl flex-shrink-0" [ngClass]="{'opacity-50': !isOvernight}"></i>
          <div class="flex flex-col justify-center min-w-0">
            <ng-container *ngIf="!isOvernight">
              <span class="text-[14px] font-bold text-gray-400 truncate">Trong ngày</span>
            </ng-container>
            <ng-container *ngIf="isOvernight">
              <span class="hidden lg:block text-[14px] font-bold truncate" [ngClass]="checkOutDate ? 'text-gray-900' : 'text-gray-400'">{{ formatDisplayDateFull(checkOutDate) }}</span>
              <span class="hidden md:block lg:hidden text-[14px] font-bold truncate" [ngClass]="checkOutDate ? 'text-gray-900' : 'text-gray-400'">{{ formatDisplayDateShort(checkOutDate) }}</span>
              <span class="block md:hidden text-[14px] font-bold truncate" [ngClass]="checkOutDate ? 'text-gray-900' : 'text-gray-400'">{{ formatDisplayDateTiny(checkOutDate) }}</span>
              <span class="text-[12px] text-gray-500 truncate">{{ formatDisplayDayOfWeek(checkOutDate) }}</span>
            </ng-container>
          </div>
        </div>
      </div>
    </div>

    <!-- Date Overlay Panel -->
    <p-popover #dateOp [style]="{width: 'auto'}" styleClass="shadow-2xl rounded-xl border border-gray-200 mt-2 p-0">
      <ng-template pTemplate="content">
        <p-datepicker 
          [ngModel]="dateRange" 
          (ngModelChange)="onDateChange($event)"
          [selectionMode]="isOvernight ? 'range' : 'single'" 
          [numberOfMonths]="2" 
          [inline]="true" 
          [minDate]="minDate"
          styleClass="w-full border-0" 
          dateFormat="dd/mm/yy">
        </p-datepicker>
      </ng-template>
    </p-popover>
  `
})
export class DateRangeSelectorComponent {
  private stateService = inject(HomeSearchStateService);
  
  minDate = new Date();

  get checkInDate(): Date | null {
    return this.stateService.state().checkInDate;
  }

  get checkOutDate(): Date | null {
    return this.stateService.state().checkOutDate;
  }

  get isOvernight(): boolean {
    return this.stateService.isDayUse() === false;
  }

  get dateRange(): Date | Date[] | null {
    if (this.isOvernight) {
      if (this.checkInDate && this.checkOutDate) return [this.checkInDate, this.checkOutDate];
      if (this.checkInDate) return [this.checkInDate];
      return null;
    } else {
      return this.checkInDate;
    }
  }

  onDateChange(value: any) {
    if (this.isOvernight) {
      if (Array.isArray(value)) {
        this.stateService.updateDates(value[0] || null, value[1] || null);
      }
    } else {
      this.stateService.updateDates(value, null);
    }
  }

  formatDisplayDateFull(date: Date | null): string {
    if (!date) return 'Thêm ngày';
    return `${date.getDate()} tháng ${date.getMonth() + 1} ${date.getFullYear()}`;
  }

  formatDisplayDateShort(date: Date | null): string {
    if (!date) return 'Thêm ngày';
    return `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}/${date.getFullYear()}`;
  }

  formatDisplayDateTiny(date: Date | null): string {
    if (!date) return 'Thêm ngày';
    return `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}`;
  }

  formatDisplayDayOfWeek(date: Date | null): string {
    if (!date) return 'Thêm ngày';
    const days = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
    return days[date.getDay()];
  }
}
