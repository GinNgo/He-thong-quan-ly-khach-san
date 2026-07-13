import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { StickySearchBarComponent } from '../../../client/home/components/sticky-search-bar/sticky-search-bar.component';
import { SearchFilterSidebarComponent, FilterState } from '../../components/search-filter-sidebar/search-filter-sidebar';
import { PropertyResultCardComponent } from '../../components/property-result-card/property-result-card';
import { ClientApiService } from '../../../../core/services/client-api.service';
import { HomeSearchStateService } from '../../../client/home/services/home-search-state.service';
import { SelectModule } from 'primeng/select';
import { PaginatorModule } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';

@Component({
  selector: 'app-property-search-page',
  standalone: true,
  imports: [
    CommonModule,
    StickySearchBarComponent,
    SearchFilterSidebarComponent,
    PropertyResultCardComponent,
    SelectModule,
    PaginatorModule,
    SkeletonModule,
    FormsModule,
    DialogModule
  ],
  template: `
    <div class="bg-gray-50 min-h-screen pb-16">
      
      <!-- Top Search Bar -->
      <app-sticky-search-bar [isVisible]="true" class="sticky top-0 z-50"></app-sticky-search-bar>

      <div class="max-w-[1200px] mx-auto px-4 pt-8">
        
        <!-- Breadcrumb / Summary -->
        <div class="mb-6">
          <h1 class="text-2xl font-bold text-gray-900">
            {{ displayLocation() || 'Tất cả kết quả' }}: {{ totalItems() }} chỗ nghỉ được tìm thấy
          </h1>
          <p class="text-sm text-gray-600 mt-1">
            {{ stateService.guestSummary() }} &bull; 
            {{ stateService.state().checkInDate | date:'dd/MM/yyyy' }} - {{ stateService.state().checkOutDate | date:'dd/MM/yyyy' }}
          </p>
        </div>

        <div class="flex flex-col lg:flex-row gap-6 items-start">
          
          <!-- Sidebar Filter -->
          <div class="w-full lg:w-[280px] flex-shrink-0">
             <app-search-filter-sidebar 
               [initialState]="currentFilterState"
               (filtersChanged)="onFiltersChanged($event)">
             </app-search-filter-sidebar>
          </div>

          <!-- Main Results Area -->
          <div class="flex-1 w-full min-w-0">
            
            <!-- Sort & Active Filters -->
            <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
              
              <!-- Active Filters Chips & Mobile Filter Button -->
              <div class="flex flex-wrap gap-2 items-center">
                <button class="lg:hidden p-2 bg-white border border-gray-200 rounded-lg shadow-sm text-gray-700 text-sm font-medium flex items-center gap-2" (click)="mobileFilterVisible = true">
                  <i class="pi pi-filter"></i> Bộ lọc
                </button>
                <span *ngFor="let type of currentFilterState.propertyTypes" class="bg-primary/10 text-primary px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1 border border-primary/20">
                  Loại: {{ type }}
                  <i class="pi pi-times cursor-pointer hover:text-red-500" (click)="removePropertyType(type)"></i>
                </span>
                <span *ngIf="currentFilterState.starRatings?.length" class="bg-primary/10 text-primary px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1 border border-primary/20">
                  {{ currentFilterState.starRatings?.length }} hạng sao
                  <i class="pi pi-times cursor-pointer hover:text-red-500" (click)="removeStarRatings()"></i>
                </span>
              </div>

              <!-- Sort Dropdown -->
              <div class="flex items-center gap-2 flex-shrink-0 ml-auto">
                 <span class="text-sm text-gray-600 font-medium">Sắp xếp:</span>
                 <p-select 
                   [options]="sortOptions" 
                   [(ngModel)]="selectedSort" 
                   optionLabel="label" 
                   optionValue="value" 
                   (onChange)="onSortChange()"
                   [style]="{'min-width': '200px'}"
                   styleClass="!border-gray-200 !shadow-sm !rounded-lg !text-sm">
                 </p-select>
              </div>
            </div>

            <!-- Loading Skeleton -->
            <ng-container *ngIf="isLoading()">
              <div *ngFor="let i of [1,2,3,4,5]" class="bg-white rounded-xl shadow-sm border border-gray-200 p-4 mb-4 flex flex-col sm:flex-row gap-4">
                <p-skeleton width="288px" height="192px" styleClass="rounded-lg hidden sm:block"></p-skeleton>
                <p-skeleton width="100%" height="192px" styleClass="rounded-lg sm:hidden"></p-skeleton>
                <div class="flex-1 flex flex-col justify-between py-2">
                  <div>
                    <p-skeleton width="70%" height="1.5rem" styleClass="mb-2"></p-skeleton>
                    <p-skeleton width="40%" height="1rem" styleClass="mb-4"></p-skeleton>
                    <p-skeleton width="30%" height="1rem" styleClass="mb-1"></p-skeleton>
                  </div>
                  <div class="flex justify-end mt-4">
                    <p-skeleton width="120px" height="2.5rem" styleClass="rounded-lg"></p-skeleton>
                  </div>
                </div>
              </div>
            </ng-container>

            <!-- Results List -->
            <ng-container *ngIf="!isLoading() && properties().length > 0">
               <app-property-result-card 
                 *ngFor="let prop of properties()" 
                 [property]="prop"
                 (viewDetails)="goToDetails($event)">
               </app-property-result-card>

               <!-- Pagination -->
               <div class="mt-8 bg-white rounded-xl shadow-sm border border-gray-200 p-2">
                 <p-paginator 
                    [first]="(pageNumber() - 1) * pageSize()"
                    [rows]="pageSize()" 
                    [totalRecords]="totalItems()" 
                    [rowsPerPageOptions]="[10, 20, 50]"
                    (onPageChange)="onPageChange($event)">
                 </p-paginator>
               </div>
            </ng-container>

            <!-- Empty State -->
            <div *ngIf="!isLoading() && properties().length === 0" class="bg-white rounded-xl shadow-sm border border-gray-200 p-12 flex flex-col items-center justify-center text-center">
              <img src="assets/images/empty-search.svg" alt="No results" class="w-48 h-48 mb-6 opacity-60" onerror="this.style.display='none'">
              <h2 class="text-2xl font-bold text-gray-800 mb-2">Không tìm thấy chỗ nghỉ nào</h2>
              <p class="text-gray-500 max-w-md mb-6">
                Rất tiếc, chúng tôi không tìm thấy kết quả nào phù hợp với tìm kiếm của bạn. Hãy thử thay đổi ngày, bỏ bớt bộ lọc hoặc phóng to bản đồ.
              </p>
              <button class="bg-primary hover:bg-primary-hover text-white font-bold py-3 px-6 rounded-lg transition-colors" (click)="clearAllFilters()">
                Xóa tất cả bộ lọc
              </button>
            </div>

          </div>
        </div>
      </div>

      <!-- Mobile Filter Dialog -->
      <p-dialog [(visible)]="mobileFilterVisible" [modal]="true" [draggable]="false" [resizable]="false" 
                position="bottom" [style]="{width: '100vw', margin: '0', 'max-height': '90vh'}" 
                styleClass="m-0 rounded-t-2xl shadow-2xl" [showHeader]="false">
         <div class="p-4 bg-white rounded-t-2xl max-h-[90vh] overflow-y-auto">
            <div class="flex justify-between items-center mb-4">
               <h3 class="font-bold text-xl">Bộ lọc</h3>
               <button class="p-2 bg-gray-100 rounded-full hover:bg-gray-200" (click)="mobileFilterVisible = false">
                 <i class="pi pi-times"></i>
               </button>
            </div>
            <app-search-filter-sidebar 
               [initialState]="currentFilterState"
               (filtersChanged)="onFiltersChanged($event); mobileFilterVisible = false">
             </app-search-filter-sidebar>
         </div>
      </p-dialog>

    </div>
  `,
  styles: [`
    :host {
      display: block;
    }
  `]
})
export class PropertySearchPageComponent implements OnInit, OnDestroy {
  
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clientApi = inject(ClientApiService);
  public stateService = inject(HomeSearchStateService);

  private destroy$ = new Subject<void>();

  mobileFilterVisible = false;

  properties = signal<any[]>([]);
  totalItems = signal<number>(0);
  isLoading = signal<boolean>(true);
  
  pageNumber = signal<number>(1);
  pageSize = signal<number>(20);
  
  displayLocation = signal<string>('');

  currentFilterState: FilterState = {
    minPrice: 0,
    maxPrice: 10000000,
    propertyTypes: [],
    starRatings: [],
    minReviewScore: null,
    amenityIds: []
  };

  selectedSort: string = 'POPULAR';
  sortOptions = [
    { label: 'Được đề xuất', value: 'POPULAR' },
    { label: 'Giá thấp nhất', value: 'PRICE_ASC' },
    { label: 'Giá cao nhất', value: 'PRICE_DESC' },
    { label: 'Đánh giá cao nhất', value: 'RATING' },
    { label: 'Gần trung tâm nhất', value: 'NEAREST' }
  ];

  ngOnInit() {
    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe(params => {
      // Sync basic search params to StateService if they exist
      if (params['keyword'] || params['provinceId']) {
        this.displayLocation.set(params['keyword'] || 'Khu vực đã chọn');
      }

      // Read filters and pagination
      this.pageNumber.set(Number(params['pageNumber']) || 1);
      this.pageSize.set(Number(params['pageSize']) || 20);
      this.selectedSort = params['sortBy'] || 'POPULAR';
      
      this.currentFilterState = {
        minPrice: Number(params['minPrice']) || 0,
        maxPrice: Number(params['maxPrice']) || 10000000,
        propertyTypes: params['propertyTypes'] ? params['propertyTypes'].split(',') : [],
        starRatings: params['starRatings'] ? params['starRatings'].split(',').map(Number) : [],
        minReviewScore: params['minReviewScore'] ? Number(params['minReviewScore']) : null,
        amenityIds: params['amenityIds'] ? params['amenityIds'].split(',').map(Number) : []
      };

      this.fetchProperties(params);
    });
  }

  fetchProperties(queryParams: any) {
    this.isLoading.set(true);
    
    // Format request DTO
    const req = {
      ...queryParams,
      pageNumber: this.pageNumber(),
      pageSize: this.pageSize(),
      sortBy: this.selectedSort,
      propertyTypes: this.currentFilterState.propertyTypes,
      minPrice: this.currentFilterState.minPrice > 0 ? this.currentFilterState.minPrice : null,
      maxPrice: this.currentFilterState.maxPrice < 10000000 ? this.currentFilterState.maxPrice : null,
      starRatings: this.currentFilterState.starRatings
    };

    // Clean up empty arrays
    if (req.propertyTypes && req.propertyTypes.length === 0) delete req.propertyTypes;
    if (req.starRatings && req.starRatings.length === 0) delete req.starRatings;

    this.clientApi.searchHotels(req).subscribe({
      next: (res: any) => {
        this.properties.set(res.content || []);
        this.totalItems.set(res.totalElements || 0);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('Search error', err);
        this.properties.set([]);
        this.totalItems.set(0);
        this.isLoading.set(false);
      }
    });
  }

  onFiltersChanged(newFilters: FilterState) {
    this.updateRoute({
      minPrice: newFilters.minPrice > 0 ? newFilters.minPrice : null,
      maxPrice: newFilters.maxPrice < 10000000 ? newFilters.maxPrice : null,
      propertyTypes: newFilters.propertyTypes.length > 0 ? newFilters.propertyTypes.join(',') : null,
      starRatings: newFilters.starRatings.length > 0 ? newFilters.starRatings.join(',') : null,
      pageNumber: 1 // Reset to page 1 on filter change
    });
  }

  onSortChange() {
    this.updateRoute({ sortBy: this.selectedSort, pageNumber: 1 });
  }

  onPageChange(event: any) {
    const pageNumber = event.page + 1;
    const pageSize = event.rows;
    this.updateRoute({ pageNumber, pageSize });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  removePropertyType(type: string) {
    const types = this.currentFilterState.propertyTypes.filter(t => t !== type);
    this.updateRoute({ propertyTypes: types.length > 0 ? types.join(',') : null, pageNumber: 1 });
  }

  removeStarRatings() {
    this.updateRoute({ starRatings: null, pageNumber: 1 });
  }

  clearAllFilters() {
    this.updateRoute({
      minPrice: null,
      maxPrice: null,
      propertyTypes: null,
      starRatings: null,
      minReviewScore: null,
      amenityIds: null,
      pageNumber: 1
    });
  }

  private updateRoute(queryParams: any) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams,
      queryParamsHandling: 'merge'
    });
  }

  goToDetails(propertyId: number) {
    const s = this.stateService.state();
    this.router.navigate(['/properties', propertyId], {
      queryParams: {
        checkInDate: s.checkInDate ? this.formatDate(s.checkInDate) : null,
        checkOutDate: s.checkOutDate ? this.formatDate(s.checkOutDate) : null,
        adultCount: s.adultCount,
        childCount: s.childCount,
        roomCount: s.roomCount
      }
    });
  }
  
  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
