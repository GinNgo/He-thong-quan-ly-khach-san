import { Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LocationAutocompleteComponent } from '../location-autocomplete/location-autocomplete.component';
import { DateRangeSelectorComponent } from '../date-range-selector/date-range-selector.component';
import { GuestRoomSelectorComponent } from '../guest-room-selector/guest-room-selector.component';
import { HomeSearchStateService } from '../../services/home-search-state.service';

@Component({
  selector: 'app-sticky-search-bar',
  standalone: true,
  imports: [CommonModule, LocationAutocompleteComponent, DateRangeSelectorComponent, GuestRoomSelectorComponent],
  template: `
    <div *ngIf="isVisible"
         class="fixed top-0 left-0 right-0 z-[60] bg-white shadow-md border-b border-gray-200 animate-slide-down">
      <div class="max-w-7xl mx-auto px-4 py-2 flex items-center justify-between gap-4 h-20">
        
        <!-- Logo -->
        <div class="hidden md:flex flex-shrink-0 items-center">
           <span class="text-xl font-bold text-primary font-serif">LuxeStay</span>
        </div>

        <!-- Compact Search Form -->
        <div class="flex-1 flex items-stretch gap-2 bg-gray-50 p-1 rounded-xl border border-gray-100 shadow-inner overflow-x-auto custom-scrollbar h-[54px]">
          
          <div class="flex-1 min-w-[200px] bg-white rounded-lg shadow-sm border border-gray-200 focus-within:ring-1 focus-within:ring-primary/20">
            <app-location-autocomplete></app-location-autocomplete>
          </div>

          <div class="flex-[1.5] min-w-[280px] bg-white rounded-lg shadow-sm border border-gray-200">
            <app-date-range-selector></app-date-range-selector>
          </div>

          <div class="flex-[1] min-w-[220px] bg-white rounded-lg shadow-sm border border-gray-200">
            <app-guest-room-selector></app-guest-room-selector>
          </div>

        </div>

        <!-- Search Button -->
        <button class="flex-shrink-0 border-0 px-6 h-[54px] bg-primary hover:bg-primary-hover text-white font-bold rounded-xl shadow-md transition-all active:scale-95 flex items-center justify-center"
                (click)="search()">
          TÌM
        </button>

      </div>
    </div>
  `,
  styles: [`
    .custom-scrollbar::-webkit-scrollbar {
      height: 4px;
    }
    .custom-scrollbar::-webkit-scrollbar-thumb {
      background-color: #CBD5E1;
      border-radius: 4px;
    }
  `]
})
export class StickySearchBarComponent {
  @Input() isVisible = false;
  
  private stateService = inject(HomeSearchStateService);

  search() {
    this.stateService.submitSearch();
  }
}
