export interface PropertyProfile {
  id?: number;
  code?: string;
  slug?: string;
  nameVi: string;
  nameEn?: string;
  propertyType: 'HOTEL' | 'MOTEL' | 'HOMESTAY' | 'APARTMENT' | 'VILLA' | 'RESORT';
  addressLine: string;
  city?: string;
  country?: string;
  provinceId: number;
  wardId: number;
  latitude?: number;
  longitude?: number;
  descriptionVi?: string;
  descriptionEn?: string;
  starRating?: number;
  phone?: string;
  email?: string;
  website?: string;
  checkinTime?: string;
  checkoutTime?: string;
  minPrice?: number;
  maxPrice?: number;
  mainImage?: string;
  status?: string;
  approvalStatus?: string;
  operationStatus?: string;
  isDemo?: boolean;
  dataSource?: string;
  averageRating?: number;
  reviewCount?: number;
  operational?: boolean;
}

export interface PropertyProfileUpdateRequest {
  profile: PropertyProfile;
  reason: string;
}
