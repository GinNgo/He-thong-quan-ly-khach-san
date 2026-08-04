import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import {
  PropertyGalleryImage,
  PropertyGalleryService
} from '../../../core/services/property-gallery.service';
import { PropertyGalleryComponent } from './property-gallery.component';
import { RoomTypeGalleryService } from '../../../core/services/room-type-gallery.service';
import { RoomGalleryService } from '../../../core/services/room-gallery.service';

describe('PropertyGalleryComponent', () => {
  const first: PropertyGalleryImage = {
    id: 1, propertyId: 7, imageUrl: 'https://cdn.example/one.jpg', primary: true,
    sourceType: 'EXTERNAL_HTTPS', sortOrder: 0, managedUpload: false, altTextVi: 'Anh mot'
  };
  const second: PropertyGalleryImage = {
    id: 2, propertyId: 7, imageUrl: '/api/public/uploads/property-7-two.png', primary: false,
    sourceType: 'MANAGED_UPLOAD', contentType: 'image/png', width: 20, height: 20,
    sortOrder: 1, managedUpload: true, altTextVi: 'Anh hai'
  };

  function galleryApi() {
    return {
      list: vi.fn(() => of([first, second])),
      addLink: vi.fn(() => of({ ...second, id: 3, imageUrl: 'https://cdn.example/three.jpg', sortOrder: 2, primary: true })),
      upload: vi.fn(() => of({ ...second, id: 4, sortOrder: 2 })),
      reorder: vi.fn((_propertyId: number, imageIds: number[]) => of(
        imageIds.map((id, sortOrder) => ({ ...(id === 1 ? first : second), id, sortOrder }))
      )),
      setPrimary: vi.fn((_propertyId: number, imageId: number) => of({ ...second, id: imageId, primary: true })),
      delete: vi.fn(() => of([second]))
    };
  }

  it('loads the tenant property gallery when the property input changes', async () => {
    const api = galleryApi();
    await TestBed.configureTestingModule({
      imports: [PropertyGalleryComponent],
      providers: [{ provide: PropertyGalleryService, useValue: api }]
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyGalleryComponent);
    fixture.componentRef.setInput('propertyId', 7);
    fixture.detectChanges();

    expect(api.list).toHaveBeenCalledWith(7);
    expect(fixture.componentInstance.images).toEqual([first, second]);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Anh dai dien');
  });

  it('submits a linked image with localized alt text and primary choice', async () => {
    const api = galleryApi();
    await TestBed.configureTestingModule({
      imports: [PropertyGalleryComponent],
      providers: [{ provide: PropertyGalleryService, useValue: api }]
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyGalleryComponent);
    fixture.componentRef.setInput('propertyId', 7);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.linkUrl = ' https://cdn.example/three.jpg ';
    component.altTextVi = ' Ho boi ';
    component.altTextEn = ' Pool ';
    component.makePrimary = true;

    component.addLink();

    expect(api.addLink).toHaveBeenCalledWith(7, {
      imageUrl: 'https://cdn.example/three.jpg',
      altTextVi: 'Ho boi',
      altTextEn: 'Pool',
      primary: true
    });
    expect(component.images.filter(image => image.primary).map(image => image.id)).toEqual([3]);
  });

  it('uploads the selected file and forwards accessible metadata', async () => {
    const api = galleryApi();
    await TestBed.configureTestingModule({
      imports: [PropertyGalleryComponent],
      providers: [{ provide: PropertyGalleryService, useValue: api }]
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyGalleryComponent);
    fixture.componentRef.setInput('propertyId', 7);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const file = new File(['image'], 'property.png', { type: 'image/png' });
    component.selectedFile = file;
    component.altTextVi = 'Phong deluxe';

    component.upload();

    expect(api.upload).toHaveBeenCalledWith(7, file, 'Phong deluxe', '', false);
    expect(component.selectedFile).toBeUndefined();
  });

  it('persists reorder and restores the prior order when the API rejects it', async () => {
    const api = galleryApi();
    await TestBed.configureTestingModule({
      imports: [PropertyGalleryComponent],
      providers: [{ provide: PropertyGalleryService, useValue: api }]
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyGalleryComponent);
    fixture.componentRef.setInput('propertyId', 7);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.move(1, -1);
    expect(api.reorder).toHaveBeenCalledWith(7, [2, 1]);
    expect(component.images.map(image => image.id)).toEqual([2, 1]);

    api.reorder.mockReturnValueOnce(throwError(() => ({ error: { message: 'Concurrent reorder' } })) as any);
    component.move(1, -1);
    expect(component.images.map(image => image.id)).toEqual([2, 1]);
    expect(component.error).toBe('Concurrent reorder');
  });

  it('sets the only primary image and accepts canonical delete results', async () => {
    const api = galleryApi();
    await TestBed.configureTestingModule({
      imports: [PropertyGalleryComponent],
      providers: [{ provide: PropertyGalleryService, useValue: api }]
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyGalleryComponent);
    fixture.componentRef.setInput('propertyId', 7);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.setPrimary(second);
    expect(component.images.filter(image => image.primary).map(image => image.id)).toEqual([2]);
    component.remove(first);
    expect(api.delete).toHaveBeenCalledWith(7, 1);
    expect(component.images).toEqual([second]);
  });

  it('routes the reusable editor to the room-type gallery API', async () => {
    const propertyApi = galleryApi();
    const roomTypeApi = galleryApi();
    await TestBed.configureTestingModule({
      imports: [PropertyGalleryComponent],
      providers: [
        { provide: PropertyGalleryService, useValue: propertyApi },
        { provide: RoomTypeGalleryService, useValue: roomTypeApi }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(PropertyGalleryComponent);
    fixture.componentRef.setInput('roomTypeId', 44);
    fixture.detectChanges();

    expect(roomTypeApi.list).toHaveBeenCalledWith(44);
    expect(propertyApi.list).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('loai phong');
  });

  it('routes the reusable editor to the physical-room gallery API', async () => {
    const propertyApi = galleryApi(); const roomApi = galleryApi();
    await TestBed.configureTestingModule({ imports: [PropertyGalleryComponent], providers: [
      { provide: PropertyGalleryService, useValue: propertyApi }, { provide: RoomGalleryService, useValue: roomApi }
    ] }).compileComponents();
    const fixture = TestBed.createComponent(PropertyGalleryComponent); fixture.componentRef.setInput('roomId', 55); fixture.detectChanges();
    expect(roomApi.list).toHaveBeenCalledWith(55); expect(propertyApi.list).not.toHaveBeenCalled();
  });
});
