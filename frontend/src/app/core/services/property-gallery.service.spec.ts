import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { PropertyGalleryService } from './property-gallery.service';

describe('PropertyGalleryService', () => {
  let service: PropertyGalleryService;
  let http: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/v1/properties/7/gallery`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(PropertyGalleryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uses one canonical property-scoped endpoint for list, primary and delete', () => {
    service.list(7).subscribe();
    http.expectOne({ method: 'GET', url: baseUrl }).flush([]);
    service.setPrimary(7, 9).subscribe();
    http.expectOne({ method: 'PUT', url: `${baseUrl}/images/9/primary` }).flush({});
    service.delete(7, 9).subscribe();
    http.expectOne({ method: 'DELETE', url: `${baseUrl}/images/9` }).flush([]);
  });

  it('sends upload metadata as multipart form data', () => {
    const file = new File(['image'], 'property.png', { type: 'image/png' });
    service.upload(7, file, 'Phong', 'Room', true).subscribe();

    const request = http.expectOne({ method: 'POST', url: `${baseUrl}/uploads` });
    expect(request.request.body).toBeInstanceOf(FormData);
    const body = request.request.body as FormData;
    expect(body.get('file')).toBe(file);
    expect(body.get('altTextVi')).toBe('Phong');
    expect(body.get('altTextEn')).toBe('Room');
    expect(body.get('primary')).toBe('true');
    request.flush({});
  });

  it('sends a complete reorder command without client-owned sort numbers', () => {
    service.reorder(7, [3, 1, 2]).subscribe();
    const request = http.expectOne({ method: 'PUT', url: `${baseUrl}/order` });
    expect(request.request.body).toEqual({ imageIds: [3, 1, 2] });
    request.flush([]);
  });
});
