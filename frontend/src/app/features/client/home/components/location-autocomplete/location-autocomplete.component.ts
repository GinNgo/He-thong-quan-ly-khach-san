import { Component, OnInit, OnDestroy, ViewChild, inject, HostListener, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PopoverModule } from 'primeng/popover';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, switchMap, finalize, tap } from 'rxjs/operators';
import { HomeSearchStateService } from '../../services/home-search-state.service';
import { ClientApiService } from '../../../../../core/services/client-api.service';

@Component({
  selector: 'app-location-autocomplete',
  standalone: true,
  imports: [CommonModule, FormsModule, PopoverModule],
  template: `
    <div class="relative w-full h-full cursor-text" (click)="focusInput($event)">
      <div class="flex items-center h-full px-3">
        <i class="pi pi-search text-primary mr-2 text-xl"></i>
        <input #searchInput type="text" 
               [(ngModel)]="keyword" 
               (ngModelChange)="onKeywordChange($event)"
               (focus)="onFocus($event)"
               (keydown)="onKeydown($event)"
               placeholder="Bạn muốn đến đâu?" 
               class="w-full h-full border-0 focus:ring-0 text-[15px] font-medium text-gray-800 bg-transparent outline-none placeholder:text-gray-400">
      </div>

      <p-popover #locOp [style]="{width: '100%', maxWidth: '800px'}" styleClass="shadow-2xl rounded-xl border border-gray-200 mt-2 p-0 overflow-hidden" (onHide)="isPopupOpen = false">
        <ng-template pTemplate="content">
          <div class="bg-white max-h-[400px] overflow-y-auto">
            
            <!-- Loading State -->
            <div *ngIf="isLoading" class="p-8 flex justify-center items-center text-gray-400">
              <i class="pi pi-spin pi-spinner text-2xl mr-3"></i> Đang tìm kiếm...
            </div>

            <!-- Empty State for Search -->
            <div *ngIf="!isLoading && keyword && !isPristineSearch && searchResults.length === 0" class="p-8 text-center text-gray-500">
              <i class="pi pi-search-minus text-3xl mb-3 text-gray-300"></i>
              <p>Không tìm thấy kết quả phù hợp cho "{{keyword}}"</p>
            </div>

            <!-- Default View (Empty Keyword or just focused) -->
            <div *ngIf="!keyword || isPristineSearch" class="flex flex-col md:flex-row p-0">
              
              <!-- Left Column: Recent & Suggestions -->
              <div class="flex-1 min-w-0 p-5 bg-white">
                
                <!-- Recent Searches -->
                <div *ngIf="recentSearches.length > 0" class="mb-6">
                  <h4 class="text-[14px] font-normal text-gray-500 mb-3">Tìm kiếm gần đây</h4>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div *ngFor="let item of recentSearches.slice(0, 2)" 
                         class="p-3 bg-gray-50 rounded-xl cursor-pointer hover:bg-gray-100 transition-colors"
                         (click)="selectRecent(item)">
                      <div class="font-bold text-gray-900 text-[14px] mb-1 flex items-center justify-between">
                        <span class="truncate pr-2">{{ item.displayLocation }}</span>
                        <div class="flex items-center text-gray-500 text-xs flex-shrink-0">
                          <i class="pi pi-user mr-1 text-[10px]"></i> {{ item.adultCount || 2 }}
                        </div>
                      </div>
                      <div class="text-[12px] text-gray-500 truncate">
                        <span *ngIf="item.checkInDate && item.checkOutDate">{{ item.checkInDate | date:'d/M/yyyy' }} - {{ item.checkOutDate | date:'d/M/yyyy' }}</span>
                        <span *ngIf="!item.checkInDate">Chưa chọn ngày</span>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Suggested Cities (Mock) -->
                <div class="mb-6">
                  <h4 class="text-[14px] font-normal text-gray-500 mb-3">Thành phố</h4>
                  <div class="flex items-center gap-3 p-2 cursor-pointer hover:bg-gray-50 rounded-xl transition-colors">
                    <img src="https://images.unsplash.com/photo-1555921015-5532091f6026?auto=format&fit=crop&w=100&q=80" alt="Mỹ Tho" class="w-12 h-12 rounded-lg object-cover">
                    <div class="flex flex-col">
                      <span class="font-bold text-gray-900 text-[14px]">Mỹ Tho (Tiền Giang)</span>
                      <span class="text-[13px] text-gray-500">(39)</span>
                    </div>
                  </div>
                </div>

                <!-- Suggested Regions (Mock) -->
                <div>
                  <div class="inline-block px-3 py-1 bg-gray-100 rounded-lg text-sm font-bold text-gray-700 mb-3">Khu vực</div>
                  <div class="flex items-center gap-3 p-2 cursor-pointer hover:bg-gray-50 rounded-xl transition-colors">
                    <img src="https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?auto=format&fit=crop&w=100&q=80" alt="Mỹ Tho Khu vực" class="w-12 h-12 rounded-lg object-cover">
                    <div class="flex flex-col">
                      <span class="font-bold text-gray-900 text-[14px]">Mỹ Tho <span class="text-gray-500 font-normal">(77)</span></span>
                      <span class="text-[13px] text-blue-600">100% <span class="text-gray-500">đã ở lại đây</span></span>
                    </div>
                  </div>
                </div>

              </div>

              <!-- Right Column: Popular Destinations -->
              <div class="flex-1 min-w-0 p-5 border-t md:border-t-0 md:border-l border-gray-200 bg-white">
                <h4 class="text-[14px] font-normal text-gray-500 mb-3">Các thành phố nổi tiếng ở Việt Nam</h4>
                <div class="flex flex-col gap-1">
                  <div *ngFor="let dest of popularDestinations" 
                       class="flex items-center gap-3 p-2 cursor-pointer hover:bg-gray-50 rounded-xl transition-colors" 
                       (click)="selectPopular(dest)">
                    <img [src]="dest.image" [alt]="dest.name" class="w-12 h-12 rounded-xl object-cover">
                    <div class="flex flex-col min-w-0">
                      <span class="font-bold text-gray-900 text-[14px] truncate">{{ dest.name }} <span class="font-normal text-gray-500">({{ dest.properties | number }})</span></span>
                      <span class="text-[13px] text-gray-500">{{ dest.subtitle || 'bãi biển, tham quan' }}</span>
                    </div>
                  </div>
                </div>
              </div>

            </div>

            <!-- Search Results View -->
            <div *ngIf="!isLoading && keyword && !isPristineSearch && searchResults.length > 0" class="py-1">
              <div *ngFor="let res of searchResults; let i = index" 
                   class="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-blue-50 transition-colors"
                   [class.bg-blue-50]="i === activeIndex"
                   (click)="selectResult(res)">
                
                <!-- Icon -->
                <div class="flex-shrink-0 text-gray-500">
                  <i *ngIf="res.type === 'PROVINCE'" class="pi pi-map-marker text-[18px]"></i>
                  <i *ngIf="res.type === 'WARD'" class="pi pi-map-marker text-[18px]"></i>
                  <i *ngIf="res.type === 'PROPERTY'" class="pi pi-building text-[18px]"></i>
                </div>

                <!-- Text -->
                <div class="flex flex-col min-w-0 flex-1">
                  <span class="text-[14px] text-gray-900 truncate">{{ res.mainText }}<span *ngIf="res.subText" class="text-gray-500">, {{ res.subText }}</span></span>
                  <span class="text-[12px] text-gray-500">{{ res.type === 'PROVINCE' ? 'Thành phố' : res.type === 'WARD' ? 'Khu vực' : 'Nơi lưu trú' }}</span>
                </div>

              </div>
            </div>

          </div>
        </ng-template>
      </p-popover>
    </div>
  `
})
export class LocationAutocompleteComponent implements OnInit, OnDestroy {
  @ViewChild('locOp') locOp: any;
  @ViewChild('searchInput') searchInput: any;

  private stateService = inject(HomeSearchStateService);
  private api = inject(ClientApiService);

  keyword: string = '';
  isPristineSearch = false;
  isPopupOpen = false;
  isLoading = false;
  
  recentSearches: any[] = [];
  popularDestinations: any[] = [];
  searchResults: any[] = [];
  activeIndex = -1;

  private searchSubject = new Subject<string>();
  private sub: Subscription;

  constructor() {
    effect(() => {
      const currentKeyword = this.stateService.state().keyword || this.stateService.state().locationDisplayName || '';
      if (this.keyword !== currentKeyword) {
        this.keyword = currentKeyword;
      }
    });

    this.sub = this.searchSubject.pipe(
      debounceTime(300),
      tap(() => this.isLoading = true),
      switchMap(term => this.api.searchAutocomplete(term).pipe(
        finalize(() => this.isLoading = false)
      ))
    ).subscribe(res => {
      this.formatSearchResults(res);
    });
  }

  ngOnInit() {
    // Sync keyword from state (now handled by effect, but keeping it here for immediate sync)
    this.keyword = this.stateService.state().keyword || this.stateService.state().locationDisplayName || '';
    this.loadRecent();
    this.loadPopular();
  }

  ngOnDestroy() {
    if (this.sub) this.sub.unsubscribe();
  }

  focusInput(event: Event) {
    if (this.searchInput) {
      this.searchInput.nativeElement.focus();
    }
    if (!this.isPopupOpen && this.locOp) {
      this.locOp.show(event);
      this.isPopupOpen = true;
    }
  }

  onFocus(event: Event) {
    this.isPristineSearch = true;
    if (this.searchInput) {
      this.searchInput.nativeElement.select();
    }
    if (!this.isPopupOpen && this.locOp) {
      this.locOp.show(event);
      this.isPopupOpen = true;
    }
  }

  onKeywordChange(val: string) {
    this.isPristineSearch = false;
    this.keyword = val;
    this.activeIndex = -1;
    // Update state directly for keyword so sticky header syncs
    this.stateService.state.update(s => ({ ...s, keyword: val, provinceId: null, wardId: null }));
    
    if (val.trim().length > 0) {
      this.searchSubject.next(val.trim());
      if (!this.isPopupOpen && this.locOp) {
        this.locOp.show(new Event('click')); // force open
        this.isPopupOpen = true;
      }
    } else {
      this.searchResults = [];
    }
  }

  onKeydown(event: KeyboardEvent) {
    if (!this.isPopupOpen || this.searchResults.length === 0) return;

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex = (this.activeIndex + 1) % this.searchResults.length;
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex = this.activeIndex <= 0 ? this.searchResults.length - 1 : this.activeIndex - 1;
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (this.activeIndex >= 0 && this.activeIndex < this.searchResults.length) {
        this.selectResult(this.searchResults[this.activeIndex]);
      } else {
        this.locOp.hide();
      }
    } else if (event.key === 'Escape') {
      this.locOp.hide();
    }
  }

  selectResult(res: any) {
    this.keyword = res.mainText;
    
    const provinceId = res.type === 'PROVINCE' ? res.id : (res.type === 'WARD' ? res.parentId : null);
    const wardId = res.type === 'WARD' ? res.id : null;
    
    // If it's a property, we might just use the keyword to search
    // We update the global state
    this.stateService.updateLocation(this.keyword, this.keyword, provinceId, wardId);
    
    this.locOp.hide();
  }

  selectRecent(item: any) {
    this.keyword = item.displayLocation || item.keyword;
    this.stateService.updateLocation(item.keyword, item.displayLocation, item.provinceId, item.wardId);
    
    if (item.checkInDate && item.checkOutDate) {
      this.stateService.updateDates(new Date(item.checkInDate), new Date(item.checkOutDate));
    }
    if (item.adultCount) {
      this.stateService.updateGuests(item.adultCount, item.childCount || 0, item.roomCount || 1);
    }
    
    this.locOp.hide();
  }

  selectPopular(dest: any) {
    this.keyword = dest.name;
    this.stateService.updateLocation(dest.name, dest.name, dest.id, null);
    this.locOp.hide();
  }

  private formatSearchResults(res: {locations: any[], properties: any[]}) {
    const formatted: any[] = [];
    
    // Format locations
    res.locations.forEach(loc => {
      formatted.push({
        id: loc.id,
        parentId: loc.parent?.id,
        type: loc.type,
        mainText: loc.nameVi,
        subText: loc.parent ? loc.parent.nameVi : 'Việt Nam',
        original: loc
      });
    });

    // Format properties
    res.properties.forEach(prop => {
      formatted.push({
        id: prop.id,
        type: 'PROPERTY',
        mainText: prop.name,
        subText: prop.addressLine || (prop.city ? prop.city : 'Việt Nam'),
        original: prop
      });
    });

    this.searchResults = formatted;
  }

  private loadRecent() {
    try {
      this.recentSearches = JSON.parse(localStorage.getItem('recentSearches') || '[]');
    } catch(e) {
      this.recentSearches = [];
    }
  }

  private loadPopular() {
    this.api.getProvinces().subscribe({
      next: (provinces) => {
        const topNames = ['Đà Nẵng', 'Hồ Chí Minh', 'Bà Rịa - Vũng Tàu', 'Hà Nội', 'Lâm Đồng'];
        const topProvinces = provinces.filter(p => topNames.some(name => p.nameVi.includes(name)));
        
        if (topProvinces.length > 0) {
          this.popularDestinations = topProvinces.map(p => ({
            id: p.id,
            name: p.nameVi.replace('Tỉnh ', '').replace('Thành phố ', ''),
            properties: Math.floor(Math.random() * 500) + 100, // mock count for UI if actual API doesn't return count quickly
            image: this.getImageForProvince(p.nameVi)
          }));
        } else {
          this.loadFallback();
        }
      },
      error: () => this.loadFallback()
    });
  }

  private loadFallback() {
    this.popularDestinations = [
      { id: null, name: 'Đà Nẵng', properties: 120, image: this.getImageForProvince('Đà Nẵng') },
      { id: null, name: 'Hồ Chí Minh', properties: 350, image: this.getImageForProvince('Hồ Chí Minh') },
      { id: null, name: 'Vũng Tàu', properties: 95, image: this.getImageForProvince('Vũng Tàu') },
      { id: null, name: 'Hà Nội', properties: 210, image: this.getImageForProvince('Hà Nội') },
      { id: null, name: 'Đà Lạt', properties: 150, image: this.getImageForProvince('Đà Lạt') }
    ];
  }

  private getImageForProvince(name: string): string {
    if (name.includes('Đà Nẵng')) return 'https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Hồ Chí Minh')) return 'https://images.unsplash.com/photo-1583417319070-4a69db38a482?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Vũng Tàu')) return 'https://images.unsplash.com/photo-1574676571597-d64c12ea847a?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Lâm Đồng') || name.includes('Đà Lạt')) return 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Khánh Hòa') || name.includes('Nha Trang')) return 'https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?auto=format&fit=crop&w=600&q=80';
    return 'https://images.unsplash.com/photo-1555921015-5532091f6026?auto=format&fit=crop&w=600&q=80';
  }
}
