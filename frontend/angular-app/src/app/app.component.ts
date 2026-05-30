import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

type AuthMode = 'login' | 'register';
type RequestState = 'idle' | 'busy' | 'success' | 'error';

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: { client_id: string; callback: (response: { credential: string }) => void }) => void;
          prompt: () => void;
        };
      };
    };
  }
}

interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

interface UserProfile {
  id: string;
  userId: string;
  fullName: string;
  phoneNumber: string;
  dateOfBirth: string;
  avatarUrl: string;
  bio: string;
  createdAt: string;
  updatedAt: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly gatewayUrl = 'http://localhost:8080';
  private readonly githubRedirectUri = 'http://127.0.0.1:4200';
  private readonly googleClientId =
    '594110397211-6in9tfk7erl59no491nbn27vug9ju3np.apps.googleusercontent.com';
  private readonly githubClientId = 'Ov23lizJnSHMpMvStDcX';

  mode: AuthMode = 'login';
  state: RequestState = 'idle';
  message = 'Login or create an account to continue.';
  responseTitle = 'Response';
  responseBody = 'Click any action to see the latest backend response here.';
  accessToken = '';
  refreshToken = '';
  profile: UserProfile | null = null;

  loginForm = {
    email: '',
    password: '',
  };

  registerForm = {
    displayName: '',
    email: '',
    password: '',
  };

  profileForm = {
    fullName: '',
    phoneNumber: '',
    dateOfBirth: '',
    avatarUrl: 'https://i.pravatar.cc/320?img=12',
    bio: '',
  };

  ngOnInit(): void {
    const code = new URLSearchParams(window.location.search).get('code');

    if (code) {
      this.message = 'Completing GitHub login...';
      void this.loginWithProvider('GITHUB', code).then(() => {
        window.history.replaceState({}, document.title, window.location.pathname);
      });
    }
  }

  get isLoggedIn(): boolean {
    return Boolean(this.accessToken);
  }

  get displayName(): string {
    return this.profile?.fullName || this.profileForm.fullName || this.registerForm.displayName || 'Your profile';
  }

  get avatarUrl(): string {
    return this.profile?.avatarUrl || this.profileForm.avatarUrl;
  }

  setMode(mode: AuthMode): void {
    this.mode = mode;
    this.message = mode === 'login' ? 'Welcome back.' : 'Create a local account.';
  }

  async register(): Promise<void> {
    await this.run('Account created', async () => {
      const auth = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.gatewayUrl}/api/auth/register`, this.registerForm),
      );

      this.captureAuth(auth);
      this.profileForm.fullName = this.registerForm.displayName;
      this.profileForm.bio = 'Learning microservices with Angular and Docker.';
      this.message = 'Account created. Now save your profile.';
      return auth;
    });
  }

  async login(): Promise<void> {
    await this.run('Logged in', async () => {
      const auth = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.gatewayUrl}/api/auth/login`, {
          providerType: 'LOCAL',
          email: this.loginForm.email,
          password: this.loginForm.password,
        }),
      );

      this.captureAuth(auth);
      this.message = 'Logged in successfully.';
      return auth;
    });
  }

  loginWithGoogle(): void {
    if (!window.google?.accounts?.id) {
      this.state = 'error';
      this.message = 'Google login script is still loading. Try again in a moment.';
      this.responseTitle = 'Google login';
      this.responseBody = this.message;
      return;
    }

    window.google.accounts.id.initialize({
      client_id: this.googleClientId,
      callback: ({ credential }) => {
        void this.loginWithProvider('GOOGLE', credential);
      },
    });
    window.google.accounts.id.prompt();
  }

  loginWithGitHub(): void {
    const scope = 'read:user user:email';
    const url =
      'https://github.com/login/oauth/authorize' +
      `?client_id=${encodeURIComponent(this.githubClientId)}` +
      `&redirect_uri=${encodeURIComponent(this.githubRedirectUri)}` +
      `&scope=${encodeURIComponent(scope)}`;

    window.location.href = url;
  }

  async saveProfile(): Promise<void> {
    await this.run('Profile saved', async () => {
      const profile = await firstValueFrom(
        this.http.post<UserProfile>(`${this.gatewayUrl}/api/users/profile`, this.profileForm, {
          headers: this.authHeaders(),
        }),
      );

      this.setProfile(profile);
      this.message = 'Profile saved.';
      return profile;
    });
  }

  async loadProfile(): Promise<void> {
    await this.run('Profile loaded', async () => {
      const profile = await firstValueFrom(
        this.http.get<UserProfile>(`${this.gatewayUrl}/api/users/profile`, {
          headers: this.authHeaders(),
        }),
      );

      this.setProfile(profile);
      this.message = 'Profile loaded.';
      return profile;
    });
  }

  async checkGateway(): Promise<void> {
    await this.run('Gateway health', async () => {
      const health = await firstValueFrom(this.http.get(`${this.gatewayUrl}/health`));
      this.message = 'Gateway is running.';
      return health;
    });
  }

  private async loginWithProvider(providerType: 'GOOGLE' | 'GITHUB', providerToken: string): Promise<void> {
    await this.run(`${providerType} login`, async () => {
      const auth = await firstValueFrom(
        this.http.post<AuthResponse>(`${this.gatewayUrl}/api/auth/login`, {
          providerType,
          providerToken,
        }),
      );

      this.captureAuth(auth);
      this.message = `Logged in with ${providerType}.`;
      return auth;
    });
  }

  logout(): void {
    this.accessToken = '';
    this.refreshToken = '';
    this.profile = null;
    this.message = 'Logged out locally.';
    this.responseTitle = 'Logged out';
    this.responseBody = 'Token cleared from the browser session.';
  }

  private async run(title: string, action: () => Promise<unknown>): Promise<void> {
    this.state = 'busy';

    try {
      const result = await action();
      this.state = 'success';
      this.responseTitle = title;
      this.responseBody = JSON.stringify(result, null, 2);
    } catch (error) {
      this.state = 'error';
      this.responseTitle = 'Request failed';
      this.responseBody = this.formatError(error);
      this.message = this.responseBody;
    }
  }

  private captureAuth(auth: AuthResponse): void {
    this.accessToken = auth.accessToken;
    this.refreshToken = auth.refreshToken;
  }

  private setProfile(profile: UserProfile): void {
    this.profile = profile;
    this.profileForm = {
      fullName: profile.fullName,
      phoneNumber: profile.phoneNumber,
      dateOfBirth: profile.dateOfBirth,
      avatarUrl: profile.avatarUrl,
      bio: profile.bio,
    };
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders({
      Authorization: `Bearer ${this.accessToken}`,
    });
  }

  private formatError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (typeof error.error === 'string') {
        return error.error;
      }

      return JSON.stringify(error.error || { message: error.message }, null, 2);
    }

    return 'Something went wrong.';
  }
}
