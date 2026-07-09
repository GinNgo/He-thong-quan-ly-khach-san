import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { SliderModule } from 'primeng/slider';
import { SelectModule } from 'primeng/select';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { ClientApiService, Hotel, RoomType } from '../../../core/services/client-api.service';

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
  imports: [CommonModule, FormsModule, RouterModule, SliderModule, SelectModule],
  templateUrl: './room-search.component.html',
  styleUrls: ['./room-search.component.css'],
})
export class RoomSearchComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private clientApi = inject(ClientApiService);

  destination = '';
  checkInDate = '';
  checkOutDate = '';
  guests = 2;

  priceRange: number[] = [0, 5000000];
  maxPriceLimit = 5000000;
  sortBy = 'recommended';
  searchError = '';
  sortOptions = [
    { label: 'De xuat truoc', value: 'recommended' },
    { label: 'Gia thap truoc', value: 'priceAsc' },
    { label: 'Sao cao truoc', value: 'ratingDesc' },
    { label: 'Ten A-Z', value: 'nameAsc' },
  ];

  roomTypes = [
    { label: 'Standard', value: 'standard', checked: false },
    { label: 'Deluxe', value: 'deluxe', checked: false },
    { label: 'Suite', value: 'suite', checked: false },
    { label: 'VIP', value: 'vip', checked: false },
  ];

  allHotels: HotelSearchResult[] = [];
  hotels: HotelSearchResult[] = [];
  isLoading = false;

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      this.destination = params['destination'] || '';
      this.checkInDate = params['checkIn'] || '';
      this.checkOutDate = params['checkOut'] || '';
      this.guests = Number(params['guests']) || 2;
      this.searchHotels();
    });
  }

  searchHotels() {
    this.searchError = '';
    if (this.checkInDate && this.checkOutDate && this.checkOutDate <= this.checkInDate) {
      this.searchError = 'Ngay tra phong phai sau ngay nhan phong.';
      return;
    }

    this.isLoading = true;
    this.clientApi
      .searchHotels(this.destination, this.checkInDate || undefined, this.checkOutDate || undefined, this.guests || undefined)
      .subscribe({
        next: (data) => this.loadRoomTypeSummaries(data),
        error: (err) => {
          console.error('Error fetching hotels:', err);
          this.allHotels = [];
          this.hotels = [];
          this.isLoading = false;
          this.searchError = 'Khong the tai danh sach khach san. Vui long kiem tra backend hoac thu lai.';
        },
      });
  }

  search() {
    this.router.navigate(['/search'], {
      queryParams: {
        destination: this.destination || null,
        checkIn: this.checkInDate || null,
        checkOut: this.checkOutDate || null,
        guests: this.guests || null,
      },
    });
  }

  applyFilters() {
    const selectedTypes = this.roomTypes.filter((type) => type.checked).map((type) => type.value);
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
      })
      .sort((a, b) => {
        if (this.sortBy === 'priceAsc') return (a.minPrice ?? Number.MAX_SAFE_INTEGER) - (b.minPrice ?? Number.MAX_SAFE_INTEGER);
        if (this.sortBy === 'ratingDesc') return (b.starRating || 0) - (a.starRating || 0);
        if (this.sortBy === 'nameAsc') return a.name.localeCompare(b.name);
        return (b.starRating || 0) - (a.starRating || 0);
      });
  }

  clearFilters() {
    this.roomTypes = this.roomTypes.map((type) => ({ ...type, checked: false }));
    this.priceRange = [0, this.maxPriceLimit];
    this.sortBy = 'recommended';
    this.applyFilters();
  }

  get activeFiltersCount(): number {
    const typeCount = this.roomTypes.filter((type) => type.checked).length;
    const priceChanged = this.priceRange[0] > 0 || this.priceRange[1] < this.maxPriceLimit ? 1 : 0;
    return typeCount + priceChanged;
  }

  get hasStayDates(): boolean {
    return Boolean(this.checkInDate && this.checkOutDate);
  }

  private loadRoomTypeSummaries(hotels: Hotel[]) {
    if (hotels.length === 0) {
      this.allHotels = [];
      this.hotels = [];
      this.isLoading = false;
      return;
    }

    forkJoin(
      hotels.map((hotel) =>
        this.clientApi.getRoomTypesByHotel(
          hotel.id,
          this.checkInDate || undefined,
          this.checkOutDate || undefined,
          this.guests || undefined
        ).pipe(
          map((roomTypes) => this.toSearchResult(hotel, roomTypes)),
          catchError(() => of(this.toSearchResult(hotel, [])))
        )
      )
    ).subscribe((results) => {
      this.allHotels = this.hasStayDates ? results.filter((hotel) => hotel.availableRoomCount > 0) : results;
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
    const totalPrices = roomTypes
      .map((roomType) => Number(roomType.totalPrice))
      .filter((price) => !Number.isNaN(price) && price > 0);
    const minPrice = prices.length ? Math.min(...prices) : null;
    const minTotalPrice = totalPrices.length ? Math.min(...totalPrices) : null;
    const availableRoomCount = roomTypes.reduce((sum, roomType) => sum + (Number(roomType.availableRooms) || 0), 0);
    const nights = roomTypes.find((roomType) => roomType.nights)?.nights || null;
    const maxGuests = roomTypes.length ? Math.max(...roomTypes.map((roomType) => roomType.maxGuest || 0)) : 0;
    const roomTypeSummary = roomTypes
      .slice(0, 3)
      .map((roomType) => roomType.nameVi || roomType.nameEn || roomType.code)
      .join(', ');

    return {
      ...hotel,
      roomTypes,
      minPrice,
      minTotalPrice,
      availableRoomCount,
      nights,
      maxGuests,
      roomTypeSummary,
    };
  }
}
