import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiUrl}/auth`;

  login(credentials: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, credentials);
  }

  register(userData: any): Observable<any> {
    // Note: API returns text, so we use responseType: 'text'
    return this.http.post(`${this.apiUrl}/register`, userData, { responseType: 'text' });
  }

  googleLogin(idToken: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/google`, { idToken });
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('token');
  }

  getAuthState(): { isAuthenticated: boolean; username: string; roles: string[]; permissions: string[] } {
    const userStr = localStorage.getItem('user');
    if (!userStr) {
      return { isAuthenticated: false, username: '', roles: [], permissions: [] };
    }

    try {
      const user = JSON.parse(userStr);
      return {
        isAuthenticated: this.isLoggedIn(),
        username: user.username || '',
        roles: user.roles || [],
        permissions: user.permissions || [],
      };
    } catch {
      return { isAuthenticated: false, username: '', roles: [], permissions: [] };
    }
  }

  getRoles(): string[] {
    return this.getAuthState().roles;
  }
}
