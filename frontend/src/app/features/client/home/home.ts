import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { CarouselModule } from 'primeng/carousel';
import { Router } from '@angular/router';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { PopoverModule } from 'primeng/popover';
import { InputTextModule } from 'primeng/inputtext';
import { ClientApiService } from '../../../core/services/client-api.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    DatePickerModule,
    SelectModule,
    FormsModule,
    CardModule,
    CarouselModule,
    AutoCompleteModule,
    PopoverModule,
    InputTextModule
  ],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit {
  private router = inject(Router);
  private clientApi = inject(ClientApiService);

  location: string = '';
  searchQuery: string = '';

  recentSearches: any[] = [];

  popularDestinations: any[] = [];

  dateRange: Date[] | undefined;

  guestInfo = {
    rooms: 1,
    adults: 2,
    children: 0
  };

  searchError = '';
  copiedCode = '';

  promotions: any[] = [];
  destinations: any[] = [];
  featuredRooms: any[] = [];
  services: any[] = [];
  reviews: any[] = [];

  ngOnInit() {
    // Load recent searches
    this.recentSearches = JSON.parse(localStorage.getItem('recentSearches') || '[]');

    // Load actual locations
    this.clientApi.getProvinces().subscribe(provinces => {
      // Find top provinces (Da Nang, Ho Chi Minh, Vung Tau, Ha Noi, Da Lat)
      const topNames = ['Đà Nẵng', 'Hồ Chí Minh', 'Bà Rịa - Vũng Tàu', 'Hà Nội', 'Lâm Đồng'];
      const topProvinces = provinces.filter(p => topNames.includes(p.nameVi));
      
      const requests = topProvinces.map(p => 
        this.clientApi.searchHotels({ provinceId: p.id, pageNumber: 1, pageSize: 1 })
      );

      if (requests.length > 0) {
        import('rxjs').then(({ forkJoin }) => {
          forkJoin(requests).subscribe((results: any[]) => {
            this.destinations = topProvinces.map((p, index) => {
              const res = results[index];
              return {
                id: p.id,
                name: p.nameVi.replace('Tỉnh ', '').replace('Thành phố ', ''),
                properties: res.totalElements || 0,
                image: this.getImageForProvince(p.nameVi)
              };
            });
            this.popularDestinations = this.destinations.map(d => ({
              ...d, count: d.properties.toString(), type: 'Nhiều lựa chọn tốt'
            }));
          });
        });
      }
    });

    this.promotions = [
      {
        title: 'Kỳ nghỉ Vàng',
        desc: 'Giảm ngay 20% cho các đặt phòng từ nay đến cuối năm. Áp dụng cho phòng Suite.',
        image: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80',
        code: 'GOLDEN26'
      },
      {
        title: 'Ưu đãi Mùa Hè',
        desc: 'Nhập mã SUMMER26 để nhận ưu đãi ăn sáng buffet miễn phí cho 2 người.',
        image: 'https://images.unsplash.com/photo-1582719508461-905c673771fd?auto=format&fit=crop&w=800&q=80',
        code: 'SUMMER26'
      },
      {
        title: 'Thành viên mới',
        desc: 'Tặng Voucher 500k cho khách hàng đăng ký tài khoản thành viên LuxeStay ngay hôm nay.',
        image: 'https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?auto=format&fit=crop&w=800&q=80',
        code: 'NEWUSER'
      }
    ];

    // Destinations will be loaded dynamically from backend

    this.featuredRooms = [
      {
        name: 'Phòng Suite Hướng Biển',
        type: 'Cao cấp',
        price: 139.00,
        image: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80'
      },
      {
        name: 'Biệt Thự Tổng Thống',
        type: 'Đẳng cấp',
        price: 155.00,
        image: 'https://images.unsplash.com/photo-1566073771259-6a8506099945?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80'
      },
      {
        name: 'Phòng Suite Brasi Plex',
        type: 'Cao cấp',
        price: 155.00,
        image: 'https://images.unsplash.com/photo-1631049307264-da0ec9d70304?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80'
      },
      {
        name: 'Phòng Suite Đại Dương',
        type: 'Cao cấp',
        price: 149.00,
        image: 'https://images.unsplash.com/photo-1590490360182-c33d57733427?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80'
      }
    ];

    this.services = [
      {
        icon: 'pi pi-user',
        title: 'Lễ Tân Hỗ Trợ 24/7',
        desc: 'Đội ngũ lễ tân chuyên nghiệp sẵn sàng đáp ứng mọi yêu cầu của bạn, từ đặt chỗ nhà hàng đến các dịch vụ cá nhân.'
      },
      {
        icon: 'pi pi-heart',
        title: 'Spa & Trị Liệu',
        desc: 'Tận hưởng không gian thư giãn tuyệt đối với các liệu trình spa cao cấp giúp tái tạo năng lượng.'
      },
      {
        icon: 'pi pi-compass',
        title: 'Ẩm Thực Tinh Hoa',
        desc: 'Khám phá thế giới ẩm thực phong phú tại các nhà hàng đạt chuẩn quốc tế của chúng tôi.'
      }
    ];

    this.reviews = [
      {
        rating: 5,
        text: 'Hoàn toàn hài lòng với chất lượng dịch vụ. Phòng ốc tuyệt vời và không gian rất thoải mái.',
        user: 'Lina Isan',
        role: 'Khách hàng VIP'
      },
      {
        rating: 5,
        text: 'Tôi đã trải nghiệm dịch vụ ở đây và thực sự ấn tượng. Một địa điểm tuyệt vời, nhân viên rất nhiệt tình. Xin cảm ơn.',
        user: 'Tora B.',
        role: 'Khách hàng VIP'
      },
      {
        rating: 5,
        text: 'Một kỳ nghỉ thật tuyệt vời mang lại sự thoải mái tối đa cho những người mới đến. Sự đổi mới ở đây rất đáng khen.',
        user: 'Normatta',
        role: 'Khách hàng'
      }
    ];
  }

  searchRooms() {
    this.searchError = '';
    
    if (!this.dateRange || !this.dateRange[0] || !this.dateRange[1]) {
      this.searchError = 'Vui lòng chọn ngày nhận và trả phòng hợp lệ.';
      return;
    }

    const checkIn = this.dateRange[0];
    const checkOut = this.dateRange[1];

    const queryParams: any = {};
    if (this.location) queryParams.location = this.location;
    queryParams.checkIn = this.formatDate(checkIn);
    queryParams.checkOut = this.formatDate(checkOut);
    queryParams.adults = this.guestInfo.adults;
    queryParams.children = this.guestInfo.children;
    queryParams.rooms = this.guestInfo.rooms;

    this.router.navigate(['/search'], { queryParams });
  }

  goToSearch() {
    this.router.navigate(['/search']);
  }

  copyPromoCode(code: string) {
    navigator.clipboard.writeText(code);
    this.copiedCode = code;
    setTimeout(() => {
      this.copiedCode = '';
    }, 1800);
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private getImageForProvince(name: string): string {
    const defaultImg = 'https://images.unsplash.com/photo-1555921015-5532091f6026?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Đà Nẵng')) return 'https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Hồ Chí Minh')) return 'https://images.unsplash.com/photo-1583417319070-4a69db38a482?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Vũng Tàu')) return 'https://images.unsplash.com/photo-1574676571597-d64c12ea847a?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Lâm Đồng') || name.includes('Đà Lạt')) return 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=600&q=80';
    if (name.includes('Khánh Hòa') || name.includes('Nha Trang')) return 'https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?auto=format&fit=crop&w=600&q=80';
    return defaultImg;
  }

  // --- Search Bar Helpers ---
  selectLocation(loc: any, op: any) {
    this.location = loc.nameVi || loc.keyword || loc.name || loc;
    this.searchQuery = loc.displayLocation || loc.nameVi || loc.keyword || loc.name || loc;
    op.hide();

    // If it's a recent search object, restore guests and dates
    if (loc.checkInDate && loc.checkOutDate) {
       this.dateRange = [new Date(loc.checkInDate), new Date(loc.checkOutDate)];
       this.guestInfo.adults = loc.adultCount || 2;
       this.guestInfo.rooms = loc.roomCount || 1;
       this.guestInfo.children = loc.childCount || 0;
    } else if (loc.id) {
       // It's a popular destination with an ID
       this.router.navigate(['/search'], { queryParams: { provinceId: loc.id, keyword: loc.name } });
    }
  }

  get checkInDate(): Date | undefined {
    return this.dateRange && this.dateRange[0] ? this.dateRange[0] : undefined;
  }

  get checkOutDate(): Date | undefined {
    return this.dateRange && this.dateRange[1] ? this.dateRange[1] : undefined;
  }

  formatDisplayDate(date: Date | undefined): string {
    if (!date) return 'dd/mm/yyyy';
    return `${date.getDate()} tháng ${date.getMonth() + 1} ${date.getFullYear()}`;
  }

  formatDisplayDayOfWeek(date: Date | undefined): string {
    if (!date) return 'Thêm ngày';
    const days = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
    return days[date.getDay()];
  }

  get guestSummary(): string {
    const { rooms, adults, children } = this.guestInfo;
    let summary = `${adults} người lớn`;
    if (children > 0) summary += `, ${children} trẻ em`;
    summary += ` • ${rooms} phòng`;
    return summary;
  }

  updateGuestInfo(guestType: 'rooms' | 'adults' | 'children', action: 'increase' | 'decrease', event: Event) {
    event.stopPropagation();
    if (action === 'increase') {
      this.guestInfo[guestType] += 1;
    } else if (action === 'decrease') {
      if (guestType === 'rooms' && this.guestInfo.rooms > 1) {
        this.guestInfo.rooms -= 1;
      } else if (guestType === 'adults' && this.guestInfo.adults > 1) {
        this.guestInfo.adults -= 1;
      } else if (guestType === 'children' && this.guestInfo.children > 0) {
        this.guestInfo.children -= 1;
      }
    }
  }
}
