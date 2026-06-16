import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RoomType {
  id?: number;
  code: string;
  nameVi: string;
  nameEn: string;
  basePrice: number;
}

export interface Room {
  id?: number;
  roomNumber: string;
  floor: number;
  roomType: RoomType;
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class RoomService {
  private apiUrl = 'http://localhost:8080/api/rooms';

  constructor(private http: HttpClient) {}

  getAllRooms(): Observable<Room[]> {
    return this.http.get<Room[]>(this.apiUrl);
  }
}
