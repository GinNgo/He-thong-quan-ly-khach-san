import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SearchServiceTabsComponent } from '../search-service-tabs/search-service-tabs.component';
import { StayTypeSelectorComponent } from '../stay-type-selector/stay-type-selector.component';
import { LocationAutocompleteComponent } from '../location-autocomplete/location-autocomplete.component';
import { DateRangeSelectorComponent } from '../date-range-selector/date-range-selector.component';
import { GuestRoomSelectorComponent } from '../guest-room-selector/guest-room-selector.component';
import { HomeSearchStateService } from '../../services/home-search-state.service';

@Component({
  selector: 'app-hero-search',
  standalone: true,
  imports: [
    CommonModule, 
    SearchServiceTabsComponent, 
    StayTypeSelectorComponent,
    LocationAutocompleteComponent,
    DateRangeSelectorComponent,
    GuestRoomSelectorComponent
  ],
  template: `
    <div class="w-full">
      
      <!-- Service Tabs -->
      <app-search-service-tabs></app-search-service-tabs>

      <!-- Main Panel Container -->
      <div class="bg-white rounded-2xl rounded-tl-none shadow-2xl p-4 md:p-6 border border-gray-100 flex flex-col gap-4">
        
        <!-- Stay Type Selector -->
        <app-stay-type-selector></app-stay-type-selector>

        <!-- Search Fields Wrapper -->
        <div class="flex flex-col lg:flex-row items-stretch gap-3 bg-gray-50/50 p-2 md:p-3 rounded-2xl border border-gray-100/50 shadow-inner">
          
          <!-- Location -->
          <div class="flex-[1.8] min-w-0 bg-white rounded-xl shadow-sm border border-gray-200 focus-within:ring-2 focus-within:ring-primary/20 focus-within:border-primary transition-all h-[65px]">
            <app-location-autocomplete></app-location-autocomplete>
          </div>

          <!-- Dates -->
          <div class="flex-[2] min-w-0 bg-white rounded-xl shadow-sm border border-gray-200 h-[65px]">
            <app-date-range-selector></app-date-range-selector>
          </div>

          <!-- Guests -->
          <div class="flex-[1.2] min-w-0 bg-white rounded-xl shadow-sm border border-gray-200 h-[65px]">
            <app-guest-room-selector></app-guest-room-selector>
          </div>

          <!-- Search Button -->
          <div class="flex-shrink-0 lg:w-[140px] h-[65px]">
            <button class="w-full h-full border-0 bg-primary hover:bg-primary-hover text-white font-bold text-[18px] rounded-xl shadow-md transition-all active:scale-95 flex items-center justify-center gap-2"
                    (click)="search()">
              <span>TÌM</span>
            </button>
          </div>

        </div>
      </div>
    </div>
  `
})
export class HeroSearchComponent {
  private stateService = inject(HomeSearchStateService);

  search() {
    this.stateService.submitSearch();
  }
}
