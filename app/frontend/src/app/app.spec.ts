import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { App } from './app';
import { AuthService } from './core/services/auth.service';

describe('Application shell for a visitor', () => {
  const auth = { isLoggedIn: () => false, login: vi.fn(), register: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('renders public navigation without requesting private account data', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('nav')?.textContent).toContain('HessBnb');
    expect(page.querySelector('nav a[href="/map"]')).not.toBeNull();
    expect(page.querySelector('main router-outlet')).not.toBeNull();
    expect(page.querySelector('nav a[href="/my-bookings"]')).toBeNull();
    expect(page.querySelector('nav a[href="/conversations"]')).toBeNull();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('delegates sign-in to the authentication service', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const login = Array.from(page.querySelectorAll('button'))
      .find(button => button.textContent?.trim() === 'Connexion');
    expect(login).toBeDefined();
    login!.click();
    expect(auth.login).toHaveBeenCalledOnce();
  });
});
