import { Component, OnInit, OnDestroy, inject, HostListener, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ClientApiService } from '../../../core/services/client-api.service';
import { LayoutStateService } from '../../../core/services/layout-state.service';
import { HeroSearchComponent } from './components/hero-search/hero-search.component';
import { StickySearchBarComponent } from './components/sticky-search-bar/sticky-search-bar.component';
import { PopularDestinationsComponent } from './components/popular-destinations/popular-destinations.component';
import { PromotionsComponent } from './components/promotions/promotions.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    HeroSearchComponent,
    StickySearchBarComponent,
    PopularDestinationsComponent,
    PromotionsComponent
  ],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit, OnDestroy {
  private router = inject(Router);
  private clientApi = inject(ClientApiService);
  private layoutState = inject(LayoutStateService);
  
  promotions: any[] = [];
  destinations: any[] = [];
  isLoadingDestinations = true;

  @ViewChild('heroSearchRef', { static: true }) heroSearchRef!: ElementRef;

  showStickySearch = false;
  private observer!: IntersectionObserver;

  ngOnInit() {
    this.loadPromotions();
    this.loadPopularDestinations();

    // IntersectionObserver to show sticky search when hero search is out of view
    this.observer = new IntersectionObserver(
      ([entry]) => {
        // if entry.isIntersecting is false, it means the hero search is out of view (scrolled past)
        this.showStickySearch = !entry.isIntersecting && entry.boundingClientRect.top < 0;
        this.layoutState.hideMainHeader.set(this.showStickySearch);
      },
      { threshold: 0 }
    );
    if (this.heroSearchRef) {
      this.observer.observe(this.heroSearchRef.nativeElement);
    }
  }

  ngOnDestroy() {
    if (this.observer) {
      this.observer.disconnect();
    }
    this.layoutState.hideMainHeader.set(false);
  }

  private loadPopularDestinations() {
    this.isLoadingDestinations = true;
    this.clientApi.getProvinces().subscribe({
      next: (provinces) => {
        const topNames = ['Đà Nẵng', 'Hồ Chí Minh', 'Bà Rịa - Vũng Tàu', 'Hà Nội', 'Lâm Đồng'];
        const topProvinces = provinces.filter(p => topNames.some(name => p.nameVi.includes(name)));
        
        if (topProvinces.length === 0) {
           this.loadFallbackDestinations();
           return;
        }

        const requests = topProvinces.map(p => 
          this.clientApi.searchHotels({ provinceId: p.id, pageNumber: 1, pageSize: 1 })
        );

        if (requests.length > 0) {
          import('rxjs').then(({ forkJoin }) => {
            forkJoin(requests).subscribe({
              next: (results: any[]) => {
                this.destinations = topProvinces.map((p, index) => {
                  const res = results[index];
                  return {
                    id: p.id,
                    name: p.nameVi.replace('Tỉnh ', '').replace('Thành phố ', ''),
                    properties: res.totalElements || 0,
                    image: this.getImageForProvince(p.nameVi)
                  };
                });
                this.isLoadingDestinations = false;
              },
              error: () => this.loadFallbackDestinations()
            });
          });
        } else {
           this.loadFallbackDestinations();
        }
      },
      error: () => this.loadFallbackDestinations()
    });
  }

  private loadFallbackDestinations() {
    this.destinations = [
      { id: null, name: 'Đà Nẵng', properties: 120, image: this.getImageForProvince('Đà Nẵng') },
      { id: null, name: 'Hồ Chí Minh', properties: 350, image: this.getImageForProvince('Hồ Chí Minh') },
      { id: null, name: 'Vũng Tàu', properties: 95, image: this.getImageForProvince('Vũng Tàu') },
      { id: null, name: 'Hà Nội', properties: 210, image: this.getImageForProvince('Hà Nội') },
      { id: null, name: 'Đà Lạt', properties: 150, image: this.getImageForProvince('Đà Lạt') }
    ];
    this.isLoadingDestinations = false;
  }

  private loadPromotions() {
    this.promotions = [
      {
        title: 'Kỳ nghỉ Vàng',
        desc: 'Giảm ngay 20% cho các đặt phòng từ nay đến cuối năm. Áp dụng cho phòng Suite tại toàn bộ hệ thống LuxeStay.',
        image: 'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?ixlib=rb-4.0.3&auto=format&fit=crop&w=800&q=80',
        code: 'GOLDEN26'
      },
      {
        title: 'Ưu đãi Mùa Hè',
        desc: 'Nhập mã SUMMER26 để nhận ưu đãi ăn sáng buffet miễn phí cho 2 người và miễn phí đưa đón sân bay.',
        image: 'https://images.unsplash.com/photo-1582719508461-905c673771fd?auto=format&fit=crop&w=800&q=80',
        code: 'SUMMER26'
      },
      {
        title: 'Thành viên mới',
        desc: 'Tặng Voucher 500k cho khách hàng đăng ký tài khoản thành viên LuxeStay ngay hôm nay. Hàng ngàn ưu đãi đang chờ đón.',
        image: 'https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?auto=format&fit=crop&w=800&q=80',
        code: 'NEWUSER'
      }
    ];
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
}
