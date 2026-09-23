import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('Protected route access', () => {
  const auth = { isLoggedIn: vi.fn(), login: vi.fn() };
  const attempt = () => TestBed.runInInjectionContext(() =>
    authGuard({} as ActivatedRouteSnapshot, { url: '/my-bookings' } as RouterStateSnapshot));

  beforeEach(() => {
    vi.resetAllMocks();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });

  it('blocks an unauthenticated visitor and starts sign-in', () => {
    auth.isLoggedIn.mockReturnValue(false);
    expect(attempt()).toBe(false);
    expect(auth.login).toHaveBeenCalledOnce();
  });

  it('allows an authenticated visitor without another sign-in', () => {
    auth.isLoggedIn.mockReturnValue(true);
    expect(attempt()).toBe(true);
    expect(auth.login).not.toHaveBeenCalled();
  });
});
