import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { PropertyGalleryImage, PropertyImageLink } from './property-gallery.service';

@Injectable({ providedIn: 'root' })
export class RoomTypeGalleryService {
  private readonly http = inject(HttpClient);
  list(id: number) { return this.http.get<PropertyGalleryImage[]>(this.base(id)); }
  addLink(id: number, request: PropertyImageLink) { return this.http.post<PropertyGalleryImage>(`${this.base(id)}/links`, request); }
  upload(id: number, file: File, altTextVi?: string, altTextEn?: string, primary = false) {
    const body = new FormData(); body.append('file', file);
    if (altTextVi?.trim()) body.append('altTextVi', altTextVi.trim());
    if (altTextEn?.trim()) body.append('altTextEn', altTextEn.trim());
    body.append('primary', String(primary));
    return this.http.post<PropertyGalleryImage>(`${this.base(id)}/uploads`, body);
  }
  reorder(id: number, imageIds: number[]) { return this.http.put<PropertyGalleryImage[]>(`${this.base(id)}/order`, { imageIds }); }
  setPrimary(id: number, imageId: number) { return this.http.put<PropertyGalleryImage>(`${this.base(id)}/images/${imageId}/primary`, {}); }
  delete(id: number, imageId: number) { return this.http.delete<PropertyGalleryImage[]>(`${this.base(id)}/images/${imageId}`); }
  private base(id: number): string { return `${environment.apiUrl}/v1/room-types/${id}/gallery`; }
}
