import { Injectable, inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { isPlatformBrowser } from '@angular/common';

export interface AuthState {
  isAuthenticated: boolean;
  username: string;
  fullName: string;
  avatarUrl: string;
  roles: string[];
  permissions: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private platformId = inject(PLATFORM_ID);
  private apiUrl = `${environment.apiUrl}/auth`;

  private authStateSubject = new BehaviorSubject<AuthState>({
    isAuthenticated: false,
    username: '',
    fullName: '',
    avatarUrl: '',
    roles: [],
    permissions: []
  });

  public currentUser$ = this.authStateSubject.asObservable();

  constructor() {
    this.initAuthState();
  }

  private initAuthState() {
    if (isPlatformBrowser(this.platformId)) {
      const token = localStorage.getItem('token');
      const userStr = localStorage.getItem('user');
      
      if (token && userStr) {
        try {
          const user = JSON.parse(userStr);
          this.authStateSubject.next({
            isAuthenticated: true,
            username: user.username || '',
            fullName: user.fullName || '',
            avatarUrl: user.avatarUrl || '',
            roles: user.roles || [],
            permissions: user.permissions || [],
          });
        } catch {
          this.clearAuthState();
        }
      }
    }
  }

  private clearAuthState() {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    }
    this.authStateSubject.next({
      isAuthenticated: false,
      username: '',
      fullName: '',
      avatarUrl: '',
      roles: [],
      permissions: []
    });
  }

  login(credentials: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/login`, credentials);
  }

  register(userData: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, userData, { responseType: 'text' });
  }

  googleLogin(idToken: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/google`, { idToken });
  }

  logout(): void {
    this.clearAuthState();
  }

  isLoggedIn(): boolean {
    return this.authStateSubject.value.isAuthenticated;
  }

  getAuthState(): AuthState {
    return this.authStateSubject.value;
  }
  
  // This method should be called right after successful login
  // to update the local state.
  setSession(token: string, user: any): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(user));
    }
    this.authStateSubject.next({
      isAuthenticated: true,
      username: user.username || '',
      fullName: user.fullName || '',
      avatarUrl: user.avatarUrl || '',
      roles: user.roles || [],
      permissions: user.permissions || []
    });
  }

  updateCurrentUser(user: { username?: string; fullName?: string; avatarUrl?: string | null }): void {
    const currentState = this.authStateSubject.value;
    if (!currentState.isAuthenticated) return;

    const nextState: AuthState = {
      ...currentState,
      username: user.username ?? currentState.username,
      fullName: user.fullName ?? currentState.fullName,
      avatarUrl: user.avatarUrl ?? ''
    };

    if (isPlatformBrowser(this.platformId)) {
      const storedUser = localStorage.getItem('user');
      let sessionUser: Record<string, unknown> = {};
      try {
        sessionUser = storedUser ? JSON.parse(storedUser) : {};
      } catch {
        sessionUser = {};
      }
      localStorage.setItem('user', JSON.stringify({
        ...sessionUser,
        username: nextState.username,
        fullName: nextState.fullName,
        avatarUrl: nextState.avatarUrl
      }));
    }

    this.authStateSubject.next(nextState);
  }

  getRoles(): string[] {
    return this.getAuthState().roles;
  }
}
