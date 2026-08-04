import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export interface PropertyGalleryImage {
  id: number;
  propertyId: number;
  imageUrl: string;
  primary: boolean;
  altTextVi?: string;
  altTextEn?: string;
  sortOrder: number;
  managedUpload: boolean;
}

export interface PropertyImageLink {
  imageUrl: string;
  altTextVi?: string;
  altTextEn?: string;
  primary: boolean;
}

@Injectable({ providedIn: 'root' })
export class PropertyGalleryService {
  private readonly http = inject(HttpClient);

  list(propertyId: number) {
    return this.http.get<PropertyGalleryImage[]>(this.baseUrl(propertyId));
  }

  addLink(propertyId: number, request: PropertyImageLink) {
    return this.http.post<PropertyGalleryImage>(`${this.baseUrl(propertyId)}/links`, request);
  }

  upload(propertyId: number, file: File, altTextVi?: string, altTextEn?: string, primary = false) {
    const body = new FormData();
    body.append('file', file);
    if (altTextVi?.trim()) body.append('altTextVi', altTextVi.trim());
    if (altTextEn?.trim()) body.append('altTextEn', altTextEn.trim());
    body.append('primary', String(primary));
    return this.http.post<PropertyGalleryImage>(`${this.baseUrl(propertyId)}/uploads`, body);
  }

  reorder(propertyId: number, imageIds: number[]) {
    return this.http.put<PropertyGalleryImage[]>(`${this.baseUrl(propertyId)}/order`, { imageIds });
  }

  setPrimary(propertyId: number, imageId: number) {
    return this.http.put<PropertyGalleryImage>(
      `${this.baseUrl(propertyId)}/images/${imageId}/primary`, {});
  }

  delete(propertyId: number, imageId: number) {
    return this.http.delete<PropertyGalleryImage[]>(
      `${this.baseUrl(propertyId)}/images/${imageId}`);
  }

  private baseUrl(propertyId: number): string {
    return `${environment.apiUrl}/v1/properties/${propertyId}/gallery`;
  }
}
