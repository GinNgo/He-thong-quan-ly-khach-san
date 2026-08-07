import { CommonModule } from '@angular/common';
<<<<<<< HEAD
import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
=======
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
>>>>>>> codex/ui-functional-audit-polish
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { SelectModule } from 'primeng/select';
import { PaginatorModule } from 'primeng/paginator';
import { SkeletonModule } from 'primeng/skeleton';
import { Subject, catchError, of, switchMap, takeUntil, tap } from 'rxjs';
<<<<<<< HEAD
import { ClientApiService, Hotel, PropertySearchParams } from '../../../../core/services/client-api.service';
=======

import { PublicI18nService } from '../../../../core/i18n/public-i18n.service';
import { ClientApiService, Hotel } from '../../../../core/services/client-api.service';
>>>>>>> codex/ui-functional-audit-polish
import { StickySearchBarComponent } from '../../../client/home/components/sticky-search-bar/sticky-search-bar.component';
import { HomeSearchStateService } from '../../../client/home/services/home-search-state.service';
import { PropertyResultCardComponent } from '../../components/property-result-card/property-result-card';
import { FilterState, SearchFilterSidebarComponent } from '../../components/search-filter-sidebar/search-filter-sidebar';
import {
  canonicalPaginationDisplayState,
  canonicalPriceDisplayState,
  canonicalPropertyTypes,
  canonicalReviewScore,
  canonicalStarRatings,
  propertySearchErrorState,
  propertySearchParamsFromRoute,
  validSearchStayDates,
  PRICE_FILTER_MAX,
  PRICE_FILTER_MIN,
  DEFAULT_PAGE_NUMBER,
  DEFAULT_PAGE_SIZE,
} from './property-search-query';

interface PageChangeEvent {
  page?: number;
  rows?: number;
}

interface PaginatorEvent { page?: number; rows?: number; }

@Component({
  selector: 'app-property-search-page',
  standalone: true,
  imports: [CommonModule, FormsModule, SelectModule, PaginatorModule, SkeletonModule, StickySearchBarComponent, SearchFilterSidebarComponent, PropertyResultCardComponent],
  template: `
    <main class="search-page">
      <app-sticky-search-bar [isVisible]="true" [embedded]="true"></app-sticky-search-bar>
      <div class="page-container">
        <header class="results-heading">
<<<<<<< HEAD
          <div><p class="eyebrow">Kết quả tìm kiếm</p><h1>{{ displayLocation() || 'Tất cả chỗ nghỉ' }}</h1>
            <p>{{ totalItems() }} chỗ nghỉ · {{ stateService.guestSummary() }} · {{ staySummary }}</p></div>
          <button #mobileFilterTrigger type="button" class="mobile-filter" data-mobile-filter-trigger
            aria-controls="mobile-property-filters" [attr.aria-expanded]="mobileFilterVisible" (click)="openMobileFilters()">
            <i class="pi pi-filter"></i> Bộ lọc <b *ngIf="activeFilterCount">{{ activeFilterCount }}</b>
=======
          <div>
            <p class="eyebrow">{{ i18n.text('PUBLIC.RESULTS.EYEBROW') }}</p>
            <h1>{{ displayLocation() || i18n.text('PUBLIC.RESULTS.ALL_STAYS') }}</h1>
            <p>{{ totalItems() }} · {{ guestSummary }} · {{ staySummary }}</p>
            <div *ngIf="isLandmarkSearch" class="landmark-context" role="status">
              <i class="pi pi-compass"></i>
              <span>{{ i18n.text('PUBLIC.RESULTS.LANDMARK_RADIUS', { radius: landmarkRadius }) }}</span>
              <button type="button" (click)="expandLandmarkRadius()" [disabled]="landmarkRadius >= 50">{{ i18n.text('PUBLIC.RESULTS.EXPAND') }}</button>
            </div>
          </div>
          <button type="button" class="mobile-filter" (click)="mobileFilterVisible = true">
            <i class="pi pi-filter"></i> {{ i18n.text('PUBLIC.RESULTS.FILTER') }} <b *ngIf="activeFilterCount">{{ activeFilterCount }}</b>
>>>>>>> codex/ui-functional-audit-polish
          </button>
        </header>

        <div class="content-grid">
          <div class="sidebar"><app-search-filter-sidebar [initialState]="currentFilterState" (filtersChanged)="onFiltersChanged($event)"></app-search-filter-sidebar></div>
          <section class="results" [attr.aria-busy]="isLoading()">
            <div class="result-tools">
              <div class="chips">
<<<<<<< HEAD
                <button *ngFor="let type of currentFilterState.propertyTypes" type="button"
                  [attr.data-filter-chip]="'propertyType:' + type"
                  [attr.aria-label]="'Remove property type filter ' + propertyTypeLabel(type)"
                  (click)="removePropertyType(type)">{{ propertyTypeLabel(type) }} <i class="pi pi-times" aria-hidden="true"></i></button>
                <button *ngFor="let star of currentFilterState.starRatings" type="button"
                  [attr.data-filter-chip]="'starRating:' + star" [attr.aria-label]="'Remove ' + star + ' star filter'"
                  (click)="removeStarRating(star)">{{ star }} sao <i class="pi pi-times" aria-hidden="true"></i></button>
                <button *ngIf="currentFilterState.minReviewScore !== null" type="button"
                  [attr.data-filter-chip]="'reviewScore:' + currentFilterState.minReviewScore"
                  [attr.aria-label]="'Bỏ bộ lọc điểm đánh giá từ ' + currentFilterState.minReviewScore"
                  (click)="removeReviewScore()">{{ currentFilterState.minReviewScore }}+ điểm <i class="pi pi-times" aria-hidden="true"></i></button>
                <button *ngIf="hasPriceFilter" type="button" data-filter-chip="price"
                  [attr.aria-label]="'Bỏ bộ lọc khoảng giá ' + priceChip" (click)="removePriceFilter()">
                  {{ priceChip }} <i class="pi pi-times" aria-hidden="true"></i></button>
                <button *ngIf="activeFilterCount" type="button" class="clear-chip" data-filter-clear
                  (click)="clearAllFilters()">Xóa tất cả</button>
              </div>
              <label class="sort"><span>Sắp xếp</span><p-select data-search-sort [options]="sortOptions" [(ngModel)]="selectedSort" optionLabel="label" optionValue="value" (onChange)="onSortChange()"></p-select></label>
=======
                <button *ngFor="let type of currentFilterState.propertyTypes" type="button" (click)="removePropertyType(type)">{{ propertyTypeLabel(type) }} <i class="pi pi-times"></i></button>
                <button *ngIf="currentFilterState.starRatings.length" type="button" (click)="removeStarRatings()">{{ currentFilterState.starRatings.join(', ') }} sao <i class="pi pi-times"></i></button>
                <button *ngIf="currentFilterState.minReviewScore" type="button" (click)="removeReviewScore()">{{ currentFilterState.minReviewScore }}+ <span aria-hidden="true">★</span> <i class="pi pi-times"></i></button>
                <button *ngIf="hasPriceFilter" type="button" (click)="removePriceFilter()">{{ priceChip }} <i class="pi pi-times"></i></button>
                <button *ngIf="activeFilterCount" type="button" class="clear-chip" (click)="clearAllFilters()">{{ i18n.text('PUBLIC.RESULTS.CLEAR_ALL') }}</button>
              </div>
              <label class="sort"><span>{{ i18n.text('PUBLIC.RESULTS.SORT') }}</span><p-select [options]="sortOptions()" [(ngModel)]="selectedSort" optionLabel="label" optionValue="value" (onChange)="onSortChange()"></p-select></label>
>>>>>>> codex/ui-functional-audit-polish
            </div>

            <div *ngIf="isLoading()" class="skeleton-list">
              <div *ngFor="let _ of [1,2,3,4]" class="skeleton-card"><p-skeleton width="245px" height="224px"></p-skeleton><div><p-skeleton width="65%" height="24px"></p-skeleton><p-skeleton width="90%" height="16px"></p-skeleton><p-skeleton width="55%" height="42px"></p-skeleton></div></div>
            </div>

<<<<<<< HEAD
            <div *ngIf="!isLoading() && errorMessage()" class="state-panel error-state" role="alert"
              aria-live="assertive" data-search-api-error [attr.data-error-code]="errorCode()">
              <i class="pi pi-exclamation-circle"></i><h2>{{ errorTitle() }}</h2><p>{{ errorMessage() }}</p>
              <button *ngIf="errorRetryable(); else editSearchAction" type="button" (click)="retry()">Thử lại</button>
              <ng-template #editSearchAction><button type="button" (click)="editSearch()">Chỉnh sửa tìm kiếm</button></ng-template>
=======
            <div *ngIf="!isLoading() && errorMessage()" class="state-panel error-state">
              <i class="pi pi-exclamation-circle"></i><h2>{{ i18n.text('PUBLIC.RESULTS.LOAD_ERROR_TITLE') }}</h2><p>{{ errorMessage() }}</p><button type="button" (click)="retry()">{{ i18n.text('PUBLIC.RESULTS.RETRY') }}</button>
>>>>>>> codex/ui-functional-audit-polish
            </div>

            <ng-container *ngIf="!isLoading() && !errorMessage() && properties().length">
              <app-property-result-card *ngFor="let property of properties(); trackBy: trackProperty" [property]="property" (viewDetails)="goToDetails($event)"></app-property-result-card>
              <div class="pagination" data-search-pagination [attr.data-page-number]="pageNumber()"><p-paginator [first]="(pageNumber()-1)*pageSize()" [rows]="pageSize()" [totalRecords]="totalItems()" [rowsPerPageOptions]="[10,20,50]" (onPageChange)="onPageChange($event)"></p-paginator></div>
            </ng-container>

<<<<<<< HEAD
            <div *ngIf="!isLoading() && !errorMessage() && !properties().length" class="state-panel" data-search-empty>
              <i class="pi pi-search"></i><h2>Không tìm thấy chỗ nghỉ phù hợp</h2><p>Hãy thử bỏ bớt bộ lọc, đổi ngày hoặc tìm trong toàn tỉnh.</p><button type="button" (click)="clearAllFilters()">Xóa bộ lọc</button>
=======
            <div *ngIf="!isLoading() && !errorMessage() && !properties().length" class="state-panel">
              <i class="pi pi-search"></i><h2>{{ i18n.text('PUBLIC.RESULTS.EMPTY_TITLE') }}</h2>
              <p *ngIf="isLandmarkSearch; else genericEmpty">{{ i18n.text('PUBLIC.RESULTS.LANDMARK_EMPTY') }}</p>
              <ng-template #genericEmpty><p>{{ i18n.text('PUBLIC.RESULTS.GENERIC_EMPTY') }}</p></ng-template>
              <div class="empty-actions">
                <button *ngIf="isLandmarkSearch" type="button" (click)="expandLandmarkRadius()" [disabled]="landmarkRadius >= 50">{{ i18n.text('PUBLIC.RESULTS.EXPAND_RADIUS') }}</button>
                <button *ngIf="isLandmarkSearch" type="button" class="secondary-action" (click)="searchLandmarkProvince()">{{ i18n.text('PUBLIC.RESULTS.SEARCH_PROVINCE') }}</button>
                <button *ngIf="!isLandmarkSearch" type="button" (click)="clearAllFilters()">{{ i18n.text('PUBLIC.RESULTS.CLEAR_FILTERS') }}</button>
              </div>
>>>>>>> codex/ui-functional-audit-polish
            </div>
          </section>
        </div>
      </div>

<<<<<<< HEAD
      <div *ngIf="mobileFilterVisible" #mobileFilterDialog id="mobile-property-filters" class="filter-drawer"
        role="dialog" aria-modal="true" aria-label="Bộ lọc" (keydown)="onMobileFilterKeydown($event)">
        <header><h2>Bộ lọc</h2><button #mobileFilterClose type="button" data-mobile-filter-close
          (click)="closeMobileFilters()" aria-label="Đóng"><i class="pi pi-times"></i></button></header>
        <app-search-filter-sidebar [initialState]="currentFilterState"
          (filtersChanged)="applyMobileFilters($event)"></app-search-filter-sidebar>
=======
      <div *ngIf="mobileFilterVisible" class="filter-drawer" role="dialog" aria-modal="true" [attr.aria-label]="i18n.text('PUBLIC.RESULTS.FILTER_DIALOG')">
        <header><h2>{{ i18n.text('PUBLIC.RESULTS.FILTER') }}</h2><button type="button" (click)="mobileFilterVisible=false" [attr.aria-label]="i18n.text('PUBLIC.RESULTS.CLOSE')"><i class="pi pi-times"></i></button></header>
        <app-search-filter-sidebar [initialState]="currentFilterState" (filtersChanged)="onFiltersChanged($event); mobileFilterVisible=false"></app-search-filter-sidebar>
>>>>>>> codex/ui-functional-audit-polish
      </div>
    </main>
  `,
  styles: [`
    .search-page{min-height:100vh;background:#f5f7fa;color:#172033}.page-container{max-width:1240px;margin:auto;padding:28px 20px 60px}.results-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:22px}.eyebrow{text-transform:uppercase;font-size:11px;font-weight:800;color:#1769e0;margin:0 0 5px}.results-heading h1{font-size:28px;line-height:1.2;margin:0;color:#12213a}.results-heading p:last-child{font-size:13px;color:#64748b;margin:8px 0 0}.content-grid{display:grid;grid-template-columns:274px minmax(0,1fr);gap:22px;align-items:start}.sidebar{position:sticky;top:96px}.result-tools{min-height:52px;display:flex;align-items:flex-start;justify-content:space-between;gap:14px;margin-bottom:14px}.chips{display:flex;align-items:center;gap:7px;flex-wrap:wrap}.chips button{border:1px solid #bcd2ef;background:#eef5ff;color:#164f96;border-radius:18px;padding:7px 10px;font-size:12px;font-weight:700;cursor:pointer}.chips .clear-chip{background:transparent;border-color:transparent;color:#1769e0}.sort{display:flex;align-items:center;gap:9px;font-size:12px;color:#64748b;white-space:nowrap}.sort ::ng-deep .p-select{min-width:190px;border-radius:6px}.skeleton-card{height:226px;display:grid;grid-template-columns:245px 1fr;gap:20px;background:#fff;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;margin-bottom:16px}.skeleton-card>div{padding:24px;display:grid;align-content:start;gap:20px}.pagination{background:#fff;border:1px solid #e2e8f0;border-radius:8px;padding:4px}.state-panel{min-height:360px;background:#fff;border:1px solid #e2e8f0;border-radius:8px;display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:30px}.state-panel>i{font-size:34px;color:#7592b6}.state-panel h2{font-size:21px;margin:15px 0 5px}.state-panel p{color:#64748b;margin:0 0 20px}.state-panel button{border:0;border-radius:6px;background:#1769e0;color:#fff;padding:11px 18px;font-weight:700}.error-state>i{color:#c2413a}.mobile-filter{display:none}.filter-drawer{display:none}
    .landmark-context{display:flex;align-items:center;gap:8px;margin-top:10px;color:#175cd3;font-size:12px;font-weight:700}.landmark-context i{font-size:14px}.landmark-context button{border:0;background:transparent;color:#1769e0;font-size:12px;font-weight:800;cursor:pointer}.landmark-context button:disabled{color:#98a2b3;cursor:not-allowed}.empty-actions{display:flex;gap:8px;flex-wrap:wrap;justify-content:center}.empty-actions .secondary-action{background:#fff;color:#1769e0;border:1px solid #1769e0}.empty-actions button:disabled{background:#e2e8f0;color:#94a3b8;cursor:not-allowed}
    @media(max-width:900px){.page-container{padding:20px 14px}.content-grid{grid-template-columns:1fr}.sidebar{display:none}.mobile-filter{display:flex;align-items:center;gap:7px;border:1px solid #cbd5e1;background:#fff;border-radius:6px;padding:10px 12px;font-weight:700}.mobile-filter b{background:#1769e0;color:#fff;border-radius:10px;padding:2px 6px}.result-tools{flex-direction:column-reverse}.sort{width:100%;justify-content:space-between}.sort ::ng-deep .p-select{flex:1}.filter-drawer{display:block;position:fixed;inset:0;z-index:110;background:#f7f9fc;padding:16px;overflow:auto}.filter-drawer header{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px}.filter-drawer header h2{margin:0}.filter-drawer header button{border:0;width:40px;height:40px;border-radius:50%}.filter-drawer app-search-filter-sidebar{display:block;max-width:520px;margin:auto}}
    @media(max-width:600px){.results-heading{align-items:flex-start}.results-heading h1{font-size:22px}.skeleton-card{grid-template-columns:1fr;height:auto}.skeleton-card p-skeleton:first-child{display:none}}
  `]
})
export class PropertySearchPageComponent implements OnInit, OnDestroy {
<<<<<<< HEAD
  private readonly route=inject(ActivatedRoute); private readonly router=inject(Router); private readonly api=inject(ClientApiService);
  readonly stateService=inject(HomeSearchStateService); private readonly destroy$=new Subject<void>(); private lastParams:Params={};
  private pendingPageRecovery: string | null = null;
  private mobileFilterFocusTimer: number | undefined;
  @ViewChild('mobileFilterTrigger') private mobileFilterTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('mobileFilterDialog') private mobileFilterDialog?: ElementRef<HTMLElement>;
  @ViewChild('mobileFilterClose') private mobileFilterClose?: ElementRef<HTMLButtonElement>;
  properties=signal<Hotel[]>([]); totalItems=signal(0); isLoading=signal(true); errorMessage=signal(''); errorTitle=signal('Không thể tải kết quả'); errorCode=signal(''); errorRetryable=signal(true); pageNumber=signal(DEFAULT_PAGE_NUMBER); pageSize=signal(DEFAULT_PAGE_SIZE); displayLocation=signal(''); mobileFilterVisible=false;
  currentFilterState:FilterState={minPrice:PRICE_FILTER_MIN,maxPrice:PRICE_FILTER_MAX,propertyTypes:[],starRatings:[],minReviewScore:null,amenityIds:[]};
  selectedSort='POPULAR'; readonly sortOptions=[{label:'Được đề xuất',value:'POPULAR'},{label:'Giá thấp nhất',value:'PRICE_ASC'},{label:'Giá cao nhất',value:'PRICE_DESC'},{label:'Đánh giá cao',value:'RATING'},{label:'Gần nhất',value:'NEAREST'}];

  ngOnInit():void{this.route.queryParams.pipe(takeUntil(this.destroy$),tap(params=>{this.lastParams=params;this.syncFromUrl(params);this.isLoading.set(true);this.errorMessage.set('');this.errorCode.set('');}),switchMap(params=>this.api.searchHotels(this.request(params)).pipe(catchError(error=>{const state=propertySearchErrorState(error);this.errorTitle.set(state.title);this.errorMessage.set(state.message);this.errorCode.set(state.code);this.errorRetryable.set(state.retryable);return of({content:[],totalElements:0,totalPages:0,number:0,size:this.pageSize()});})))).subscribe(res=>{const content=res.content||[];const recoveryKey=`${this.pageNumber()}:${res.totalPages}`;if(!content.length&&res.totalPages>0&&this.pageNumber()>res.totalPages){if(this.pendingPageRecovery!==recoveryKey){this.pendingPageRecovery=recoveryKey;this.updateRoute({pageNumber:res.totalPages});}return;}this.pendingPageRecovery=null;this.properties.set(content);this.totalItems.set(res.totalElements||0);this.isLoading.set(false);});}
  get staySummary():string{const routeDates=validSearchStayDates(this.lastParams);if(routeDates)return `${this.formatDateDisplay(routeDates.checkIn)} - ${this.formatDateDisplay(routeDates.checkOut)}`;if(this.lastParams['checkInDate']||this.lastParams['checkOutDate'])return 'Ngày lưu trú không hợp lệ';const s=this.stateService.state();return `${this.formatDateDisplay(s.checkInDate)} - ${this.formatDateDisplay(s.checkOutDate)}`;}
  get activeFilterCount():number{return this.currentFilterState.propertyTypes.length+this.currentFilterState.starRatings.length+(this.currentFilterState.minReviewScore!==null?1:0)+(this.hasPriceFilter?1:0);}
  get hasPriceFilter():boolean{return this.currentFilterState.minPrice>PRICE_FILTER_MIN||this.currentFilterState.maxPrice<PRICE_FILTER_MAX;}
  get priceChip():string{return `${this.vnd(this.currentFilterState.minPrice)} - ${this.currentFilterState.maxPrice>=PRICE_FILTER_MAX?'10.000.000 ₫+':this.vnd(this.currentFilterState.maxPrice)}`;}
  onFiltersChanged(f:FilterState):void{
    const priceState=canonicalPriceDisplayState(f.minPrice,f.maxPrice);
    const propertyTypes=canonicalPropertyTypes(f.propertyTypes);
    const starRatings=canonicalStarRatings(f.starRatings);
    const minReviewScore=canonicalReviewScore(f.minReviewScore);
    this.updateRoute({
      minPrice:priceState.minPrice>PRICE_FILTER_MIN?priceState.minPrice:null,
      maxPrice:priceState.maxPrice<PRICE_FILTER_MAX?priceState.maxPrice:null,
      propertyTypes:propertyTypes.length?propertyTypes.join(','):null,
      starRatings:starRatings.length?starRatings.join(','):null,
      minReviewScore,
      pageNumber:1
    });
  }
  onSortChange():void{this.updateRoute({sortBy:this.selectedSort,pageNumber:1});}
  onPageChange(e:PageChangeEvent):void{const pagination=canonicalPaginationDisplayState((e.page??0)+1,e.rows??this.pageSize());this.updateRoute(pagination);window.scrollTo({top:0,behavior:'smooth'});}
  removePropertyType(t:string):void{const v=this.currentFilterState.propertyTypes.filter(x=>x!==t);this.updateRoute({propertyTypes:v.length?v.join(','):null,pageNumber:1});}
  removeStarRating(star:number):void{const v=this.currentFilterState.starRatings.filter(value=>value!==star);this.updateRoute({starRatings:v.length?v.join(','):null,pageNumber:1});}
  removeReviewScore():void{this.updateRoute({minReviewScore:null,pageNumber:1});} removePriceFilter():void{this.updateRoute({minPrice:null,maxPrice:null,pageNumber:1});}
  clearAllFilters():void{this.updateRoute({minPrice:null,maxPrice:null,propertyTypes:null,starRatings:null,minReviewScore:null,amenityIds:null,pageNumber:1});} retry():void{this.updateRoute({_retry:Date.now()});} editSearch():void{this.router.navigate(['/']);}
  openMobileFilters():void{
    this.mobileFilterVisible=true;
    if(this.mobileFilterFocusTimer!==undefined)globalThis.clearTimeout(this.mobileFilterFocusTimer);
    this.mobileFilterFocusTimer=globalThis.setTimeout(()=>{
      this.mobileFilterFocusTimer=undefined;
      this.mobileFilterClose?.nativeElement.focus();
    },0);
  }
  closeMobileFilters():void{
    if(this.mobileFilterFocusTimer!==undefined){globalThis.clearTimeout(this.mobileFilterFocusTimer);this.mobileFilterFocusTimer=undefined;}
    this.mobileFilterVisible=false;
    this.mobileFilterTrigger?.nativeElement.focus();
  }
  applyMobileFilters(filters:FilterState):void{
    this.onFiltersChanged(filters);
    this.closeMobileFilters();
  }
  onMobileFilterKeydown(event:KeyboardEvent):void{
    if(event.key==='Escape'){
      event.preventDefault();
      this.closeMobileFilters();
      return;
    }
    if(event.key!=='Tab')return;
    const focusable=this.mobileFilterDialog?.nativeElement.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );
    if(!focusable?.length)return;
    const first=focusable[0];
    const last=focusable[focusable.length-1];
    if(event.shiftKey&&document.activeElement===first){event.preventDefault();last.focus();}
    else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
  }
  goToDetails(id:number):void{this.router.navigate(['/hotel',id],{queryParams:{...this.stateService.bookingQueryParams()},fragment:'rooms'});} trackProperty(_:number,p:Hotel):number{return p.id;}
  propertyTypeLabel(t:string):string{return ({HOTEL:'Khách sạn',RESORT:'Khu nghỉ dưỡng',APARTMENT:'Căn hộ',VILLA:'Biệt thự',HOMESTAY:'Homestay',MOTEL:'Nhà nghỉ',GUEST_HOUSE:'Nhà khách',HOSTEL:'Hostel'} as Record<string,string>)[t]||t;}
  private syncFromUrl(p:Params):void{
    const routeState=propertySearchParamsFromRoute(p);
    const name=p['displayLocation']||routeState.keyword||'Tất cả chỗ nghỉ';
    this.displayLocation.set(name);
    this.stateService.updateLocation(routeState.keyword||'',name,routeState.provinceId??null,routeState.wardId??null,
      routeState.landmarkId??null,routeState.latitude??null,routeState.longitude??null,routeState.radiusKm??null);
    const routeDates=validSearchStayDates(p);
    if(routeDates)this.stateService.updateDates(routeDates.checkIn,routeDates.checkOut);
    if(routeState.adultCount||routeState.roomCount)this.stateService.updateGuests(routeState.adultCount||1,routeState.childCount||0,routeState.roomCount||1);
    const pagination=canonicalPaginationDisplayState(routeState.pageNumber,routeState.pageSize);
    this.pageNumber.set(pagination.pageNumber);
    this.pageSize.set(pagination.pageSize);
    this.selectedSort=routeState.sortBy||'POPULAR';
    this.currentFilterState={
      ...canonicalPriceDisplayState(routeState.minPrice,routeState.maxPrice),
      propertyTypes:routeState.propertyTypes||[],
      starRatings:routeState.starRatings||[],
      minReviewScore:routeState.minReviewScore??null,
      amenityIds:routeState.amenityIds||[]
    };
  }
  private request(p:Params):PropertySearchParams{const r=propertySearchParamsFromRoute(p);return{...r,propertyTypes:this.currentFilterState.propertyTypes.length?this.currentFilterState.propertyTypes:undefined,starRatings:this.currentFilterState.starRatings.length?this.currentFilterState.starRatings:undefined,minReviewScore:this.currentFilterState.minReviewScore??undefined};}
  private updateRoute(q:Params):void{this.router.navigate([],{relativeTo:this.route,queryParams:q,queryParamsHandling:'merge'});} private vnd(v:number):string{return `${new Intl.NumberFormat('vi-VN').format(v)} ₫`;} private formatDateDisplay(v:Date|null):string{return v?new Intl.DateTimeFormat('vi-VN',{day:'2-digit',month:'2-digit',year:'numeric'}).format(v):'Chưa chọn';}
  ngOnDestroy():void{if(this.mobileFilterFocusTimer!==undefined)globalThis.clearTimeout(this.mobileFilterFocusTimer);this.destroy$.next();this.destroy$.complete();}
=======
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ClientApiService);
  readonly i18n = inject(PublicI18nService);
  readonly stateService = inject(HomeSearchStateService);
  private readonly destroy$ = new Subject<void>();
  private lastParams: Params = {};

  properties = signal<Hotel[]>([]);
  totalItems = signal(0);
  isLoading = signal(true);
  errorMessage = signal('');
  pageNumber = signal(1);
  pageSize = signal(20);
  displayLocation = signal('');
  mobileFilterVisible = false;
  currentFilterState: FilterState = { minPrice: 0, maxPrice: 10000000, propertyTypes: [], starRatings: [], minReviewScore: null, amenityIds: [] };
  selectedSort = 'POPULAR';

  readonly sortOptions = computed<Array<{ label: string; value: string }>>(() => [
      { label: this.i18n.text('PUBLIC.RESULTS.SORT_POPULAR'), value: 'POPULAR' },
      { label: this.i18n.text('PUBLIC.RESULTS.SORT_PRICE_LOW'), value: 'PRICE_ASC' },
      { label: this.i18n.text('PUBLIC.RESULTS.SORT_PRICE_HIGH'), value: 'PRICE_DESC' },
      { label: this.i18n.text('PUBLIC.RESULTS.SORT_RATING'), value: 'RATING' },
      { label: this.i18n.text('PUBLIC.RESULTS.SORT_NEAREST'), value: 'NEAREST' }
  ]);

  get isLandmarkSearch(): boolean { return Boolean(this.lastParams['landmarkId']); }
  get landmarkRadius(): number { return Number(this.lastParams['radiusKm']) || 5; }
  get guestSummary(): string {
    const state = this.stateService.state();
    const children = state.childCount ? `, ${this.i18n.count('PUBLIC.GUESTS.CHILD_COUNT', state.childCount)}` : '';
    return `${this.i18n.count('PUBLIC.GUESTS.ADULT_COUNT', state.adultCount)}${children}, ${this.i18n.count('PUBLIC.GUESTS.ROOM_COUNT', state.roomCount)}`;
  }
  get staySummary(): string { const state = this.stateService.state(); return `${this.formatDateDisplay(state.checkInDate)} - ${this.formatDateDisplay(state.checkOutDate)}`; }
  get activeFilterCount(): number { return this.currentFilterState.propertyTypes.length + this.currentFilterState.starRatings.length + (this.currentFilterState.minReviewScore ? 1 : 0) + (this.hasPriceFilter ? 1 : 0); }
  get hasPriceFilter(): boolean { return this.currentFilterState.minPrice > 0 || this.currentFilterState.maxPrice < 10000000; }
  get priceChip(): string { return `${this.vnd(this.currentFilterState.minPrice)} - ${this.currentFilterState.maxPrice >= 10000000 ? '10,000,000 VND+' : this.vnd(this.currentFilterState.maxPrice)}`; }

  ngOnInit(): void {
    this.route.queryParams.pipe(
      takeUntil(this.destroy$),
      tap(params => { this.lastParams = params; this.syncFromUrl(params); this.isLoading.set(true); this.errorMessage.set(''); }),
      switchMap(params => this.api.searchHotels(this.request(params)).pipe(catchError(() => {
        this.errorMessage.set(this.i18n.text('PUBLIC.SEARCH.FALLBACK_ERROR'));
        return of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: this.pageSize() });
      })))
    ).subscribe(res => { this.properties.set(res.content || []); this.totalItems.set(res.totalElements || 0); this.isLoading.set(false); });
  }

  onFiltersChanged(filters: FilterState): void { this.updateRoute({ minPrice: filters.minPrice > 0 ? filters.minPrice : null, maxPrice: filters.maxPrice < 10000000 ? filters.maxPrice : null, propertyTypes: filters.propertyTypes.length ? filters.propertyTypes.join(',') : null, starRatings: filters.starRatings.length ? filters.starRatings.join(',') : null, minReviewScore: filters.minReviewScore, pageNumber: 1 }); }
  onSortChange(): void { this.updateRoute({ sortBy: this.selectedSort, pageNumber: 1 }); }
  onPageChange(event: PaginatorEvent): void { this.updateRoute({ pageNumber: (event.page ?? 0) + 1, pageSize: event.rows ?? this.pageSize() }); window.scrollTo({ top: 0, behavior: 'smooth' }); }
  removePropertyType(type: string): void { const values = this.currentFilterState.propertyTypes.filter(item => item !== type); this.updateRoute({ propertyTypes: values.length ? values.join(',') : null, pageNumber: 1 }); }
  removeStarRatings(): void { this.updateRoute({ starRatings: null, pageNumber: 1 }); }
  removeReviewScore(): void { this.updateRoute({ minReviewScore: null, pageNumber: 1 }); }
  removePriceFilter(): void { this.updateRoute({ minPrice: null, maxPrice: null, pageNumber: 1 }); }
  clearAllFilters(): void { this.updateRoute({ minPrice: null, maxPrice: null, propertyTypes: null, starRatings: null, minReviewScore: null, amenityIds: null, pageNumber: 1 }); }
  retry(): void { this.updateRoute({ _retry: Date.now() }); }
  goToDetails(id: number): void { this.router.navigate(['/hotel', id], { queryParams: { ...this.stateService.bookingQueryParams() }, fragment: 'rooms' }); }
  trackProperty(_: number, property: Hotel): number { return property.id; }
  propertyTypeLabel(type: string): string { const key = ({ HOTEL: 'TYPE_HOTEL', RESORT: 'TYPE_RESORT', APARTMENT: 'TYPE_APARTMENT', VILLA: 'TYPE_VILLA', HOMESTAY: 'TYPE_HOMESTAY', MOTEL: 'TYPE_MOTEL', GUEST_HOUSE: 'TYPE_GUEST_HOUSE', HOSTEL: 'TYPE_HOSTEL' } as Record<string, string>)[type]; return key ? this.i18n.text(`PUBLIC.HOME_CARDS.${key}`) : type; }

  private syncFromUrl(params: Params): void {
    const name = params['displayLocation'] || params['keyword'] || this.i18n.text('PUBLIC.RESULTS.ALL_STAYS');
    const landmarkId = params['landmarkId'] ? Number(params['landmarkId']) : null;
    const provinceId = params['provinceId'] ? Number(params['provinceId']) : null;
    const wardId = params['wardId'] ? Number(params['wardId']) : null;
    this.displayLocation.set(name);
    this.stateService.restoreLocation({ keyword: params['keyword'] || '', displayName: name, selectedSuggestionType: landmarkId ? 'LANDMARK' : wardId ? 'WARD' : provinceId ? 'PROVINCE' : null, provinceId, wardId, landmarkId, radiusKm: params['radiusKm'] ? Number(params['radiusKm']) : null, latitude: params['latitude'] ? Number(params['latitude']) : null, longitude: params['longitude'] ? Number(params['longitude']) : null });
    if (params['checkInDate']) this.stateService.updateDates(new Date(`${params['checkInDate']}T00:00:00`), params['checkOutDate'] ? new Date(`${params['checkOutDate']}T00:00:00`) : null);
    if (params['adultCount'] || params['roomCount']) this.stateService.updateGuests(Number(params['adultCount']) || 1, Number(params['childCount']) || 0, Number(params['roomCount']) || 1);
    this.pageNumber.set(Number(params['pageNumber']) || 1);
    this.pageSize.set(Number(params['pageSize']) || 20);
    this.selectedSort = params['sortBy'] || (landmarkId ? 'NEAREST' : 'POPULAR');
    this.currentFilterState = { minPrice: Number(params['minPrice']) || 0, maxPrice: params['maxPrice'] ? Number(params['maxPrice']) : 10000000, propertyTypes: this.list(params['propertyTypes']), starRatings: this.list(params['starRatings']).map(Number), minReviewScore: params['minReviewScore'] ? Number(params['minReviewScore']) : null, amenityIds: [] };
  }

  private request(params: Params): Params {
    const request: Params = { ...params, pageNumber: this.pageNumber(), pageSize: this.pageSize(), sortBy: this.selectedSort, propertyTypes: this.currentFilterState.propertyTypes, starRatings: this.currentFilterState.starRatings, minReviewScore: this.currentFilterState.minReviewScore, minPrice: this.currentFilterState.minPrice || null, maxPrice: this.currentFilterState.maxPrice < 10000000 ? this.currentFilterState.maxPrice : null };
    delete request['_retry'];
    if (!(request['propertyTypes'] as string[]).length) delete request['propertyTypes'];
    if (!(request['starRatings'] as number[]).length) delete request['starRatings'];
    return request;
  }

  expandLandmarkRadius(): void { const current = this.landmarkRadius; const next = current < 10 ? 10 : current < 25 ? 25 : current < 50 ? 50 : 50; if (next > current) this.updateRoute({ radiusKm: next, pageNumber: 1 }); }
  searchLandmarkProvince(): void { this.updateRoute({ landmarkId: null, radiusKm: null, latitude: null, longitude: null, displayLocation: null, sortBy: 'POPULAR', pageNumber: 1 }); }
  private updateRoute(queryParams: Params): void { this.router.navigate([], { relativeTo: this.route, queryParams, queryParamsHandling: 'merge' }); }
  private list(value: unknown): string[] { return value ? String(value).split(',').filter(Boolean) : []; }
  private vnd(value: number): string { return `${new Intl.NumberFormat(this.i18n.dateLocale()).format(value)} ${this.i18n.dateLocale() === 'en-US' ? 'VND' : '₫'}`; }
  private formatDateDisplay(value: Date | null): string { return value ? new Intl.DateTimeFormat(this.i18n.dateLocale(), { day: '2-digit', month: '2-digit', year: 'numeric' }).format(value) : this.i18n.text('PUBLIC.DATES.NOT_SELECTED'); }
  ngOnDestroy(): void { this.destroy$.next(); this.destroy$.complete(); }
>>>>>>> codex/ui-functional-audit-polish
}
