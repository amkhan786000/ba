import { Injectable } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { ApiService } from './api.service';

/**
 * Browser push notifications for this device: notifications pop up on the phone / computer even when Rahbar is
 * closed. Free (no per-message cost). On iPhone / iPad they work only after "Add to Home Screen".
 */
@Injectable({ providedIn: 'root' })
export class PushService {
  constructor(private api: ApiService) {}

  /** The browser can do push at all (needs HTTPS, a service worker and the Push API). */
  get supported(): boolean {
    return typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
  }

  /** "granted", "denied" or "default" (not asked yet). */
  get permission(): NotificationPermission {
    return this.supported ? Notification.permission : 'denied';
  }

  /** iPhone / iPad Safari outside a home-screen app: push needs "Add to Home Screen" first. */
  get needsHomeScreen(): boolean {
    const ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    const standalone = (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) || (navigator as any).standalone === true;
    return ios && !standalone && !('PushManager' in window);
  }

  private registration(): Promise<ServiceWorkerRegistration> {
    return navigator.serviceWorker.register('/sw.js');
  }

  /** Is this browser currently subscribed? */
  async isOn(): Promise<boolean> {
    if (!this.supported) return false;
    const reg = await navigator.serviceWorker.getRegistration('/');
    return !!(reg && (await reg.pushManager.getSubscription()));
  }

  /** Asks permission, subscribes this browser and registers it with the server. */
  async turnOn(): Promise<void> {
    if (!this.supported) throw new Error('This browser does not support notifications.');
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') throw new Error('Notifications were not allowed. You can allow them in the browser\'s site settings.');
    const reg = await this.registration();
    await navigator.serviceWorker.ready;
    const config = await firstValueFrom(this.api.get<{ publicKey: string }>('/push/config'));
    let sub = await reg.pushManager.getSubscription();
    if (!sub) {
      sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(config.publicKey) });
    }
    await firstValueFrom(this.api.post('/push/subscribe', sub.toJSON()));
  }

  /** Unsubscribes this browser and tells the server. */
  async turnOff(): Promise<void> {
    if (!this.supported) return;
    const reg = await navigator.serviceWorker.getRegistration('/');
    const sub = reg ? await reg.pushManager.getSubscription() : null;
    if (!sub) return;
    const endpoint = sub.endpoint;
    await sub.unsubscribe();
    await firstValueFrom(this.api.post('/push/unsubscribe', { endpoint }));
  }

  test(): Observable<{ sent: number; message: string }> {
    return this.api.post<{ sent: number; message: string }>('/push/test', {});
  }
}

function base64UrlToBytes(value: string): Uint8Array {
  const base64 = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}
