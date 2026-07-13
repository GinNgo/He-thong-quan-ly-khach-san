import { Component, OnInit, inject, OnDestroy } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, FormsModule, Validators } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { SliderModule } from 'primeng/slider';
import { SelectModule } from 'primeng/select';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, takeUntil } from 'rxjs/operators';
import { ClientApiService, Hotel, RoomType } from '../../../core/services/client-api.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface HotelSearchResult extends Hotel {
  roomTypes: RoomType[];
  minPrice: number | null;
  minTotalPrice: number | null;
  availableRoomCount: number;
  nights: number | null;
  maxGuests: number;
  roomTypeSummary: string;
}

@Component({
  selector: 'app-room-search',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterModule, SliderModule, SelectModule, AutoCompleteModule],
  templateUrl: './room-search.component.html',
  styleUrls: ['./room-search.component.css'],
  providers: [DatePipe]
})
export class RoomSearchComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clientApi = inject(ClientApiService);
  private fb = inject(FormBuilder);
  private http = inject(HttpClient);
  private datePipe = inject(DatePipe);

  searchForm!: FormGroup;
  private destroy$ = new Subject<void>();

  locations: any[] = [];
  filteredLocations: any[] = [];
  selectedLocation: any;
  
  priceRange: number[] = [0, 5000000];
  maxPriceLimit = 5000000;
  searchError = '';
  sortOptions = [
    { label: 'Gần tôi nhất', value: 'NEAREST' },
    { label: 'Đề xuất trước', value: 'POPULAR' },
    { label: 'Giá thấp nhất', value: 'PRICE_ASC' },
    { label: 'Giá cao nhất', value: 'PRICE_DESC' },
    { label: 'Đánh giá cao', value: 'RATING' },
  ];

  roomTypesFilter = [
    { label: 'Standard', value: 'standard', checked: false },
    { label: 'Deluxe', value: 'deluxe', checked: false },
    { label: 'Suite', value: 'suite', checked: false },
    { label: 'VIP', value: 'vip', checked: false },
  ];

  allHotels: HotelSearchResult[] = [];
  hotels: HotelSearchResult[] = [];
  isLoading = false;

  ngOnInit(): void {
    const today = new Date();
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);

    this.searchForm = this.fb.group({
      keyword: [''],
      provinceId: [null],
      wardId: [null],
      checkInDate: [this.formatDate(today), Validators.required],
      checkOutDate: [this.formatDate(tomorrow), Validators.required],
      adultCount: [2, [Validators.required, Validators.min(1)]],
      childCount: [0, Validators.min(0)],
      roomCount: [1, [Validators.required, Validators.min(1)]],
      latitude: [null],
      longitude: [null],
      radiusKm: [null],
      sortBy: ['POPULAR'],
      pageNumber: [1],
      pageSize: [20]
    });

    this.searchForm.get('checkInDate')?.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(val => {
      const checkIn = new Date(val);
      const checkOut = new Date(this.searchForm.value.checkOutDate);
      if (checkOut <= checkIn) {
        checkIn.setDate(checkIn.getDate() + 1);
        this.searchForm.patchValue({ checkOutDate: this.formatDate(checkIn) });
      }
    });

    this.route.queryParams.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      if (Object.keys(params).length > 0) {
        this.searchForm.patchValue({
          keyword: params['keyword'] || '',
          provinceId: params['provinceId'] ? Number(params['provinceId']) : null,
          wardId: params['wardId'] ? Number(params['wardId']) : null,
          checkInDate: params['checkInDate'] || this.formatDate(today),
          checkOutDate: params['checkOutDate'] || this.formatDate(tomorrow),
          adultCount: Number(params['adultCount']) || 2,
          childCount: Number(params['childCount']) || 0,
          roomCount: Number(params['roomCount']) || 1,
          latitude: params['latitude'] ? Number(params['latitude']) : null,
          longitude: params['longitude'] ? Number(params['longitude']) : null,
          sortBy: params['sortBy'] || 'POPULAR'
        });
        
        if (params['keyword']) {
           this.selectedLocation = { nameVi: params['keyword'] };
        }
        
        this.searchHotels();
      }
    });
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private formatDate(date: Date): string {
    return this.datePipe.transform(date, 'yyyy-MM-dd') || '';
  }

  filterLocation(event: any) {
    const query = event.query;
    if (!query || query.length < 2) return;
    
    this.http.get<any>(`${environment.apiUrl}/public/locations/search?keyword=${query}`).subscribe(res => {
      this.filteredLocations = res.content || res;
    });
  }

  onLocationSelect(event: any) {
    const loc = event.value;
    this.searchForm.patchValue({
      keyword: loc.nameVi,
      provinceId: loc.locationType === 'PROVINCE' ? loc.id : (loc.locationType === 'WARD' ? loc.parent?.id : null),
      wardId: loc.locationType === 'WARD' ? loc.id : null
    });
  }

  getUserLocation() {
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          this.searchForm.patchValue({
            latitude: position.coords.latitude,
            longitude: position.coords.longitude,
            radiusKm: 20 // default 20km
          });
          // search again
          this.search();
        },
        (error) => {
          console.error("Error getting location", error);
          alert("Không thể lấy vị trí hiện tại. Vui lòng cho phép trình duyệt truy cập vị trí.");
        }
      );
    } else {
      alert("Trình duyệt không hỗ trợ Geolocation.");
    }
  }

  searchHotels() {
    this.searchError = '';
    if (this.searchForm.invalid) {
      this.searchError = 'Vui lòng kiểm tra lại thông tin tìm kiếm.';
      return;
    }

    const checkIn = new Date(this.searchForm.value.checkInDate);
    const checkOut = new Date(this.searchForm.value.checkOutDate);
    if (checkOut <= checkIn) {
      this.searchError = 'Ngày trả phòng phải sau ngày nhận phòng.';
      return;
    }

    this.isLoading = true;
    this.clientApi.searchHotels(this.searchForm.value).subscribe({
      next: (response: any) => {
        // response.content holds the hotels
        const hotels = response.content || [];
        this.loadRoomTypeSummaries(hotels);
      },
      error: (err) => {
        console.error('Error fetching hotels:', err);
        this.allHotels = [];
        this.hotels = [];
        this.isLoading = false;
        this.searchError = 'Không thể tải danh sách khách sạn. Vui lòng thử lại sau.';
      },
    });
  }

  search() {
    // Update URL, triggering queryParams subscription
    const val = this.searchForm.value;
    // Save to local storage for "recent searches"
    let history = JSON.parse(localStorage.getItem('recentSearches') || '[]');
    history.unshift({ ...val, displayLocation: this.selectedLocation?.nameVi, createdAt: new Date() });
    localStorage.setItem('recentSearches', JSON.stringify(history.slice(0, 5)));

    this.router.navigate(['/search'], {
      queryParams: {
        keyword: val.keyword || null,
        provinceId: val.provinceId || null,
        wardId: val.wardId || null,
        checkInDate: val.checkInDate,
        checkOutDate: val.checkOutDate,
        adultCount: val.adultCount,
        childCount: val.childCount,
        roomCount: val.roomCount,
        latitude: val.latitude || null,
        longitude: val.longitude || null,
        sortBy: val.sortBy
      },
    });
  }

  applyFilters() {
    const selectedTypes = this.roomTypesFilter.filter((type) => type.checked).map((type) => type.value);
    const [minPrice, maxPrice] = this.priceRange;

    this.hotels = this.allHotels
      .filter((hotel) => {
        const matchesPrice = hotel.minPrice === null || (hotel.minPrice >= minPrice && hotel.minPrice <= maxPrice);
        const matchesTypes =
          selectedTypes.length === 0 ||
          hotel.roomTypes.some((roomType) => {
            const haystack = `${roomType.code} ${roomType.nameVi} ${roomType.nameEn}`.toLowerCase();
            return selectedTypes.some((type) => haystack.includes(type));
          });

        return matchesPrice && matchesTypes;
      });
  }

  clearFilters() {
    this.roomTypesFilter = this.roomTypesFilter.map((type) => ({ ...type, checked: false }));
    this.priceRange = [0, this.maxPriceLimit];
    this.searchForm.patchValue({ sortBy: 'POPULAR' });
    this.applyFilters();
  }

  get activeFiltersCount(): number {
    const typeCount = this.roomTypesFilter.filter((type) => type.checked).length;
    const priceChanged = this.priceRange[0] > 0 || this.priceRange[1] < this.maxPriceLimit ? 1 : 0;
    return typeCount + priceChanged;
  }

  private loadRoomTypeSummaries(hotels: Hotel[]) {
    if (hotels.length === 0) {
      this.allHotels = [];
      this.hotels = [];
      this.isLoading = false;
      return;
    }

    const { checkInDate, checkOutDate, adultCount } = this.searchForm.value;

    forkJoin(
      hotels.map((hotel) =>
        this.clientApi.getRoomTypesByHotel(hotel.id, checkInDate, checkOutDate, adultCount).pipe(
          map((roomTypes) => this.toSearchResult(hotel, roomTypes)),
          catchError(() => of(this.toSearchResult(hotel, [])))
        )
      )
    ).subscribe((results) => {
      this.allHotels = results;
      const highestPrice = Math.max(...this.allHotels.map((hotel) => hotel.minPrice || 0), 0);
      if (highestPrice > 0) {
        this.maxPriceLimit = Math.ceil(highestPrice / 500000) * 500000;
        this.priceRange = [0, this.maxPriceLimit];
      }
      this.applyFilters();
      this.isLoading = false;
    });
  }

  private toSearchResult(hotel: Hotel, roomTypes: RoomType[]): HotelSearchResult {
    const prices = roomTypes.map((roomType) => Number(roomType.basePrice)).filter((price) => !Number.isNaN(price));
    const minPrice = prices.length ? Math.min(...prices) : null;
    const availableRoomCount = roomTypes.reduce((sum, roomType) => sum + (Number(roomType.availableRooms) || 0), 0);
    const roomTypeSummary = roomTypes.slice(0, 3).map((roomType) => roomType.nameVi || roomType.code).join(', ');

    return {
      ...hotel,
      roomTypes,
      minPrice,
      minTotalPrice: null,
      availableRoomCount,
      nights: null,
      maxGuests: 0,
      roomTypeSummary,
    };
  }
}
