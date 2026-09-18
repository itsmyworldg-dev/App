// Web Push & Android Native Push Bridge
// Supports both Web Push (ServiceWorker PushManager) and Native Android Push (FCM + Native Notification Bridge)
import { apiGet, apiPost, apiDelete } from './api.js';

function urlBase64ToUint8Array(base64url) {
  const padding = '='.repeat((4 - (base64url.length % 4)) % 4);
  const base64 = (base64url + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  const arr = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
  return arr;
}

export function pushSupported() {
  if (typeof window !== 'undefined' && window.AndroidNativeAuth) return true;
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

// Current status for UI purposes: 'unsupported' | 'default' | 'denied' | 'granted'
export function pushPermissionState() {
  if (typeof window !== 'undefined' && window.AndroidNativeAuth) {
    if (typeof window.AndroidNativeAuth.isNotificationPermissionGranted === 'function') {
      return window.AndroidNativeAuth.isNotificationPermissionGranted() ? 'granted' : 'default';
    }
    return 'granted';
  }
  if (!pushSupported()) return 'unsupported';
  return (typeof Notification !== 'undefined' && Notification.permission) ? Notification.permission : 'default';
}

// Safe to call anytime (e.g. right after login) — syncs FCM token to backend on Android,
// or subscribes to Web Push on standard browsers.
export async function syncPushSubscription() {
  if (typeof window !== 'undefined' && window.AndroidNativeAuth) {
    const token = (typeof window.androidFcmToken === 'string' && window.androidFcmToken)
      || (typeof window.AndroidNativeAuth.getFcmToken === 'function' ? window.AndroidNativeAuth.getFcmToken() : null);
    if (token) {
      try {
        await apiPost('/api/save-fcm-token', { token: token, platform: 'android' });
        console.log('[Push] Native FCM token synced to backend successfully');
      } catch(e) {
        console.warn('[Push] Error syncing FCM token:', e);
      }
      if (typeof window.AndroidNativeAuth.syncFcmToken === 'function') {
        const uid = (typeof currentUser !== 'undefined' && currentUser && currentUser.id) ? String(currentUser.id) : '';
        window.AndroidNativeAuth.syncFcmToken(uid);
      }
    }
    return;
  }

  if (!pushSupported() || (typeof Notification !== 'undefined' && Notification.permission !== 'granted')) return;
  try {
    const reg = await navigator.serviceWorker.ready;
    let sub = await reg.pushManager.getSubscription();
    if (!sub) {
      const { vapid_public_key } = await apiGet('/api/config');
      if (!vapid_public_key) return; // server not configured yet
      sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapid_public_key),
      });
    }
    const json = sub.toJSON();
    await apiPost('/api/push', { endpoint: json.endpoint, keys: json.keys });
  } catch (e) { /* non-fatal */ }
}

// Direct user click handler to request permission and subscribe
export async function requestPushPermission() {
  if (typeof window !== 'undefined' && window.AndroidNativeAuth) {
    if (typeof window.AndroidNativeAuth.requestNotificationPermission === 'function') {
      window.AndroidNativeAuth.requestNotificationPermission();
    }
    await syncPushSubscription();
    return 'granted';
  }

  if (!pushSupported()) return 'unsupported';
  const result = await Notification.requestPermission();
  if (result === 'granted') await syncPushSubscription();
  return result;
}

// Called on sign-out to remove push token from server
export async function clearPushSubscription() {
  if (typeof window !== 'undefined' && window.AndroidNativeAuth) {
    try {
      const token = (typeof window.androidFcmToken === 'string' && window.androidFcmToken)
        || (typeof window.AndroidNativeAuth.getFcmToken === 'function' ? window.AndroidNativeAuth.getFcmToken() : null);
      if (token) {
        await apiDelete('/api/save-fcm-token?token=' + encodeURIComponent(token)).catch(() => {});
      }
    } catch(e) {}
    return;
  }

  if (!pushSupported()) return;
  try {
    const reg = await navigator.serviceWorker.ready;
    const sub = await reg.pushManager.getSubscription();
    if (sub) {
      await apiDelete('/api/push?endpoint=' + encodeURIComponent(sub.endpoint)).catch(() => {});
      await sub.unsubscribe();
    }
  } catch (e) { }
}
