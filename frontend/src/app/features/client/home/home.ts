import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { SelectModule } from 'primeng/select';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { CarouselModule } from 'primeng/carousel';
import { Router } from '@angular/router';

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
    CarouselModule
  ],
  templateUrl: './home.html',
  styleUrls: ['./home.css']
})
export class HomeComponent implements OnInit {
  private router = inject(Router);

  checkIn: Date | undefined;
  checkOut: Date | undefined;
  guests: any | undefined;
  guestOptions: any[] = [];

  featuredRooms: any[] = [];
  services: any[] = [];
  reviews: any[] = [];
  promotions: any[] = [];
  destinations: any[] = [];

  ngOnInit() {
    this.guestOptions = [
      { label: '1 Khách', value: 1 },
      { label: '2 Khách', value: 2 },
      { label: '3 Khách', value: 3 },
      { label: '4 Khách', value: 4 }
    ];

    this.promotions = [
      {
        title: 'Ưu đãi mùa hè 2026',
        desc: 'Giảm ngay 20% cho các đặt phòng từ 3 đêm trở lên. Trải nghiệm mùa hè tuyệt vời.',
        image: 'https://images.unsplash.com/photo-1571896349842-33c89424de2d?auto=format&fit=crop&w=800&q=80',
        code: 'SUMMER26'
      },
      {
        title: 'Thành viên mới',
        desc: 'Tặng Voucher 500k cho khách hàng đăng ký tài khoản thành viên LuxeStay ngay hôm nay.',
        image: 'https://images.unsplash.com/photo-1571003123894-1f0594d2b5d9?auto=format&fit=crop&w=800&q=80',
        code: 'NEWUSER'
      }
    ];

    this.destinations = [
      { name: 'Đà Lạt', properties: 124, image: 'https://images.unsplash.com/photo-1528127269322-539801943592?auto=format&fit=crop&w=600&q=80' },
      { name: 'Nha Trang', properties: 86, image: 'https://images.unsplash.com/photo-1499793983690-e29da59ef1c2?auto=format&fit=crop&w=600&q=80' },
      { name: 'Phú Quốc', properties: 152, image: 'https://images.unsplash.com/photo-1506929562872-bb421503ef21?auto=format&fit=crop&w=600&q=80' },
      { name: 'Đà Nẵng', properties: 198, image: 'https://images.unsplash.com/photo-1559592413-7cec4d0cae2b?auto=format&fit=crop&w=600&q=80' },
      { name: 'Sapa', properties: 95, image: 'https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?auto=format&fit=crop&w=600&q=80' }
    ];

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
    const queryParams: any = {};
    if (this.checkIn) queryParams.checkIn = this.checkIn.toISOString().split('T')[0];
    if (this.checkOut) queryParams.checkOut = this.checkOut.toISOString().split('T')[0];
    if (this.guests) queryParams.guests = this.guests; // guests is now bound to the value

    this.router.navigate(['/room-search'], { queryParams });
  }
}
