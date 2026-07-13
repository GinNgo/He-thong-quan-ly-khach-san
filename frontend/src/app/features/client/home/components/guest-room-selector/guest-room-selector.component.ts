import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PopoverModule } from 'primeng/popover';
import { HomeSearchStateService } from '../../services/home-search-state.service';

@Component({
  selector: 'app-guest-room-selector',
  standalone: true,
  imports: [CommonModule, PopoverModule],
  template: `
    <div class="flex-1 min-w-0 h-full bg-transparent relative cursor-pointer hover:bg-gray-50 transition-colors" (click)="guestOp.toggle($event)">
      <div class="flex justify-between items-center h-full px-3">
        <div class="flex items-center min-w-0 flex-1 pr-2">
          <i class="pi pi-users text-primary mr-2 text-xl flex-shrink-0"></i>
          <div class="flex flex-col justify-center min-w-0 flex-1">
            <span class="hidden lg:block text-[14px] font-bold text-gray-900 truncate">
              {{ adults }} người lớn<span *ngIf="children > 0">, {{ children }} trẻ em</span>
            </span>
            <span class="hidden lg:block text-[12px] text-gray-500 truncate">{{ rooms }} phòng</span>
            
            <span class="block lg:hidden text-[14px] font-bold text-gray-900 truncate">
              {{ adults + children }} khách <span class="hidden sm:inline">· {{ rooms }} phòng</span>
            </span>
            <span class="block lg:hidden sm:hidden text-[12px] text-gray-500 truncate">{{ rooms }} phòng</span>
          </div>
        </div>
        <i class="pi pi-chevron-down text-gray-400 text-sm flex-shrink-0"></i>
      </div>
      
      <!-- Guests Overlay Panel -->
      <p-popover #guestOp [style]="{width: '320px'}" styleClass="shadow-2xl rounded-xl border border-gray-200 mt-2 p-3">
        <ng-template pTemplate="content">
          <!-- Rooms -->
          <div class="flex justify-between items-center py-3 border-b border-gray-100">
            <div class="font-bold text-gray-900 text-[14px]">Phòng</div>
            <div class="flex items-center gap-4">
              <button class="w-8 h-8 rounded-full border border-primary flex items-center justify-center text-primary hover:bg-primary-50 transition-colors"
                      [disabled]="rooms <= 1"
                      [class.opacity-50]="rooms <= 1"
                      (click)="updateCount('rooms', -1, $event)">
                <i class="pi pi-minus text-xs"></i>
              </button>
              <span class="font-bold w-4 text-center">{{ rooms }}</span>
              <button class="w-8 h-8 rounded-full border border-primary flex items-center justify-center text-primary hover:bg-primary-50 transition-colors"
                      (click)="updateCount('rooms', 1, $event)">
                <i class="pi pi-plus text-xs"></i>
              </button>
            </div>
          </div>
          <!-- Adults -->
          <div class="flex justify-between items-center py-3 border-b border-gray-100">
            <div class="font-bold text-gray-900 text-[14px]">Người lớn</div>
            <div class="flex items-center gap-4">
              <button class="w-8 h-8 rounded-full border border-primary flex items-center justify-center text-primary hover:bg-primary-50 transition-colors"
                      [disabled]="adults <= 1"
                      [class.opacity-50]="adults <= 1"
                      (click)="updateCount('adults', -1, $event)">
                <i class="pi pi-minus text-xs"></i>
              </button>
              <span class="font-bold w-4 text-center">{{ adults }}</span>
              <button class="w-8 h-8 rounded-full border border-primary flex items-center justify-center text-primary hover:bg-primary-50 transition-colors"
                      (click)="updateCount('adults', 1, $event)">
                <i class="pi pi-plus text-xs"></i>
              </button>
            </div>
          </div>
          <!-- Children -->
          <div class="flex justify-between items-center py-3">
            <div class="flex flex-col">
              <span class="font-bold text-gray-900 text-[14px]">Trẻ em</span>
              <span class="text-[11px] text-gray-500">0 - 17 tuổi</span>
            </div>
            <div class="flex items-center gap-4">
              <button class="w-8 h-8 rounded-full border border-primary flex items-center justify-center text-primary hover:bg-primary-50 transition-colors"
                      [disabled]="children <= 0"
                      [class.opacity-50]="children <= 0"
                      (click)="updateCount('children', -1, $event)">
                <i class="pi pi-minus text-xs"></i>
              </button>
              <span class="font-bold w-4 text-center">{{ children }}</span>
              <button class="w-8 h-8 rounded-full border border-primary flex items-center justify-center text-primary hover:bg-primary-50 transition-colors"
                      (click)="updateCount('children', 1, $event)">
                <i class="pi pi-plus text-xs"></i>
              </button>
            </div>
          </div>
        </ng-template>
      </p-popover>
    </div>
  `
})
export class GuestRoomSelectorComponent {
  private stateService = inject(HomeSearchStateService);

  get adults() { return this.stateService.state().adultCount; }
  get children() { return this.stateService.state().childCount; }
  get rooms() { return this.stateService.state().roomCount; }

  updateCount(type: 'adults' | 'children' | 'rooms', delta: number, event: Event) {
    event.stopPropagation();
    let a = this.adults;
    let c = this.children;
    let r = this.rooms;

    if (type === 'adults') a += delta;
    if (type === 'children') c += delta;
    if (type === 'rooms') r += delta;

    this.stateService.updateGuests(a, c, r);
  }
}
