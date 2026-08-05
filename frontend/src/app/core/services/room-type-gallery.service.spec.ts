import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { RoomTypeGalleryService } from './room-type-gallery.service';

describe('RoomTypeGalleryService', () => {
  let service: RoomTypeGalleryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(RoomTypeGalleryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses the dedicated room-type gallery contract', () => {
    service.addLink(12, { imageUrl: 'https://cdn.example/room.jpg', altTextVi: 'Phong deluxe', primary: true }).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/v1/room-types/12/gallery/links`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body.primary).toBe(true);
    request.flush({});
  });

  it('sends exact ordering and image lifecycle commands', () => {
    service.reorder(12, [8, 7]).subscribe();
    const reorder = http.expectOne(`${environment.apiUrl}/v1/room-types/12/gallery/order`);
    expect(reorder.request.body).toEqual({ imageIds: [8, 7] });
    reorder.flush([]);

    service.setPrimary(12, 8).subscribe();
    http.expectOne({ method: 'PUT', url: `${environment.apiUrl}/v1/room-types/12/gallery/images/8/primary` }).flush({});
    service.delete(12, 7).subscribe();
    http.expectOne({ method: 'DELETE', url: `${environment.apiUrl}/v1/room-types/12/gallery/images/7` }).flush([]);
  });
});
