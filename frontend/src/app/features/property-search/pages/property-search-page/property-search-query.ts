import { HttpErrorResponse } from '@angular/common/http';
import { Params } from '@angular/router';

import { PropertySearchParams } from '../../../../core/services/client-api.service';

export interface PropertySearchErrorState {
  title: string;
  message: string;
  code: string;
  retryable: boolean;
}

export interface SearchStayDates {
  checkIn: Date;
  checkOut: Date;
}

export interface PriceFilterDisplayState {
  minPrice: number;
  maxPrice: number;
}

export const PROPERTY_TYPE_FILTERS = [
  'HOTEL', 'RESORT', 'APARTMENT', 'VILLA', 'HOMESTAY', 'MOTEL', 'GUEST_HOUSE', 'HOSTEL',
] as const;
export const STAR_RATING_FILTERS = [5, 4, 3, 2, 1] as const;
export const REVIEW_SCORE_FILTERS = [9, 8, 7, 6] as const;
export const PRICE_FILTER_MIN = 0;
export const PRICE_FILTER_MAX = 10_000_000;
export const PRICE_FILTER_STEP = 100_000;

export function propertySearchParamsFromRoute(params: Params): PropertySearchParams {
  return {
    keyword: textValue(params['keyword']),
    provinceId: numberValue(params['provinceId']),
    wardId: numberValue(params['wardId']),
    landmarkId: numberValue(params['landmarkId']),
    checkInDate: textValue(params['checkInDate']),
    checkOutDate: textValue(params['checkOutDate']),
    adultCount: numberValue(params['adultCount']),
    childCount: numberValue(params['childCount']),
    roomCount: numberValue(params['roomCount']),
    latitude: numberValue(params['latitude']),
    longitude: numberValue(params['longitude']),
    radiusKm: numberValue(params['radiusKm']),
    sortBy: textValue(params['sortBy']),
    pageNumber: numberValue(params['pageNumber']),
    pageSize: numberValue(params['pageSize']),
    propertyTypes: optionalList(canonicalPropertyTypes(stringList(params['propertyTypes']))),
    stayType: textValue(params['stayType']),
    minPrice: numberValue(params['minPrice']),
    maxPrice: numberValue(params['maxPrice']),
    starRatings: optionalList(canonicalStarRatings(numberList(params['starRatings']))),
    minReviewScore: canonicalReviewScore(numberValue(params['minReviewScore'])) ?? undefined,
    amenityIds: numberList(params['amenityIds']),
    freeCancellation: booleanValue(params['freeCancellation']),
    payAtProperty: booleanValue(params['payAtProperty']),
    breakfastIncluded: booleanValue(params['breakfastIncluded']),
  };
}

export function canonicalPropertyTypes(values?: readonly string[]): string[] {
  const selected = new Set((values || []).map(value => value.trim().toUpperCase()));
  return PROPERTY_TYPE_FILTERS.filter(value => selected.has(value));
}

export function canonicalStarRatings(values?: readonly number[]): number[] {
  const selected = new Set((values || []).map(Number).filter(Number.isFinite));
  return STAR_RATING_FILTERS.filter(value => selected.has(value));
}

export function canonicalReviewScore(value: unknown): number | null {
  if (value === null || value === undefined || (typeof value === 'string' && !value.trim())) return null;
  const score = Number(value);
  return Number.isFinite(score) && score >= 0 && score <= 10 ? score : null;
}

export function canonicalPriceDisplayState(minValue: unknown, maxValue: unknown): PriceFilterDisplayState {
  const minPrice = canonicalDisplayPrice(minValue, PRICE_FILTER_MIN);
  const maxPrice = canonicalDisplayPrice(maxValue, PRICE_FILTER_MAX);
  return {
    minPrice: Math.min(minPrice, maxPrice),
    maxPrice: Math.max(minPrice, maxPrice),
  };
}

export function validSearchStayDates(params: Params): SearchStayDates | null {
  const checkIn = isoDate(textValue(params['checkInDate']));
  const checkOut = isoDate(textValue(params['checkOutDate']));
  if (!checkIn || !checkOut || checkOut <= checkIn) return null;
  return { checkIn, checkOut };
}

export function propertySearchErrorState(error: unknown): PropertySearchErrorState {
  if (error instanceof HttpErrorResponse && error.status === 400) {
    const payload = apiErrorPayload(error.error);
    return {
      title: 'Yêu cầu tìm kiếm không hợp lệ',
      message: payload.message || 'Kiểm tra lại ngày lưu trú và các điều kiện tìm kiếm.',
      code: payload.code || 'INVALID_REQUEST',
      retryable: false,
    };
  }
  return {
    title: 'Không thể tải kết quả',
    message: 'Không thể kết nối dịch vụ tìm kiếm. Trạng thái của bạn vẫn được giữ nguyên.',
    code: 'SEARCH_UNAVAILABLE',
    retryable: true,
  };
}

function textValue(value: unknown): string | undefined {
  const first = Array.isArray(value) ? value[0] : value;
  if (first === null || first === undefined) return undefined;
  const text = String(first).trim();
  return text || undefined;
}

function numberValue(value: unknown): number | undefined {
  const text = textValue(value);
  return text === undefined ? undefined : Number(text);
}

function stringList(value: unknown): string[] | undefined {
  if (value === null || value === undefined) return undefined;
  const values = (Array.isArray(value) ? value : [value])
    .flatMap(item => String(item).split(','))
    .map(item => item.trim())
    .filter(Boolean);
  return values.length ? values : undefined;
}

function numberList(value: unknown): number[] | undefined {
  return stringList(value)?.map(Number);
}

function optionalList<T>(values: T[]): T[] | undefined {
  return values.length ? values : undefined;
}

function booleanValue(value: unknown): boolean | undefined {
  const text = textValue(value)?.toLowerCase();
  if (text === 'true') return true;
  if (text === 'false') return false;
  return undefined;
}

function canonicalDisplayPrice(value: unknown, fallback: number): number {
  if (value === null || value === undefined || (typeof value === 'string' && !value.trim())) return fallback;
  const price = Number(value);
  if (!Number.isFinite(price)) return fallback;
  return Math.min(PRICE_FILTER_MAX, Math.max(PRICE_FILTER_MIN, price));
}

function isoDate(value?: string): Date | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  date.setHours(0, 0, 0, 0);
  return date;
}

function apiErrorPayload(value: unknown): { code?: string; message?: string } {
  if (!value || typeof value !== 'object') return {};
  const payload = value as Record<string, unknown>;
  return {
    code: typeof payload['code'] === 'string' ? payload['code'] : undefined,
    message: typeof payload['message'] === 'string' ? payload['message'] : undefined,
  };
}
