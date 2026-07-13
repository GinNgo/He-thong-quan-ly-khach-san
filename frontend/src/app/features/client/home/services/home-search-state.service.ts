import { Injectable, signal, computed, effect } from '@angular/core';
import { Router } from '@angular/router';

export type StayType = 'OVERNIGHT' | 'DAY_USE';

export interface HomeSearchState {
  keyword: string;
  locationDisplayName: string;
  provinceId: number | null;
  wardId: number | null;
  propertyTypes: string[];
  stayType: StayType;
  checkInDate: Date | null;
  checkOutDate: Date | null;
  adultCount: number;
  childCount: number;
  roomCount: number;
}

@Injectable({
  providedIn: 'root'
})
export class HomeSearchStateService {
  
  // Initialize with defaults
  private defaultCheckIn = new Date();
  private defaultCheckOut = new Date();
  
  constructor(private router: Router) {
    this.defaultCheckOut.setDate(this.defaultCheckOut.getDate() + 1);
    
    // Initial state setup
    this.state.set({
      keyword: '',
      locationDisplayName: '',
      provinceId: null,
      wardId: null,
      propertyTypes: [], // empty means all
      stayType: 'OVERNIGHT',
      checkInDate: this.defaultCheckIn,
      checkOutDate: this.defaultCheckOut,
      adultCount: 2,
      childCount: 0,
      roomCount: 1
    });
  }

  // Reactive state using Angular Signals
  readonly state = signal<HomeSearchState>({} as HomeSearchState);

  // Computed derivations if needed
  readonly guestSummary = computed(() => {
    const s = this.state();
    let summary = `${s.adultCount} người lớn`;
    if (s.childCount > 0) summary += `, ${s.childCount} trẻ em`;
    summary += ` • ${s.roomCount} phòng`;
    return summary;
  });

  readonly isDayUse = computed(() => this.state().stayType === 'DAY_USE');

  // Actions
  updateLocation(keyword: string, displayName: string, provinceId: number | null, wardId: number | null) {
    this.state.update(s => ({ ...s, keyword, locationDisplayName: displayName, provinceId, wardId }));
  }

  updateStayType(stayType: StayType) {
    this.state.update(s => {
       const newState = { ...s, stayType };
       if (stayType === 'OVERNIGHT') {
         // ensure checkout is after checkin
         if (newState.checkInDate && (!newState.checkOutDate || newState.checkOutDate <= newState.checkInDate)) {
           const nextDay = new Date(newState.checkInDate);
           nextDay.setDate(nextDay.getDate() + 1);
           newState.checkOutDate = nextDay;
         }
       }
       return newState;
    });
  }

  updatePropertyTypes(types: string[]) {
    this.state.update(s => ({ ...s, propertyTypes: types }));
  }

  updateDates(checkIn: Date | null, checkOut: Date | null) {
     this.state.update(s => {
       const newState = { ...s, checkInDate: checkIn, checkOutDate: checkOut };
       if (checkIn && checkOut && checkOut <= checkIn && s.stayType === 'OVERNIGHT') {
          const nextDay = new Date(checkIn);
          nextDay.setDate(nextDay.getDate() + 1);
          newState.checkOutDate = nextDay;
       }
       return newState;
     });
  }

  updateGuests(adultCount: number, childCount: number, roomCount: number) {
     this.state.update(s => ({
       ...s,
       adultCount: Math.max(1, adultCount),
       childCount: Math.max(0, childCount),
       roomCount: Math.max(1, roomCount)
     }));
  }

  submitSearch() {
    const s = this.state();
    if (!s.checkInDate || (s.stayType === 'OVERNIGHT' && !s.checkOutDate)) {
       // Should validate in UI first, but fail safe here
       console.error("Invalid dates");
       return false;
    }

    const queryParams: any = {};
    if (s.keyword && !s.provinceId && !s.wardId) {
      queryParams.keyword = s.keyword;
    }
    if (s.provinceId) queryParams.provinceId = s.provinceId;
    if (s.wardId) queryParams.wardId = s.wardId;
    if (s.propertyTypes.length > 0) queryParams.propertyTypes = s.propertyTypes.join(',');
    
    queryParams.stayType = s.stayType;
    queryParams.checkInDate = this.formatDate(s.checkInDate);
    if (s.stayType === 'OVERNIGHT' && s.checkOutDate) {
      queryParams.checkOutDate = this.formatDate(s.checkOutDate);
    }
    
    queryParams.adultCount = s.adultCount;
    queryParams.childCount = s.childCount;
    queryParams.roomCount = s.roomCount;

    // Save recent search
    this.saveRecentSearch(s);

    this.router.navigate(['/search'], { queryParams });
    return true;
  }

  private saveRecentSearch(s: HomeSearchState) {
    if (!s.keyword && !s.provinceId) return;
    
    try {
      const recent = JSON.parse(localStorage.getItem('recentSearches') || '[]');
      const newEntry = {
         displayLocation: s.locationDisplayName || s.keyword,
         keyword: s.keyword,
         provinceId: s.provinceId,
         wardId: s.wardId,
         checkInDate: s.checkInDate,
         checkOutDate: s.checkOutDate,
         adultCount: s.adultCount,
         childCount: s.childCount,
         roomCount: s.roomCount
      };
      
      // Remove duplicates based on location
      const filtered = recent.filter((r: any) => r.displayLocation !== newEntry.displayLocation);
      filtered.unshift(newEntry);
      
      // Keep only top 5
      if (filtered.length > 5) filtered.length = 5;
      
      localStorage.setItem('recentSearches', JSON.stringify(filtered));
    } catch(e) {
      console.error("Error saving recent search", e);
    }
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
