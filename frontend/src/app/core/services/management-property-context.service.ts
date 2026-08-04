import { Injectable } from '@angular/core';
import { BehaviorSubject, distinctUntilChanged } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ManagementPropertyContextService {
  private readonly propertyIdSubject = new BehaviorSubject<number | undefined>(undefined);
  readonly propertyId$ = this.propertyIdSubject.asObservable().pipe(distinctUntilChanged());

  select(propertyId?: number): void {
    this.propertyIdSubject.next(propertyId);
  }
}
