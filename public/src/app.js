import { apiGet, apiPost, apiPatch, apiDelete } from './modules/api.js';
import { fileToCompressedDataURI } from './modules/compress.js';
import { haversineKm } from './modules/geo.js';
import { currentUser, setCurrentUser } from './modules/state.js';
import {
  initAuth, checkAuth, requireAuth, loadPublicConfig, renderGoogleButton,
  openAuthModal, closeAuthModal, setAuthTab, doLogin, doSignup, logout,
  hideAuthModalUI
} from './modules/auth.js';
import { pushSupported, pushPermissionState, requestPushPermission } from './modules/push.js';
import {
  escapeHtml, timeAgo, fmtDist, fmtClockTime, bearing, stepText, maneuverRotation,
  avatarInitial, memberSinceLabel, isIOS, isAndroid, haversine,
  hypeBadgeHtml, liveBadgeHtml, navArrowIcon, userLocIcon, searchPinIcon, coercePhotonResult
} from './modules/utils.js';
import { isNetworkError, queueOfflineAction, flushOfflineQueue } from './modules/offlineQueue.js';

/* ---------------- Mock data ---------------- */
const CATS = {
  waterfall:{label:'Waterfalls', color:'#00C2CB'},
  viewpoint:{label:'Viewpoints', color:'#FFB020'},
  restaurant:{label:'Food', color:'#FF3B5C'},
  cafe:{label:'Cafés', color:'#FF8A3D'},
  heritage:{label:'Heritage', color:'#5B21B6'}
};
// Two broad, one-tap groups sitting above the fine-grained category chips.
// Bhukkad ("foodie") rolls up the eating categories; Ghumakkad ("wanderer")
// rolls up the travel/photo-spot categories. Selecting either is just a
// shortcut for filtering to that set of CATS keys — "All" (default) stays
// unfiltered until one of these, or a chip, is tapped.
const GROUPS = {
  bhukkad:{ label:'Bhukkad', sub:'Eat & drink spots', emoji:'🍛', cats:['restaurant','cafe'] },
  ghumakkad:{ label:'Ghumakkad', sub:'Travel & photo spots', emoji:'🎒', cats:['waterfall','viewpoint','heritage'] }
};

// Real data only. Both start empty and are populated from the live API
// (loadPlaces() / loadFeed() below) — no invented spots, no invented posts.
// Add real spots from the admin panel and they'll show up here automatically.
let PLACES = [];
let POSTS = []; // every post — photo or video — the Feed is unified, there's no separate reels array

let activeCat = 'all';
let map, markers = [];
let geoConfirmed = false;
// currentUser now lives in src/modules/state.js (imported above)
const VERIFIED_BADGE = `<svg class="verified-badge" width="13" height="13" viewBox="0 0 24 24" fill="currentColor" title="Official Mera Thikaana account"><path d="M12 1.5l2.6 2.1 3.3-.4 1 3.2 3 1.5-1 3.2 1.9 2.7-2.6 2.1.3 3.3-3.3.4-1.6 2.9-3-1.4-3 1.4-1.6-2.9-3.3-.4.3-3.3-2.6-2.1 1.9-2.7-1-3.2 3-1.5 1-3.2 3.3.4z"/><path d="M9 12.3l2 2 4.2-4.6" fill="none" stroke="var(--white)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
let pendingAction = null;    // fn to re-run once login completes
let liveMode = false;        // true once /api/places responds successfully

/* ---------------- API helpers (now in src/modules/api.js) ---------------- */
/* ---------------- Image compression (now in src/modules/compress.js) ---------------- */

/* ---------------- Auth (now in src/modules/auth.js + state.js) ---------------- */
initAuth({ renderWhoBar, closeUIModal, pushUIModal, refreshUnreadBadge, refreshNotifBadge, showSuggestedFollows: openSuggestedFollows });
function renderWhoBar(){
  renderProfileView();
  renderProfileNavIcon();
}
// Bottom-nav Profile tab shows the user's real avatar (not a generic
// outline) once signed in — same "who am I" glance pattern as Instagram's
// tab bar. Falls back to the outline icon signed out or if no avatar is set.
function renderProfileNavIcon(){
  if(window.AndroidNativeAuth && window.AndroidNativeAuth.updateCurrentUser){
    if(currentUser){
      window.AndroidNativeAuth.updateCurrentUser(
        String(currentUser.id || ''),
        currentUser.handle || '',
        currentUser.avatar_url || '',
        currentUser.display_name || currentUser.handle || ''
      );
    } else {
      window.AndroidNativeAuth.updateCurrentUser('', '', '', '');
    }
  }
  const item = document.querySelector('.navitem[data-v="profile"]');
  if(!item) return;
  if(currentUser && currentUser.avatar_url){
    item.innerHTML = `<div class="navitem-avatar" style="background-image:url('${currentUser.avatar_url}')"></div><span>Profile</span>`;
  } else {
    item.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4.4 3.6-7 8-7s8 2.6 8 7"/></svg><span>Profile</span>`;
  }
}

/* ---- Profile tab: this IS the sign-in/sign-up surface, Google button included ---- */
let profAuthTab = 'login';
function renderProfileView(){
  const el = document.getElementById('profileBody');
  if(!el) return;

  if(currentUser){
    el.innerHTML = `
      <div class="who-bar"><span>Signed in as <b>${currentUser.handle}</b>${currentUser.is_official ? VERIFIED_BADGE : ''}</span><span class="logout" onclick="logout()">Sign out</span></div>
      <div class="p-head">
        <div class="p-avatar" ${currentUser.avatar_url?`style="background-image:url('${currentUser.avatar_url}');background-size:cover;"`:''}>${!currentUser.avatar_url ? avatarInitial(currentUser) : ''}</div>
        <div>
          <div class="p-name">${currentUser.handle}${currentUser.is_official ? VERIFIED_BADGE : ''}</div>
          <div class="p-handle">@${currentUser.handle}</div>
          <div id="pGemsBadge"></div>
          <span class="followpill" style="margin-top:8px;display:inline-block;" onclick="openEditProfile()">Edit profile</span>
        </div>
      </div>
      <div id="pCompletionNudge"></div>
      <div id="pGamification"></div>
      <div id="pushSettingsRow"></div>
      <div class="p-stats">
        <div class="p-stat"><b id="pStatPosts">–</b><span>Posts</span></div>
        <div class="p-stat" style="cursor:pointer;" onclick="openOwnFollowList('followers')"><b id="pStatFollowers">–</b><span>Followers</span></div>
        <div class="p-stat" style="cursor:pointer;" onclick="openOwnFollowList('following')"><b id="pStatFollowing">–</b><span>Following</span></div>
        <div class="p-stat"><b id="pStatThikanas">–</b><span>Thikanas added</span></div>
        <div class="p-stat"><b>${memberSinceLabel(currentUser)}</b><span>Member since</span></div>
      </div>
      <div class="p-tabs">
        <div class="p-tab" id="pTabPosts" onclick="setProfileTab('posts')">Posts</div>
        <div class="p-tab" id="pTabPlaces" onclick="setProfileTab('places')">My Thikanas</div>
      </div>
      <div class="p-grid" id="pGrid"></div>
      <div class="p-menu-row" onclick="showView('biz')">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 21h18M5 21V9l7-5 7 5v12M9 21v-6h6v6"/></svg>
        <span>For businesses</span>
      </div>`;
    setProfileTab(profileTab);
    loadOwnFollowCounts();
    renderPushSettingsRow();
    loadGamificationCard();
    return;
  }

  el.innerHTML = `
    <div class="p-signin-card">
      <div class="p-avatar" style="margin:0 auto 14px;"></div>
      <h3 style="font-family:'Fraunces',serif;color:var(--forest);margin:0 0 4px;">Sign in to Mera Thikaana</h3>
      <p style="color:var(--ink-soft);font-size:12.5px;margin:0 0 18px;">Save posts, submit hidden gems, and claim your business.</p>
      <div id="gsiBtnProfile" style="display:flex;justify-content:center;margin-bottom:14px;"></div>
      <div id="gsiDividerProfile" class="divider">or with a username</div>
      <div id="profTabPills" style="display:flex;gap:8px;justify-content:center;margin-bottom:14px;"></div>
      <div id="profAuthForm"></div>
    </div>`;
  renderProfTabPills();
  renderProfAuthForm();
  setTimeout(renderGoogleButton, 0);
}
// Tab pills live in their own function so switching tabs can refresh just the
// highlight (setProfAuthTab used to only re-render the form below, which left
// "Log in" looking selected even after tapping "Sign up").
// Push notifications row on the signed-in Profile tab — the only place in
// the app allowed to trigger the browser's permission prompt, since that
// requires a direct user gesture (a click), not something we can do on
// page load. Shows nothing on browsers that don't support Push at all
// (e.g. desktop Safari) rather than a button that can never work — except
// on iOS, where "unsupported" almost always just means "not added to the
// Home Screen yet" (iOS only exposes the Push API to an installed PWA,
// never to a regular Safari tab), so that case gets its own explanation
// instead of silently showing nothing.
function renderPushSettingsRow(){
  const el = document.getElementById('pushSettingsRow');
  if(!el) return;
  if(!pushSupported()){
    el.innerHTML = (isIOS() && !isAlreadyInstalled())
      ? `<div class="push-row" style="font-size:12px;color:var(--ink-soft);margin:6px 0 2px;">🔔 On iPhone, notifications only work once Mera Thikaana is added to your Home Screen — tap Share <span style="font-family:inherit;">⬆️</span> then <b>Add to Home Screen</b>, and open it from there.</div>`
      : '';
    return;
  }

  const state = pushPermissionState();
  if(state === 'granted'){
    el.innerHTML = `<div class="push-row" style="font-size:12px;color:var(--ink-soft);margin:6px 0 2px;">🔔 Push notifications are on for this device</div>`;
  } else if(state === 'denied'){
    el.innerHTML = `<div class="push-row" style="font-size:12px;color:var(--ink-soft);margin:6px 0 2px;">🔕 Notifications are blocked in your browser settings</div>`;
  } else {
    el.innerHTML = `<div class="push-row" style="margin:6px 0 2px;">
      <span class="followpill" onclick="enablePushNotifications()">🔔 Enable notifications</span>
      <span style="display:block;font-size:11.5px;color:var(--ink-soft);margin-top:4px;">Get likes, follows and messages even when the app is closed</span>
    </div>`;
  }

  if (window.AndroidNativeAuth && window.AndroidNativeAuth.openDefaultAppSettings) {
    el.innerHTML += `<div class="push-row" style="margin:10px 0 2px;border-top:1px dashed var(--line);padding-top:8px;">
      <span class="followpill" onclick="window.AndroidNativeAuth.openDefaultAppSettings()" style="cursor:pointer;">🔗 Open supported links in app</span>
      <span style="display:block;font-size:11.5px;color:var(--ink-soft);margin-top:4px;">Set Mera Thikaana as default so trip and place links open directly in app instead of browser</span>
    </div>`;
  }
}
async function enablePushNotifications(){
  await requestPushPermission();
  renderPushSettingsRow();
}

/* ---------------- Enable-notifications banner (auto-shown on open) ----------------
   The actual browser permission prompt needs a direct click to fire at all
   (silently calling Notification.requestPermission() on page load either
   does nothing on Safari/iOS or gets treated as spammy and auto-denied by
   some Chrome versions) — so "ask for permission on opening the site"
   means showing OUR OWN banner first, and letting a tap on "Enable" be the
   gesture that triggers the real prompt. Shown once per sign-in, skipped
   if already decided (granted/denied) or unsupported, and snoozed for two
   weeks after a dismiss so it isn't naggy. */
const PUSH_DISMISS_KEY = 'thikana_push_dismissed_at';
const PUSH_SNOOZE_DAYS = 14;
function wasPushRecentlyDismissed(){
  const at = Number(localStorage.getItem(PUSH_DISMISS_KEY) || 0);
  if(!at) return false;
  return (Date.now() - at) / (1000*60*60*24) < PUSH_SNOOZE_DAYS;
}
function hidePushSheetUI(){ document.getElementById('pushSheet').classList.remove('active'); }
function closePushSheet(){ closeUIModal('push', hidePushSheetUI); }
function dismissPushPrompt(){
  closePushSheet();
  localStorage.setItem(PUSH_DISMISS_KEY, String(Date.now()));
}
async function enablePushFromPrompt(){
  await requestPushPermission();
  closePushSheet();
  renderPushSettingsRow(); // in case the Profile tab is already rendered underneath
}
function maybeShowPushPrompt(){
  if(!currentUser || !pushSupported()) return;
  if(Notification.permission !== 'default') return; // already granted or denied — nothing to ask
  if(wasPushRecentlyDismissed()) return;
  if(document.getElementById('installSheet').classList.contains('active')){
    // Don't stack two banners — try again shortly after the install one clears.
    setTimeout(maybeShowPushPrompt, 4000);
    return;
  }
  document.getElementById('pushSheet').classList.add('active');
  pushUIModal('push');
}

// Android Native FCM Token Sync
window.syncNativeFcmToken = async function(token) {
  try {
    const t = token || window.androidFcmToken || (window.AndroidNativeAuth && window.AndroidNativeAuth.getFcmToken ? window.AndroidNativeAuth.getFcmToken() : null);
    if (!t) return;
    window.androidFcmToken = t;
    if (window.AndroidNativeAuth && typeof window.AndroidNativeAuth.syncFcmToken === 'function') {
      const uid = (typeof currentUser !== 'undefined' && currentUser && currentUser.id) ? String(currentUser.id) : '';
      window.AndroidNativeAuth.syncFcmToken(uid);
    }
    // Also sync directly via API fetch with session credentials
    await apiPost('/api/save-fcm-token', { token: t, platform: 'android' });
  } catch(e) {}
};

window.onFcmTokenReceived = function(token) {
  try {
    window.androidFcmToken = token;
    window.syncNativeFcmToken(token);
  } catch(e) {}
};

if (window.AndroidNativeAuth && typeof window.AndroidNativeAuth.getFcmToken === 'function') {
  try {
    const savedToken = window.AndroidNativeAuth.getFcmToken();
    if (savedToken) {
      window.androidFcmToken = savedToken;
      setTimeout(() => window.syncNativeFcmToken(savedToken), 500);
    }
  } catch(e) {}
}
function renderProfTabPills(){
  const el = document.getElementById('profTabPills');
  if(!el) return;
  el.innerHTML = `
    <span onclick="setProfAuthTab('login')" style="padding:6px 14px;border-radius:100px;font-family:'JetBrains Mono',monospace;font-size:11.5px;cursor:pointer;${profAuthTab==='login'?'background:var(--forest);color:var(--white)':'color:var(--ink-soft)'}">Log in</span>
    <span onclick="setProfAuthTab('signup')" style="padding:6px 14px;border-radius:100px;font-family:'JetBrains Mono',monospace;font-size:11.5px;cursor:pointer;${profAuthTab==='signup'?'background:var(--forest);color:var(--white)':'color:var(--ink-soft)'}">Sign up</span>`;
}
function setProfAuthTab(tab){ profAuthTab = tab; renderProfTabPills(); renderProfAuthForm(); }
function setProfAuthMsg(msg, isErr){
  const el = document.getElementById('profAuthMsg');
  if(el){ el.textContent = msg; el.style.color = isErr ? 'var(--rust)' : 'var(--ink-soft)'; }
}
function renderProfAuthForm(){
  const el = document.getElementById('profAuthForm');
  if(!el) return;
  el.innerHTML = profAuthTab === 'login' ? `
      <input type="text" id="profUsername" placeholder="Username" autocomplete="username">
      <input type="password" id="profPass" placeholder="Password" autocomplete="current-password">
      <button class="btn-primary" style="margin-top:2px" onclick="profDoLogin()">Log in</button>
      <div id="profAuthMsg" style="margin-top:10px;font-size:12px;color:var(--ink-soft);"></div>` : `
      <input type="text" id="profUsername" placeholder="Choose a username" autocomplete="username">
      <input type="password" id="profPass" placeholder="Choose a password (4+ characters)" autocomplete="new-password">
      <button class="btn-primary" style="margin-top:2px" onclick="profDoSignup()">Create account</button>
      <div id="profAuthMsg" style="margin-top:10px;font-size:12px;color:var(--ink-soft);"></div>`;
}
async function profDoLogin(){
  const username = document.getElementById('profUsername').value.trim();
  const password = document.getElementById('profPass').value;
  if(!username){ setProfAuthMsg('Enter your username.', true); return; }
  try{
    const { user } = await apiPost('/api/auth/login', { username, password });
    setCurrentUser(user);
    if(window.AndroidNativeAuth && typeof window.AndroidNativeAuth.syncFcmToken === 'function'){
      window.AndroidNativeAuth.syncFcmToken(String(user.id));
    }
    renderWhoBar();
  }catch(e){
    setProfAuthMsg((e.data && e.data.message) || 'Login failed.', true);
  }
}
async function profDoSignup(){
  const username = document.getElementById('profUsername').value.trim();
  const password = document.getElementById('profPass').value;
  if(username.length < 3){ setProfAuthMsg('Username must be at least 3 characters.', true); return; }
  if(password.length < 4){ setProfAuthMsg('Password must be at least 4 characters.', true); return; }
  try{
    const { user } = await apiPost('/api/auth/signup', { username, password });
    setCurrentUser(user);
    if(window.AndroidNativeAuth && typeof window.AndroidNativeAuth.syncFcmToken === 'function'){
      window.AndroidNativeAuth.syncFcmToken(String(user.id));
    }
    renderWhoBar();
    // Fresh account just created — show the "suggested accounts" onboarding
    // step so they don't land on an empty Following feed. Fully skippable.
    setTimeout(openSuggestedFollows, 300);
  }catch(e){
    setProfAuthMsg((e.data && e.data.message) || 'Signup failed.', true);
  }
}
// Persistent sign-in entry point shown on the Discover top bar, so signing in
// isn't only reachable by digging into the Profile tab or hitting a login wall
// mid-action.

/* ---------------- Install app prompt (Android only) ---------------- */
// Android's Chrome fires `beforeinstallprompt` when the PWA criteria (manifest,
// icons, served over https) are met and the app isn't installed yet — iOS never
// fires this event at all, so gating on it already keeps this Android-only in
// practice. We still check the UA and standalone-display state defensively.
const INSTALL_DISMISS_KEY = 'thikana_install_dismissed_at';
const INSTALL_SNOOZE_DAYS = 14;
let deferredInstallPrompt = null;

function isAlreadyInstalled(){
  return window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
}
function wasRecentlyDismissed(){
  const at = Number(localStorage.getItem(INSTALL_DISMISS_KEY) || 0);
  if(!at) return false;
  const days = (Date.now() - at) / (1000*60*60*24);
  return days < INSTALL_SNOOZE_DAYS;
}
function hideInstallSheetUI(){ document.getElementById('installSheet').classList.remove('active'); }
function closeInstallSheet(){ closeUIModal('install', hideInstallSheetUI); }
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredInstallPrompt = e;
  if(isAndroid() && !isAlreadyInstalled() && !wasRecentlyDismissed()){
    document.getElementById('installSheet').classList.add('active');
    pushUIModal('install');
  }
});
window.addEventListener('appinstalled', () => {
  closeInstallSheet();
  localStorage.removeItem(INSTALL_DISMISS_KEY);
  deferredInstallPrompt = null;
});
async function triggerInstall(){
  if(!deferredInstallPrompt){ closeInstallSheet(); return; }
  deferredInstallPrompt.prompt();
  try{ await deferredInstallPrompt.userChoice; }catch(e){}
  deferredInstallPrompt = null;
  closeInstallSheet();
}
function dismissInstallPrompt(){
  closeInstallSheet();
  localStorage.setItem(INSTALL_DISMISS_KEY, String(Date.now()));
}

/* ---------------- Stories (spotlight thikanas) ---------------- */
function buildStories(){
  const row = document.getElementById('storiesRow');
  if(!row) return;
  row.innerHTML = '';
  PLACES.forEach(p=>{
    const el = document.createElement('div');
    el.className = `story${p.isHyped?' is-hyped':''}`;
    el.onclick = ()=>openDetail(p.id);
    el.innerHTML = `
      <div class="story-ring">
        <div class="story-inner">
          <div class="story-thumb${p.img?'':' no-photo'}" ${p.img?`style="background-image:url('${p.img}')"`:''}>${p.img?'':'📍'}</div>
        </div>
        ${p.isHyped ? `<span class="story-hype-tag">🔥</span>` : ''}
      </div>
      <span>${p.gem ? '✦ ' : ''}${escapeHtml(p.name)}</span>`;
    row.appendChild(el);
  });
}
/* ---------------- Chips ---------------- */
function buildChips(){
  const row = document.getElementById('chiprow');
  const hype = document.createElement('div');
  hype.className = 'chip hype-chip'; hype.id = 'chipHype';
  hype.innerHTML = `<span class="flame">🔥</span> Trending`;
  hype.onclick = () => setCat('trending', hype);
  row.appendChild(hype);
  const all = document.createElement('div');
  all.className = 'chip active'; all.textContent = 'All'; all.id = 'chipAll';
  all.onclick = () => setCat('all', all);
  row.appendChild(all);
  Object.entries(CATS).forEach(([key,c])=>{
    const el = document.createElement('div');
    el.className = 'chip';
    el.innerHTML = `<span class="sw" style="background:${c.color}"></span>${c.label}`;
    el.onclick = () => setCat(key, el);
    row.appendChild(el);
  });
}
function buildGroupBar(){
  const row = document.getElementById('groupBar');
  row.innerHTML = '';
  Object.entries(GROUPS).forEach(([key,g])=>{
    const el = document.createElement('div');
    el.className = `groupseg ${key}`;
    el.innerHTML = `<span class="g-emoji">${g.emoji}</span><span class="g-text"><span class="g-label">${g.label}</span><span class="g-sub">${g.sub}</span></span>`;
    // Tapping an already-active group turns it back off (returns to "All")
    // instead of doing nothing — same one-tap-in, one-tap-out feel either way.
    el.onclick = () => setCat(activeCat === key ? 'all' : key, el);
    row.appendChild(el);
  });
}
function setCat(key, el){
  activeCat = key;
  document.querySelectorAll('.chip, .groupseg').forEach(c=>c.classList.remove('active'));
  if(key === 'all'){
    document.getElementById('chipAll').classList.add('active');
  } else if(el){
    el.classList.add('active');
  }
  renderMarkers();
  renderSheetList();
}

/* ---------------- Map (Mappls Vector Maps SDK) ---------------- */
const MAPPLS_KEY = '93923d3d2698b0ce7acc49ccb48f77f3';
// Small compatibility helpers so the rest of the app can keep calling one
// flyTo/removeMarker/etc. shape no matter which Mappls SDK method name
// ends up being the live one on a given build of the map_sdk script.
function mapFlyTo(m, lat, lng, zoom){
  if(!m) return;
  try{
    if(typeof m.flyTo === 'function'){ m.flyTo({ center:{lat,lng}, zoom, duration:700 }); return; }
  }catch(e){}
  if(typeof m.setCenter === 'function') m.setCenter({lat,lng});
  if(zoom != null && typeof m.setZoom === 'function') m.setZoom(zoom);
}
// The underlying SDK's fitBounds() has turned out to be unreliable no
// matter how carefully the bounds box is built — even a proper min/max box
// (see safeBoundsFromPoints below) can still make it send the camera off to
// an empty patch of ocean instead of framing the points, and only a manual
// zoom in/out afterward gets it to redraw correctly. Rather than keep
// chasing that, this never calls the SDK's fitBounds at all: it computes a
// center + zoom itself from a plain min/max box and flies the camera there
// directly.
function safeBoundsFromPoints(points){
  let minLat=Infinity, maxLat=-Infinity, minLng=Infinity, maxLng=-Infinity;
  for(const p of points){
    const lat = p[0], lng = p[1];
    if(!Number.isFinite(lat) || !Number.isFinite(lng)) continue;
    if(lat < minLat) minLat = lat;
    if(lat > maxLat) maxLat = lat;
    if(lng < minLng) minLng = lng;
    if(lng > maxLng) maxLng = lng;
  }
  if(!Number.isFinite(minLat) || !Number.isFinite(minLng)) return null;
  return [[minLat, minLng], [maxLat, maxLng]];
}
// Degrees-of-span → zoom lookup, tuned for this app's always-local (Ranchi
// and nearby) distances rather than a general-purpose formula. Larger spans
// get lower (more zoomed-out) zoom levels.
const AUTOZOOM_TABLE = [
  [0.003,16], [0.006,15], [0.012,14], [0.025,13], [0.05,12],
  [0.1,11], [0.2,10], [0.4,9], [0.8,8], [1.6,7], [3.2,6]
];
function autoZoomForSpan(span){
  for(const [maxSpan, zoom] of AUTOZOOM_TABLE){ if(span <= maxSpan) return zoom; }
  return 5;
}
function safeFitBounds(mapObj, points, padding){
  if(!mapObj) return;
  const bounds = safeBoundsFromPoints(points);
  if(!bounds) return;
  const [[minLat,minLng],[maxLat,maxLng]] = bounds;
  const center = { lat:(minLat+maxLat)/2, lng:(minLng+maxLng)/2 };
  // A single point (both corners equal) — just fly in close on it.
  if(minLat === maxLat && minLng === maxLng){
    mapFlyTo(mapObj, center.lat, center.lng, 15);
    return;
  }
  let zoom = autoZoomForSpan(Math.max(maxLat-minLat, maxLng-minLng));
  if(padding) zoom -= 1; // rough stand-in for the breathing room padding used to give
  mapFlyTo(mapObj, center.lat, center.lng, zoom);
}
function removeMarker(m, mapObj){
  if(!m) return;
  try{ if(typeof m.remove === 'function'){ m.remove(); return; } }catch(e){}
  try{ mappls.remove({ map: mapObj || map, layer: m }); }catch(e){}
}
function markerClick(m, fn){
  if(typeof m.addListener === 'function') m.addListener('click', fn);
  else if(typeof m.on === 'function') m.on('click', fn);
}
function initMap(){
  try{
    if(typeof mappls === 'undefined' || typeof mappls.Map !== 'function'){
      throw new Error('mappls_sdk_unavailable');
    }
    map = new mappls.Map('map', {
      center: {lat:23.38, lng:85.40},
      zoom: 10,
      zoomControl: false,
      search: false,
      geolocation: false,
      // Disables the SDK's own default info-window popup when a native
      // POI icon baked into the base tiles (a restaurant/shop Mappls
      // already knows about, not one of our own markers) gets clicked —
      // that popup links back out to the Mappls app/site, which isn't
      // something the app wants triggerable at all. Per Mappls' own docs,
      // clickableIcons:false on its own isn't reliably enough to fully
      // suppress that default popup — clickableIcons_callback needs to be
      // registered alongside it (even as a no-op) for the override to
      // actually take. The map's own 'click' listener (handleMapClick,
      // wired up right after this map is constructed) is what shows our
      // own popup instead.
      clickableIcons: false,
      clickableIcons_callback: () => {},
      // The SDK's own fullscreen button (if the build injects one) would
      // otherwise fullscreen the bare map via the browser Fullscreen API —
      // not something the app wants control over. Harmless no-op if this
      // build doesn't support the option; the CSS below hides any native
      // control either way.
      fullscreenControl: false
    });
    if(typeof map.on === 'function') map.on('click', handleMapClick);
    renderMarkers();
  }catch(e){
    // Never let a map failure (bad/domain-restricted key, blocked script,
    // slow network, etc.) take the rest of the app down with it — auth,
    // feed, profile, etc. all boot below this call and must still run.
    console.error('Map failed to initialize:', e);
    map = null;
    const mapEl = document.getElementById('map');
    if(mapEl){
      mapEl.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;padding:24px;text-align:center;color:var(--ink-soft,#666);font-size:14px;">Map couldn\'t load right now. Everything else still works — pull down to retry.</div>';
    }
  }
}

/* ---------------- Live data loading (falls back to mock data if the API isn't reachable) ---------------- */
// ---- Cache-first rendering: show whatever we last saw instantly, then
// silently refresh from the network. This is purely a perceived-speed
// layer — /api/* stays the source of truth, this never blocks or replaces
// a real fetch, it just gives the UI something to paint on the very first
// frame instead of a blank/loading state on every open. Guarded because
// localStorage can throw in private-browsing/quota-exceeded situations.
function readLocalCache(key){
  try{ const raw = localStorage.getItem(key); return raw ? JSON.parse(raw) : null; }
  catch(e){ return null; }
}
function writeLocalCache(key, data){
  try{ localStorage.setItem(key, JSON.stringify(data)); }catch(e){ /* quota/private-mode — non-fatal */ }
}

// Shared place-row mapper — used by the initial loadPlaces() fetch AND by
// pollPlacesOnce()'s background refresh, so the two never drift apart.
function mapApiPlace(p){
  let gallery = null;
  if (p.gallery_json) {
    try { const arr = JSON.parse(p.gallery_json); if (Array.isArray(arr) && arr.length) gallery = arr; }
    catch(e) { /* ignore malformed gallery_json */ }
  }
  return {
    id:p.id, name:p.name, cat:p.category, lat:p.lat, lng:p.lng,
    gem: !!p.is_hidden_gem,
    verified: p.status === 'approved',
    desc: p.description || '',
    // No fallback stock photo — if there's no real cover photo yet, the
    // UI shows an honest "no photo yet" placeholder instead of a random
    // stock image of somewhere else.
    img: p.cover_photo_url || '',
    gallery,
    video: p.video_url || null,
    explorerType: p.explorer_type || null,
    bestVisitingTime: p.best_visiting_time || null,
    difficulty: p.difficulty_level || null,
    parkingInfo: p.parking_info || null,
    routeInfo: p.route_info || null,
    customRouteNotes: p.custom_route_notes || null,
    // Hype = real recent traffic (fresh posts + the likes/comments they
    // pulled in), computed server-side in /api/places. isHyped flags the
    // handful of spots currently trending; hypeRank is their 1-based
    // order among those (1 = most active right now).
    hypeScore: p.hype_score || 0,
    isHyped: !!p.is_hyped,
    hypeRank: p.hype_rank || null,
    // "Live Thikana" — how many people are checked in right now (see
    // functions/api/place-presence.js). Who exactly (friends vs strangers)
    // is only fetched on demand when the detail sheet opens — this count
    // alone is enough to badge the card/marker without a per-place fetch.
    liveCount: p.live_count || 0,
    submittedBy: p.submitted_by || null,
    submittedByHandle: p.submitted_by_handle || null,
    status: p.status || 'approved'
  };
}
async function loadPlaces(){
  const cached = readLocalCache('thikana_cache_places');
  if(cached && cached.length && !PLACES.length){
    PLACES = cached;
    liveMode = true;
    renderMarkers(); renderSheetList(); buildUploadSelect(); buildStories();
  } else if(!PLACES.length){
    showSheetListSkeleton();
  }
  try{
    const { places } = await apiGet('/api/places');
    if(places && places.length){
      PLACES = places.map(mapApiPlace);
      liveMode = true;
      writeLocalCache('thikana_cache_places', PLACES);
    }
  }catch(e){ /* stay on mock/cached data — expected when previewing this file standalone or offline */ }
  renderMarkers();
  renderSheetList();
  buildUploadSelect();
  buildStories();
}
let feedMode = 'explore'; // 'explore' | 'following'
function mapApiPost(p){
  // media_json (when present) holds every image of a multi-photo post as a
  // JSON array; img/gallery[0] is always the same as media_data either way,
  // so anything that only reads .img keeps working unchanged.
  let gallery = null;
  if (p.media_json) {
    try { const arr = JSON.parse(p.media_json); if (Array.isArray(arr) && arr.length > 1) gallery = arr; }
    catch(e) { /* ignore malformed media_json */ }
  }
  return {
    id:p.id, user:p.handle, user_id:p.user_id, avatar_url:p.avatar_url,
    place:p.place_name, place_id:p.place_id,
    img:p.media_data, gallery, media_type:p.media_type, post_kind:p.post_kind||'post',
    cap:p.caption||'', likes:p.likes_count||0, liked:!!p.is_liked, saved:!!p.is_saved,
    comments_count:p.comments_count||0, official: !!p.is_official,
    edited: !!p.updated_at,
  };
}
// Set on every cold load / mode switch; cleared once loadMoreFeed() hits an
// empty page, so the infinite-scroll listener knows to stop asking.
let feedNoMorePosts = false;
let feedLoadingMore = false;

async function loadFeed(mode){
  if(mode) feedMode = mode;
  feedNoMorePosts = false;
  const cacheKey = 'thikana_cache_feed_' + feedMode;
  const cached = readLocalCache(cacheKey);
  if(cached && cached.length){
    POSTS = cached;
    liveMode = true;
    renderFeed();
    if(currentUser) renderProfileGrid();
  } else if(!POSTS.length){
    showFeedSkeleton();
  }
  try{
    const path = feedMode === 'following' ? '/api/posts?feed=following' : '/api/posts';
    const { posts } = await apiGet(path);
    POSTS = (posts || []).map(mapApiPost);
    liveMode = true;
    writeLocalCache(cacheKey, POSTS);
  }catch(e){
    // Following feed needs a live backend + login — explore falls back to mock/cached
    // data, following just shows empty (unless we already painted a cached copy above)
    // rather than mixing in demo posts that aren't "followed".
    if(feedMode === 'following' && !cached) POSTS = [];
  }
  renderFeed();
  if(currentUser) renderProfileGrid();
}
// Infinite scroll: fetches one older page (posts.id < the oldest post
// currently on screen) and appends it. The backend ranks that page
// unseen-first (see posts.js), so an old post the viewer hasn't seen yet
// surfaces ahead of an old one they have — this, plus loadFeed() above no
// longer being the only way to reach older posts, is what actually gets a
// returning visitor past "the same two posts on top" (see feed-seen
// tracking below for the other half: marking posts as seen in the first
// place).
async function loadMoreFeed(){
  if(feedLoadingMore || feedNoMorePosts || !POSTS.length) return;
  feedLoadingMore = true;
  try{
    const oldestId = POSTS.reduce((min,p)=> p.id < min ? p.id : min, POSTS[0].id);
    const { posts } = await apiGet(feedApiUrl('before_id=' + oldestId));
    const older = (posts || []).map(mapApiPost);
    if(!older.length){ feedNoMorePosts = true; return; }
    POSTS = POSTS.concat(older);
    appendFeedPosts(older);
  }catch(e){ /* transient — next scroll tick just tries again */ }
  finally{ feedLoadingMore = false; }
}
function setFeedMode(mode){
  document.getElementById('feedTabExplore').classList.toggle('active', mode==='explore');
  document.getElementById('feedTabFollowing').classList.toggle('active', mode==='following');
  if(mode === 'following' && !currentUser){ openAuthModal(); return; }
  feedPendingNewPosts = null; feedPendingNewCount = 0; hideFeedNewPill(); // a mode switch invalidates anything queued for the old feed
  loadFeed(mode);
}

/* ---------------- Live feed refresh (Instagram-style) ----------------
   loadFeed() above is the "cold load" path (cache → full network replace →
   full render). This is the background keep-alive: while the Feed tab is
   actually on screen it quietly re-checks every FEED_POLL_MS and reconciles
   instead of re-loading — using two small targeted requests rather than
   re-fetching the top 50 and diffing a snapshot (that approach's flaw: the
   feed endpoint only ever returns the newest 50, so a post pushed out of
   that window by newer posts looked identical to an actual delete — a false
   "vanish" under enough feed volume). Now:
     - additions come from GET /api/posts?since_id=<highest id we've seen> —
       genuinely new posts only, never a paging artifact;
     - deletions come from GET /api/posts?ids=<ids currently on screen> —
       whichever of those ids don't come back were actually deleted, checked
       directly rather than inferred from a snapshot.
   New posts slide straight in if the reader is already at the top,
   otherwise they're held behind a tappable "N new posts" pill so an
   in-progress read/scroll is never yanked out from under someone. */
const FEED_POLL_MS = 8000;
const FEED_IDS_CHECK_CAP = 100; // matches MAX_IDS_LOOKUP in functions/api/posts.js — only the newest on-screen ids need a delete-check each tick
let feedPollTimer = null;
let feedPendingNewPosts = null; // posts queued behind the pill, newest first, not yet merged into POSTS
let feedPendingNewCount = 0;
let feedMaxSeenId = 0; // highest post id currently rendered — drives since_id on the next poll

function feedIsAtTop(){
  const list = document.getElementById('feedList');
  return !!list && list.scrollTop < 40;
}
function feedApiUrl(extraQuery){
  const base = feedMode === 'following' ? '/api/posts?feed=following' : '/api/posts';
  return base + (base.includes('?') ? '&' : '?') + extraQuery;
}
function recomputeFeedMaxSeenId(){
  feedMaxSeenId = POSTS.reduce((max,p)=>Math.max(max,p.id), 0);
  if(feedPendingNewPosts) feedMaxSeenId = feedPendingNewPosts.reduce((max,p)=>Math.max(max,p.id), feedMaxSeenId);
}
async function pollFeedOnce(){
  if(document.hidden) return;

  // 1) Deletions — ask only about ids actually on screen or queued (never
  // the whole feed), so there's never a "was it deleted or just paged out"
  // question. Pending (queued-behind-the-pill) ids go first since that set
  // is usually small; whatever room is left is filled from what's visible.
  const idsFromPending = feedPendingNewPosts ? feedPendingNewPosts.map(p=>p.id) : [];
  const idsFromScreen = POSTS.map(p=>p.id);
  const checkIds = idsFromPending.concat(idsFromScreen).slice(0, FEED_IDS_CHECK_CAP);
  if(checkIds.length){
    let stillExist;
    try{
      const data = await apiGet(feedApiUrl('ids=' + checkIds.join(',')));
      stillExist = new Set((data.posts || []).map(p=>p.id));
    }catch(e){ stillExist = null; } // transient hiccup — skip the delete-check this tick, don't guess
    if(stillExist){
      const removedIds = checkIds.filter(id=>!stillExist.has(id));
      if(removedIds.length){
        removedIds.forEach(id=>{
          const node = document.querySelector(`#feedList [data-post-id="${id}"]`);
          if(node) node.remove();
        });
        POSTS = POSTS.filter(p=>!removedIds.includes(p.id));
        if(feedPendingNewPosts) feedPendingNewPosts = feedPendingNewPosts.filter(p=>!removedIds.includes(p.id));
      }
    }
  }

  // 2) Additions — genuinely new posts only, via since_id.
  recomputeFeedMaxSeenId();
  let added;
  try{
    const data = await apiGet(feedApiUrl('since_id=' + feedMaxSeenId));
    added = (data.posts || []).map(mapApiPost);
  }catch(e){ added = []; }

  if(added.length){
    if(feedIsAtTop() && !feedPendingNewCount){
      POSTS = added.concat(POSTS);
      renderFeed();
      if(currentUser) renderProfileGrid();
    } else {
      feedPendingNewPosts = added.concat(feedPendingNewPosts || []);
      feedPendingNewCount = feedPendingNewPosts.length;
      showFeedNewPill(feedPendingNewCount);
    }
  }
  writeLocalCache('thikana_cache_feed_' + feedMode, POSTS);
}
function showFeedNewPill(count){
  const list = document.getElementById('feedList');
  let pill = document.getElementById('feedNewPill');
  if(!pill){
    pill = document.createElement('div');
    pill.id = 'feedNewPill';
    pill.className = 'feed-new-pill';
    pill.onclick = applyFeedPending;
    list.insertBefore(pill, list.firstChild); // sticky only works positioned within the scroll container itself
  }
  pill.textContent = count === 1 ? 'New post ↑' : `${count} new posts ↑`;
  pill.classList.add('show');
  if(typeof updateVibesBadge === 'function') updateVibesBadge(count);
}
function hideFeedNewPill(){
  const pill = document.getElementById('feedNewPill');
  if(pill) pill.classList.remove('show');
  if(typeof updateVibesBadge === 'function') updateVibesBadge(0);
}
function applyFeedPending(){
  if(feedPendingNewPosts) POSTS = feedPendingNewPosts.concat(POSTS);
  feedPendingNewPosts = null;
  feedPendingNewCount = 0;
  hideFeedNewPill();
  if(typeof updateVibesBadge === 'function') updateVibesBadge(0);
  renderFeed();
  if(currentUser) renderProfileGrid();
  document.getElementById('feedList').scrollTo({ top:0, behavior:'smooth' });
}
function startFeedPoll(){
  stopFeedPoll();
  feedPollTimer = setInterval(pollFeedOnce, FEED_POLL_MS);
}
function stopFeedPoll(){ if(feedPollTimer){ clearInterval(feedPollTimer); feedPollTimer = null; } }
// If the reader scrolls back to the top themselves while a "new posts" pill
// is showing, just fold it in for them rather than making them tap it too.
// (Module scripts run after the DOM is parsed, same as the rest of this
// file's top-level init code below — no DOMContentLoaded wait needed here.)
document.getElementById('feedList')?.addEventListener('scroll', ()=>{
  if(feedPendingNewCount && feedIsAtTop()) applyFeedPending();
}, { passive:true });
// Infinite scroll — fires loadMoreFeed() (see above) once the reader is
// within ~2 screens of the bottom, same "load a bit before you need it"
// margin as most feeds use so it never shows a bare loading gap.
const FEED_LOAD_MORE_MARGIN_PX = 1200;
document.getElementById('feedList')?.addEventListener('scroll', ()=>{
  const list = document.getElementById('feedList');
  if(!list) return;
  if(list.scrollHeight - list.scrollTop - list.clientHeight < FEED_LOAD_MORE_MARGIN_PX) loadMoreFeed();
}, { passive:true });
// A pending "seen" batch shouldn't get lost just because the tab was
// backgrounded or closed mid-flush-delay.
document.addEventListener('visibilitychange', ()=>{ if(document.hidden) flushFeedSeen(); });

/* ---------------- Live places/thikana refresh ----------------
   Same idea as the feed poll above, but for the map + Discover list: while
   Discover is on screen (the only place a detail sheet can be opened from —
   see jumpToPlace), quietly re-fetch /api/places and reconcile. A place
   removed by an admin disappears from the map/list within one tick for
   every viewer, and if its detail sheet happens to be open right now, it's
   closed with a toast instead of being left showing a ghost thikana. */
const PLACES_POLL_MS = 15000;
let placesPollTimer = null;
async function pollPlacesOnce(){
  if(document.hidden) return;
  let places;
  try{
    const data = await apiGet('/api/places');
    places = data.places || [];
  }catch(e){ return; }
  if(!places.length) return;

  const newIds = new Set(places.map(p=>p.id));
  const removedIds = PLACES.filter(p=>!newIds.has(p.id)).map(p=>p.id);
  const changed = removedIds.length || places.length !== PLACES.length || places.some(p=>!PLACES.find(x=>x.id===p.id));
  if(!changed) return; // nothing moved — skip the marker/list rebuild entirely

  if(removedIds.length && openDetailId != null && removedIds.includes(openDetailId)){
    closeDetail();
    showToast('This thikana was removed.', 2600);
  }

  PLACES = places.map(mapApiPlace);
  writeLocalCache('thikana_cache_places', PLACES);
  renderMarkers();
  renderSheetList();
  buildStories();
}
function startPlacesPoll(){
  stopPlacesPoll();
  placesPollTimer = setInterval(pollPlacesOnce, PLACES_POLL_MS);
}
function stopPlacesPoll(){ if(placesPollTimer){ clearInterval(placesPollTimer); placesPollTimer = null; } }

let pinIconSeq = 0;
function pinIcon(cat, gem, verified, hyped, img, liveCount){
  const color = CATS[cat].color;
  const uid = `pinclip${++pinIconSeq}`;
  const ringColor = verified === false ? '#FF3B5C' : '#FFB020';
  // "Live Thikana" corner dot — a small pulsing green marker on top of the
  // pin, independent of hyped/gem/verified status, when anyone's actually
  // checked in right now (see functions/api/place-presence.js). Same
  // transform/opacity-only pulse shape as hype-pulse below — no
  // box-shadow animation, so it costs nothing extra to run continuously
  // on every live pin on the map at once.
  function liveDot(cx, cy){
    if(!liveCount) return '';
    return `<circle cx="${cx}" cy="${cy}" r="6.4" fill="none" stroke="#22C55E" stroke-width="1.5" opacity="0.6" class="live-pulse-ring"/>
      <circle cx="${cx}" cy="${cy}" r="4" fill="#22C55E" stroke="#FFFFFF" stroke-width="1.5"/>`;
  }
  // Trending pins get a wider canvas: a pulsing amber halo behind the usual
  // dot, plus a small flame chip — bigger and busier on purpose, so a
  // hyped spot visually pulls the eye on the map the same way it jumps to
  // the top of the lists.
  if(hyped){
    const size = 40, cx = 20, cy = 20, r = 15;
    const photo = img ? `
        <defs><clipPath id="${uid}"><circle cx="${cx}" cy="${cy}" r="${r}"/></clipPath></defs>
        <circle cx="${cx}" cy="${cy}" r="${r}" fill="${color}"/>
        <image href="${img}" xlink:href="${img}" x="${cx-r}" y="${cy-r}" width="${r*2}" height="${r*2}" clip-path="url(#${uid})" preserveAspectRatio="xMidYMid slice"/>
        <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#FFFFFF" stroke-width="2.5"/>`
      : `<circle cx="${cx}" cy="${cy}" r="${r-3}" fill="${color}" stroke="#FFFFFF" stroke-width="3"/>`;
    return { html:`<div style="width:${size}px;height:${size}px;">
      <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" style="overflow:visible">
        <circle cx="${cx}" cy="${cy}" r="18" fill="none" stroke="#FFB020" stroke-width="2.5" opacity="0.55" class="hype-pulse"/>
        ${gem ? `<circle cx="${cx}" cy="${cy}" r="${r+1}" fill="none" stroke="${ringColor}" stroke-width="2" stroke-dasharray="2 2"/>` : ''}
        ${photo}
        <text x="${cx}" y="7" font-size="13" text-anchor="middle">🔥</text>
        ${liveDot(34, 6)}
      </svg></div>`,
      width:size, height:size };
  }
  // A small precise circular photo pin, centered exactly on the spot's
  // coordinate (same anchor convention as the plain dot it replaces) —
  // shows the place's actual cover photo with a category-colour ring,
  // falling back to a plain coloured dot when there's no photo yet.
  const size = 30, cx = 15, cy = 15, r = 11;
  const photo = img ? `
      <defs><clipPath id="${uid}"><circle cx="${cx}" cy="${cy}" r="${r}"/></clipPath></defs>
      <circle cx="${cx}" cy="${cy}" r="${r}" fill="${color}"/>
      <image href="${img}" xlink:href="${img}" x="${cx-r}" y="${cy-r}" width="${r*2}" height="${r*2}" clip-path="url(#${uid})" preserveAspectRatio="xMidYMid slice"/>
      <circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="#FFFFFF" stroke-width="2"/>`
    : `<circle cx="${cx}" cy="${cy}" r="8" fill="${color}" stroke="#FFFFFF" stroke-width="2.5"/>`;
  return { html:`<div style="width:${size}px;height:${size}px;"><svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" style="overflow:visible">
      ${gem ? `<circle cx="${cx}" cy="${cy}" r="${r+2}" fill="none" stroke="${ringColor}" stroke-width="2" stroke-dasharray="2 2"/>` : ''}
      ${photo}
      ${liveDot(25, 5)}
    </svg></div>`,
    width:size, height:size };
}
function renderMarkers(){
  if(!map) return; // Mappls SDK script (or the config it needs) hasn't finished loading yet
  markers.forEach(m=>removeMarker(m));
  markers = [];
  // Plain pins first, hyped ones last — added later so trending spots sit on
  // top of the pile instead of getting buried under nearby ordinary pins.
  const list = filteredPlaces().slice().sort((a,b)=> (a.isHyped?1:0) - (b.isHyped?1:0));
  list.forEach(p=>{
    const icon = pinIcon(p.cat,p.gem,p.verified,p.isHyped,p.img,p.liveCount);
    const m = new mappls.Marker({ map:map, position:{lat:p.lat,lng:p.lng}, html:icon.html, width:icon.width, height:icon.height, fitbounds:false });
    markerClick(m, ()=>openDetail(p.id));
    markers.push(m);
  });
}
// Verification badge for a place card/detail view. A hidden gem that hasn't
// been approved yet reads "unverified" (rust, the gem-badge default color);
// once an admin approves it, it flips to a forest checkmark "verified" —
// same visual language as the official-account post badge.
function placeBadgeHtml(p){
  if(p.gem){
    return p.verified
      ? `<div class="gem-badge" style="color:var(--forest)">${VERIFIED_BADGE} Verified Thikaana</div>`
      : `<div class="gem-badge"><span class="spark">✦</span> Community Added</div>`;
  }
  return `<div class="gem-badge" style="color:var(--ink-soft)">Community verified</div>`;
}
// "Discovered by @handle" credit (Task 1 growth loop) — quiet social proof
// on a hidden gem's detail sheet, not a popup. Only shows once the places
// API has a submitter *and* their handle (seeded/official spots have
// neither). Taps straight into that person's profile, same as any other
// avatar/handle in the app.
function discoveredByHtml(p){
  if(!p.submittedBy || !p.submittedByHandle) return '';
  return `<div class="discovered-by" style="font-family:'JetBrains Mono',monospace;font-size:11px;color:var(--ink-soft);margin:2px 0 10px;cursor:pointer;" onclick="openUserProfile(${p.submittedBy})">Discovered by <b style="color:var(--forest);">@${escapeHtml(p.submittedByHandle)}</b></div>`;
}
// Nearest-first ordering, applied on top of whatever category/group filter
// is active — so results are always location-wise, and only the *category*
// changes when a chip or group is tapped.
let userLoc = null; // {lat,lng} once geolocation resolves; null = keep default order
/* haversineKm now in src/modules/geo.js */
// ---- Last-known-location cache -----------------------------------------
// A real GPS fix always wins once it comes in, but that can take a few
// seconds (or fail entirely — permission denied, no signal indoors, etc).
// Rather than leaving distance sorting/"near me" blank until then, seed
// userLoc immediately from wherever the device last reported being, read
// from localStorage below. It's just a starting guess — every real fix
// (requestUserLocation, locateMe, or a GPS tick during navigation)
// overwrites both userLoc and this cache with the fresh position.
const LAST_LOC_KEY = 'thikana_last_loc';
function saveLastKnownLoc(lat, lng){
  try{ localStorage.setItem(LAST_LOC_KEY, JSON.stringify({ lat, lng, ts: Date.now() })); }
  catch(e){ /* quota/private-mode — just skip the cache, no functional loss */ }
}
function loadLastKnownLoc(){
  try{ return JSON.parse(localStorage.getItem(LAST_LOC_KEY)); }
  catch(e){ return null; }
}
(function seedLastKnownLoc(){
  const last = loadLastKnownLoc();
  if(last && typeof last.lat === 'number' && typeof last.lng === 'number') userLoc = { lat: last.lat, lng: last.lng };
})();
function distKm(p){ return userLoc ? haversineKm(userLoc.lat, userLoc.lng, p.lat, p.lng) : null; }
function distText(p){
  const km = distKm(p);
  if(km === null) return '';
  return km < 1 ? `${Math.round(km*1000)} m away` : `${km.toFixed(1)} km away`;
}
function requestUserLocation(){
  if(!navigator.geolocation) return;
  navigator.geolocation.getCurrentPosition(
    pos => {
      userLoc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      saveLastKnownLoc(userLoc.lat, userLoc.lng);
      renderMarkers(); renderSheetList(); buildStories();
    },
    () => { /* denied/unavailable — silently fall back to default order, no nagging */ },
    { enableHighAccuracy:false, timeout:8000, maximumAge:300000 }
  );
}
/* ---------------- "My location" button ---------------- */
let userLocMarker = null;
// Explicit, user-tapped version of requestUserLocation(): unlike the quiet
// one that runs on load just to sort by distance, this one gives visible
// feedback (a spinning icon, a real error toast) and actually recenters the
// map on a "you are here" dot — the button people expect bottom-right on
// any map, same spot Google Maps puts it.
function locateMe(){
  const btn = document.getElementById('locateBtn');
  if(!navigator.geolocation){
    showToast("Location isn't available on this device.");
    return;
  }
  btn.classList.add('is-locating');
  navigator.geolocation.getCurrentPosition(
    pos => {
      userLoc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
      saveLastKnownLoc(userLoc.lat, userLoc.lng);
      btn.classList.remove('is-locating');
      btn.classList.add('is-active');
      if(userLocMarker) removeMarker(userLocMarker);
      const uicon = userLocIcon();
      userLocMarker = new mappls.Marker({ map:map, position:{lat:userLoc.lat,lng:userLoc.lng}, html:uicon.html, width:uicon.width, height:uicon.height, fitbounds:false });
      mapFlyTo(map, userLoc.lat, userLoc.lng, Math.max(map.getZoom ? map.getZoom() : 14, 14));
      renderMarkers(); renderSheetList(); buildStories();
    },
    err => {
      btn.classList.remove('is-locating');
      showToast(err.code === 1
        ? "Location access denied — enable it in your browser/device settings."
        : "Couldn't get your location right now.");
    },
    { enableHighAccuracy:true, timeout:10000, maximumAge:0 }
  );
}

/* ---------------- Real-world place search (Mappls Place Search + your own thikanas) ---------------- */
// Two sources, one box: your own PLACES (matched instantly, client-side,
// no network call) always show first, then real-world results below —
// so searching "Hundru" finds your already-added Hundru Falls thikana
// itself, not just a generic address near it.
let mapSearchTimer = null;
let mapSearchMarker = null;
let mapSearchResultsData = [];
let mapSearchOpen = false;

// Collapsed-to-icon search bar: tapping the pill (or the icon while open,
// which has morphed into a back arrow) toggles between a plain round
// button and the full search field, with the results panel + a dimmed
// backdrop fading in alongside it.
function toggleMapSearch(e){
  if(e) e.stopPropagation();
  mapSearchOpen ? collapseMapSearch() : expandMapSearch();
}
function expandMapSearch(){
  if(mapSearchOpen) return;
  mapSearchOpen = true;
  document.getElementById('mapSearch').classList.add('expanded');
  document.getElementById('mapSearchBackdrop').classList.add('show');
  const input = document.getElementById('mapSearchInput');
  setTimeout(()=>{ input.focus(); if(input.value) onMapSearchInput(); }, 220);
}
function collapseMapSearch(){
  if(!mapSearchOpen) return;
  mapSearchOpen = false;
  document.getElementById('mapSearch').classList.remove('expanded');
  document.getElementById('mapSearchBackdrop').classList.remove('show');
  document.getElementById('mapSearchInput').blur();
  hideMapSearchResults();
}
function onMapSearchKeydown(e){
  if(e.key === 'Escape') collapseMapSearch();
}
function localPlaceMatches(q){
  const ql = q.toLowerCase();
  return PLACES.filter(p => p.name.toLowerCase().includes(ql)).slice(0, 6);
}
function renderMapSearchResults(localMatches, remoteResults, remoteState){
  // remoteState: null (not searched yet), 'loading', 'error', or 'done'
  let html = '';
  if(localMatches.length){
    html += `<div class="map-search-section">Your thikanas</div>`;
    html += localMatches.map(p=>{
      const c = CATS[p.cat];
      return `<div class="map-search-item" onclick="pickLocalPlaceResult(${p.id})">
        <span class="msi-pin" style="color:${c.color}">📍</span>
        <div class="msi-text">
          <b>${escapeHtml(p.name)}${p.isHyped?' 🔥':''}</b>
          <span>${c.label}${p.gem?' · Hidden gem':''}</span>
        </div>
      </div>`;
    }).join('');
  }
  if(remoteState === 'loading'){
    html += `<div class="map-search-loading">Searching…</div>`;
  } else if(remoteState === 'error'){
    html += `<div class="map-search-empty">Place search failed — check your connection.</div>`;
  } else if(remoteState === 'done'){
    if(remoteResults.length){
      html += `<div class="map-search-section">More places</div>`;
      html += remoteResults.map((r,i)=>`
        <div class="map-search-item" onclick="pickMapSearchResult(${i})">
          <span class="msi-pin">🌐</span>
          <div class="msi-text">
            <b>${(r.display_name||'').split(',')[0]}</b>
            <span>${r.display_name||''}</span>
          </div>
        </div>`).join('');
    } else if(!localMatches.length){
      html += `<div class="map-search-empty">No matching places found — try a different search.</div>`;
    }
  }
  const box = document.getElementById('mapSearchResults');
  if(!html){ hideMapSearchResults(); return; }
  box.innerHTML = html;
  box.style.display = 'block';
  requestAnimationFrame(()=> box.classList.add('show'));
}
function onMapSearchInput(){
  const q = document.getElementById('mapSearchInput').value.trim();
  document.getElementById('mapSearchClear').style.display = q ? 'flex' : 'none';
  document.getElementById('mapSearch').classList.toggle('has-value', !!q);
  clearTimeout(mapSearchTimer);
  if(!q.length){ hideMapSearchResults(); return; }
  const localMatches = localPlaceMatches(q);
  if(q.length < 3){
    // Too short to bother the Mappls search API yet, but your own thikanas can still
    // match instantly since that's just a local array filter.
    renderMapSearchResults(localMatches, [], null);
    return;
  }
  renderMapSearchResults(localMatches, [], 'loading');
  mapSearchTimer = setTimeout(()=>runMapSearch(q, localMatches), 400);
}
// Uses Photon (photon.komoot.io), a free OSM-backed geocoder, for the
// "remote" place lookup — no API key required, so this keeps working
// independent of the Mappls key's status/domain-whitelisting. The map
// itself (rendering, pins, routing) is untouched and still runs on Mappls;
// this only replaces the text-search-as-you-type lookup.
// Biased (not restricted) toward wherever the person is / Jharkhand, where
// the seeded thikanas are, via Photon's lat/lon bias params — a search for
// "Hundru" should rank the right region first without blocking searches
// anywhere else.
async function runMapSearch(q, localMatches){
  try{
    const loc = userLoc ? [userLoc.lat, userLoc.lng] : [23.38, 85.40];
    const url = `https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&lat=${loc[0]}&lon=${loc[1]}&limit=7`;
    const res = await fetch(url);
    if(!res.ok) throw new Error(`photon_http_${res.status}`);
    const data = await res.json();
    const list = (data && data.features) || [];
    mapSearchResultsData = list.map(coercePhotonResult).filter(Boolean).slice(0, 7);
    renderMapSearchResults(localMatches, mapSearchResultsData, 'done');
  }catch(e){
    console.error('[map search] failed:', e); // check devtools console for the real cause
    renderMapSearchResults(localMatches, [], 'error');
  }
}
// Picking one of *your own* thikanas from the search results: just opens
// its normal detail view, same as tapping its pin or card would — no
// separate OSM-style pin/popup needed since it's already a real listing.
function pickLocalPlaceResult(id){
  const p = PLACES.find(x=>x.id===id);
  document.getElementById('mapSearchInput').value = p ? p.name : '';
  document.getElementById('mapSearchClear').style.display = 'flex';
  document.getElementById('mapSearch').classList.toggle('has-value', !!p);
  collapseMapSearch();
  if(mapSearchMarker){ removeMarker(mapSearchMarker); mapSearchMarker = null; }
  if(p) mapFlyTo(map, p.lat, p.lng, 15);
  openDetail(id);
}
function pickMapSearchResult(i){
  const r = mapSearchResultsData[i];
  if(!r) return;
  const lat = parseFloat(r.lat), lng = parseFloat(r.lon);
  const label = (r.display_name||'Selected place').split(',')[0];
  const full = r.display_name || '';
  document.getElementById('mapSearchInput').value = label;
  document.getElementById('mapSearchClear').style.display = 'flex';
  document.getElementById('mapSearch').classList.add('has-value');
  collapseMapSearch();
  if(mapSearchMarker) removeMarker(mapSearchMarker);
  const spicon = searchPinIcon();
  mapSearchMarker = new mappls.Marker({ map:map, position:{lat,lng}, html:spicon.html, width:spicon.width, height:spicon.height, fitbounds:false });
  const popupHtml = `
    <div style="font-family:'JetBrains Mono',monospace;font-size:11.5px;max-width:200px;">
      <b style="font-family:'Fraunces',serif;font-size:14px;color:#1A1523;display:block;margin-bottom:3px;">${label}</b>
      <span style="color:#746E86;">${full}</span>
      <div class="map-search-popup-actions">
        <button class="map-search-navigate" onclick="navigateToDestination({lat:${lat},lng:${lng},name:'${label.replace(/'/g,"\\'")}'})">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><polygon points="3 11 22 2 13 21 11 13 3 11"/></svg>
          Navigate
        </button>
        <button class="map-search-suggest" onclick="suggestGemFromSearch(${lat},${lng},'${label.replace(/'/g,"\\'")}')">✦ Suggest</button>
      </div>
    </div>`;
  if(typeof mapSearchMarker.setPopup === 'function'){
    // Mappls' real signature: setPopup(htmlString, options) — two args, not
    // one options object. Passing an object as the first arg (what this
    // used to do) gets stringified straight into the popup as literally
    // "[object Object]". {openPopup:true} also replaces the separate
    // .openPopup() call this used to make.
    mapSearchMarker.setPopup(popupHtml, { openPopup: true });
  }
  mapFlyTo(map, lat, lng, 15);
}
// Tapping a raw spot on the basemap itself — a road, an unlabeled patch, or
// a native POI label Mappls bakes into its own tiles (e.g. "Ranchi
// Station") that isn't one of our own place pins. clickableIcons:false
// (see initMap) already suppresses Mappls' own info-window for these — it
// used to link out to the Mappls app/site, which isn't something this app
// wants triggering — but that left native POI taps doing nothing useful at
// all. This replaces that with our own popup: reverse-geocode the tapped
// point (Photon, same free service the search box already uses) for a
// name, then offer Navigate / Add to trip, same actions a search result
// already gets via pickMapSearchResult above.
let mapClickMarker = null;
async function handleMapClick(e){
  // Different SDK builds surface the clicked point differently — same
  // extraction already proven out in initGemPinMap's click handler.
  const ll = (e && (e.lngLat || e.latlng || e.lnglat)) || {};
  const lat = ll.lat != null ? ll.lat : (Array.isArray(e && e.lngLat) ? e.lngLat[1] : null);
  const lng = ll.lng != null ? ll.lng : (Array.isArray(e && e.lngLat) ? e.lngLat[0] : null);
  if(lat == null || lng == null) return;

  if(mapClickMarker){ removeMarker(mapClickMarker); mapClickMarker = null; }
  const spicon = searchPinIcon();
  const thisMarker = mapClickMarker = new mappls.Marker({ map:map, position:{lat,lng}, html:spicon.html, width:spicon.width, height:spicon.height, fitbounds:false });
  if(typeof thisMarker.setPopup === 'function'){
    thisMarker.setPopup(`<div style="font-family:'JetBrains Mono',monospace;font-size:11.5px;">Loading…</div>`, { openPopup: true });
  }

  // Photon's reverse lookup returns whatever named feature it knows that's
  // *nearest* to the tapped point — with no distance cutoff of its own. Left
  // unchecked, that means a shop or landmark's name "bleeds" across a wide
  // radius: tapping anywhere within a few hundred metres of e.g. a "Sudhir
  // Ranjan" storefront would show that same name, even nowhere near it.
  // MAX_NAME_DISTANCE_KM draws a real line: only trust the name when the
  // tapped point is actually close to where that feature sits; otherwise
  // fall back to its address (still often meaningful at that range) or, with
  // neither, the raw coordinates — never a named result that isn't actually
  // nearby.
  const MAX_NAME_DISTANCE_KM = 0.025; // ~25m — tight enough that a small building's name doesn't bleed across a much wider area than its actual footprint
  const coordLabel = `${lat.toFixed(5)}, ${lng.toFixed(5)}`;
  let label = coordLabel, full = '';
  try{
    const res = await fetch(`https://photon.komoot.io/reverse?lon=${lng}&lat=${lat}`);
    if(res.ok){
      const data = await res.json();
      const first = data && data.features && data.features[0];
      const coerced = first ? coercePhotonResult(first) : null;
      if(coerced){
        const distKm = haversine(lat, lng, coerced.lat, coerced.lon);
        if(coerced.name && distKm <= MAX_NAME_DISTANCE_KM){
          label = coerced.name;
          full = coerced.addr || coordLabel;
        } else if(coerced.addr){
          // Outside the name's real radius (or the feature had no name at
          // all) — the address is still legitimately relevant at this
          // range, coordinates just back it up underneath.
          label = coerced.addr.split(',')[0].trim() || coordLabel;
          full = coordLabel;
        }
      }
    }
  }catch(e){ /* keep the coordinate fallback — Navigate/Add as thikana still work fine without a name */ }

  // The popup may have been dismissed (or replaced by a newer tap) while
  // the reverse-geocode call above was in flight — don't resurrect a stale
  // marker/popup at that point.
  if(mapClickMarker !== thisMarker) return;
  const safeLabel = label.replace(/'/g,"\\'");
  const popupHtml = `
    <div style="font-family:'JetBrains Mono',monospace;font-size:11.5px;max-width:200px;">
      <b style="font-family:'Fraunces',serif;font-size:14px;color:#1A1523;display:block;margin-bottom:3px;">${escapeHtml(label)}</b>
      ${full ? `<span style="color:#746E86;">${escapeHtml(full)}</span>` : ''}
      <div class="map-search-popup-actions">
        <button class="map-search-navigate" onclick="navigateToDestination({lat:${lat},lng:${lng},name:'${safeLabel}'})">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><polygon points="3 11 22 2 13 21 11 13 3 11"/></svg>
          Navigate
        </button>
        <button class="map-search-suggest" onclick="suggestGemFromSearch(${lat},${lng},'${safeLabel}')">+ Add as thikana</button>
      </div>
    </div>`;
  if(typeof thisMarker.setPopup === 'function'){
    thisMarker.setPopup(popupHtml, { openPopup: true });
  }
}
// Adds a non-Thikana location (from the map-tap popup above, or the trip
// planner's own "More places" search below) as a trip stop. Distinct from
// addToTripPlan (which looks up an existing PLACES row) only in that there's
// no real place id to key on — a negative, timestamp-based id keeps it safe
// from ever colliding with a real (positive) places.id, while still being a
// plain number the rest of the trip-planner code (removeTripStop,
// navigateTripLeg, etc.) already expects.
function addExternalTripStop(lat, lng, name){
  if(tripStops.length >= TRIP_MAX_STOPS){ showToast(`A trip can have up to ${TRIP_MAX_STOPS} stops — open "Plan a trip" to swap one out.`, 3000); return; }
  tripStops.push({ id: -(Date.now() + Math.floor(Math.random()*1000)), name, lat, lng, cat:null, gem:false, img:null });
  tripRoute = null;
  tripPlanId = null;
  showToast(`Added ${name} to your trip · ${tripStops.length}/${TRIP_MAX_STOPS}`, 2200);
  if(isModalOpen('trip')) renderTripPlanner();
}
function clearMapSearch(e){
  if(e) e.stopPropagation();
  document.getElementById('mapSearchInput').value = '';
  document.getElementById('mapSearchClear').style.display = 'none';
  document.getElementById('mapSearch').classList.remove('has-value');
  hideMapSearchResults();
  if(mapSearchMarker){ removeMarker(mapSearchMarker); mapSearchMarker = null; }
  if(mapSearchOpen) document.getElementById('mapSearchInput').focus();
}
function hideMapSearchResults(){
  const box = document.getElementById('mapSearchResults');
  box.classList.remove('show');
  setTimeout(()=>{ if(!box.classList.contains('show')) box.style.display = 'none'; }, 260);
}
// Bridges a searched real-world spot into the existing hidden-gem submission
// flow — same GPS-pin form as before, just pre-filled from the search
// result instead of the device's own GPS, since the person is pointing at
// somewhere specific rather than standing there right now.
function suggestGemFromSearch(lat, lng, name){
  if(map.closePopup) map.closePopup();
  requireAuth(async ()=>{
    await showView('addgem');
    gemLocated = true;
    setTimeout(()=>{
      initGemPinMap();
      placeGemMarker(lat, lng);
      if(typeof gemMap.setCenter === 'function') gemMap.setCenter({lat,lng});
      if(typeof gemMap.setZoom === 'function') gemMap.setZoom(16);
      const nameEl = document.getElementById('gName');
      if(nameEl) nameEl.value = name;
      const text = document.getElementById('gGeoText');
      const geoBox = document.getElementById('gGeoBox');
      if(text) text.textContent = `Pinned from search: ${name}. Drag the pin only to fine-tune it.`;
      if(geoBox) geoBox.className = 'geo-box geo-ok';
    }, 150);
  });
}
function filteredPlaces(){
  let list = PLACES;
  if(activeCat === 'trending'){
    // Trending ignores category/group filters on purpose — it's a single
    // ranked list of whatever's hottest right now, ordered by hype score
    // (falls back to distance/name for spots tied at zero so the list stays
    // stable rather than jumping around).
    return list.filter(p=>p.isHyped).sort((a,b)=> b.hypeScore - a.hypeScore);
  }
  if(activeCat !== 'all'){
    const grp = GROUPS[activeCat];
    list = grp ? list.filter(p=>grp.cats.includes(p.cat)) : list.filter(p=>p.cat===activeCat);
  }
  if(userLoc){
    list = list.slice().sort((a,b) => distKm(a) - distKm(b));
  }
  // Even outside the dedicated Trending tab, hyped spots float to the top of
  // whatever list is showing — same "more traffic = more visible" idea,
  // just gentler than a full takeover. Distance/category ordering still
  // applies within each of the two groups.
  return list.slice().sort((a,b) => (b.isHyped - a.isHyped));
}

/* ---------------- Discover carousel ---------------- */
/* ---------------- Lazy image loading ----------------
   Feed cards, place cards, and the profile grid render photos as CSS
   background-images (not <img> tags, so the standard loading="lazy"
   attribute doesn't apply). This gives the same effect manually: instead of
   an inline background-image, elements get a data-lazy-bg="<url>" attribute
   and a shared IntersectionObserver swaps it in only once the element is
   within ~200px of the viewport. Cuts a lot of wasted downloads on a long
   feed/grid/place-list where most cards are never actually scrolled to. */
let lazyBgObserver = null;
function ensureLazyBgObserver(){
  if(lazyBgObserver) return lazyBgObserver;
  if(typeof IntersectionObserver === 'undefined') return null;
  lazyBgObserver = new IntersectionObserver((entries)=>{
    entries.forEach(entry=>{
      if(!entry.isIntersecting) return;
      const el = entry.target;
      const url = el.getAttribute('data-lazy-bg');
      if(url){
        el.style.backgroundImage = `url('${url}')`;
        // Fade the image in rather than having it pop in abruptly — the
        // opacity/transition were set inline at render time (see
        // lazyBgAttrs); flipping to 1 on the next frame lets the browser
        // actually animate the transition instead of skipping it.
        requestAnimationFrame(()=>{ el.style.opacity = '1'; });
      }
      el.removeAttribute('data-lazy-bg');
      lazyBgObserver.unobserve(el);
    });
  }, { rootMargin: '200px' });
  return lazyBgObserver;
}
// Call after inserting new cards into the DOM (root defaults to the whole
// document, or pass a specific container to scope the query).
function observeLazyBg(root){
  const obs = ensureLazyBgObserver();
  const els = (root || document).querySelectorAll('[data-lazy-bg]');
  if(!obs){
    // No IntersectionObserver support — load everything immediately rather
    // than leaving images permanently blank.
    els.forEach(el=>{
      const url = el.getAttribute('data-lazy-bg');
      if(url){ el.style.backgroundImage = `url('${url}')`; el.style.opacity = '1'; }
      el.removeAttribute('data-lazy-bg');
    });
    return;
  }
  els.forEach(el=>obs.observe(el));
}
// Builds the style+data attributes for a lazy background image in one go —
// used everywhere a card used to write style="background-image:url(...)"
// directly. `eager` skips the lazy path entirely (e.g. a post's first/cover
// image, which is usually already near the viewport anyway). The lazy path
// starts transparent and fades in once the observer swaps the real image in
// (see observeLazyBg above), instead of the photo just popping into place.
function lazyBgAttrs(url, eager){
  if(!url) return '';
  return eager ? `style="background-image:url('${url}')"` : `data-lazy-bg="${url}" style="opacity:0;transition:opacity .35s var(--ease)"`;
}

function showSheetListSkeleton(){
  const el = document.getElementById('sheetList');
  if(!el) return;
  el.innerHTML = Array.from({length:4}).map(()=>`
    <div class="place-card" style="pointer-events:none;">
      <div class="skel" style="width:100%;height:118px;border-radius:0;"></div>
      <div class="place-info">
        <div class="skel skel-line w60"></div>
        <div class="skel skel-line w35"></div>
      </div>
    </div>`).join('');
}
function renderSheetList(){
  const list = filteredPlaces();
  const label = activeCat === 'trending'
    ? `${list.length} trending right now`
    : `${list.length} thikana${list.length!==1?'s':''} nearby`;
  document.getElementById('sheetCount').textContent = label;
  const el = document.getElementById('sheetList');
  if(activeCat === 'trending' && !list.length){
    el.innerHTML = `<div class="empty-gallery">No spot has enough recent posts to be trending yet — the moment somewhere gets a burst of visits and photos, it'll show up here first.</div>`;
    return;
  }
  el.innerHTML = '';
  list.forEach(p=>{
    const c = CATS[p.cat];
    const card = document.createElement('div');
    card.className = `place-card${p.isHyped?' is-hyped':''}`;
    card.dataset.placeId = p.id;
    card.onclick = ()=>openDetail(p.id);
    card.innerHTML = `
      <div class="place-thumb${p.img?'':' no-photo'}" ${p.img?lazyBgAttrs(p.img):''}>${p.img?'':'<span>No photo yet</span>'}</div>
      <div class="place-info">
        ${hypeBadgeHtml(p)}
        ${liveBadgeHtml(p)}
        <p class="name">${escapeHtml(p.name)}</p>
        <p class="meta"><span class="tag" style="background:${c.color}">${c.label}</span>${userLoc ? `<span class="dist">${distText(p)}</span>` : ''}</p>
        ${placeBadgeHtml(p)}
      </div>`;
    el.appendChild(card);
  });
  observeLazyBg(el);
  initDiscoverCarouselSync();
}
// Swiping the carousel gently pans the map to whichever card is currently
// centered, and highlights that card — the "cards and map move together"
// feel this replaced the old Grid View/Map View toggle with. Debounced on
// scroll-end (not every scroll tick) so it's one smooth pan per swipe
// rather than the map fighting the finger the whole way through. Setup is
// idempotent — renderSheetList() replaces #sheetList's children on every
// call, but the container itself persists, so the listener only needs
// attaching once.
let discoverCarouselSyncBound = false;
function initDiscoverCarouselSync(){
  const el = document.getElementById('sheetList');
  if(!el || discoverCarouselSyncBound) return;
  discoverCarouselSyncBound = true;
  let settleTimer = null;
  el.addEventListener('scroll', () => {
    clearTimeout(settleTimer);
    settleTimer = setTimeout(() => {
      const cards = el.querySelectorAll('.place-card');
      if(!cards.length) return;
      const wrapMid = el.getBoundingClientRect().left + el.clientWidth / 2;
      let closest = null, closestDist = Infinity;
      cards.forEach(card => {
        const r = card.getBoundingClientRect();
        const dist = Math.abs((r.left + r.width / 2) - wrapMid);
        if(dist < closestDist){ closestDist = dist; closest = card; }
      });
      if(!closest) return;
      cards.forEach(c => c.classList.toggle('is-centered', c === closest));
      const p = PLACES.find(x => String(x.id) === closest.dataset.placeId);
      if(p && map){
        const zoom = typeof map.getZoom === 'function' ? map.getZoom() : 14;
        mapFlyTo(map, p.lat, p.lng, zoom);
      }
    }, 140);
  }, { passive:true });
}

/* ---------------- Detail overlay ---------------- */
// Builds one combined list of every photo/video pinned to a place — its own
// submitted cover + gallery + flick, plus every photo/video from posts
// tagged to it (including each image of a multi-photo post) — for the
// single auto-sliding, swipeable carousel at the top of its detail page.
// Dedups by URL: submitting a new Thikana with a cover photo also creates a
// matching feed post with that same photo (see places.js), so without this
// the cover would appear twice.
function buildDetailMediaSlides(p){
  const slides = [];
  const seen = new Set();
  const push = (src, type, postId) => {
    if(!src || seen.has(src)) return;
    seen.add(src);
    slides.push({ src, type, postId: postId || null });
  };
  push(p.img, 'photo');
  (p.gallery || []).forEach(u => push(u, 'photo'));
  push(p.video, 'video');
  POSTS.filter(post => post.place_id === p.id).forEach(post => {
    if(post.gallery && post.gallery.length){
      post.gallery.forEach(u => push(u, 'photo', post.id));
    } else if(post.img){
      push(post.img, post.media_type === 'video' ? 'video' : 'photo', post.id);
    }
  });
  return slides;
}
let detailCarouselTimer = null;
// Auto-advances every 4s; any manual swipe/drag resets that timer rather
// than fighting it, so the carousel never jumps mid-gesture.
function initDetailCarousel(slides){
  clearInterval(detailCarouselTimer);
  if(slides.length < 2) return;
  const box = document.getElementById('detailCarousel');
  const track = document.getElementById('detailCarouselTrack');
  if(!box || !track) return;
  let idx = 0;
  const go = (next) => {
    idx = ((next % slides.length) + slides.length) % slides.length;
    track.style.transition = '';
    track.style.transform = `translateX(-${idx*100}%)`;
    box.querySelectorAll('.post-img-dots span').forEach((d,di)=>d.classList.toggle('on', di===idx));
    box.querySelectorAll('.post-img-slide video').forEach(v=>v.pause());
    if(slides[idx].type === 'video'){
      const v = track.children[idx] && track.children[idx].querySelector('video');
      if(v) v.play().catch(()=>{});
    }
  };
  const restartTimer = () => {
    clearInterval(detailCarouselTimer);
    detailCarouselTimer = setInterval(() => go(idx+1), 4000);
  };
  let startX = null, dragging = false, dragged = false, boxWidth = 0;
  box.addEventListener('touchstart', (e)=>{
    if(e.touches.length !== 1) return;
    startX = e.touches[0].clientX; dragging = true; dragged = false;
    boxWidth = box.getBoundingClientRect().width || 1;
    clearInterval(detailCarouselTimer);
  }, { passive:true });
  box.addEventListener('touchmove', (e)=>{
    if(!dragging) return;
    const dx = e.touches[0].clientX - startX;
    if(Math.abs(dx) > 8) dragged = true;
    track.style.transition = 'none';
    track.style.transform = `translateX(calc(-${idx*100}% + ${dx}px))`;
  }, { passive:true });
  box.addEventListener('touchend', (e)=>{
    if(!dragging) return;
    dragging = false;
    const dx = (e.changedTouches[0].clientX - startX) / boxWidth;
    track.style.transition = '';
    if(dx < -0.18) go(idx+1);
    else if(dx > 0.18) go(idx-1);
    else go(idx);
    restartTimer();
  });
  restartTimer();
}

/* ---------------- Rich share card ----------------
   One reusable "share card" look, used everywhere a thikana/post gets
   shared: the share-sheet preview, the compact card inside a chat bubble,
   and (conceptually) what a WhatsApp/social link unfurl shows via
   functions/share/*. Top half is the exact same auto-sliding, swipeable
   media carousel as a thikana's own detail page; bottom half is a small
   map tile with the place's name/category over it — so a shared card
   always reads as "a real place, at a real spot on the map", not just a
   random photo.

   Multiple cards can be on screen at once (several shared messages in a
   thread), so — unlike the single #detailCarousel above — every card gets
   its own unique DOM id and its own auto-advance timer, tracked in this
   map and torn down the moment its element leaves the DOM. */
const MAPPLS_STATIC_KEY = '93923d3d2698b0ce7acc49ccb48f77f3'; // same public map-render key already used in index.html
function staticMapTileUrl(lat, lng, w, h, zoom){
  if(lat == null || lng == null) return '';
  return `https://apis.mappls.com/advancedmaps/v1/${MAPPLS_STATIC_KEY}/still_image?center=${lat},${lng}&zoom=${zoom||15}&size=${w||400}x${h||140}&markers=${lat},${lng}`;
}
// Multi-stop version of staticMapTileUrl above, built the same, known-working
// way (same still_image endpoint, same public render key) rather than the
// separate Route Image API this used to call. That endpoint sits behind
// Mappls' "Reserved APIs" tier, which needs its own provisioned key — this
// app only has the public web-render key, so those requests were coming
// back as a blank/whited-out tile that never even fired the <img> onerror
// (no network error, just an unusable image), leaving the message bubble's
// map strip looking broken. Plotting every stop as a pin on the same
// still_image call the single-place cards already use successfully avoids
// that whole class of failure — no drawn route line, but a real map with
// every stop marked and centered/zoomed to fit them all.
function staticTripMapUrl(points, w, h){
  if(!points || points.length < 2) return '';
  const lats = points.map(p=>p[0]), lngs = points.map(p=>p[1]);
  const centerLat = (Math.min(...lats) + Math.max(...lats)) / 2;
  const centerLng = (Math.min(...lngs) + Math.max(...lngs)) / 2;
  const spread = Math.max(Math.max(...lats) - Math.min(...lats), Math.max(...lngs) - Math.min(...lngs));
  // Rough "does it fit" heuristic — plenty good enough for a small preview
  // tile in a chat bubble, no need for a real bounds-fitting calculation.
  let zoom = 14;
  if(spread > 0.5) zoom = 8;
  else if(spread > 0.2) zoom = 10;
  else if(spread > 0.08) zoom = 11;
  else if(spread > 0.03) zoom = 12;
  else if(spread > 0.01) zoom = 13;
  const markers = points.map(p => `${p[0]},${p[1]}`).join('|');
  return `https://apis.mappls.com/advancedmaps/v1/${MAPPLS_STATIC_KEY}/still_image?center=${centerLat},${centerLng}&zoom=${zoom}&size=${w||500}x${h||150}&markers=${markers}`;
}
/* ---------------- Rendered share cards (WhatsApp/social link previews) ----------------
   WhatsApp unfurls a link into exactly one image — there's no way to show
   photos and a map as separate elements, so whatever we want a recipient to
   see has to be flattened into a single picture up front.

   That picture is drawn here, on a <canvas>, in the same visual language as
   the in-app rich share card (media on top, map strip along the bottom with
   the name over it). Doing it on the phone rather than on the server means
   no WASM rasteriser in the Worker bundle, and the preview ends up being
   literally the card you see in the app.

   Generated lazily — the first time something is actually shared — then
   uploaded once and reused (see functions/api/share-card.js). */
const SHARE_CARD_W = 1200, SHARE_CARD_H = 630;
const SHARE_CARD_MAP_H = 200;          // height of the map strip along the bottom
const SHARE_CARD_PAPER = '#F7F3EA';    // matches --paper, so gaps never look like holes
const SHARE_CARD_INK = '#1B2B21';

// Same still_image tiles staticMapTileUrl/staticTripMapUrl build, but routed
// through our own origin. Drawing a cross-origin image onto a canvas taints
// it, and a tainted canvas throws on toDataURL — so the card renderer has to
// pull the map from /api/map-tile instead of straight from apis.mappls.com.
function proxiedMapTileUrl(center, zoom, w, h, markers){
  const q = new URLSearchParams({ center, zoom:String(zoom), size:`${w}x${h}` });
  if(markers) q.set('markers', markers);
  return `/api/map-tile?${q.toString()}`;
}
function shareCardPlaceMapUrl(lat, lng){
  if(lat == null || lng == null) return '';
  return proxiedMapTileUrl(`${lat},${lng}`, 15, SHARE_CARD_W, SHARE_CARD_MAP_H, `${lat},${lng}`);
}
// Mirrors staticTripMapUrl's fit heuristic — good enough for a preview strip,
// no need for real bounds maths.
function shareCardTripMapUrl(points){
  if(!points || points.length < 2) return '';
  const lats = points.map(p=>p[0]), lngs = points.map(p=>p[1]);
  const centerLat = (Math.min(...lats) + Math.max(...lats)) / 2;
  const centerLng = (Math.min(...lngs) + Math.max(...lngs)) / 2;
  const spread = Math.max(Math.max(...lats) - Math.min(...lats), Math.max(...lngs) - Math.min(...lngs));
  let zoom = 14;
  if(spread > 0.5) zoom = 8;
  else if(spread > 0.2) zoom = 10;
  else if(spread > 0.08) zoom = 11;
  else if(spread > 0.03) zoom = 12;
  else if(spread > 0.01) zoom = 13;
  const markers = points.map(p=>`${p[0]},${p[1]}`).join('|');
  return proxiedMapTileUrl(`${centerLat},${centerLng}`, zoom, SHARE_CARD_W, SHARE_CARD_MAP_H, markers);
}

// Resolves to an <img>, or to null if it can't be loaded *without tainting*.
// crossOrigin='anonymous' is deliberate: a host that won't send CORS headers
// fails the load outright here, which we can handle, instead of loading fine
// and then blowing up at export time with a SecurityError.
function loadImageForCanvas(src){
  return new Promise(resolve=>{
    if(!src) return resolve(null);
    const img = new Image();
    img.crossOrigin = 'anonymous';
    let settled = false;
    const done = v => { if(!settled){ settled = true; resolve(v); } };
    img.onload = ()=>done(img);
    img.onerror = ()=>done(null);
    // Don't let one slow/stuck asset hold the whole card hostage.
    setTimeout(()=>done(null), 7000);
    img.src = src;
  });
}
// object-fit: cover, by hand.
function drawImageCover(ctx, img, dx, dy, dw, dh){
  const ir = img.width / img.height, dr = dw / dh;
  let sw, sh, sx, sy;
  if(ir > dr){ sh = img.height; sw = sh * dr; sx = (img.width - sw) / 2; sy = 0; }
  else       { sw = img.width;  sh = sw / dr; sx = 0; sy = (img.height - sh) / 2; }
  ctx.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);
}
function drawPhotoPlaceholder(ctx, x, y, w, h, label){
  ctx.save();
  ctx.fillStyle = '#E8E0D0';
  ctx.fillRect(x, y, w, h);
  ctx.fillStyle = '#9A8F7C';
  ctx.font = '600 44px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.fillText(label || '📍', x + w/2, y + h/2);
  ctx.restore();
}
function truncateToWidth(ctx, text, maxW){
  if(ctx.measureText(text).width <= maxW) return text;
  let s = text;
  while(s.length > 1 && ctx.measureText(s + '…').width > maxW) s = s.slice(0, -1);
  return s + '…';
}

// Draws the whole card and returns a JPEG data URI (or null if the canvas
// turned out to be unexportable after all — callers just fall back to the
// plain cover photo in that case).
async function buildShareCardImage({ photos, photoLabels, mapUrl, title, subtitle }){
  const canvas = document.createElement('canvas');
  canvas.width = SHARE_CARD_W; canvas.height = SHARE_CARD_H;
  const ctx = canvas.getContext('2d');
  if(!ctx) return null;

  ctx.fillStyle = SHARE_CARD_PAPER;
  ctx.fillRect(0, 0, SHARE_CARD_W, SHARE_CARD_H);

  const mediaH = SHARE_CARD_H - SHARE_CARD_MAP_H;

  // ---- Media band: one photo, or up to four side by side for a trip ----
  const srcs = (photos || []).slice(0, 4);
  const loaded = await Promise.all(srcs.map(loadImageForCanvas));
  const usable = loaded.filter(Boolean).length;

  if(!srcs.length || !usable){
    drawPhotoPlaceholder(ctx, 0, 0, SHARE_CARD_W, mediaH, '🗺️');
  } else if(srcs.length === 1){
    if(loaded[0]) drawImageCover(ctx, loaded[0], 0, 0, SHARE_CARD_W, mediaH);
    else drawPhotoPlaceholder(ctx, 0, 0, SHARE_CARD_W, mediaH, '📍');
  } else {
    const gap = 4;
    const cellW = (SHARE_CARD_W - gap * (srcs.length - 1)) / srcs.length;
    srcs.forEach((_, i)=>{
      const x = i * (cellW + gap);
      if(loaded[i]) drawImageCover(ctx, loaded[i], x, 0, cellW, mediaH);
      else drawPhotoPlaceholder(ctx, x, 0, cellW, mediaH, '📍');
      // Per-stop name chip, same idea as the trip carousel's slide labels.
      const label = photoLabels && photoLabels[i];
      if(label){
        ctx.save();
        ctx.font = '600 26px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
        const text = truncateToWidth(ctx, label, cellW - 48);
        const tw = ctx.measureText(text).width;
        ctx.fillStyle = 'rgba(0,0,0,0.55)';
        ctx.fillRect(x + 16, mediaH - 62, tw + 28, 42);
        ctx.fillStyle = '#fff';
        ctx.textBaseline = 'middle';
        ctx.fillText(text, x + 30, mediaH - 41);
        ctx.restore();
      }
    });
  }

  // ---- Map strip ----
  const mapImg = await loadImageForCanvas(mapUrl);
  if(mapImg){
    drawImageCover(ctx, mapImg, 0, mediaH, SHARE_CARD_W, SHARE_CARD_MAP_H);
  } else {
    ctx.fillStyle = '#DFE9E2';
    ctx.fillRect(0, mediaH, SHARE_CARD_W, SHARE_CARD_MAP_H);
  }
  // Dark scrim so the title stays readable over any map.
  const grad = ctx.createLinearGradient(0, mediaH, 0, SHARE_CARD_H);
  grad.addColorStop(0, 'rgba(0,0,0,0.15)');
  grad.addColorStop(1, 'rgba(0,0,0,0.78)');
  ctx.fillStyle = grad;
  ctx.fillRect(0, mediaH, SHARE_CARD_W, SHARE_CARD_MAP_H);

  // ---- Title + subtitle over the strip ----
  ctx.save();
  ctx.textBaseline = 'alphabetic';
  ctx.fillStyle = '#fff';
  ctx.font = '700 54px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  ctx.fillText(truncateToWidth(ctx, title || 'Mera Thikaana', SHARE_CARD_W - 100), 50, SHARE_CARD_H - 78);
  if(subtitle){
    ctx.font = '500 30px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
    ctx.fillStyle = 'rgba(255,255,255,0.86)';
    ctx.fillText(truncateToWidth(ctx, subtitle, SHARE_CARD_W - 100), 50, SHARE_CARD_H - 34);
  }
  // Wordmark, top-right, so a forwarded card still says where it came from.
  ctx.font = '700 28px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  ctx.textAlign = 'right';
  ctx.fillStyle = 'rgba(255,255,255,0.92)';
  ctx.shadowColor = 'rgba(0,0,0,0.45)'; ctx.shadowBlur = 12;
  ctx.fillText('Mera Thikaana', SHARE_CARD_W - 40, 56);
  ctx.restore();

  try{
    return canvas.toDataURL('image/jpeg', 0.82);
  }catch(e){
    // Tainted after all (an image slipped through without CORS) — the
    // share still works, it just falls back to the plain cover photo.
    return null;
  }
}

function formatTripDate(d){
  return (d || new Date()).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function computeTripBounds(stops){
  if(!stops || stops.length < 2) return null;
  const lats = stops.map(p=>p[0]), lngs = stops.map(p=>p[1]);
  const minLat = Math.min(...lats), maxLat = Math.max(...lats);
  const minLng = Math.min(...lngs), maxLng = Math.max(...lngs);
  const centerLat = (minLat + maxLat) / 2;
  const centerLng = (minLng + maxLng) / 2;
  const spread = Math.max(maxLat - minLat, maxLng - minLng);
  let zoom = 14;
  if(spread > 0.5) zoom = 8;
  else if(spread > 0.2) zoom = 10;
  else if(spread > 0.08) zoom = 11;
  else if(spread > 0.03) zoom = 12;
  else if(spread > 0.01) zoom = 13;

  const latSpan = Math.max(maxLat - minLat, 0.003) * 1.7;
  const lngSpan = Math.max(maxLng - minLng, 0.003) * 1.7;
  const top = centerLat + latSpan / 2;
  const bottom = centerLat - latSpan / 2;
  const left = centerLng - lngSpan / 2;
  const right = centerLng + lngSpan / 2;

  return {
    centerLat, centerLng, zoom,
    pins: stops.map(([lat, lng]) => ({
      xPct: ((lng - left) / (right - left)) * 100,
      yPct: ((top - lat) / (top - bottom)) * 100
    }))
  };
}

async function buildTripShareCardImage({ stops, tripName }){
  if(!stops || stops.length < 2) return null;
  const canvas = document.createElement('canvas');
  canvas.width = SHARE_CARD_W;
  canvas.height = SHARE_CARD_H;
  const ctx = canvas.getContext('2d');
  if(!ctx) return null;

  const headerH = 320;
  const mapH = 230;
  const stripH = 80;
  const mapY = headerH;
  const stripY = headerH + mapH;

  // 1. Cover / Hero Photo
  const firstPhotoStop = stops.find(s => s.img) || null;
  const coverImg = firstPhotoStop ? await loadImageForCanvas(firstPhotoStop.img) : null;
  if(coverImg){
    drawImageCover(ctx, coverImg, 0, 0, SHARE_CARD_W, headerH);
  } else {
    const bg = ctx.createLinearGradient(0, 0, SHARE_CARD_W, headerH);
    bg.addColorStop(0, '#8B3DFF');
    bg.addColorStop(0.6, '#5B21B6');
    bg.addColorStop(1, '#3B0A91');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, SHARE_CARD_W, headerH);
  }

  // Gradient scrim over header
  const scrim = ctx.createLinearGradient(0, 0, 0, headerH);
  scrim.addColorStop(0, 'rgba(10,8,6,0.55)');
  scrim.addColorStop(0.28, 'rgba(10,8,6,0.05)');
  scrim.addColorStop(0.62, 'rgba(10,8,6,0.05)');
  scrim.addColorStop(1, 'rgba(10,8,6,0.8)');
  ctx.fillStyle = scrim;
  ctx.fillRect(0, 0, SHARE_CARD_W, headerH);

  // Brand header
  ctx.save();
  ctx.textBaseline = 'alphabetic';
  ctx.textAlign = 'left';
  ctx.fillStyle = '#fff';
  ctx.font = 'italic 700 30px Georgia, "Fraunces", serif';
  ctx.fillText('Mera Thikaana', 40, 52);
  ctx.font = '700 12px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  ctx.fillStyle = 'rgba(255,255,255,0.85)';
  ctx.fillText('EXPLORE · MEET · DISCOVER', 40, 68);

  // "Planned Trip" pill badge
  ctx.font = '600 20px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  const badgeText = 'Planned Trip';
  const badgeW = ctx.measureText(badgeText).width + 56;
  ctx.fillStyle = 'rgba(20,16,10,0.5)';
  ctx.beginPath();
  if(ctx.roundRect) ctx.roundRect(SHARE_CARD_W - 40 - badgeW, 28, badgeW, 40, 20);
  else ctx.rect(SHARE_CARD_W - 40 - badgeW, 28, badgeW, 40);
  ctx.fill();
  ctx.fillStyle = '#fff';
  ctx.fillText(badgeText, SHARE_CARD_W - 40 - badgeW + 28, 54);

  // Title
  ctx.font = '700 46px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  ctx.fillStyle = '#fff';
  ctx.fillText(truncateToWidth(ctx, tripName || 'A trip', SHARE_CARD_W - 80), 40, headerH - 62);

  // Subtitle: Planned for <date> · X Places
  ctx.font = '600 24px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
  ctx.fillStyle = 'rgba(255,255,255,0.9)';
  ctx.fillText(`Planned for ${formatTripDate()}  ·  ${stops.length} Places`, 40, headerH - 26);
  ctx.restore();

  // 2. Map strip with numbered pins
  const bounds = computeTripBounds(stops.map(s => [s.lat, s.lng]));
  const mapUrl = bounds ? proxiedMapTileUrl(`${bounds.centerLat},${bounds.centerLng}`, bounds.zoom, SHARE_CARD_W, mapH) : '';
  const mapImg = mapUrl ? await loadImageForCanvas(mapUrl) : null;
  if(mapImg){
    drawImageCover(ctx, mapImg, 0, mapY, SHARE_CARD_W, mapH);
  } else {
    ctx.fillStyle = '#DFE9E2';
    ctx.fillRect(0, mapY, SHARE_CARD_W, mapH);
  }

  // Draw numbered purple pin circles on the map
  if(bounds && bounds.pins){
    bounds.pins.forEach((pin, idx) => {
      const px = (pin.xPct / 100) * SHARE_CARD_W;
      const py = mapY + (pin.yPct / 100) * mapH;
      ctx.save();
      ctx.beginPath();
      ctx.arc(px, py, 15, 0, Math.PI * 2);
      ctx.fillStyle = '#5B21B6';
      ctx.fill();
      ctx.lineWidth = 3;
      ctx.strokeStyle = '#fff';
      ctx.stroke();
      ctx.fillStyle = '#fff';
      ctx.font = '700 15px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(idx + 1), px, py + 1);
      ctx.restore();
    });
  }

  // 3. Dark bottom stop bar
  ctx.fillStyle = '#161821';
  ctx.fillRect(0, stripY, SHARE_CARD_W, stripH);
  ctx.save();
  ctx.textBaseline = 'middle';
  const midY = stripY + stripH / 2;
  const maxStopX = SHARE_CARD_W - 260;
  let curX = 40;
  for(let i = 0; i < stops.length && curX < maxStopX; i++){
    ctx.beginPath();
    ctx.arc(curX + 13, midY, 13, 0, Math.PI * 2);
    ctx.fillStyle = '#5B21B6';
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.textAlign = 'center';
    ctx.font = '700 13px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
    ctx.fillText(String(i + 1), curX + 13, midY + 1);
    curX += 34;

    ctx.textAlign = 'left';
    ctx.font = '700 18px -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif';
    ctx.fillStyle = '#fff';
    const stopName = truncateToWidth(ctx, stops[i].name || '', Math.max(60, maxStopX - curX - 20));
    ctx.fillText(stopName, curX, midY);
    curX += ctx.measureText(stopName).width + 34;
  }
  ctx.textAlign = 'right';
  ctx.font = 'italic 700 24px Georgia, "Fraunces", serif';
  ctx.fillStyle = '#00C2CB';
  ctx.fillText('✈ Trip Planned!', SHARE_CARD_W - 40, midY);
  ctx.restore();

  try{
    return canvas.toDataURL('image/jpeg', 0.85);
  }catch(e){
    return null;
  }
}

// In-flight/completed generations, keyed 'place:12' / 'trip:3', so opening
// the same share sheet twice doesn't redo the work.
const shareCardJobs = new Map();
function shareCardKey(kind, id){ return `${kind}:${id}`; }

// Fire-and-forget or awaited: kicked off when a share sheet opens so the card is
// uploaded by the time the link is actually sent.
function ensureShareCard(kind, id, build){
  const key = shareCardKey(kind, id);
  if(shareCardJobs.has(key)) return shareCardJobs.get(key);
  const job = (async ()=>{
    try{
      const spec = await build();
      if(!spec) return null;
      const dataUri = kind === 'trip' ? await buildTripShareCardImage(spec) : await buildShareCardImage(spec);
      if(!dataUri) return null;
      const res = await apiPost('/api/share-card', { kind, id, image: dataUri });
      return (res && res.url) || null;
    }catch(e){
      return null;
    }
  })();
  shareCardJobs.set(key, job);
  return job;
}
// Builds the card spec for a thikana currently in the share sheet.
function shareCardSpecForPlace(placeId){
  const p = PLACES.find(x=>x.id===placeId);
  if(!p) return null;
  const slides = buildDetailMediaSlides(p) || [];
  const photo = (slides.find(s=>s.type !== 'video') || {}).src || p.img || null;
  return {
    photos: photo ? [photo] : [],
    mapUrl: shareCardPlaceMapUrl(p.lat, p.lng),
    title: p.name,
    subtitle: (CATS[p.cat] && CATS[p.cat].label) || '',
  };
}
function shareCardSpecForTrip(stops, tripName){
  if(!stops || !stops.length) return null;
  return {
    stops: stops.map(s => {
      const p = PLACES.find(x => x.id === (s.place_id != null ? s.place_id : s.id));
      return {
        name: s.name || (p && p.name) || '',
        lat: s.lat,
        lng: s.lng,
        img: (p && p.img) || null
      };
    }),
    tripName: tripName || 'A trip'
  };
}

/* ---------------- Sharing a trip as an external link ----------------
   Sharing into a DM/group only needs a trip_plans row (see saveAndShareTrip);
   sharing to WhatsApp additionally needs a public URL to paste, which is what
   /share/trip/:id is. Saving is idempotent per planner session — tripPlanId
   is reused once set, so hitting WhatsApp then Copy link doesn't create two
   trips. */
async function ensureTripSaved(){
  if(tripPlanId) return tripPlanId;
  const { plan } = await apiPost('/api/trip-plans', {
    stops: tripStops.map(s=>({ place_id:s.id, name:s.name, lat:s.lat, lng:s.lng, category:s.cat })),
  });
  tripPlanId = plan.id;
  return tripPlanId;
}
function tripShareName(){
  if(!tripStops.length) return 'A trip';
  return `${tripStops[0].name} → ${tripStops[tripStops.length-1].name}`;
}
// Saves (if needed), kicks off the rich card render, and awaits it up to a short timeout
// so WhatsApp's scraper reliably receives the new tile immediately upon sharing.
async function prepareTripShareLink(){
  const id = await ensureTripSaved();
  const cardPromise = ensureShareCard('trip', id, async ()=>shareCardSpecForTrip(
    tripStops.map(s=>({ place_id:s.id, name:s.name, lat:s.lat, lng:s.lng })), tripShareName()
  ));
  try {
    // Wait up to 1.8s for the rich card to upload before sending the URL
    await Promise.race([cardPromise, new Promise(r => setTimeout(r, 1800))]);
  } catch(e) {}
  return `${window.location.origin}/share/trip/${id}`;
}
async function shareTripToWhatsApp(){
  if(tripStops.length < TRIP_MIN_STOPS) return;
  try{
    const url = await prepareTripShareLink();
    const text = `${tripShareName()} — a trip on Mera Thikaana`;
    if (window.AndroidNativeAuth && window.AndroidNativeAuth.shareToWhatsApp) {
      window.AndroidNativeAuth.shareToWhatsApp(text, url);
      return;
    }
    window.open(`https://wa.me/?text=${encodeURIComponent(text + ' ' + url)}`, '_blank', 'noopener');
  }catch(e){
    showToast((e.data && e.data.message) || "Couldn't build a share link — try again.", 3000);
  }
}
async function shareTripExternally(){
  if(tripStops.length < TRIP_MIN_STOPS) return;
  try{
    const url = await prepareTripShareLink();
    const text = `${tripShareName()} — a trip on Mera Thikaana`;
    if(navigator.share){
      try{
        await navigator.share({ title: tripShareName(), text, url });
        return;
      }catch(e){
        if(e && e.name === 'AbortError') return;
      }
    }
    copyTextToClipboard(url);
  }catch(e){
    showToast((e.data && e.data.message) || "Couldn't build a share link — try again.", 3000);
  }
}
async function copyTripLink(){
  if(tripStops.length < TRIP_MIN_STOPS) return;
  try{
    copyTextToClipboard(await prepareTripShareLink());
  }catch(e){
    showToast((e.data && e.data.message) || "Couldn't build a share link — try again.", 3000);
  }
}
// Shared by the place/post and trip copy paths.
function copyTextToClipboard(url){
  if(navigator.clipboard && navigator.clipboard.writeText){
    navigator.clipboard.writeText(url)
      .then(()=>showToast('Link copied — paste it anywhere, including WhatsApp.', 2600))
      .catch(()=>{ window.prompt('Copy this link:', url); });
  } else {
    window.prompt('Copy this link:', url);
  }
}

const richCarouselTimers = new Map();
function stopRichCarousel(cardId){
  const t = richCarouselTimers.get(cardId);
  if(t){ clearInterval(t); richCarouselTimers.delete(cardId); }
}
// Same swipe/auto-advance behaviour as initDetailCarousel, generalized to
// take an explicit card id so many instances can run side by side.
function initRichCarousel(cardId, slides){
  stopRichCarousel(cardId);
  if(!slides || slides.length < 2) return;
  const box = document.getElementById(cardId);
  const track = document.getElementById(cardId+'-track');
  if(!box || !track) return;
  let idx = 0, dragged = false;
  const go = (next) => {
    if(!track.isConnected){ stopRichCarousel(cardId); return; } // card scrolled out of/removed from the DOM
    idx = ((next % slides.length) + slides.length) % slides.length;
    track.style.transition = '';
    track.style.transform = `translateX(-${idx*100}%)`;
    box.querySelectorAll('.post-img-dots span').forEach((d,di)=>d.classList.toggle('on', di===idx));
    box.querySelectorAll('.post-img-slide video').forEach(v=>v.pause());
    if(slides[idx].type === 'video'){
      const v = track.children[idx] && track.children[idx].querySelector('video');
      if(v) v.play().catch(()=>{});
    }
  };
  const restart = () => {
    stopRichCarousel(cardId);
    richCarouselTimers.set(cardId, setInterval(() => go(idx+1), 4000));
  };
  let startX = null, dragging = false, boxWidth = 0;
  box.addEventListener('touchstart', (e)=>{
    if(e.touches.length !== 1) return;
    startX = e.touches[0].clientX; dragging = true; dragged = false;
    boxWidth = box.getBoundingClientRect().width || 1;
    stopRichCarousel(cardId);
  }, { passive:true });
  box.addEventListener('touchmove', (e)=>{
    if(!dragging) return;
    const dx = e.touches[0].clientX - startX;
    if(Math.abs(dx) > 8) dragged = true;
    track.style.transition = 'none';
    track.style.transform = `translateX(calc(-${idx*100}% + ${dx}px))`;
  }, { passive:true });
  box.addEventListener('touchend', (e)=>{
    if(!dragging) return;
    dragging = false;
    const dx = (e.changedTouches[0].clientX - startX) / boxWidth;
    track.style.transition = '';
    if(dx < -0.18) go(idx+1);
    else if(dx > 0.18) go(idx-1);
    else go(idx);
    restart();
  });
  // A swipe that just happened shouldn't also fire the card's own onclick
  // (opening the place) — mirrors the same guard initPostCarousel uses.
  box.addEventListener('click', (e)=>{
    if(dragged){ e.stopPropagation(); dragged = false; }
  });
  restart();
}
// Builds the full auto-sliding gallery for whatever's being shared — a
// thikana's every photo/video (same helper the detail page itself uses),
// or a post's own gallery/single image. Returns [] when the local PLACES/
// POSTS state doesn't have the target yet (e.g. rendering a shared card
// from data that arrived over the wire) — callers fall back to a single
// static cover image in that case rather than showing nothing.
function buildShareSlides(target){
  if(!target) return [];
  if(target.place_id != null){
    const p = PLACES.find(x=>x.id===target.place_id);
    if(p) return buildDetailMediaSlides(p);
  } else if(target.post_id != null){
    const post = POSTS.find(x=>x.id===target.post_id);
    if(post){
      if(post.gallery && post.gallery.length) return post.gallery.map(u=>({src:u, type:'photo'}));
      if(post.img) return [{ src: post.img, type: post.media_type === 'video' ? 'video' : 'photo' }];
    }
  }
  return [];
}
// cardId must be unique per card on screen at once (e.g. `share-msg-42`).
// `onclick` is a literal inline-handler string (already known-safe call
// sites only — no user text is ever spliced in as JS, only as text).
function richShareCardHtml(opts){
  const { cardId, slides, coverFallback, name, category, lat, lng, compact, onclick } = opts;
  const safeName = escapeHtml(name || 'A thikana');
  const safeCat = category ? escapeHtml(category).toUpperCase() : '';
  let mediaHtml;
  if(slides && slides.length){
    mediaHtml = `<div class="rsc-media" id="${cardId}">
        <div class="post-img-track" id="${cardId}-track">${slides.map(s => s.type === 'video'
          ? `<div class="post-img-slide"><video src="${s.src}" muted loop playsinline preload="metadata"></video></div>`
          : `<div class="post-img-slide" style="background-image:url('${s.src}')"></div>`).join('')}</div>
        ${slides.length > 1 ? `<div class="post-img-dots">${slides.map((_,d)=>`<span class="${d===0?'on':''}"></span>`).join('')}</div>` : ''}
      </div>`;
  } else if(coverFallback){
    mediaHtml = `<div class="rsc-media rsc-media-static" style="background-image:url('${coverFallback}')"></div>`;
  } else {
    mediaHtml = `<div class="rsc-media rsc-media-empty"></div>`;
  }
  const mapUrl = staticMapTileUrl(lat, lng, 500, compact ? 90 : 150);
  return `<div class="rich-share-card${compact ? ' compact' : ''}"${onclick ? ` onclick="${onclick}"` : ''}>
      ${mediaHtml}
      <div class="rsc-map">
        ${mapUrl ? `<img class="rsc-map-img" src="${mapUrl}" alt="" onerror="this.style.display='none';this.closest('.rsc-map').classList.add('rsc-map-fallback');">` : ''}
        <div class="rsc-map-overlay"></div>
        <div class="rsc-map-info">
          <svg class="rsc-pin" width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7z"/></svg>
          <div style="min-width:0;">
            <div class="rsc-title">${safeName}</div>
            ${safeCat ? `<div class="rsc-sub">${safeCat}</div>` : ''}
          </div>
        </div>
      </div>
    </div>`;
}
// Same visual language as richShareCardHtml above (sliding media on top, a
// map strip along the bottom with a name/info overlay) but built for a
// multi-stop trip instead of one place: the top carousel slides through
// each stop's own cover photo (with a small name chip so it's clear which
// stop is which), and the strip along the bottom is a still map with every
// stop pinned (see staticTripMapUrl), rather than a single marker at one
// point.
function buildTripShareSlides(stops){
  if(!stops || !stops.length) return [];
  return stops.map(s=>{
    const p = PLACES.find(x=>x.id===s.place_id);
    return (p && p.img) ? { src:p.img, label:s.name } : null;
  }).filter(Boolean);
}
function tripShareCardHtml(opts){
  const { cardId, stops, tripName, compact, onclick } = opts;
  const slides = buildTripShareSlides(stops);
  let mediaHtml;
  if(slides.length){
    mediaHtml = `<div class="rsc-media" id="${cardId}">
        <div class="post-img-track" id="${cardId}-track">${slides.map(s =>
          `<div class="post-img-slide" style="background-image:url('${s.src}')"><div class="trip-slide-label">${escapeHtml(s.label)}</div></div>`
        ).join('')}</div>
        ${slides.length > 1 ? `<div class="post-img-dots">${slides.map((_,d)=>`<span class="${d===0?'on':''}"></span>`).join('')}</div>` : ''}
      </div>`;
  } else {
    mediaHtml = `<div class="rsc-media rsc-media-empty" style="display:flex;align-items:center;justify-content:center;font-size:28px;">🗺️</div>`;
  }
  const routeUrl = staticTripMapUrl(stops.map(s=>[s.lat,s.lng]), 500, compact ? 90 : 150);
  const stopsLine = stops.map(s=>escapeHtml(s.name)).join(' → ');
  return { html: `<div class="rich-share-card${compact ? ' compact' : ''}"${onclick ? ` onclick="${onclick}"` : ''}>
      ${mediaHtml}
      <div class="rsc-map">
        ${routeUrl ? `<img class="rsc-map-img" src="${routeUrl}" alt="" onerror="this.style.display='none';this.closest('.rsc-map').classList.add('rsc-map-fallback');">` : ''}
        <div class="rsc-map-overlay"></div>
        <div class="rsc-map-info">
          <svg class="rsc-pin" width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7z"/></svg>
          <div style="min-width:0;">
            <div class="rsc-title">${escapeHtml(tripName || 'A trip')}</div>
            <div class="rsc-sub" style="text-transform:none;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${stops.length} stop${stops.length===1?'':'s'} · ${stopsLine}</div>
          </div>
        </div>
      </div>
    </div>`, slides };
}
let openDetailId = null; // which thikana's detail sheet is open, if any — lets pollPlacesOnce() notice if it just got deleted out from under the viewer
function openDetail(id){
  const p = PLACES.find(x=>x.id===id);
  const c = CATS[p.cat];
  openDetailId = id;

  const slides = buildDetailMediaSlides(p);
  const carouselHtml = slides.length ? `
    <div class="detail-photo detail-photo-carousel" id="detailCarousel">
      <div class="post-img-track" id="detailCarouselTrack">${slides.map(s => s.type === 'video'
        ? `<div class="post-img-slide"><video src="${s.src}" muted loop playsinline preload="metadata"></video></div>`
        : `<div class="post-img-slide" style="background-image:url('${s.src}')"></div>`).join('')}</div>
      ${slides.length > 1 ? `<div class="post-img-dots">${slides.map((_,d)=>`<span class="${d===0?'on':''}"></span>`).join('')}</div>` : ''}
      <div class="detail-close" onclick="closeDetail()">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M18 6L6 18M6 6l12 12"/></svg>
      </div>
    </div>` : `
    <div class="detail-photo no-photo">
      <span>No cover photo yet</span>
      <div class="detail-close" onclick="closeDetail()">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M18 6L6 18M6 6l12 12"/></svg>
      </div>
    </div>`;

  const explorerLabel = p.explorerType === 'bhukkad' ? 'Bhukkad — foodie spot' : p.explorerType === 'ghumakkad' ? 'Ghumakkad — explorer spot' : null;
  const tripFacts = [
    explorerLabel ? ['Type', explorerLabel] : null,
    p.bestVisitingTime ? ['Best time to visit', escapeHtml(p.bestVisitingTime)] : null,
    p.difficulty ? ['Difficulty', p.difficulty[0].toUpperCase()+p.difficulty.slice(1)] : null,
    p.parkingInfo ? ['Parking', escapeHtml(p.parkingInfo)] : null,
    p.routeInfo ? ['Standard route', escapeHtml(p.routeInfo)] : null,
  ].filter(Boolean);
  const tripFactsHtml = tripFacts.length
    ? `<div class="field-label" style="margin-top:14px">Trip info</div><div class="trip-facts">${tripFacts.map(([k,v])=>`<div class="trip-fact"><b>${k}:</b> ${v}</div>`).join('')}</div>`
    : '';

  document.getElementById('detailBody').innerHTML = `
    ${carouselHtml}
    <div class="detail-body">
      <span class="tag" style="background:${c.color}">${c.label}</span>
      ${hypeBadgeHtml(p)}
      ${liveBadgeHtml(p)}
      <h2>${escapeHtml(p.name)}</h2>
      ${placeBadgeHtml(p)}
      ${discoveredByHtml(p)}
      <p class="detail-desc">${escapeHtml(p.desc)}</p>
      ${tripFactsHtml}
      <div class="field-label" style="margin-top:14px">Who's here</div>
      <div id="livePresence" class="live-presence"><div class="skel skel-line w60"></div></div>
      <div class="field-label" style="margin-top:14px">Trail notes &amp; route corrections</div>
      ${p.customRouteNotes ? `<div class="trail-note trail-note-official"><b>Official trail note:</b> ${escapeHtml(p.customRouteNotes)}</div>` : ''}
      <div id="routeSuggestions"><div class="skel skel-line" style="width:70%;margin:8px 0;"></div></div>
      <div class="route-suggest-box">
        <textarea id="routeSuggestInput" rows="2" placeholder="Know a better way here? e.g. &quot;Google Maps route ends here, follow this trail.&quot;"></textarea>
        <button class="btn-ghost" onclick="submitRouteSuggestion(${p.id})">Suggest a route</button>
      </div>
      <div class="detail-actions">
        <button class="btn-nav" onclick="startNavigation(${p.id})">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="3 11 22 2 13 21 11 13 3 11"/></svg>
          Navigate
        </button>
        <div class="btn-save" onclick="addToTripPlan(${p.id})" title="Add to a trip">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="6" cy="19" r="2.2"/><circle cx="18" cy="5" r="2.2"/><path d="M8.2 17.8L15 8.5"/></svg>
        </div>
        <div class="btn-save" onclick="this.classList.toggle('saved')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M19 21l-7-5-7 5V5a2 2 0 012-2h10a2 2 0 012 2z"/></svg>
        </div>
        <div class="btn-save" onclick="openShareSheet({place_id:${p.id}})" title="Share this thikana">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>
        </div>
      </div>
      <a href="https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}" target="_blank" rel="noopener" class="top-link" style="display:inline-block;margin-top:10px;">Open in Google Maps instead</a>
    </div>`;
  document.getElementById('overlay').classList.add('active');
  pushUIModal('detail');
  initDetailCarousel(slides);
  loadRouteSuggestions(p.id);
  loadLivePresence(p.id);
}
// "Live Thikana" — who's genuinely checked in at this spot right now (see
// functions/api/place-presence.js). Friends first (people the viewer
// follows), then a plain count of everyone else present, so a spot never
// reads as empty just because the viewer doesn't know anyone there yet.
// Same "fetch into a placeholder div, bail quietly if the sheet closed
// mid-flight" shape as loadRouteSuggestions right above.
async function loadLivePresence(placeId){
  const el = document.getElementById('livePresence');
  if(!el) return;
  try{
    const { checked_in, friends, others_count } = await apiGet(`/api/place-presence?place_id=${placeId}`);
    if(!el.isConnected) return;
    const avatars = friends.slice(0, 6).map(f => `
      <div class="presence-avatar" style="cursor:pointer;${f.avatar_url?`background-image:url('${f.avatar_url}');background-size:cover;`:''}"
           onclick="closeDetail();setTimeout(()=>openUserProfile(${f.id}),300);" title="@${escapeHtml(f.handle)}">${!f.avatar_url?avatarInitial(f):''}</div>`).join('');
    let summary;
    if(friends.length){
      const names = friends.slice(0, 2).map(f => '@'+escapeHtml(f.handle)).join(', ');
      const rest = friends.length - Math.min(2, friends.length);
      summary = rest > 0 ? `${names} and ${rest} more dost here now` : `${names} ${friends.length>1?'are':'is'} here now`;
    } else {
      summary = "No one you follow is here right now";
    }
    if(others_count > 0) summary += ` · ${others_count} more ${others_count===1?'person':'people'} nearby`;
    const btnHtml = checked_in
      ? `<button class="btn-ghost live-checkin-btn" id="presenceCheckinBtn" onclick="leavePlaceCheckin(${placeId})">You're checked in here · tap to leave</button>`
      : `<button class="btn-ghost live-checkin-btn" id="presenceCheckinBtn" onclick="checkInAtPlace(${placeId})">I'm here right now</button>`;
    el.innerHTML = `
      ${avatars ? `<div class="presence-avatars">${avatars}</div>` : ''}
      <div class="presence-summary">${summary}</div>
      ${btnHtml}`;
  }catch(e){
    if(el.isConnected) el.innerHTML = `<div class="presence-summary">Couldn't load who's here right now.</div>`;
  }
}
// Checking in is geofenced exactly like posting a photo (same 300m radius,
// see MAX_DISTANCE_METERS in functions/api/place-presence.js) — a real GPS
// fix has to agree the caller is actually at the spot.
function checkInAtPlace(placeId){
  requireAuth(()=>{
    if(!navigator.geolocation){ showToast("Location isn't available in this browser.", 3000); return; }
    const btn = document.getElementById('presenceCheckinBtn');
    if(btn){ btn.disabled = true; btn.textContent = 'Checking in…'; }
    navigator.geolocation.getCurrentPosition(async (pos)=>{
      try{
        const ciResult = await apiPost('/api/place-presence', { place_id: placeId, lat: pos.coords.latitude, lng: pos.coords.longitude });
        showToast("You're checked in — dosts nearby can see you're here.", 3000);
        handleXpResult(ciResult && ciResult.xp);
        loadLivePresence(placeId);
        loadPlaces(); // refreshes this spot's live badge/marker dot everywhere else too
      }catch(e){
        if(e && e.status === 401){ openAuthModal(); }
        else if(e && e.status === 422){ alert((e.data && e.data.message) || "You're too far from this spot to check in."); }
        else alert((e && e.data && e.data.message) || "Couldn't check you in — try again.");
        if(btn){ btn.disabled = false; btn.textContent = "I'm here right now"; }
      }
    }, ()=>{
      showToast("Couldn't get your location — enable location access to check in.", 3500);
      if(btn){ btn.disabled = false; btn.textContent = "I'm here right now"; }
    }, { enableHighAccuracy: true, timeout: 8000 });
  });
}
function leavePlaceCheckin(placeId){
  const btn = document.getElementById('presenceCheckinBtn');
  if(btn) btn.disabled = true;
  apiDelete(`/api/place-presence?place_id=${placeId}`)
    .catch(()=>{})
    .finally(()=>{ loadLivePresence(placeId); loadPlaces(); });
}
async function loadRouteSuggestions(placeId){
  const el = document.getElementById('routeSuggestions');
  if(!el) return;
  try{
    const { suggestions } = await apiGet(`/api/route-suggestions?place_id=${placeId}`);
    if(!el.isConnected) return; // detail sheet may have been closed while this was in flight
    if(!suggestions || !suggestions.length){
      el.innerHTML = `<div style="font-size:12.5px;color:var(--ink-soft);">No community route tips yet — be the first to help the next visitor.</div>`;
      return;
    }
    el.innerHTML = suggestions.map(s => `
      <div class="trail-note${s.is_admin ? ' trail-note-official' : ''}">
        <b>${s.is_admin ? 'Admin' : '@'+s.handle}${s.is_admin ? '' : ''}:</b> ${escapeHtml(s.note)}
      </div>`).join('');
  }catch(e){
    el.innerHTML = '';
  }
}
function submitRouteSuggestion(placeId){
  requireAuth(async () => {
    const input = document.getElementById('routeSuggestInput');
    const note = input.value.trim();
    if(!note) return;
    input.disabled = true;
    try{
      await apiPost('/api/route-suggestions', { place_id: placeId, note });
      input.value = '';
      showToast('Thanks — your route tip is live for other visitors.', 3000);
      loadRouteSuggestions(placeId);
    }catch(e){
      if(e.status === 401){ openAuthModal(); }
      else alert((e.data && e.data.message) || 'Could not post that — try again.');
    }finally{
      input.disabled = false;
    }
  });
}
function hideDetailUI(){
  document.getElementById('overlay').classList.remove('active');
  clearInterval(detailCarouselTimer);
  // The sheet's DOM (and any playing video in it) isn't torn down until the
  // next openDetail() call, so without this a video left mid-play just
  // keeps decoding invisibly in the background after the sheet is closed.
  document.querySelectorAll('#detailBody video').forEach(v=>v.pause());
  openDetailId = null;
}
function closeDetail(){ closeUIModal('detail', hideDetailUI); }

/* ---------------- Toast helper ---------------- */
function showToast(msg, ms){
  const el = document.getElementById('routeToast');
  el.classList.remove('xp-toast');
  el.textContent = msg; el.style.display = 'block';
  requestAnimationFrame(()=>el.classList.add('show'));
  clearTimeout(el._t);
  el._t = setTimeout(()=>{ el.classList.remove('show'); setTimeout(()=>{ el.style.display='none'; }, 260); }, ms || 4000);
}

/* ---------------- Gamification: XP toast + level-up/badge popups ----------------
   Every XP-earning endpoint (check-in, post, gem submission) returns an
   `xp` field shaped like awardXp()'s result in functions/_lib/xp.js:
   { xp_gained, xp_total, level, leveled_up, level_title, new_badges }, or
   null if that award silently failed server-side (never blocks the action
   itself — see each endpoint's own try/catch around awardXp()).

   Popup priority (per the "mix — big for major milestones, small for
   routine ones" choice): the XP toast always shows immediately since it's
   routine and shouldn't block anything. A level-up or badge unlock is a
   bigger deal and queues instead — shown one at a time, in case a single
   action nets more than one (e.g. a check-in that both levels you up and
   crosses a badge's threshold in the same call). */
let rewardPopupQueue = [];
let rewardPopupShowing = false;
function handleXpResult(xp){
  if(!xp) return;
  const toastEl = document.getElementById('routeToast');
  toastEl.classList.add('xp-toast');
  showToast(`+${xp.xp_gained} XP`, 1800);
  if(xp.leveled_up) rewardPopupQueue.push({ kind:'level', level:xp.level, title:xp.level_title });
  if(Array.isArray(xp.new_badges)) xp.new_badges.forEach(b => rewardPopupQueue.push({ kind:'badge', badge:b }));
  advanceRewardQueue();
}
function advanceRewardQueue(){
  if(rewardPopupShowing || !rewardPopupQueue.length) return;
  rewardPopupShowing = true;
  const next = rewardPopupQueue.shift();
  if(next.kind === 'level') showLevelUpPopup(next.level, next.title);
  else showBadgePopup(next.badge);
}
function showLevelUpPopup(level, title){
  document.getElementById('levelUpNum').textContent = level;
  document.getElementById('levelUpTitle').textContent = title;
  document.getElementById('levelUpOverlay').classList.add('active');
}
function dismissLevelUpPopup(){
  document.getElementById('levelUpOverlay').classList.remove('active');
  rewardPopupShowing = false;
  advanceRewardQueue();
}
function showBadgePopup(badge){
  document.getElementById('badgeIcon').textContent = badge.icon;
  document.getElementById('badgeName').textContent = badge.name;
  document.getElementById('badgeDesc').textContent = badge.description;
  document.getElementById('badgeOverlay').classList.add('active');
}
function dismissBadgePopup(){
  document.getElementById('badgeOverlay').classList.remove('active');
  rewardPopupShowing = false;
  advanceRewardQueue();
}

/* ---------------- Turn-by-turn navigation (OSRM steps, no key needed) ---------------- */
const NAV_FOLLOW_ZOOM = 18; // close, street-level zoom — like Google Maps nav mode
const NAV_PITCH = 60; // max tilt the SDK allows — strongest 3D "driving" perspective
const NAV_CAM_MS = 750; // camera animation duration — roughly matches the GPS fix interval so movement reads as continuous, not a jump-cut
const NAV_HEADING_MIN_MOVE_KM = 0.003; // ignore heading changes from GPS jitter under ~3m of movement
const NAV_PREVIEW_SHOW_KM = 0.4; // show the "Then ..." next-next-turn preview once within this distance of the upcoming maneuver
// Off-route detection + auto-reroute. NAV_OFFROUTE_KM is how far from the
// planned line counts as "off it" — generous enough that normal GPS jitter
// near a road doesn't trigger false positives. NAV_OFFROUTE_STRIKES is how
// many consecutive fixes have to be over that before we actually reroute,
// so a single noisy fix (tunnel, tall buildings, a bad HDOP reading)
// doesn't kick off a reroute for nothing — you have to be consistently off
// the line for a few seconds running.
const NAV_OFFROUTE_KM = 0.06;
const NAV_OFFROUTE_STRIKES = 3;
let navOffRouteStrikes = 0, navRerouting = false;
let navRouteCoords = []; // flat [lat,lng] samples of the current planned route, for off-route distance checks
// Speed-based zoom: walking/stationary stays close-in, highway speed pulls
// out so more of the road ahead is visible — same idea as Google Maps
// easing the camera back the faster you're going.
const NAV_ZOOM_STOPS = [ // [minSpeedMps, zoom] — first is a floor, later ones need decreasing zoom for greater speed... sorted ascending by speed
  { minSpeedMps: 0,    zoom: 18.5 },
  { minSpeedMps: 3,    zoom: 17.5 }, // ~11 km/h+
  { minSpeedMps: 8,    zoom: 17 },   // ~29 km/h+
  { minSpeedMps: 14,   zoom: 16.3 }, // ~50 km/h+
  { minSpeedMps: 22,   zoom: 15.5 }, // ~79 km/h+
];
let navCurrentZoom = NAV_FOLLOW_ZOOM;
let navMap = null, navSteps = [], navStepIndex = 0, navDestPlace = null;
let navUserMarker = null, navRouteLine = null, navWatchId = null, navLastPos = null;
let navLastFixTime = null; // pos.timestamp of the last accepted GPS fix — used to derive speed when the device doesn't report pos.coords.speed itself
// Multi-stop trip navigation state. navTripQueue holds the stops still to
// come after the one currently being navigated to; null means "not
// navigating a trip" (a plain single-destination nav via startNavigation()/
// navigateToDestination() directly). navTripIndex/navTripTotal are purely
// for the "stop X of Y" toast between legs.
let navTripQueue = null, navTripIndex = 0, navTripTotal = 0, navTripAdvanceTimer = null;
// Breadcrumb trail: the actual path traveled so far this navigation
// session, drawn as its own line separate from navRouteLine (the planned
// route). Pure GPS + client-side drawing — no network involved at all, so
// this keeps working exactly the same with zero signal. navTrail holds the
// raw [lat,lng] points; navTrailLine is the polyline currently on the map
// for them (redrawn, not appended-to, since the SDK has no live path-edit
// API we can rely on).
let navTrail = [], navTrailLine = null;
const NAV_TRAIL_MIN_MOVE_KM = 0.008; // ~8m — skips GPS jitter points while stationary
const NAV_TRAIL_MAX_POINTS = 5000; // long-trip safety cap; oldest points drop first
let navFollowing = true, navMuted = false, navStepsOpen = false;
// Heading-up rotation state: navHeadingUp toggles whether the map rotates to
// match travel direction (Google-Maps-style) vs. staying north-up.
// navCurrentBearing is whatever bearing is actually applied to the map right
// now, so the arrow icon (which does NOT auto-rotate with the map — it's a
// plain DOM/SVG marker) can be drawn relative to it and still visually point
// the right way on screen. navLastHeading is the last accepted heading,
// carried forward when the device is stationary/jittery.
let navHeadingUp = true, navCurrentBearing = 0, navLastHeading = 0;
// 3D tilt vs. flat (2D, north-facing-camera) nav view — Google-Maps-style
// toggle. Persisted across sessions (like Google Maps remembers your last
// choice) rather than always resetting to 3D. navPitchValue() is what every
// camera call below should read instead of the NAV_PITCH constant directly,
// so flipping this mid-navigation is a single source of truth.
let navTiltEnabled = true;
try{ navTiltEnabled = localStorage.getItem('thikana_nav_tilt') !== '0'; }catch(e){}
function navPitchValue(){ return navTiltEnabled ? NAV_PITCH : 0; }
// The heading actually painted on the arrow icon right now, kept separate
// from navLastHeading (which tracks the latest *accepted* GPS heading) so
// animateNavMarkerTo() below always knows the true starting angle to
// interpolate from, even mid-animation.
let navMarkerHeading = 0, navMarkerAnimFrame = null;
// Compass-driven map rotation. navDeviceHeading is the phone's actual
// physical compass heading from the DeviceOrientation sensor (null if the
// sensor/permission isn't available, in which case GPS course is the
// fallback). navBearingRAF drives a continuous per-frame loop that eases
// navCurrentBearing toward whichever heading is current — this is what
// makes the map spin the instant you turn the phone, instead of only ever
// updating once a second on a GPS fix (the old behavior, which is why it
// read as janky/stepped rather than smooth).
let navDeviceHeading = null, navCompassActive = false, navOrientationHandler = null;
let navGotAbsoluteHeading = false; // true once a real absolute-orientation event has arrived — see handleDeviceOrientation
let navBearingRAF = null;
const NAV_BEARING_SMOOTHING = 0.18; // per-frame ease factor toward the target bearing (shortest-path)
// Low-pass factor for the raw compass reading itself (applied in
// handleDeviceOrientation, before the value ever becomes navBearingTick's
// easing target) — separate from NAV_BEARING_SMOOTHING above, which only
// smooths the map's approach *toward* navDeviceHeading. Magnetometer noise
// (worse when the phone is held upright/vertical rather than flat) was
// making navDeviceHeading itself jitter a few degrees per sensor event;
// without filtering it at the source, the camera easing just chases that
// noise every frame, which read as the map wobbling side-to-side rather
// than settling on a heading.
const NAV_HEADING_FILTER_ALPHA = 0.25;
// True while a camera move WE triggered (follow updates, recenter, initial
// setup, tilt toggle) is in flight, so the zoomstart listener can tell that
// apart from a real user gesture and not misfire.
// Kept deliberately short — just long enough to swallow the start event our
// own easeTo() call dispatches, not the whole animation — so a real pinch-
// zoom-out has almost the entire ~1s gap between GPS fixes to register.
// (A single shared flag used to also get refreshed by every one of the
// ~60/sec bearing-rotation ticks below, which meant it was ALWAYS "in
// flight" and a real zoom gesture could never break follow at all — that's
// the "always recentred, can't zoom out" bug. Only zoomstart breaks follow
// now — dragging/rotating the map no longer does, so pan/rotate gestures
// just get panned/rotated straight back on the next GPS tick instead of
// switching auto-follow off, keeping the map centered and gyro-rotating
// through everything except a manual zoom.)
const NAV_PROGRAMMATIC_CAM_MS = 350;
let navProgrammaticCam = false, navProgrammaticCamTimer = null;
function markNavProgrammaticCam(){
  navProgrammaticCam = true;
  clearTimeout(navProgrammaticCamTimer);
  navProgrammaticCamTimer = setTimeout(()=>{ navProgrammaticCam = false; }, NAV_PROGRAMMATIC_CAM_MS);
}

/* ---------------- Trip presence: other people's live dots while navigating a shared trip ----------------
   Not the old meetup/location-share system (see CHANGES.md) — there's no
   meetup pin, no forced destination, nobody "arrives" for anyone else.
   This is just: while you're navigating a stop that belongs to a shared
   trip (navTripPlanId set — see navigateToDestination above), your
   position gets posted to /api/trip-presence on a short interval, and
   everyone else currently doing the same for that same trip shows up as a
   colored dot on your nav map, and vice versa. Each person still follows
   their own route at their own pace; zooming out on the nav map is what
   "see where your friends are" means here — there's no separate view for
   it. Starts in openNavOverlay() (only if navTripPlanId is set), stops in
   cleanupNavigation() — nothing outlives the nav session itself, and
   nothing runs at all for a plain non-trip or unshared-trip navigation. */
let navTripPlanId = null;
let navPresencePingTimer = null, navPresencePollTimer = null;
let navPresenceMarkers = new Map(); // other user's id -> their marker on navMap
const TRIP_PRESENCE_PING_MS = 8000;
const TRIP_PRESENCE_POLL_MS = 8000;
const TRIP_PRESENCE_COLORS = ['#3B82F6','#F59E0B','#8B5CF6','#14B8A6','#EF4444','#EC4899','#84CC16'];
// Stable per-user color so the same person's dot doesn't change shade
// between poll ticks or across a page reload.
function tripPresenceColorFor(userId){
  let h = 0;
  const s = String(userId);
  for(let i=0;i<s.length;i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return TRIP_PRESENCE_COLORS[h % TRIP_PRESENCE_COLORS.length];
}
function tripPresenceDotIcon(user){
  const color = tripPresenceColorFor(user.id);
  const bg = user.avatar_url ? `background-image:url('${user.avatar_url}');background-size:cover;` : '';
  const html = `<div style="width:26px;height:26px;border-radius:50%;background:${color};border:2px solid #fff;box-shadow:0 1px 5px rgba(0,0,0,.35);display:flex;align-items:center;justify-content:center;color:#fff;font-size:11px;font-weight:700;font-family:'Work Sans',sans-serif;${bg}">${user.avatar_url ? '' : avatarInitial({ handle:user.display_name || user.handle })}</div>`;
  return { html, width:26, height:26 };
}
function startTripPresence(){
  if(!navTripPlanId) return;
  stopTripPresenceTimers();
  sendTripPresencePing();
  navPresencePingTimer = setInterval(sendTripPresencePing, TRIP_PRESENCE_PING_MS);
  pollTripPresence();
  navPresencePollTimer = setInterval(pollTripPresence, TRIP_PRESENCE_POLL_MS);
  document.addEventListener('visibilitychange', onTripPresenceVisibilityChange);
}
function stopTripPresenceTimers(){
  if(navPresencePingTimer){ clearInterval(navPresencePingTimer); navPresencePingTimer = null; }
  if(navPresencePollTimer){ clearInterval(navPresencePollTimer); navPresencePollTimer = null; }
}
async function sendTripPresencePing(){
  if(!navTripPlanId || !navLastPos) return;
  try{ await apiPost('/api/trip-presence', { trip_plan_id:navTripPlanId, lat:navLastPos[0], lng:navLastPos[1] }); }catch(e){}
}
async function pollTripPresence(){
  if(!navTripPlanId) return;
  try{
    const { users } = await apiGet(`/api/trip-presence?trip_plan_id=${navTripPlanId}`);
    renderTripPresenceMarkers(users || []);
  }catch(e){}
}
function renderTripPresenceMarkers(users){
  if(!navMap) return;
  const seen = new Set();
  users.forEach(u=>{
    seen.add(u.id);
    const pos = { lat:u.lat, lng:u.lng };
    const existing = navPresenceMarkers.get(u.id);
    if(existing){
      if(typeof existing.setPosition === 'function') existing.setPosition(pos);
      else if(typeof existing.setLngLat === 'function') existing.setLngLat([pos.lng, pos.lat]);
    } else {
      const icon = tripPresenceDotIcon(u);
      navPresenceMarkers.set(u.id, new mappls.Marker({ map:navMap, position:pos, html:icon.html, width:icon.width, height:icon.height, fitbounds:false }));
    }
  });
  // Anyone not in this tick's list has stopped navigating this trip (nav
  // closed, tab hidden, or just gone stale server-side) — drop their dot.
  navPresenceMarkers.forEach((marker, uid)=>{
    if(!seen.has(uid)){ removeMarker(marker, navMap); navPresenceMarkers.delete(uid); }
  });
}
// Tab hidden = broadcasting stops (nothing persists once you're not
// actively here — see the removal note in CHANGES.md); coming back while
// nav is still open resumes it under the same trip.
function onTripPresenceVisibilityChange(){
  if(!navTripPlanId) return;
  if(window.AndroidNativeAuth) return; // Native foreground service keeps trip presence active when screen is off
  if(document.hidden){
    stopTripPresenceTimers();
    apiDelete(`/api/trip-presence?trip_plan_id=${navTripPlanId}`).catch(()=>{});
  } else if(navMap){
    startTripPresence();
  }
}
function stopTripPresence(){
  document.removeEventListener('visibilitychange', onTripPresenceVisibilityChange);
  stopTripPresenceTimers();
  navPresenceMarkers.forEach(m=>removeMarker(m, navMap));
  navPresenceMarkers = new Map();
  if(navTripPlanId){
    const tid = navTripPlanId;
    apiDelete(`/api/trip-presence?trip_plan_id=${tid}`).catch(()=>{});
  }
  navTripPlanId = null;
}

function startNavigation(placeId){
  const place = PLACES.find(p=>p.id===placeId);
  if(!place) return;
  navigateToDestination({ lat:place.lat, lng:place.lng, name:place.name, cat:place.cat, gem:place.gem });
}
// Generic version behind startNavigation — also used for plain searched
// spots (OSM results) that aren't one of your own PLACES and so have no
// category/gem styling to fall back on.
function navigateToDestination(dest, navOpts){
  // iOS 13+ only grants DeviceOrientation access when requestPermission()
  // is called synchronously inside a user gesture. This function's own
  // work is already async (GPS fix, routing fetch), so the request has to
  // fire right here at the top — before any of that — rather than later
  // once openNavOverlay() actually creates the map.
  requestCompassPermission();
  if(!navigator.geolocation){ showToast("Location isn't available in this browser."); return; }
  if(map.closePopup) map.closePopup();
  showToast('Finding your location…', 8000);
  navigator.geolocation.getCurrentPosition(async (pos)=>{
    const from = [pos.coords.latitude, pos.coords.longitude];
    try{
      // Routed through our own backend (/api/route) rather than calling
      // Mappls directly — the routing API needs an OAuth bearer token
      // (client_id/client_secret), which can't safely live in this
      // frontend JS. The backend proxy still returns the same
      // routes[]/geometry/legs[].steps shape, so the rest of the nav code
      // below reads it unchanged.
      const url = `/api/route?from=${from[0]},${from[1]}&to=${dest.lat},${dest.lng}`;
      const res = await fetch(url);
      const data = await res.json();
      if(!data.routes || !data.routes.length) throw new Error('no_route');
      const route = data.routes[0];
      navSteps = route.legs[0].steps;
      navStepIndex = 0;
      navDestPlace = dest;
      hideDetailUI();
      // Only touch navTripPlanId when this call actually specifies one —
      // arriveNavigation()'s own leg-to-leg hand-off calls this with no
      // navOpts at all, and it needs whatever the trip's first leg set to
      // just carry forward untouched (see startTripNavigation/
      // navigateTripLeg above and startTripPresence below).
      if(navOpts && navOpts.tripPlanId !== undefined) navTripPlanId = navOpts.tripPlanId;
      // Default is {replace:true} — a place's detail sheet hands straight
      // into nav without closing itself first, so nav should take its spot
      // on the stack. Callers that already closed their own modal first
      // (navigateTripLeg) pass {replace:false} instead —
      // otherwise this would blindly overwrite whatever's now on top
      // (e.g. the Messages thread underneath), leaving it stuck.
      openNavOverlay(route, from, navOpts || { replace:true });
    }catch(e){
      showToast("Couldn't start navigation right now — try 'Open in Google Maps' instead.", 5000);
    }
  }, ()=>{
    showToast('Location permission denied — try "Open in Google Maps" instead.', 5000);
  }, { enableHighAccuracy:true });
}

/* ---- Compass-driven map rotation (smooth, turns with the phone) ---- */
// Compensates the raw sensor reading for however the phone is currently
// held (portrait vs. rotated to landscape) so "north" stays correct.
function navScreenAngle(){
  if(screen.orientation && typeof screen.orientation.angle === 'number') return screen.orientation.angle;
  if(typeof window.orientation === 'number') return window.orientation;
  return 0;
}
function handleDeviceOrientation(e){
  // Requiring e.absolute here used to mean: on any browser/webview that
  // fires plain 'deviceorientation' without ever setting that flag (common
  // on Android WebViews and non-Chrome mobile browsers), heading stayed
  // null forever and rotation fell all the way back to GPS-course, which
  // only updates once per accepted fix (~1s, and only past ~3m of
  // movement) — that gap is what read as the screen "delaying" behind an
  // actual phone turn. Now we accept alpha from either event, and once a
  // properly-flagged absolute reading has shown up at least once, we
  // prefer it and ignore the noisier relative-only ones from then on.
  const isAbsolute = e.absolute === true || e.type === 'deviceorientationabsolute';
  if(isAbsolute) navGotAbsoluteHeading = true;
  if(!isAbsolute && navGotAbsoluteHeading) return;
  let heading = null;
  if(typeof e.webkitCompassHeading === 'number' && !isNaN(e.webkitCompassHeading)){
    heading = e.webkitCompassHeading; // iOS Safari — already a true compass heading
  } else if(typeof e.alpha === 'number' && !isNaN(e.alpha)){
    // alpha increases counter-clockwise from (magnetic, if absolute) north
    // — flip to a clockwise compass bearing and correct for screen rotation.
    heading = (360 - e.alpha + navScreenAngle()) % 360;
  }
  if(heading != null && !isNaN(heading)){
    heading = (heading + 360) % 360;
    // Filter the raw reading (see NAV_HEADING_FILTER_ALPHA above) instead of
    // assigning it straight to navDeviceHeading — wrap-safe shortest-path
    // delta, same trick navBearingTick uses for the map's own easing.
    if(navDeviceHeading == null){
      navDeviceHeading = heading;
    } else {
      const filterDelta = ((heading - navDeviceHeading + 540) % 360) - 180;
      navDeviceHeading = (navDeviceHeading + filterDelta * NAV_HEADING_FILTER_ALPHA + 360) % 360;
    }
  }
}
function requestCompassPermission(){
  try{
    if(typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function'){
      DeviceOrientationEvent.requestPermission().catch(()=>{});
    }
  }catch(e){}
}
function startCompassTracking(){
  if(navCompassActive) return;
  const attach = ()=>{
    if(navCompassActive) return;
    navOrientationHandler = handleDeviceOrientation;
    // Attach both rather than feature-detecting and picking one —
    // 'ondeviceorientationabsolute' in window being true doesn't guarantee
    // the browser ever actually dispatches it, which previously left some
    // devices with no heading source at all. handleDeviceOrientation
    // sorts out which one to trust.
    window.addEventListener('deviceorientationabsolute', navOrientationHandler, true);
    window.addEventListener('deviceorientation', navOrientationHandler, true);
    navCompassActive = true;
  };
  if(typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function'){
    // Permission was already requested (see navigateToDestination); this
    // just resolves immediately if it's already been granted/denied.
    DeviceOrientationEvent.requestPermission().then(state=>{ if(state === 'granted') attach(); }).catch(()=>{});
  } else {
    attach(); // no permission gate (Android/desktop) — attach straight away
  }
}
function stopCompassTracking(){
  if(navOrientationHandler){
    window.removeEventListener('deviceorientationabsolute', navOrientationHandler, true);
    window.removeEventListener('deviceorientation', navOrientationHandler, true);
    navOrientationHandler = null;
  }
  navCompassActive = false;
  navDeviceHeading = null;
  navGotAbsoluteHeading = false;
}
// Applies a new bearing to the map immediately (no easeTo tween — the
// smoothing already happens frame-by-frame in navBearingTick below) and
// keeps the arrow icon's on-screen angle correct as the map spins under it.
function applyNavBearing(b){
  navCurrentBearing = (b + 360) % 360;
  // This runs continuously (~60/sec) while navBearingTick is actively
  // smoothing the heading. setBearing() below is a plain, unguarded map
  // call — rotatestart is no longer listened for at all (manual rotation
  // no longer breaks follow, see openNavOverlay), so there's nothing left
  // to suppress here.
  if(navMap && typeof navMap.setBearing === 'function') navMap.setBearing(navCurrentBearing);
  updateNavCompassUI();
  if(navUserMarker){
    const arrowAngle = navHeadingUp ? (navMarkerHeading - navCurrentBearing + 360) % 360 : navMarkerHeading;
    const arrowIcon = navArrowIcon(arrowAngle);
    if(typeof navUserMarker.setIcon === 'function') navUserMarker.setIcon({html:arrowIcon.html, width:arrowIcon.width, height:arrowIcon.height});
  }
}
// Runs every frame while navigating: eases navCurrentBearing toward
// whichever heading is authoritative right now (device compass first,
// GPS-course as fallback) via the shortest rotational path, so the map
// rotates continuously as you turn the phone instead of snapping once per
// GPS fix. Skips while the user has broken auto-follow (manual drag/rotate)
// so it doesn't fight their gesture — same rule updateNavCamera follows.
function navBearingTick(){
  if(!navMap){ navBearingRAF = null; return; }
  if(navFollowing){
    const target = navHeadingUp ? (navDeviceHeading != null ? navDeviceHeading : navLastHeading) : 0;
    const delta = ((target - navCurrentBearing + 540) % 360) - 180; // shortest path, e.g. 350°→10° goes through 20°, not 340°
    // Threshold raised from 0.05° to 1.5° — sub-1.5° deltas are just
    // residual sensor noise (see NAV_HEADING_FILTER_ALPHA), not an actual
    // turn, so re-issuing setBearing() for them only added to the wobble
    // rather than tracking anything real.
    if(Math.abs(delta) > 1.5) applyNavBearing(navCurrentBearing + delta * NAV_BEARING_SMOOTHING);
  }
  navBearingRAF = requestAnimationFrame(navBearingTick);
}
function startNavBearingLoop(){
  if(!navBearingRAF) navBearingRAF = requestAnimationFrame(navBearingTick);
}
function stopNavBearingLoop(){
  if(navBearingRAF){ cancelAnimationFrame(navBearingRAF); navBearingRAF = null; }
}

function openNavOverlay(route, startCoord, opts){
  document.getElementById('navOverlay').classList.add('active');
  if(opts && opts.replace){ replaceUIModal('nav'); } else { pushUIModal('nav'); }

  // Face the map in the direction of travel from the very first frame,
  // rather than snapping to heading-up only after the first GPS fix.
  navHeadingUp = true; navFollowing = true; navMuted = false; navStepsOpen = false;
  const firstCoord = route.geometry.coordinates[Math.min(1, route.geometry.coordinates.length-1)];
  navLastHeading = firstCoord ? bearing(startCoord[0], startCoord[1], firstCoord[1], firstCoord[0]) : 0;
  navCurrentBearing = navLastHeading;
  navMarkerHeading = 0; // drawn relative to navCurrentBearing, which is 0 here — see arrow comment below
  if(navMarkerAnimFrame){ cancelAnimationFrame(navMarkerAnimFrame); navMarkerAnimFrame = null; }

  navMap = new mappls.Map('navMap', {
    center:{lat:startCoord[0],lng:startCoord[1]}, zoom:NAV_FOLLOW_ZOOM, zoomControl:false,
    bearing:navCurrentBearing, pitch:navPitchValue(), clickableIcons:false, clickableIcons_callback: () => {}
  });
  markNavProgrammaticCam();
  navRouteCoords = route.geometry.coordinates.map(c=>[c[1], c[0]]);
  // Polyline path needs {lat,lng} objects (same shape used in
  // rerouteFromCurrentPosition and the trip-planner map below) — separate
  // from navRouteCoords above, which stays [lat,lng] arrays for the
  // off-route distance math.
  const coords = route.geometry.coordinates.map(c=>({lat:c[1], lng:c[0]}));
  navOffRouteStrikes = 0; navRerouting = false;
  navCurrentZoom = NAV_FOLLOW_ZOOM;
  navRouteLine = new mappls.Polyline({ map:navMap, path:coords, strokeColor:'#FF3B5C', strokeOpacity:0.9, strokeWeight:6 });
  const destIcon = navDestPlace.cat ? pinIcon(navDestPlace.cat, navDestPlace.gem, navDestPlace.verified, false, navDestPlace.img) : searchPinIcon();
  new mappls.Marker({ map:navMap, position:{lat:navDestPlace.lat,lng:navDestPlace.lng}, html:destIcon.html, width:destIcon.width, height:destIcon.height, fitbounds:false });
  // Arrow is drawn relative to navCurrentBearing (0 here, since the map
  // itself is already facing travel direction) — see updateNavCamera().
  const arrowIcon0 = navArrowIcon(0);
  navUserMarker = new mappls.Marker({ map:navMap, position:{lat:startCoord[0],lng:startCoord[1]}, html:arrowIcon0.html, width:arrowIcon0.width, height:arrowIcon0.height, fitbounds:false });
  // Google-Maps-style nav: zoom in close on the start location rather than
  // zooming out to fit the whole route — the route line is still visible,
  // it just isn't what the initial view frames.
  if(typeof navMap.setCenter === 'function') navMap.setCenter({lat:startCoord[0],lng:startCoord[1]});
  if(typeof navMap.setZoom === 'function') navMap.setZoom(NAV_FOLLOW_ZOOM);
  if(typeof navMap.setBearing === 'function') navMap.setBearing(navCurrentBearing);
  if(typeof navMap.setPitch === 'function') navMap.setPitch(navPitchValue());
  setTimeout(()=>{ if(typeof navMap.resize === 'function') navMap.resize(); }, 60);

  // Only zooming out/in manually switches off auto-follow (and stops the
  // gyro/heading-up rotation, since that's gated on the same navFollowing
  // flag) — the recenter button only appears once that happens. Dragging or
  // rotating the map no longer breaks follow at all: the next GPS-driven
  // camera tick (~1/sec) just pans/rotates it straight back, so the map
  // stays centered and gyro-rotating through those, exactly like it does
  // when nothing is touched.
  document.getElementById('navRecenterBtn').classList.remove('visible');
  updateNavCompassUI();
  updateNavTiltUI();
  // Only a genuine user gesture should break auto-follow, not our own
  // programmatic camera calls used to keep the map following — checking
  // e.originalEvent isn't reliable across gesture types/SDK builds, so we
  // track our own moves via navProgrammaticCam instead.
  if(typeof navMap.on === 'function'){
    navMap.on('zoomstart', ()=>{
      if(navProgrammaticCam) return;
      navFollowing = false;
      document.getElementById('navRecenterBtn').classList.add('visible');
    });
  }
  document.getElementById('navStepsPanel').classList.remove('open');
  document.getElementById('navDestChev').classList.remove('open');
  document.getElementById('navMuteBtn').innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/></svg>';
  document.getElementById('navDestName').textContent = navTripQueue ? `${navDestPlace.name} (stop ${navTripIndex} of ${navTripTotal})` : navDestPlace.name;
  document.getElementById('navDestSub').textContent = `${(CATS[navDestPlace.cat] && CATS[navDestPlace.cat].label) || 'Destination'} · Driving directions`;

  navLastPos = startCoord;
  navLastFixTime = null;
  navTrail = [[startCoord[0], startCoord[1]]];
  navTrailLine = null;
  updateNavInstruction(startCoord);
  renderNavStepsList();
  speak(stepText(navSteps[0]));

  if(navWatchId) navigator.geolocation.clearWatch(navWatchId);
  navWatchId = navigator.geolocation.watchPosition(onNavPosition, ()=>{}, { enableHighAccuracy:true, maximumAge:1000 });
  startCompassTracking();
  startNavBearingLoop();

  // Only for a leg that belongs to a shared trip (see navTripPlanId above) —
  // a plain single-place nav or a solo/unsent trip leaves this null and
  // nothing presence-related ever runs.
  if(navTripPlanId) startTripPresence();

  if(window.AndroidNativeAuth && window.AndroidNativeAuth.startBackgroundNavigation){
    try{
      const dName = (navDestPlace && navDestPlace.name) || 'Destination';
      const dLat = (navDestPlace && navDestPlace.lat) || 0;
      const dLng = (navDestPlace && navDestPlace.lng) || 0;
      const tId = navTripPlanId || '';
      const steps = (navSteps && navSteps.length) ? JSON.stringify(navSteps) : '';
      window.AndroidNativeAuth.startBackgroundNavigation(dName, dLat, dLng, tId, steps);
    }catch(err){}
  }
}

// Moves the arrow marker from its last drawn spot to a new GPS fix over
// durationMs instead of teleporting there instantly. Raw GPS fixes land
// roughly once a second (see watchPosition's maximumAge below) while the
// camera itself glides continuously via updateNavCamera's easeTo — without
// this, the blue arrow used to visibly snap/step at every fix while the
// map kept panning smoothly underneath it, the classic "janky dot" look.
// Runs on the same NAV_CAM_MS duration as the camera ease so both move in
// lockstep, the way Google/Uber-style nav dots read as continuous motion.
function animateNavMarkerTo(fromPos, toPos, fromHeading, toHeading, durationMs){
  if(navMarkerAnimFrame){ cancelAnimationFrame(navMarkerAnimFrame); navMarkerAnimFrame = null; }
  if(!fromPos){ setNavMarkerAt(toPos, toHeading); return; }
  const start = performance.now();
  // Shortest-path angle interpolation so e.g. 350°→10° turns through 20°,
  // not the long way round through 340°.
  const headingDelta = ((toHeading - fromHeading + 540) % 360) - 180;
  function step(now){
    const t = Math.min(1, (now - start) / durationMs);
    const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic — matches the feel of --ease elsewhere
    setNavMarkerAt(
      [fromPos[0] + (toPos[0]-fromPos[0])*eased, fromPos[1] + (toPos[1]-fromPos[1])*eased],
      (fromHeading + headingDelta*eased + 360) % 360
    );
    if(t < 1) navMarkerAnimFrame = requestAnimationFrame(step);
    else navMarkerAnimFrame = null;
  }
  navMarkerAnimFrame = requestAnimationFrame(step);
}
function setNavMarkerAt(pos, heading){
  if(!navUserMarker) return;
  navMarkerHeading = heading;
  if(typeof navUserMarker.setPosition === 'function') navUserMarker.setPosition({lat:pos[0],lng:pos[1]});
  else if(typeof navUserMarker.setLngLat === 'function') navUserMarker.setLngLat([pos[1],pos[0]]);
  // The arrow marker is a plain DOM/SVG icon, not something the map SDK
  // rotates for us — so its on-screen angle has to be drawn relative to
  // whatever bearing is currently applied to the map (0 when north-up,
  // the live heading when heading-up), not the raw compass heading.
  const arrowAngle = navHeadingUp ? (heading - navCurrentBearing + 360) % 360 : heading;
  const arrowIcon = navArrowIcon(arrowAngle);
  if(typeof navUserMarker.setIcon === 'function') navUserMarker.setIcon({html:arrowIcon.html, width:arrowIcon.width, height:arrowIcon.height});
  else {
    // Fallback for SDK builds without a setIcon method: drop the old arrow
    // and place a fresh one at the new heading/position.
    removeMarker(navUserMarker, navMap);
    navUserMarker = new mappls.Marker({ map:navMap, position:{lat:pos[0],lng:pos[1]}, html:arrowIcon.html, width:arrowIcon.width, height:arrowIcon.height, fitbounds:false });
  }
}
// Off-route helper: closest distance from `cur` to any segment of the
// planned route. Checked against segments (not just the sampled vertices)
// so a straight stretch between two far-apart route points doesn't read as
// "off route" just because no vertex happens to be nearby. navRouteCoords
// is a flat list of [lat,lng] — long routes are still just a few hundred
// points, cheap enough to scan every fix.
function distanceToRouteLine(cur){
  let min = Infinity;
  for(let i = 0; i < navRouteCoords.length - 1; i++){
    const d = distanceToSegmentKm(cur, navRouteCoords[i], navRouteCoords[i+1]);
    if(d < min) min = d;
  }
  return min;
}
// Approximates lat/lng as flat local coordinates (fine over the short
// segment lengths a route is sampled at) to project `p` onto segment a→b
// and measures the real haversine distance to that projected point.
function distanceToSegmentKm(p, a, b){
  const latRad = a[0] * Math.PI / 180;
  const kmPerDegLat = 110.574, kmPerDegLng = 111.320 * Math.cos(latRad);
  const ax = 0, ay = 0;
  const bx = (b[1]-a[1]) * kmPerDegLng, by = (b[0]-a[0]) * kmPerDegLat;
  const px = (p[1]-a[1]) * kmPerDegLng, py = (p[0]-a[0]) * kmPerDegLat;
  const abLenSq = bx*bx + by*by;
  let t = abLenSq > 0 ? ((px*bx + py*by) / abLenSq) : 0;
  t = Math.max(0, Math.min(1, t));
  const projLat = a[0] + t * (b[0]-a[0]), projLng = a[1] + t * (b[1]-a[1]);
  return haversine(p[0], p[1], projLat, projLng);
}
// Re-fetches a route from wherever the user actually is now to the same
// destination, and swaps it in without tearing down the nav session —
// same map, same trip-presence/voice/trail state, just a fresh line and
// step list. Mirrors what navigateToDestination() does for a brand-new
// nav, minus the parts that only make sense when opening the overlay.
async function rerouteFromCurrentPosition(cur){
  if(navRerouting || !navDestPlace) return;
  navRerouting = true;
  navOffRouteStrikes = 0;
  showToast('Recalculating route…', 2500);
  speak('Ek second, recalculating…');
  try{
    const url = `/api/route?from=${cur[0]},${cur[1]}&to=${navDestPlace.lat},${navDestPlace.lng}`;
    const res = await fetch(url);
    const data = await res.json();
    if(!data.routes || !data.routes.length) throw new Error('no_route');
    const route = data.routes[0];
    navSteps = route.legs[0].steps;
    navStepIndex = 0;
    if(navRouteLine) removeMarker(navRouteLine, navMap);
    const coords = route.geometry.coordinates.map(c=>({lat:c[1], lng:c[0]}));
    navRouteCoords = route.geometry.coordinates.map(c=>[c[1], c[0]]);
    navRouteLine = new mappls.Polyline({ map:navMap, path:coords, strokeColor:'#FF3B5C', strokeOpacity:0.9, strokeWeight:6 });
    updateNavInstruction(cur);
    renderNavStepsList();
    speak(stepText(navSteps[0]));
  }catch(e){
    showToast("Couldn't recalculate the route — keep going, we'll try again.", 3000);
  }finally{
    navRerouting = false;
  }
}
function onNavPosition(pos){
  if(!navMap) return;
  const cur = [pos.coords.latitude, pos.coords.longitude];
  const movedKm = navLastPos ? haversine(navLastPos[0], navLastPos[1], cur[0], cur[1]) : 0;
  // Prefer the device's real compass/course heading when it's actually
  // moving; otherwise fall back to the bearing between fixes, but only
  // update it once we've moved a few meters — under that, GPS noise makes
  // the bearing swing wildly and the map/arrow would spin in place.
  let heading = navLastHeading;
  if(pos.coords.heading != null && !isNaN(pos.coords.heading) && (pos.coords.speed == null || pos.coords.speed > 0.3)){
    heading = pos.coords.heading;
  } else if(navLastPos && movedKm > NAV_HEADING_MIN_MOVE_KM){
    heading = bearing(navLastPos[0], navLastPos[1], cur[0], cur[1]);
  }
  navLastHeading = heading;

  // Speed-based zoom: pos.coords.speed (m/s) is the GPS-reported ground
  // speed where the device provides it; fall back to distance/time between
  // fixes when it doesn't. Picks the zoom for the highest speed bracket
  // we've reached so the camera eases out well before the road ahead
  // actually needs the room, not after.
  let speedMps = (pos.coords.speed != null && !isNaN(pos.coords.speed) && pos.coords.speed >= 0) ? pos.coords.speed : null;
  if(speedMps == null && navLastPos){
    const dtSec = navLastFixTime ? (pos.timestamp - navLastFixTime) / 1000 : null;
    if(dtSec && dtSec > 0.2) speedMps = (movedKm * 1000) / dtSec;
  }
  navLastFixTime = pos.timestamp;
  if(speedMps != null){
    let zoom = NAV_ZOOM_STOPS[0].zoom;
    for(const stop of NAV_ZOOM_STOPS){ if(speedMps >= stop.minSpeedMps) zoom = stop.zoom; }
    navCurrentZoom = zoom;
  }

  saveLastKnownLoc(cur[0], cur[1]);
  animateNavMarkerTo(navLastPos, cur, navMarkerHeading, heading, NAV_CAM_MS);
  if(navFollowing) updateNavCamera(cur, heading);
  navLastPos = cur;
  updateNavTrail(cur);

  const distToDest = haversine(cur[0], cur[1], navDestPlace.lat, navDestPlace.lng);
  if(distToDest < 0.03){ arriveNavigation(); return; }

  // Off-route detection: how far is the current fix from the nearest point
  // on the planned line? A single noisy fix over the threshold doesn't
  // reroute on its own — it takes NAV_OFFROUTE_STRIKES in a row, so a dip
  // in accuracy near tall buildings/trees doesn't trigger a pointless
  // reroute mid-lane.
  if(!navRerouting && navRouteCoords.length){
    const offKm = distanceToRouteLine(cur);
    if(offKm > NAV_OFFROUTE_KM){
      navOffRouteStrikes++;
      if(navOffRouteStrikes >= NAV_OFFROUTE_STRIKES) rerouteFromCurrentPosition(cur);
    } else {
      navOffRouteStrikes = 0;
    }
  }

  let advanced = false;
  while(navStepIndex < navSteps.length - 1){
    const nextLoc = navSteps[navStepIndex + 1].maneuver.location; // [lon, lat]
    const d = haversine(cur[0], cur[1], nextLoc[1], nextLoc[0]);
    if(d < 0.03){
      navStepIndex++;
      advanced = true;
      speak(stepText(navSteps[navStepIndex]));
    } else break;
  }
  updateNavInstruction(cur);
  if(advanced) renderNavStepsList();
}

// Appends the new fix to the breadcrumb trail (skipping GPS jitter under
// ~8m) and redraws the trail line. This has nothing to do with the planned
// route or the network — it's just "everywhere the device has actually
// been this session" — so it's exactly as reliable offline as the blue
// arrow itself is.
function updateNavTrail(cur){
  const lastPt = navTrail[navTrail.length - 1];
  if(lastPt && haversine(lastPt[0], lastPt[1], cur[0], cur[1]) < NAV_TRAIL_MIN_MOVE_KM) return;
  navTrail.push([cur[0], cur[1]]);
  if(navTrail.length > NAV_TRAIL_MAX_POINTS) navTrail.shift();
  if(navTrailLine) removeMarker(navTrailLine, navMap);
  const path = navTrail.map(([lat,lng]) => ({ lat, lng }));
  navTrailLine = new mappls.Polyline({ map:navMap, path, strokeColor:'#22C55E', strokeOpacity:0.85, strokeWeight:4 });
}

// Moves the camera to follow the user — recentering AND re-zooming/re-tilting
// back to nav zoom every time, so a manual pinch-zoom-out gets pulled back in
// the moment following resumes (matches Google Maps' "snap back in" feel).
// Animated (not an instant jump) wherever the SDK supports it.
function updateNavCamera(cur, heading){
  // Bearing is intentionally NOT set here anymore — it's driven every
  // frame by navBearingTick() instead (see above). Doing it both places
  // used to mean a fresh 750ms eased bearing tween restarted on every GPS
  // fix (~once/sec), which is exactly what made the rotation look like it
  // was stepping/stuttering rather than turning smoothly. Pan/zoom/pitch
  // still ease on each fix since position updates are naturally spaced out
  // and easing them keeps that part of the camera move fluid.
  const center = {lat:cur[0], lng:cur[1]};
  const pitch = navPitchValue();
  markNavProgrammaticCam();
  if(typeof navMap.easeTo === 'function'){
    navMap.easeTo({ center, zoom:navCurrentZoom, pitch, duration:NAV_CAM_MS });
  } else if(typeof navMap.flyTo === 'function'){
    navMap.flyTo({ center, zoom:navCurrentZoom, pitch, duration:NAV_CAM_MS });
  } else {
    // Instant fallback for SDK builds without animated camera moves.
    if(typeof navMap.setCenter === 'function') navMap.setCenter(center);
    if(typeof navMap.setZoom === 'function') navMap.setZoom(navCurrentZoom);
    if(typeof navMap.setPitch === 'function') navMap.setPitch(pitch);
  }
}

// Tilt/flat toggle — flips between the 3D driving perspective (pitch 60°)
// and a straight-down flat view, the same "3D building" button Google Maps
// shows during turn-by-turn. Takes effect immediately regardless of whether
// we're currently following (matches Google Maps: it's a view preference,
// not tied to auto-follow), and the choice is remembered for next time.
function toggleNavTilt(){
  navTiltEnabled = !navTiltEnabled;
  try{ localStorage.setItem('thikana_nav_tilt', navTiltEnabled ? '1' : '0'); }catch(e){}
  updateNavTiltUI();
  if(!navMap) return;
  const pitch = navPitchValue();
  markNavProgrammaticCam();
  if(typeof navMap.easeTo === 'function') navMap.easeTo({ pitch, duration:NAV_CAM_MS });
  else if(typeof navMap.setPitch === 'function') navMap.setPitch(pitch);
}
function updateNavTiltUI(){
  const btn = document.getElementById('navTiltBtn');
  if(!btn) return;
  btn.classList.toggle('flat', !navTiltEnabled);
  btn.title = navTiltEnabled ? 'Switch to flat view' : 'Switch to 3D tilted view';
}

/* ---- Extra chrome: recenter, orientation toggle, mute, and the upcoming-steps list ---- */
// Tapping recenter doesn't just re-pan/zoom — setting navFollowing back to
// true also makes navBearingTick() (above) resume smoothing the map's
// rotation toward the live compass heading on the very next frame, since
// it's gated on navFollowing too and was frozen at whatever bearing was
// active the moment auto-follow broke. So heading-up "gyro" rotation always
// resumes automatically the moment this is tapped — nothing extra needed.
function recenterNav(){
  navFollowing = true;
  document.getElementById('navRecenterBtn').classList.remove('visible');
  if(navMap && navLastPos) updateNavCamera(navLastPos, navLastHeading);
}
// Compass tap: flip heading-up (rotates with travel direction) vs. north-up.
// If we're still following, re-apply the camera immediately; otherwise the
// new mode just takes effect next time following resumes/recenter is tapped.
function toggleNavOrientation(){
  navHeadingUp = !navHeadingUp;
  updateNavCompassUI();
  if(navMap && navFollowing && navLastPos) updateNavCamera(navLastPos, navLastHeading);
}
function updateNavCompassUI(){
  const svg = document.getElementById('navCompassSvg');
  if(!svg) return;
  // Needle always points at true north on screen, so it counter-rotates
  // against whatever bearing is currently applied to the map.
  svg.style.transform = `rotate(${-navCurrentBearing}deg)`;
}
function toggleNavMute(){
  navMuted = !navMuted;
  if(navMuted && 'speechSynthesis' in window) window.speechSynthesis.cancel();
  document.getElementById('navMuteBtn').innerHTML = navMuted
    ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M23 9l-6 6M17 9l6 6"/></svg>'
    : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/></svg>';
}
function toggleNavSteps(){
  navStepsOpen = !navStepsOpen;
  document.getElementById('navStepsPanel').classList.toggle('open', navStepsOpen);
  document.getElementById('navDestChev').classList.toggle('open', navStepsOpen);
}
function renderNavStepsList(){
  const el = document.getElementById('navStepsList');
  if(!el || !navSteps.length) return;
  el.innerHTML = navSteps.map((step, i) => `
    <div class="nav-step-row ${i < navStepIndex ? 'done' : ''}">
      <div class="nsr-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="transform:rotate(${maneuverRotation(step.maneuver.modifier)}deg);">
          <path d="M12 19V5M12 5l-6 6M12 5l6 6"/>
        </svg>
      </div>
      <div class="nsr-text">${stepText(step)}</div>
      <div class="nsr-dist">${fmtDist(step.distance/1000)}</div>
    </div>`).join('');
}

function updateNavInstruction(cur){
  const step = navSteps[navStepIndex];
  document.getElementById('navInstrText').textContent = stepText(step);
  document.getElementById('navTurnSvg').style.transform = `rotate(${maneuverRotation(step.maneuver.modifier)}deg)`;

  let distToNext;
  if(cur && navStepIndex < navSteps.length - 1){
    const loc = navSteps[navStepIndex + 1].maneuver.location;
    distToNext = haversine(cur[0], cur[1], loc[1], loc[0]);
  } else {
    distToNext = step.distance / 1000;
  }
  document.getElementById('navInstrDist').textContent = fmtDist(distToNext);

  // "Then ..." preview of the maneuver after the one you're counting down
  // to — Google-Maps-style heads-up so back-to-back turns aren't a
  // surprise. Only shows once you're close enough that it's actually
  // relevant, not for the whole length of a long step.
  const previewStep = navSteps[navStepIndex + 2];
  const previewEl = document.getElementById('navNextInstr');
  if(previewEl){
    if(previewStep && distToNext < NAV_PREVIEW_SHOW_KM){
      previewEl.textContent = `Then ${stepText(previewStep)}`;
      previewEl.classList.add('visible');
    } else {
      previewEl.classList.remove('visible');
    }
  }

  // The current step isn't finished yet — count only what's actually left
  // of it (distToNext, already computed live above from the real GPS fix),
  // not navSteps[navStepIndex].distance (its full original length from
  // wherever the step started). Using the full length meant this readout
  // only moved once an entire maneuver step completed — for a long step
  // (easily a minute or more), it looked completely frozen the whole time
  // despite genuinely moving, which read as "stuck" right alongside the
  // rotation issue above even though it's a separate cause.
  let remDist = 0, remDur = 0;
  for(let i = navStepIndex; i < navSteps.length; i++){
    if(i === navStepIndex){
      const stepDistM = distToNext * 1000;
      const frac = step.distance > 0 ? Math.min(1, stepDistM / step.distance) : 1;
      remDist += stepDistM;
      remDur += step.duration * frac;
    } else {
      remDist += navSteps[i].distance;
      remDur += navSteps[i].duration;
    }
  }
  document.getElementById('navDistLeft').textContent = `${fmtDist(remDist / 1000)} · ${Math.max(1, Math.round(remDur / 60))} min`;
  document.getElementById('navEta').textContent = fmtClockTime(new Date(Date.now() + remDur * 1000));
}

function arriveNavigation(){
  document.getElementById('navInstrText').textContent = `You've arrived at ${navDestPlace.name}`;
  document.getElementById('navInstrDist').textContent = '';
  const previewEl = document.getElementById('navNextInstr');
  if(previewEl) previewEl.classList.remove('visible');
  document.getElementById('navDistLeft').textContent = '0 m';
  document.getElementById('navEta').textContent = 'Arrived';
  speak(`Yaar, you've arrived at ${navDestPlace.name}`);
  if(navWatchId){ navigator.geolocation.clearWatch(navWatchId); navWatchId = null; }
  // Mid-trip: hand off to the next stop automatically instead of ending the
  // session here. navigateToDestination() re-fetches a fresh GPS fix and
  // re-routes from wherever the user actually is now, then replaces this
  // same nav overlay with the next leg (openNavOverlay is always called
  // with {replace:true}), so it reads as one continuous trip rather than a
  // series of separate navigations.
  if(navTripQueue && navTripQueue.length){
    const next = navTripQueue.shift();
    navTripIndex++;
    showToast(`On to stop ${navTripIndex} of ${navTripTotal}: ${next.name}`, 3200);
    navTripAdvanceTimer = setTimeout(()=>{ navTripAdvanceTimer = null; navigateToDestination(next); }, 2200);
  } else if(navTripQueue){
    showToast("Trip complete — you've reached your last stop!", 3000);
    navTripQueue = null; navTripIndex = 0; navTripTotal = 0;
  }
}

function cleanupNavigation(){
  if(navWatchId){ navigator.geolocation.clearWatch(navWatchId); navWatchId = null; }
  if(navMarkerAnimFrame){ cancelAnimationFrame(navMarkerAnimFrame); navMarkerAnimFrame = null; }
  stopNavBearingLoop();
  stopCompassTracking();
  if('speechSynthesis' in window) window.speechSynthesis.cancel();
  document.getElementById('navOverlay').classList.remove('active');
  document.getElementById('navStepsPanel').classList.remove('open');
  document.getElementById('navRecenterBtn').classList.remove('visible');
  stopTripPresence(); // before navMap is torn down below, so its own marker cleanup still has a map to remove from
  if(navMap){ try{ if(typeof navMap.remove === 'function') navMap.remove(); }catch(e){} navMap = null; }
  navSteps = []; navStepIndex = 0; navDestPlace = null; navUserMarker = null; navRouteLine = null; navLastPos = null;
  navTrail = []; navTrailLine = null;
  navRouteCoords = []; navOffRouteStrikes = 0; navRerouting = false; navLastFixTime = null;
  navFollowing = true; navMuted = false; navStepsOpen = false;
  navHeadingUp = true; navCurrentBearing = 0; navLastHeading = 0;
  navProgrammaticCam = false; clearTimeout(navProgrammaticCamTimer); navProgrammaticCamTimer = null;
  navTripQueue = null; navTripIndex = 0; navTripTotal = 0;
  clearTimeout(navTripAdvanceTimer); navTripAdvanceTimer = null;
  if(window.AndroidNativeAuth && window.AndroidNativeAuth.stopBackgroundNavigation){
    try{ window.AndroidNativeAuth.stopBackgroundNavigation(); }catch(err){}
  }
}
function endNavigation(){ closeUIModal('nav', cleanupNavigation); }

// Browser/OS built-in text-to-speech is all this has to work with — no
// paid AI voice API involved, so this can only pick from whatever voices
// are already installed on the device, never invent a new one. Most
// Android phones ship at least one Indian-English (en-IN) voice, which
// reads the Hinglish loanwords in stepText() (chalo/yaar/seedha/etc, all
// spelled in Latin script) far more naturally than a US/UK voice or a
// strict Hindi (hi-IN) voice would for the English portions — so en-IN is
// preferred first, hi-IN second, whatever's default last.
let cachedNavVoice = null;
function pickNavVoice(){
  if(!('speechSynthesis' in window)) return null;
  const voices = window.speechSynthesis.getVoices();
  if(!voices.length) return null;
  return voices.find(v => v.lang === 'en-IN')
      || voices.find(v => v.lang && v.lang.startsWith('en-IN'))
      || voices.find(v => v.lang === 'hi-IN')
      || voices.find(v => v.lang && v.lang.startsWith('hi'))
      || null;
}
if('speechSynthesis' in window){
  // Voice lists load asynchronously on first page load in many browsers —
  // getVoices() can return [] right away, with the real list only firing
  // this event a moment later. Re-picking here (in addition to the
  // just-in-time fallback inside speak() itself) means navigation started
  // very early doesn't get stuck on a bad pick made before the list
  // populated.
  window.speechSynthesis.onvoiceschanged = () => { cachedNavVoice = pickNavVoice(); };
  cachedNavVoice = pickNavVoice();
}
function speak(text){
  try{
    if(navMuted) return;
    if(!('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1; u.pitch = 1;
    if(!cachedNavVoice) cachedNavVoice = pickNavVoice();
    if(cachedNavVoice){ u.voice = cachedNavVoice; u.lang = cachedNavVoice.lang; }
    window.speechSynthesis.speak(u);
  }catch(e){}
}

/* ---------------- Trip / day-plan builder ----------------
   Pick 2-4 thikanas and get them stitched into one route with per-leg +
   total drive-time estimates. Sends every stop to /api/route in a single
   request (via the `stops` param — see functions/api/route.js) instead of
   one from/to call per hop, and reuses the exact response shape
   (routes[0].legs[]/.geometry/.distance/.duration) single-destination
   turn-by-turn nav already parses via navigateToDestination(). Turn-by-turn
   guidance for an individual leg hands off into that same existing flow
   (navigateTripLeg below) rather than reimplementing multi-stop voice
   nav — this builder's job stops at "here's the route and the drive times". */
const TRIP_MIN_STOPS = 2;
const TRIP_MAX_STOPS = 4;
let tripStops = [];      // ordered [{id,name,lat,lng,cat,gem,img}, ...]
let tripRoute = null;    // { legs:[{distance,duration}], distance, duration, geometry } once built
let tripMap = null, tripMarkers = [];
let tripPickerQuery = '';
let tripExtResults = [];  // Photon matches for the trip-picker's "more places" search
let tripExtState = null;  // null|loading|error|done — mirrors mapSearch's remoteState
let tripExtTimer = null;
let tripTargetGroupId = null; // set when the planner was opened from inside a group thread — drives the one-tap "Share" button
let tripTargetUserId = null;  // same, but for a DM thread
// The saved trip_plans row id these exact tripStops correspond to — set
// once they've been saved+shared (see saveAndShareTrip) or once you've
// opened someone else's shared trip (see openSharedTrip). Cleared the
// moment the stop list changes, since at that point it's a different trip
// than whatever got saved under that id. This is what lets startTripNavigation()
// know whether to broadcast live presence (see navTripPlanId in the nav
// section below) — no id, no presence, just a plain solo navigation.
let tripPlanId = null;
// Discover-page share picker: a small inline panel inside the trip overlay
// (not a separate overlay/history entry) for picking a DM or group to send
// the built trip to, when the planner wasn't already opened from inside a
// specific chat (see tripTargetGroupId/tripTargetUserId above, which skip
// this and send with one tap instead).
let tripShareOpen = false;
let tripShareQuery = '';
let tripShareSearchTimer = null;

function openTripPlanner(){
  pushUIModal('trip');
  document.getElementById('tripOverlay').classList.add('active');
  renderTripPlanner();
}
// Opened from a chat's 🗺️ icon — works whether that chat is a group or a
// 1:1 DM, so "Share" below always has somewhere to send to.
function openTripPlannerForChat(){
  tripTargetGroupId = msgIsGroup && msgGroup ? msgGroup.id : null;
  tripTargetUserId = !msgIsGroup && msgOtherUser ? msgOtherUser.id : null;
  openTripPlanner();
}
function hideTripPlannerUI(){
  document.getElementById('tripOverlay').classList.remove('active');
  if(tripMap){ try{ if(typeof tripMap.remove === 'function') tripMap.remove(); }catch(e){} }
  tripMap = null; tripMarkers = [];
  tripTargetGroupId = null; tripTargetUserId = null;
  tripShareOpen = false; tripShareQuery = ''; clearTimeout(tripShareSearchTimer);
}
// Saves the current trip planner state as a real trip_plans row, then
// shares it into the given DM or group — the trip bubble that lands there
// (see renderBubble's shared_trip_id branch) is what lets the recipient(s)
// open it (see openSharedTrip) and, once shared, is also what lets
// startTripNavigation() broadcast live presence (see tripPlanId above and
// navTripPlanId in the nav section below) — every future call in this
// planner session reuses the same target.
// { group_id } or { to_user_id }, mirroring the two shapes POST
// /api/messages already accepts.
async function saveAndShareTrip(target){
  if(tripStops.length < TRIP_MIN_STOPS) return;
  try{
    const { plan } = await apiPost('/api/trip-plans', {
      stops: tripStops.map(s=>({ place_id:s.id, name:s.name, lat:s.lat, lng:s.lng, category:s.cat })),
    });
    await apiPost('/api/messages', {
      group_id: target.group_id || undefined,
      to_user_id: target.group_id ? undefined : target.to_user_id || undefined,
      shared_trip_id: plan.id,
    });
    tripPlanId = plan.id;
    showToast('Trip sent!', 2000);
    closeTripPlanner();
  }catch(e){
    showToast((e.data && e.data.message) || "Couldn't send that trip — try again.", 3000);
  }
}
// One-tap path: the planner was opened from inside a specific chat (see
// openTripPlannerForChat), so there's only ever one place to send to.
function sendTripToGroup(){
  saveAndShareTrip({ group_id: tripTargetGroupId, to_user_id: tripTargetUserId });
}
// Opened from Discover (no chat context to send to) — shows a small
// inline picker of DMs/groups instead, same list the chat inbox uses.
function openTripSharePicker(){
  if(tripStops.length < TRIP_MIN_STOPS) return;
  tripShareOpen = true;
  tripShareQuery = '';
  renderTripPlanner();
  loadTripShareRecents();
}
function closeTripSharePicker(){
  tripShareOpen = false;
  clearTimeout(tripShareSearchTimer);
  renderTripPlanner();
}
function onTripShareSearchInput(){
  const el = document.getElementById('tripShareSearchInput');
  tripShareQuery = el ? el.value.trim() : '';
  clearTimeout(tripShareSearchTimer);
  if(!tripShareQuery){ loadTripShareRecents(); return; }
  tripShareSearchTimer = setTimeout(async ()=>{
    try{
      const { users } = await apiGet(`/api/users?search=${encodeURIComponent(tripShareQuery)}`);
      renderTripShareList(users.map(u=>({ kind:'dm', id:u.id, name:u.display_name || u.handle, sub:`@${u.handle}`, avatar_url:u.avatar_url })));
    }catch(e){}
  }, 300);
}
async function loadTripShareRecents(){
  try{
    const { conversations } = await apiGet('/api/messages');
    renderTripShareList(conversations.map(c=>c.is_group
      ? { kind:'group', id:c.conversation_id, name:c.group_name || 'Group', sub:'Group', avatar_url:c.group_photo_url }
      : { kind:'dm', id:c.other.id, name:c.other.display_name || c.other.handle, sub:`@${c.other.handle}`, avatar_url:c.other.avatar_url }
    ));
  }catch(e){
    const el = document.getElementById('tripShareList');
    if(el) el.innerHTML = `<div class="msg-empty">Message someone first to share with them, or search above.</div>`;
  }
}
function renderTripShareList(items){
  const el = document.getElementById('tripShareList');
  if(!el) return;
  if(!items.length){ el.innerHTML = `<div class="msg-empty">No one yet — search above to find someone.</div>`; return; }
  el.innerHTML = items.map(it=>`
    <div class="msg-inbox-row" onclick="pickTripShareTarget('${it.kind}',${it.id})">
      <div class="m-avatar" ${it.avatar_url?`style="background-image:url('${it.avatar_url}');background-size:cover;"`:''}>${!it.avatar_url?avatarInitial({handle:it.name}):''}</div>
      <div class="msg-inbox-info"><div class="msg-inbox-name">${escapeHtml(it.name)}</div><div class="msg-inbox-preview">${escapeHtml(it.sub)}</div></div>
    </div>`).join('');
}
function pickTripShareTarget(kind, id){
  tripShareOpen = false;
  saveAndShareTrip(kind==='group' ? { group_id:id } : { to_user_id:id });
}
function closeTripPlanner(){ closeUIModal('trip', hideTripPlannerUI); }

// Quick-add from a place's own detail card, so browsing straight into a
// trip doesn't require re-finding the same spot in the picker afterward.
function addToTripPlan(placeId){
  const p = PLACES.find(x=>x.id===placeId);
  if(!p) return;
  if(tripStops.some(s=>s.id===p.id)){ showToast(`${p.name} is already in your trip.`, 2500); return; }
  if(tripStops.length >= TRIP_MAX_STOPS){ showToast(`A trip can have up to ${TRIP_MAX_STOPS} stops — open "Plan a trip" to swap one out.`, 3000); return; }
  tripStops.push({ id:p.id, name:p.name, lat:p.lat, lng:p.lng, cat:p.cat, gem:p.gem, img:p.img });
  tripRoute = null; // stale once the stop list changes
  tripPlanId = null; // no longer matches whatever was last saved under an id
  showToast(`Added ${p.name} to your trip · ${tripStops.length}/${TRIP_MAX_STOPS}`, 2200);
  if(isModalOpen('trip')) renderTripPlanner();
}
function removeTripStop(placeId){
  tripStops = tripStops.filter(s=>s.id!==placeId);
  tripRoute = null;
  tripPlanId = null;
  renderTripPlanner();
}
function clearTripPlan(){
  tripStops = []; tripRoute = null; tripPlanId = null;
  renderTripPlanner();
}
function onTripPickerInput(){
  const el = document.getElementById('tripPickerInput');
  tripPickerQuery = el ? el.value.trim() : '';
  clearTimeout(tripExtTimer);
  if(tripPickerQuery.length < 3){
    // Too short to bother Photon yet, but your own thikanas still filter
    // instantly since tripPickerMatches() is just a local array scan.
    tripExtResults = []; tripExtState = null;
    renderTripPickerResults();
    return;
  }
  tripExtState = 'loading';
  renderTripPickerResults();
  tripExtTimer = setTimeout(()=>runTripExtSearch(tripPickerQuery), 400);
}
// Same Photon lookup runMapSearch() uses for the main map search box, reused
// here so the trip planner isn't limited to your own thikanas — any real
// place (a station, a market, a relative's house) can become a stop.
async function runTripExtSearch(q){
  try{
    const loc = userLoc ? [userLoc.lat, userLoc.lng] : [23.38, 85.40];
    const res = await fetch(`https://photon.komoot.io/api/?q=${encodeURIComponent(q)}&lat=${loc[0]}&lon=${loc[1]}&limit=6`);
    if(!res.ok) throw new Error(`photon_http_${res.status}`);
    const data = await res.json();
    const list = (data && data.features) || [];
    tripExtResults = list.map(coercePhotonResult).filter(Boolean).slice(0, 6);
    tripExtState = 'done';
  }catch(e){
    console.error('[trip ext search] failed:', e);
    tripExtResults = [];
    tripExtState = 'error';
  }
  renderTripPickerResults();
}
function addExternalStopFromSearch(i){
  const r = tripExtResults[i];
  if(!r) return;
  const label = (r.display_name||'Selected place').split(',')[0].trim();
  addExternalTripStop(r.lat, r.lon, label);
  tripPickerQuery = ''; tripExtResults = []; tripExtState = null;
  const input = document.getElementById('tripPickerInput');
  if(input) input.value = '';
  renderTripPickerResults();
}
function tripPickerMatches(){
  const ql = tripPickerQuery.toLowerCase();
  const picked = new Set(tripStops.map(s=>s.id));
  return PLACES.filter(p => !picked.has(p.id) && (!ql || p.name.toLowerCase().includes(ql))).slice(0, 8);
}
function addTripStopFromPicker(placeId){
  addToTripPlan(placeId);
  tripPickerQuery = '';
  renderTripPlanner();
}
function formatTripDuration(seconds){
  const mins = Math.round(seconds / 60);
  return mins < 60 ? `${mins} min drive` : `${Math.floor(mins/60)}h ${mins%60}m drive`;
}

async function computeTripRoute(){
  if(tripStops.length < TRIP_MIN_STOPS) return;
  const btn = document.getElementById('tripBuildBtn');
  if(btn){ btn.disabled = true; btn.textContent = 'Building route…'; }
  // Lead the route off from wherever the user actually is right now (when
  // we have a fix), so "Build route" traces the real path — your location
  // → stop 1 → stop 2 → … — instead of starting cold from whichever stop
  // happened to get added first. TRIP_MAX_STOPS (4) + this one extra point
  // still stays under /api/route's 6-point cap.
  const fromUserLoc = !!userLoc;
  try{
    const points = fromUserLoc
      ? [`${userLoc.lat},${userLoc.lng}`, ...tripStops.map(s=>`${s.lat},${s.lng}`)]
      : tripStops.map(s=>`${s.lat},${s.lng}`);
    const res = await fetch(`/api/route?stops=${encodeURIComponent(points.join('|'))}`);
    const data = await res.json();
    if(!data.routes || !data.routes.length) throw new Error('no_route');
    const route = data.routes[0];
    tripRoute = { legs: route.legs, distance: route.distance, duration: route.duration, geometry: route.geometry, fromUserLoc };
  }catch(e){
    console.error('[trip route] failed:', e); // real cause lands here — see runMapSearch for why we don't hide this anymore
    tripRoute = null;
    showToast("Couldn't build that route — try again in a moment.", 4000);
  }
  renderTripPlanner();
}

// Runs the whole trip as one continuous turn-by-turn session: navigates
// from your current location to stop 1 using the existing single-destination
// nav engine, then — once arriveNavigation() sees more stops queued up — is
// handed the next stop automatically, and so on, so nobody has to keep
// re-opening navigation by hand between stops.
// tripPlanId (module state above) rides along as navOpts.tripPlanId on this
// FIRST leg only — arriveNavigation()'s own hand-off calls
// navigateToDestination() with no navOpts at all for every leg after this
// one, and navigateToDestination() only ever touches navTripPlanId when a
// tripPlanId key is actually present, so it just carries forward untouched
// for the rest of the trip (see navigateToDestination below).
function startTripNavigation(){
  if(tripStops.length < 1) return;
  if(!navigator.geolocation){ showToast("Location isn't available in this browser."); return; }
  navTripQueue = tripStops.slice(1);
  navTripTotal = tripStops.length;
  navTripIndex = 1;
  const planId = tripPlanId;
  closeTripPlanner();
  setTimeout(()=>navigateToDestination(tripStops[0], { tripPlanId: planId, replace:true }), 250);
}

// Hands a single leg off to the existing single-destination turn-by-turn
// flow — same one "Navigate" on a place card uses. Carries tripPlanId
// along too (a lone leg still counts as "navigating this trip" for
// presence purposes, same as the full multi-stop run above).
function navigateTripLeg(index){
  const dest = tripStops[index];
  if(!dest) return;
  const planId = tripPlanId;
  closeTripPlanner();
  setTimeout(()=>navigateToDestination({ lat:dest.lat, lng:dest.lng, name:dest.name, cat:dest.cat, gem:dest.gem }, { replace:false, tripPlanId: planId }), 300);
}

// Reached by tapping a shared-trip bubble in a DM or group thread (see
// renderBubble's shared_trip_id branch / pendingTripShares above) — loads
// that trip straight into the planner, exactly as if you'd built it
// yourself, so you can look at the stops, recompute the route, and Start
// or navigate a single leg the same way the creator could. tripPlanId is
// set to the ORIGINAL shared plan's id (not a copy) so that starting
// navigation from here broadcasts/sees live presence under the same id
// everyone else in that chat is using — editing the stops afterward clears
// it again, same as any other edit (see addToTripPlan/removeTripStop/
// clearTripPlan above).
function openSharedTrip(cardId){
  const data = pendingTripShares.get(cardId);
  if(!data || !data.stops || !data.stops.length) return;
  tripStops = data.stops.map(s=>({ id:s.place_id, name:s.name, lat:s.lat, lng:s.lng, cat:s.category, gem:false, img:null }));
  tripRoute = null;
  tripPlanId = data.tripId;
  tripTargetGroupId = null; tripTargetUserId = null; // viewing, not tied to this chat as a share target
  closeMessages();
  setTimeout(()=>openTripPlanner(), 300);
}

function renderTripPlanner(){
  const body = document.getElementById('tripBody');
  if(!body) return;
  if(tripShareOpen){ renderTripSharePicker(body); return; }
  const canRoute = tripStops.length >= TRIP_MIN_STOPS;
  body.innerHTML = `
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;">Plan a trip</h2>
      <div style="font-size:12.5px;color:var(--ink-soft);margin-bottom:14px;">Pick ${TRIP_MIN_STOPS}–${TRIP_MAX_STOPS} thikanas — we'll stitch them into one route with drive times, in the order you add them.</div>

      ${tripRoute && tripRoute.fromUserLoc ? `<div style="display:flex;align-items:center;gap:10px;background:var(--paper-dim);border-radius:12px;padding:8px 10px;margin-bottom:8px;">
        <div style="width:22px;height:22px;border-radius:50%;background:var(--waterfall,#2A9D8F);color:#fff;font-size:12px;display:flex;align-items:center;justify-content:center;flex-shrink:0;">📍</div>
        <div style="flex:1;min-width:0;"><b style="font-size:13px;">Your location</b><span style="font-size:11px;color:var(--ink-soft);display:block;">Starting point</span></div>
      </div>` : ''}
      ${tripStops.length ? `<div style="display:flex;flex-direction:column;gap:8px;margin-bottom:14px;">
        ${tripStops.map((s,i)=>{
          // When the route starts from the user's live location, that leg is
          // prepended as an extra point, so tripRoute.legs[i] (not legs[i-1])
          // is the drive that lands on stop i — see computeTripRoute().
          const leg = tripRoute ? (tripRoute.fromUserLoc ? tripRoute.legs[i] : (i > 0 ? tripRoute.legs[i-1] : null)) : null;
          const fromLabel = i===0 ? (tripRoute && tripRoute.fromUserLoc ? 'from your location' : null) : `from stop ${i}`;
          const subLabel = leg && fromLabel ? `${fmtDist(leg.distance/1000)} · ${Math.round(leg.duration/60)} min ${fromLabel}` : (i===0 && !(tripRoute && tripRoute.fromUserLoc) ? 'Starting point' : '');
          return `
          <div style="display:flex;align-items:center;gap:10px;background:var(--paper-dim);border-radius:12px;padding:8px 10px;">
            <div style="width:22px;height:22px;border-radius:50%;background:var(--forest);color:#fff;font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center;flex-shrink:0;">${i+1}</div>
            <div style="flex:1;min-width:0;">
              <b style="font-size:13px;display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(s.name)}</b>
              ${subLabel ? `<span style="font-size:11px;color:var(--ink-soft);">${subLabel}</span>` : ''}
            </div>
            <div style="cursor:pointer;color:var(--forest);padding:4px;" onclick="navigateTripLeg(${i})" title="Turn-by-turn to this stop">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="3 11 22 2 13 21 11 13 3 11"/></svg>
            </div>
            <div style="cursor:pointer;color:var(--ink-soft);padding:4px;" onclick="removeTripStop(${s.id})" title="Remove">
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
            </div>
          </div>`;
        }).join('')}
      </div>` : `<div style="font-size:12.5px;color:var(--ink-soft);padding:10px 2px;">No stops yet — search below, or tap "Add to trip" on any thikana.</div>`}

      ${tripStops.length < TRIP_MAX_STOPS ? `
        <div class="field-label">Add a stop</div>
        <input id="tripPickerInput" type="text" placeholder="Search thikanas or any place…" value="${escapeHtml(tripPickerQuery)}" oninput="onTripPickerInput()" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid var(--line);font-size:13px;margin:6px 0 8px;box-sizing:border-box;">
        <div id="tripPickerResults"></div>
      ` : ''}

      ${tripRoute ? `
        <div id="tripMapWrap" style="height:180px;border-radius:14px;overflow:hidden;margin:14px 0 10px;">
          <div id="tripMap" style="width:100%;height:100%;"></div>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center;background:var(--paper-dim);border-radius:12px;padding:10px 14px;margin-bottom:6px;">
          <span style="font-size:13px;font-weight:600;color:var(--forest);">Total: ${fmtDist(tripRoute.distance/1000)}</span>
          <span style="font-family:'JetBrains Mono',monospace;font-size:12px;color:var(--ink-soft);">${formatTripDuration(tripRoute.duration)}</span>
        </div>
      ` : ''}
    </div>
    <div style="padding:12px 18px 18px;border-top:1px solid var(--paper-dim);display:flex;flex-direction:column;gap:8px;">
      ${(tripRoute || canRoute) ? `<div style="display:flex;gap:8px;">
        ${tripRoute ? `<button class="btn-primary" style="flex:1;" onclick="startTripNavigation()">▶ Start</button>` : ''}
        ${canRoute ? `<button class="btn-primary" style="flex:1;" onclick="${(tripTargetGroupId || tripTargetUserId) ? 'sendTripToGroup()' : 'openTripSharePicker()'}">Share in app</button>` : ''}
      </div>` : ''}
      ${canRoute ? `<div class="share-external-row" style="margin:0;">
        <div class="share-ext-btn" onclick="shareTripToWhatsApp()">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor"><path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38a9.9 9.9 0 004.74 1.2h.01c5.46 0 9.9-4.45 9.9-9.91C21.96 6.45 17.5 2 12.04 2zm5.8 14.16c-.24.68-1.4 1.32-1.93 1.4-.5.08-1.12.11-1.8-.11-.42-.13-.95-.31-1.64-.6-2.88-1.24-4.76-4.14-4.9-4.33-.14-.19-1.17-1.56-1.17-2.98 0-1.42.74-2.11 1-2.4.26-.29.57-.36.76-.36.19 0 .38 0 .55.01.18.01.41-.07.64.49.24.58.81 2 .88 2.14.07.14.12.31.02.5-.09.19-.14.3-.28.46-.14.16-.29.36-.42.48-.14.13-.28.28-.12.55.16.28.72 1.19 1.55 1.93 1.06.95 1.96 1.24 2.24 1.38.28.14.44.12.6-.07.16-.19.68-.79.87-1.06.18-.28.36-.23.61-.14.24.09 1.55.73 1.82.86.26.14.44.2.5.31.06.12.06.68-.18 1.36z"/></svg>
          <span>WhatsApp</span>
        </div>
        <div class="share-ext-btn" onclick="shareTripExternally()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>
          <span>Share via…</span>
        </div>
        <div class="share-ext-btn ghost" onclick="copyTripLink()">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
          <span>Copy link</span>
        </div>
      </div>` : ''}
      <div style="display:flex;gap:8px;">
        <button class="btn-primary" id="tripBuildBtn" style="flex:1;" ${canRoute ? '' : 'disabled'} onclick="computeTripRoute()">
          ${tripRoute ? 'Recalculate route' : 'Build route'}
        </button>
        ${tripStops.length ? `<button class="followpill" onclick="clearTripPlan()">Clear</button>` : ''}
      </div>
    </div>`;
  renderTripPickerResults();
  if(tripRoute) setTimeout(initTripMap, 30);
}
// Small inline panel swapped into the same tripBody/tripOverlay instead of
// stacking a whole separate overlay — picks a DM or group to send the
// built trip to when the planner has no chat context of its own (see
// openTripSharePicker above; the chat-opened case skips this and sends
// with one tap via sendTripToGroup instead).
function renderTripSharePicker(body){
  body.innerHTML = `
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <div style="display:flex;align-items:center;gap:10px;margin-bottom:14px;">
        <div style="cursor:pointer;color:var(--ink-soft);padding:4px;margin:-4px;" onclick="closeTripSharePicker()" title="Back">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M15 18l-6-6 6-6"/></svg>
        </div>
        <h2 style="font-size:17px;margin:0;">Share this trip</h2>
      </div>
      <input type="text" id="tripShareSearchInput" placeholder="Search people…" value="${escapeHtml(tripShareQuery)}" oninput="onTripShareSearchInput()" style="width:100%;border:1px solid var(--line);border-radius:100px;padding:10px 15px;font-family:'Work Sans',sans-serif;font-size:13px;background:var(--paper);margin:8px 0 10px;box-sizing:border-box;">
      <div class="share-sheet-list" id="tripShareList"><div class="msg-empty">Loading…</div></div>
    </div>`;
}
function renderTripPickerResults(){
  const el = document.getElementById('tripPickerResults');
  if(!el) return;
  const matches = tripPickerMatches();
  let html = matches.map(p=>{
    const c = CATS[p.cat];
    return `<div class="map-search-item" onclick="addTripStopFromPicker(${p.id})">
      <span class="msi-pin" style="color:${c.color}">📍</span>
      <div class="msi-text"><b>${escapeHtml(p.name)}</b><span>${c.label}${p.gem?' · Hidden gem':''}</span></div>
    </div>`;
  }).join('');

  if(tripPickerQuery.length >= 3){
    if(tripExtState === 'loading'){
      html += `<div class="map-search-loading">Searching more places…</div>`;
    } else if(tripExtState === 'error'){
      html += `<div class="map-search-empty">Place search failed — check your connection.</div>`;
    } else if(tripExtState === 'done'){
      if(tripExtResults.length){
        html += `<div class="map-search-section">More places</div>`;
        html += tripExtResults.map((r,i)=>{
          const label = (r.display_name||'Selected place').split(',')[0];
          return `<div class="map-search-item" onclick="addExternalStopFromSearch(${i})">
            <span class="msi-pin">🌐</span>
            <div class="msi-text"><b>${escapeHtml(label)}</b><span>${escapeHtml(r.display_name||'')}</span></div>
          </div>`;
        }).join('');
      }
    }
  }

  el.innerHTML = html || `<div style="font-size:12px;color:var(--ink-soft);padding:8px 2px;">${tripPickerQuery ? 'No matches.' : 'Type a name to search.'}</div>`;
}
function initTripMap(){
  const el = document.getElementById('tripMap');
  if(!el || !tripRoute || !tripStops.length) return;
  tripMap = new mappls.Map('tripMap', { center:{lat:tripStops[0].lat,lng:tripStops[0].lng}, zoom:11, zoomControl:false, clickableIcons:false, clickableIcons_callback: () => {} });
  // A freshly-constructed map hasn't finished its internal style/tile load
  // yet (this is a WebGL vector map, same family as Mapbox GL). Calling
  // fitBounds synchronously right after `new mappls.Map(...)` runs it
  // against that not-yet-ready internal state, which is what was sending
  // the camera off to a blank patch of ocean until a manual zoom forced a
  // redraw. Do the marker/polyline/fitBounds setup once the map reports
  // itself actually loaded instead of guessing with a timeout.
  const setupTripMapContents = ()=>{
    const coords = tripRoute.geometry.coordinates.map(c=>({lat:c[1], lng:c[0]}));
    new mappls.Polyline({ map:tripMap, path:coords, strokeColor:'#FF3B5C', strokeOpacity:0.9, strokeWeight:5 });
    tripMarkers = tripStops.map(s=>{
      const icon = s.cat ? pinIcon(s.cat, s.gem, true, false, s.img) : searchPinIcon();
      return new mappls.Marker({ map:tripMap, position:{lat:s.lat,lng:s.lng}, html:icon.html, width:icon.width, height:icon.height, fitbounds:false });
    });
    if(tripRoute.fromUserLoc && userLoc){
      const uicon = userLocIcon();
      tripMarkers.push(new mappls.Marker({ map:tripMap, position:{lat:userLoc.lat,lng:userLoc.lng}, html:uicon.html, width:uicon.width, height:uicon.height, fitbounds:false }));
    }
    // Fit to the stops AND the actual route geometry — a road route can bow
    // out well past a straight line between stops, so bounding just the
    // stops can still crop part of the polyline.
    const routePts = (tripRoute.geometry && tripRoute.geometry.coordinates || []).map(c=>[c[1], c[0]]);
    safeFitBounds(tripMap, tripStops.map(s=>[s.lat,s.lng]).concat(routePts), 30);
  };
  if(typeof tripMap.on === 'function'){
    let done = false;
    tripMap.on('load', ()=>{ if(done) return; done = true; setupTripMapContents(); });
    // Safety net: some SDK builds/cached tiles can fire 'load' before this
    // listener attaches, or not fire it at all. Fall back after a beat so
    // the map never just sits there un-set-up.
    setTimeout(()=>{ if(done) return; done = true; setupTripMapContents(); }, 400);
  } else {
    setupTripMapContents();
  }
}

/* ---------------- Feed ---------------- */
function showFeedSkeleton(){
  const el = document.getElementById('feedList');
  if(!el) return;
  el.innerHTML = Array.from({length:3}).map(()=>`
    <div class="skel-post">
      <div class="skel-post-head">
        <div class="skel skel-post-avatar"></div>
        <div class="skel-lines">
          <div class="skel skel-line w35"></div>
        </div>
      </div>
      <div class="skel skel-post-photo"></div>
    </div>`).join('');
}
function renderFeed(){
  const el = document.getElementById('feedList');
  el.innerHTML = '';
  if(feedMode === 'following' && POSTS.length === 0){
    el.innerHTML = `<div class="p-empty" style="margin:20px 14px;">Follow people to see their posts here — try liking a few thikanas in Explore first.</div>`;
    return;
  }
  if(feedMode === 'explore' && POSTS.length === 0){
    el.innerHTML = `<div class="p-empty" style="margin:20px 14px;">No posts yet — be the first to share a thikana!</div>`;
    return;
  }
  POSTS.forEach((post,i)=>{
    const div = buildPostCardEl(post, i);
    el.appendChild(div);
  });
  initFeedVideoObserver(el);
  observeLazyBg(el);
  initFeedSeenObserver(el);
  POSTS.forEach((post,i)=>populateFollowPill(post,i));
}
// Builds one Feed post <div> — shared by renderFeed() (full cold render)
// and appendFeedPosts() (infinite-scroll "load more"), so an appended older
// page gets exactly the same markup/behavior as the initial render instead
// of a slowly-drifting second copy of this template.
function buildPostCardEl(post, i){
  const div = document.createElement('div');
  div.className = 'post';
  div.dataset.postId = post.id; // lets pollFeedOnce() pull a single deleted card out without touching the rest of the feed
  const isMe = currentUser && post.user_id === currentUser.id;
  div.innerHTML = `
      <div class="post-head">
        <div class="avatar-ring" onclick="openUserProfile(${post.user_id})" style="cursor:pointer;"><div class="avatar" ${post.avatar_url?`style="background-image:url('${post.avatar_url}');background-size:cover;"`:''}></div></div>
        <div class="post-user-block">
          <div class="post-headrow">
            <div class="post-user" onclick="openUserProfile(${post.user_id})">${post.user}${post.official ? VERIFIED_BADGE : ''}</div>
            ${(!isMe && post.user_id>0) ? `<span class="followpill" id="followpill-post-${i}" onclick="event.stopPropagation();toggleFollow(${post.user_id}, this)">Follow</span>` : ''}
            ${isMe ? `<div class="post-menu-btn" onclick="event.stopPropagation();openPostMenu(event, ${post.id})"><svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg></div>` : ''}
          </div>
          <div class="post-loc-chip" onclick="jumpToPlace(${post.place_id})">
            <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7z"/></svg>
            ${escapeHtml(post.place)}${userLoc && PLACES.find(p=>p.id===post.place_id) ? `<span class="pdist">${distText(PLACES.find(p=>p.id===post.place_id))}</span>` : ''}
          </div>
        </div>
      </div>
      ${renderPostImgHtml(post, i)}
      <div class="post-actions">
        <div class="icon-btn like-btn ${post.liked?'liked':''}" id="like-${i}" onclick="likePost(${i})">
          <svg viewBox="0 0 24 24" fill="${post.liked?'currentColor':'none'}" stroke="currentColor" stroke-width="1.8"><path d="M20.8 4.6a5.5 5.5 0 00-7.8 0L12 5.6l-1-1a5.5 5.5 0 00-7.8 7.8l1 1L12 21l7.8-7.6 1-1a5.5 5.5 0 000-7.8z"/></svg>
        </div>
        <div class="icon-btn" onclick="openComments(${post.id})">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 11.5a8.4 8.4 0 01-8.9 8.4 8.6 8.6 0 01-3.8-.9L3 21l1.9-5.4A8.4 8.4 0 1121 11.5z"/></svg>
        </div>
        <div class="icon-btn" onclick="openShareSheet({post_id:${post.id}, place_id:${post.place_id}})">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>
        </div>
        <div class="grow"></div>
        <div class="icon-btn save-btn ${post.saved?'saved':''}" id="save-${i}" onclick="savePost(${i})">
          <svg viewBox="0 0 24 24" fill="${post.saved?'currentColor':'none'}" stroke="currentColor" stroke-width="1.8"><path d="M19 21l-7-5-7 5V5a2 2 0 012-2h10a2 2 0 012 2z"/></svg>
        </div>
      </div>
      <div class="post-likes" id="likecount-${i}">${post.likes.toLocaleString('en-IN')} likes</div>
      <div class="post-cap" id="postcap-${i}"><b>${escapeHtml(post.user)}${post.official ? VERIFIED_BADGE : ''}</b> ${escapeHtml(post.cap)}${post.edited ? '<span class="post-edited-tag">(edited)</span>' : ''}
        ${post.comments_count>0 ? `<span class="comment-link" onclick="openComments(${post.id})">View all ${post.comments_count} comments</span>` : ''}
      </div>`;
  if(post.gallery) initPostCarousel(div.querySelector(`#postimg-${i}`), div.querySelector(`#posttrack-${i}`), post);
  return div;
}
function populateFollowPill(post, i){
  if(!currentUser || post.user_id === currentUser.id || post.user_id<0) return;
  apiGet(`/api/follows?user_id=${post.user_id}`).then(({is_following})=>{
    const pill = document.getElementById(`followpill-post-${i}`);
    if(pill){ pill.textContent = is_following ? 'Following' : 'Follow'; pill.classList.toggle('following', is_following); }
  }).catch(()=>{});
}
// Appends one older "load more" page (see loadMoreFeed()) without touching
// what's already rendered — cheaper than a full renderFeed() and doesn't
// reset scroll position or in-flight video playback.
function appendFeedPosts(newPosts){
  const el = document.getElementById('feedList');
  if(!el || !newPosts.length) return;
  const startIndex = POSTS.length - newPosts.length; // POSTS already has newPosts concatenated on by loadMoreFeed()
  newPosts.forEach((post,j)=>{
    el.appendChild(buildPostCardEl(post, startIndex + j));
  });
  initFeedVideoObserver(el); // re-scans all .post-video-wrap — cheap, and keeps one-video-at-a-time correct
  observeLazyBg(el);
  initFeedSeenObserver(el);
  newPosts.forEach((post,j)=>populateFollowPill(post, startIndex + j));
}
// Instagram-style pull-to-refresh on the Feed. Only the #ptr-indicator's
// height changes (see styles.css) — since it's a flex sibling directly
// above #feedList, growing it pushes the feed down, no transform needed on
// the scroll container itself. Only activates when the feed is already
// scrolled to the very top, so it never fights normal scrolling or the
// per-post carousel drag (initPostCarousel), which is bound to each photo
// box individually and doesn't stop propagation.
function initPullToRefresh(){
  const list = document.getElementById('feedList');
  const ind = document.getElementById('feedPTR');
  if(!list || !ind) return;
  const THRESHOLD = 60, MAX = 88;
  let startY = null, pulling = false, refreshing = false;
  list.addEventListener('touchstart', (e)=>{
    if(refreshing || e.touches.length !== 1 || list.scrollTop > 0){ startY = null; pulling = false; return; }
    startY = e.touches[0].clientY; pulling = true;
    ind.classList.remove('snap');
  }, { passive:true });
  list.addEventListener('touchmove', (e)=>{
    if(!pulling || startY === null || refreshing) return;
    const dy = e.touches[0].clientY - startY;
    if(dy <= 0 || list.scrollTop > 0){ pulling = false; ind.classList.add('snap'); ind.style.height = '0px'; ind.classList.remove('ready'); return; }
    const h = Math.min(MAX, dy * 0.5); // resistance, like a real overscroll
    ind.style.height = h + 'px';
    ind.classList.toggle('ready', h >= THRESHOLD * 0.6);
  }, { passive:true });
  list.addEventListener('touchend', ()=>{
    if(!pulling){ startY = null; return; }
    pulling = false;
    ind.classList.add('snap');
    const h = parseFloat(ind.style.height) || 0;
    if(h >= THRESHOLD * 0.6 && !refreshing){
      refreshing = true;
      ind.style.height = '40px';
      ind.classList.add('refreshing');
      Promise.resolve(loadFeed()).catch(()=>{}).finally(()=>{
        setTimeout(()=>{
          ind.classList.remove('refreshing', 'ready');
          ind.style.height = '0px';
          refreshing = false;
        }, 300); // let the spinner read as "done" for a beat rather than snapping shut instantly
      });
    } else {
      ind.style.height = '0px';
      ind.classList.remove('ready');
    }
    startY = null;
  });
}
// Renders a post's image area: a plain photo/reel-thumb for a single-image
// post, a swipeable carousel (dots + tap-to-advance) for a gallery post, or
// an inline muted video for a video post (post_kind 'reel') — the Feed is
// unified, so video plays right in the card instead of a separate
// full-screen swipe-through view. Autoplay/pause as it scrolls in and out of
// view, and per-video mute state, is wired up in initFeedVideoObserver()
// after the whole feed is in the DOM.
function renderPostImgHtml(post, i){
  if(post.media_type === 'video'){
    return `<div class="post-img post-video-wrap" id="postimg-${i}">
      <video id="postvideo-${i}" src="${post.img}" loop playsinline muted preload="metadata"></video>
      <div class="post-video-mute" onclick="togglePostVideoMute(event, ${i})">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" id="postvideomuteicon-${i}"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M23 9l-6 6M17 9l6 6"/></svg>
      </div>
    </div>`;
  }
  if(!post.gallery){
    return `<div class="post-img" ${lazyBgAttrs(post.img)} ondblclick="likePost(${i}, true)">
      <svg class="big-heart" id="heart-${i}" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21s-6.7-4.35-9.3-8.1C.86 10.1 1.4 6.6 4.2 5c2.1-1.2 4.6-.7 6.1 1.1L12 8l1.7-1.9c1.5-1.8 4-2.3 6.1-1.1 2.8 1.6 3.34 5.1 1.5 7.9C18.7 16.65 12 21 12 21z"/></svg>
    </div>`;
  }
  const slides = post.gallery.map((src,d)=>`<div class="post-img-slide" ${lazyBgAttrs(src, d===0)}></div>`).join('');
  const dots = post.gallery.map((_,d)=>`<span class="${d===0?'on':''}"></span>`).join('');
  return `<div class="post-img" id="postimg-${i}" data-idx="0" ondblclick="likePost(${i}, true)">
      <div class="post-img-dots">${dots}</div>
      <div class="post-img-count">1/${post.gallery.length}</div>
      <div class="post-img-track" id="posttrack-${i}">${slides}</div>
      <svg class="big-heart" id="heart-${i}" viewBox="0 0 24 24" fill="currentColor"><path d="M12 21s-6.7-4.35-9.3-8.1C.86 10.1 1.4 6.6 4.2 5c2.1-1.2 4.6-.7 6.1 1.1L12 8l1.7-1.9c1.5-1.8 4-2.3 6.1-1.1 2.8 1.6 3.34 5.1 1.5 7.9C18.7 16.65 12 21 12 21z"/></svg>
    </div>`;
}
// Only the video that's mostly on-screen actually plays — keeps it to one
// video decoding at a time, same as any short-form video feed — and pauses
// everything when the Feed isn't the active view at all.
let feedVideoObserver = null;
function initFeedVideoObserver(el){
  if(feedVideoObserver) feedVideoObserver.disconnect();
  const wraps = el.querySelectorAll('.post-video-wrap');
  if(!wraps.length) return;
  feedVideoObserver = new IntersectionObserver((entries)=>{
    entries.forEach(entry=>{
      const vid = entry.target.querySelector('video');
      if(!vid) return;
      if(entry.isIntersecting && entry.intersectionRatio > 0.6){ vid.play().catch(()=>{}); }
      else{ vid.pause(); }
    });
  }, { root: el, threshold: [0, 0.6, 1] });
  wraps.forEach(w => feedVideoObserver.observe(w));
}
// ---------------- Feed seen-tracking ----------------
// Marks a post "seen" once it's actually spent a beat on screen (not just
// been fetched/rendered), so a quick scroll-past doesn't count. Feeds
// GET /api/post-views (see that file), which posts.js then uses to rank
// unseen posts ahead of repeats. Reconnects on every render/append, same
// pattern as initFeedVideoObserver above.
const FEED_SEEN_DWELL_MS = 800;
const FEED_SEEN_FLUSH_MS = 2500;
let feedSeenObserver = null;
let feedSeenDwellTimers = new Map(); // post id -> setTimeout handle, while it's on screen but hasn't hit the dwell time yet
let feedSeenPending = new Set();     // post ids confirmed seen this session, not yet flushed to the server
let feedSeenFlushTimer = null;
let feedSeenSentIds = new Set();     // post ids already flushed this session — skip re-queuing them
function initFeedSeenObserver(el){
  if(!currentUser) return; // no account to track "seen" against — see posts.js header comment
  if(feedSeenObserver) feedSeenObserver.disconnect();
  feedSeenDwellTimers.forEach(t=>clearTimeout(t));
  feedSeenDwellTimers = new Map();
  const cards = el.querySelectorAll('.post[data-post-id]');
  if(!cards.length) return;
  feedSeenObserver = new IntersectionObserver((entries)=>{
    entries.forEach(entry=>{
      const id = parseInt(entry.target.dataset.postId, 10);
      if(!Number.isInteger(id) || feedSeenSentIds.has(id)) return;
      if(entry.isIntersecting && entry.intersectionRatio > 0.6){
        if(feedSeenDwellTimers.has(id)) return; // already timing this one
        feedSeenDwellTimers.set(id, setTimeout(()=>{
          feedSeenDwellTimers.delete(id);
          feedSeenPending.add(id);
          scheduleFeedSeenFlush();
        }, FEED_SEEN_DWELL_MS));
      } else {
        const t = feedSeenDwellTimers.get(id);
        if(t){ clearTimeout(t); feedSeenDwellTimers.delete(id); } // scrolled past before the dwell time — doesn't count
      }
    });
  }, { root: el, threshold: [0, 0.6, 1] });
  cards.forEach(c => feedSeenObserver.observe(c));
}
function scheduleFeedSeenFlush(){
  if(feedSeenFlushTimer) return;
  feedSeenFlushTimer = setTimeout(flushFeedSeen, FEED_SEEN_FLUSH_MS);
}
async function flushFeedSeen(){
  feedSeenFlushTimer = null;
  if(!feedSeenPending.size) return;
  const ids = Array.from(feedSeenPending);
  feedSeenPending = new Set();
  ids.forEach(id=>feedSeenSentIds.add(id));
  try{ await apiPost('/api/post-views', { ids }); }
  catch(e){ /* best-effort — a missed mark just means that post ranks as unseen a bit longer, not a correctness bug */ }
}

function togglePostVideoMute(evt, i){
  evt.stopPropagation();
  const vid = document.getElementById(`postvideo-${i}`);
  if(!vid) return;
  vid.muted = !vid.muted;
  const wrap = document.getElementById(`postimg-${i}`);
  if(wrap) wrap.classList.toggle('unmuted', !vid.muted);
  const icon = document.getElementById(`postvideomuteicon-${i}`);
  if(icon){
    icon.innerHTML = vid.muted
      ? '<path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M23 9l-6 6M17 9l6 6"/>'
      : '<path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/><path d="M18.5 5.5a9 9 0 0 1 0 13"/>';
  }
}
// Tap the right/left third of a gallery post to step through its photos, or
// drag/swipe left-right — same gestures as Instagram's multi-photo posts.
// Wired up after the elements are in the DOM since it needs real element
// widths. Takes the box/track elements directly (rather than looking them
// up by index) so the same swipe logic works for both an indexed Feed card
// and the single non-indexed post viewer (see initPostViewerMedia below).
function initPostCarousel(box, track, post){
  if(!box || !track) return;
  const n = post.gallery.length;
  const go = (idx)=>{
    idx = Math.max(0, Math.min(n-1, idx));
    box.dataset.idx = idx;
    track.style.transition = '';
    track.style.transform = `translateX(-${idx*100}%)`;
    box.querySelectorAll('.post-img-dots span').forEach((d,di)=>d.classList.toggle('on', di===idx));
    const countEl = box.querySelector('.post-img-count');
    if(countEl) countEl.textContent = `${idx+1}/${n}`;
  };

  let startX = null, startY = null, dragging = false, dragged = false, boxWidth = 0;

  box.addEventListener('touchstart', (e)=>{
    if(e.touches.length !== 1) return;
    const t = e.touches[0];
    startX = t.clientX; startY = t.clientY; dragging = true; dragged = false;
    boxWidth = box.getBoundingClientRect().width || 1;
  }, { passive:true });

  box.addEventListener('touchmove', (e)=>{
    if(!dragging) return;
    const t = e.touches[0];
    const dx = t.clientX - startX, dy = t.clientY - startY;
    if(!dragged && Math.abs(dx) < Math.abs(dy)) return; // vertical scroll — let the page handle it
    dragged = true;
    const idx = parseInt(box.dataset.idx, 10) || 0;
    // Resist dragging past the first/last photo instead of sliding off into nothing.
    let drag = dx;
    if((idx === 0 && dx > 0) || (idx === n-1 && dx < 0)) drag = dx * 0.35;
    track.style.transition = 'none';
    track.style.transform = `translateX(calc(-${idx*100}% + ${drag}px))`;
  }, { passive:true });

  box.addEventListener('touchend', (e)=>{
    if(!dragging) return;
    dragging = false;
    if(!dragged){ startX = startY = null; return; }
    const t = e.changedTouches[0];
    const dx = t.clientX - startX;
    const idx = parseInt(box.dataset.idx, 10) || 0;
    startX = startY = null;
    if(Math.abs(dx) > boxWidth * 0.18){
      go(dx < 0 ? idx + 1 : idx - 1);
    } else {
      go(idx); // snap back
    }
  });

  box.addEventListener('click', (e)=>{
    if(dragged) return; // a swipe just happened — don't also treat it as a tap-to-advance
    const rect = box.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const idx = parseInt(box.dataset.idx, 10) || 0;
    if(x < rect.width * 0.3) go(idx - 1);
    else if(x > rect.width * 0.7) go(idx + 1);
  });
}

/* ---------------- Post viewer (tapping a tile in Profile > Posts) ----------------
   The small Profile grid stays plain square thumbnails (a grid full of
   autoplaying videos at once would be both noisy and heavy) — but tapping
   one opens it full-screen with the exact same media treatment Vibes uses:
   a video autoplays (muted, tap to unmute) and a gallery is swipeable, via
   the same .post-img/.post-video-wrap markup and the same initPostCarousel
   swipe logic as the Feed. Only one of these is ever open at a time, so
   (unlike the indexed postimg-${i}/postvideo-${i} ids the Feed uses) this
   uses a single fixed set of ids — same pattern as the place-detail photo
   carousel above (#detailCarousel/#detailCarouselTrack). */
let postViewerPostId = null;
function openPostViewer(postId){
  const post = POSTS.find(p => p.id === postId);
  if(!post) return;
  postViewerPostId = postId;
  renderPostViewer(post);
  document.getElementById('postViewerOverlay').classList.add('active');
  pushUIModal('postviewer');
}
function closePostViewer(){ closeUIModal('postviewer', hidePostViewerUI); }
function hidePostViewerUI(){
  const overlay = document.getElementById('postViewerOverlay');
  if(overlay) overlay.classList.remove('active');
  // Same reason as hideDetailUI() — a video left mid-play would otherwise
  // just keep decoding invisibly in the background after this closes.
  document.querySelectorAll('#postViewerBody video').forEach(v=>v.pause());
  postViewerPostId = null;
}
function renderPostViewerMediaHtml(post){
  if(post.media_type === 'video'){
    return `<div class="post-img post-video-wrap" id="pvMediaWrap">
      <video id="pvVideo" src="${post.img}" loop playsinline muted autoplay preload="auto"></video>
      <div class="post-video-mute" onclick="togglePostViewerMute(event)">
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" id="pvMuteIcon"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M23 9l-6 6M17 9l6 6"/></svg>
      </div>
    </div>`;
  }
  if(!post.gallery){
    return `<div class="post-img" id="pvMediaWrap" style="background-image:url('${post.img}')"></div>`;
  }
  const slides = post.gallery.map(src => `<div class="post-img-slide" style="background-image:url('${src}')"></div>`).join('');
  const dots = post.gallery.map((_,d) => `<span class="${d===0?'on':''}"></span>`).join('');
  return `<div class="post-img" id="pvMediaWrap" data-idx="0">
      <div class="post-img-dots">${dots}</div>
      <div class="post-img-count">1/${post.gallery.length}</div>
      <div class="post-img-track" id="pvTrack">${slides}</div>
    </div>`;
}
function renderPostViewer(post){
  document.getElementById('postViewerBody').innerHTML = `
    <div class="detail-close" onclick="closePostViewer()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body" style="padding-top:0;overflow-y:auto;">
      ${renderPostViewerMediaHtml(post)}
      <div class="post-loc-chip" style="margin-top:4px;" onclick="closePostViewer();jumpToPlace(${post.place_id})">
        <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2a7 7 0 00-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 00-7-7z"/></svg>
        ${escapeHtml(post.place)}
      </div>
      ${post.cap ? `<div class="post-cap" style="margin-top:8px;">${escapeHtml(post.cap)}${post.edited ? '<span class="post-edited-tag">(edited)</span>' : ''}</div>` : ''}
    </div>`;
  if(post.gallery) initPostCarousel(document.getElementById('pvMediaWrap'), document.getElementById('pvTrack'), post);
  // Autoplay — this viewer only ever shows the one post that's on screen
  // (unlike the Feed's IntersectionObserver, which has many candidates to
  // choose between), so it can just play immediately rather than waiting
  // to be scrolled into view.
  const vid = document.getElementById('pvVideo');
  if(vid) vid.play().catch(()=>{});
}
function togglePostViewerMute(evt){
  evt.stopPropagation();
  const vid = document.getElementById('pvVideo');
  if(!vid) return;
  vid.muted = !vid.muted;
  const wrap = document.getElementById('pvMediaWrap');
  if(wrap) wrap.classList.toggle('unmuted', !vid.muted);
  const icon = document.getElementById('pvMuteIcon');
  if(icon){
    icon.innerHTML = vid.muted
      ? '<path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M23 9l-6 6M17 9l6 6"/>'
      : '<path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/><path d="M18.5 5.5a9 9 0 0 1 0 13"/>';
  }
}

/* ---------------- Edit / delete a post you made (apiPatch/apiDelete now in src/modules/api.js) ---------------- */
function closePostMenu(){
  const el = document.getElementById('postMenuSheet');
  if(el) el.remove();
  document.removeEventListener('click', closePostMenu, true);
}
// A tiny floating action sheet ("Edit caption" / "Delete post") anchored to
// whichever "⋯" button was tapped — feed cards and the profile grid both
// call this with the post's id.
function openPostMenu(evt, postId){
  closePostMenu();
  const sheet = document.createElement('div');
  sheet.className = 'post-menu-sheet';
  sheet.id = 'postMenuSheet';
  sheet.innerHTML = `
    <div onclick="closePostMenu();editPostCaption(${postId})">Edit caption</div>
    <div class="danger" onclick="closePostMenu();deletePostConfirm(${postId})">Delete post</div>`;
  document.body.appendChild(sheet);
  const r = evt.currentTarget.getBoundingClientRect();
  const sheetW = 150;
  let left = r.right - sheetW;
  left = Math.max(8, Math.min(left, window.innerWidth - sheetW - 8));
  sheet.style.left = left + 'px';
  sheet.style.top = (r.bottom + 6 + window.scrollY) + 'px';
  setTimeout(()=>document.addEventListener('click', closePostMenu, true), 0);
}
function findPostRef(postId){
  const idx = POSTS.findIndex(p=>p.id===postId);
  if(idx > -1) return { arr: POSTS, idx };
  return null;
}
async function editPostCaption(postId){
  const ref = findPostRef(postId);
  const current = ref ? ref.arr[ref.idx].cap : '';
  const next = prompt('Edit caption', current);
  if(next === null) return; // cancelled
  try{
    await apiPatch(`/api/posts?id=${postId}`, { caption: next });
    if(ref){ ref.arr[ref.idx].cap = next; ref.arr[ref.idx].edited = true; }
    renderFeed(); renderProfileGrid();
    showToast('Caption updated.', 2200);
  }catch(e){
    alert((e.data && e.data.message) || 'Could not update the caption — try again.');
  }
}
async function deletePostConfirm(postId){
  if(!confirm('Delete this post? This can\'t be undone.')) return;
  try{
    await apiDelete(`/api/posts?id=${postId}`);
    POSTS = POSTS.filter(p=>p.id!==postId);
    renderFeed(); renderProfileGrid();
    showToast('Post deleted.', 2200);
  }catch(e){
    alert((e.data && e.data.message) || 'Could not delete that post — try again.');
  }
}

// Jumps from a post's location chip straight to that thikana's detail sheet —
// keeps every post anchored to its place, not just a caption mention.
function jumpToPlace(placeId){
  if(!PLACES.find(p=>p.id===placeId)) return;
  showView('discover');
  setTimeout(()=>openDetail(placeId), 260);
}
// Opens whatever a shared link (/share/place/:id or /share/post/:id — see
// functions/share/*) pointed at, once PLACES/POSTS have loaded. A shared
// post opens its own swipe-through viewer when it's in whatever feed page
// is currently loaded; otherwise (an older post that scrolled past the
// initial page) this falls back to opening its thikana directly, which the
// share link always includes as a second query param for exactly this
// reason — the link never dead-ends.
function handleDeepLinkParams(){
  const params = new URLSearchParams(window.location.search);
  const postParam = params.get('post');
  const placeParam = params.get('place');
  const tripParam = params.get('trip');
  if(!postParam && !placeParam && !tripParam) return;
  history.replaceState(null, '', window.location.pathname); // don't re-trigger this on refresh/back
  if(postParam){
    const postId = Number(postParam);
    const post = POSTS.find(p=>p.id===postId);
    if(post){
      showView('feed');
      setTimeout(()=>openPostViewer(postId), 300);
      return;
    }
  }
  if(tripParam){ openTripFromLink(Number(tripParam)); return; }
  if(placeParam) jumpToPlace(Number(placeParam));
}
// Opens a trip someone sent as a /share/trip/:id link. The plan is fetched
// rather than read from local state — the recipient has never seen it before.
// Access is allowed because sharing it as a link set public_share (see
// functions/api/share-card.js); a trip that was only ever shared into a chat
// still 403s here, which is the intended behaviour.
async function openTripFromLink(tripId){
  if(!tripId || Number.isNaN(tripId)) return;
  requireAuth(async ()=>{
    try{
      const { plan } = await apiGet(`/api/trip-plans?id=${tripId}`);
      if(!plan || !plan.stops || !plan.stops.length) return;
      tripStops = plan.stops.map(st=>({ id:st.place_id, name:st.name, lat:st.lat, lng:st.lng, cat:st.category, gem:false, img:null }));
      tripRoute = null;
      tripPlanId = plan.id;
      tripTargetGroupId = null; tripTargetUserId = null;
      showView('discover');
      setTimeout(()=>openTripPlanner(), 320);
    }catch(e){
      showToast(e.status === 403
        ? "That trip isn't shared with you."
        : "Couldn't open that trip.", 3000);
    }
  });
}
function likePost(i, fromDoubleTap){
  const post = POSTS[i];
  if(fromDoubleTap && post.liked){
    const heart = document.getElementById(`heart-${i}`);
    heart.classList.remove('pop'); void heart.offsetWidth; heart.classList.add('pop');
    return;
  }
  requireAuth(async ()=>{
    const wasLiked = post.liked;
    post.liked = !post.liked;
    post.likes += post.liked ? 1 : -1;
    const likeEl = document.getElementById(`likecount-${i}`);
    if(likeEl) likeEl.textContent = `${post.likes.toLocaleString('en-IN')} likes`;
    const btn = document.getElementById(`like-${i}`);
    if(btn){ btn.classList.toggle('liked', post.liked); btn.querySelector('svg').setAttribute('fill', post.liked ? 'currentColor' : 'none'); }
    if(post.liked || fromDoubleTap){
      const heart = document.getElementById(`heart-${i}`);
      if(heart){ heart.classList.remove('pop'); void heart.offsetWidth; heart.classList.add('pop'); }
    }
    if(post.id > 0){
      try{ await apiPost('/api/likes', { post_id: post.id }); }
      catch(e){
        if(isNetworkError(e)){
          // Offline: keep the optimistic tap as-is instead of reverting it,
          // and replay the same toggle once we're back online (see the
          // 'online' handler below) instead of losing the tap entirely.
          queueOfflineAction('POST', '/api/likes', { post_id: post.id }, post.liked ? 'Like' : 'Unlike');
          showToast("You're offline — this will sync when you're back online.", 2600);
        } else {
          post.liked = wasLiked; post.likes += wasLiked ? 1 : -1; renderFeed();
        }
      }
    }
  });
}
function savePost(i){
  const post = POSTS[i];
  requireAuth(async ()=>{
    const was = post.saved;
    post.saved = !post.saved;
    const btn = document.getElementById(`save-${i}`);
    if(btn){ btn.classList.toggle('saved', post.saved); btn.querySelector('svg').setAttribute('fill', post.saved ? 'currentColor' : 'none'); }
    if(post.id > 0){
      try{ await apiPost('/api/saved', { post_id: post.id }); }
      catch(e){
        if(isNetworkError(e)){
          queueOfflineAction('POST', '/api/saved', { post_id: post.id }, post.saved ? 'Save' : 'Unsave');
          showToast("You're offline — this will sync when you're back online.", 2600);
        } else {
          post.saved = was; renderFeed();
        }
      }
    }
  });
}

/* ---------------- Follow ---------------- */
function toggleFollow(userId, btnEl){
  requireAuth(async ()=>{
    try{
      const { following } = await apiPost('/api/follows', { user_id: userId });
      if(btnEl){ btnEl.textContent = following ? 'Following' : 'Follow'; btnEl.classList.toggle('following', following); }
      // Every follow pill for this same user across the app (feed cards,
      // follower/following lists, Find Dost) should flip together, not
      // just the one that was tapped — otherwise the same person can show
      // as "Follow" in one place and "Following" in another until a
      // manual refresh.
      document.querySelectorAll(`.followpill[onclick*="toggleFollow(${userId},"]`).forEach(el=>{
        if(el === btnEl) return;
        el.textContent = following ? 'Following' : 'Follow';
        el.classList.toggle('following', following);
      });
      // If that user's own profile sheet is open, its follower count should
      // move immediately too, the same way Instagram's does.
      if(userId === openProfileUserId){
        const countEl = document.getElementById('uprofFollowersCount');
        if(countEl){ countEl.textContent = Math.max(0, (parseInt(countEl.textContent,10)||0) + (following ? 1 : -1)); }
      }
      // Your own "Following" count (shown on your Profile tab) just
      // changed too — refresh it in the background so it's correct the
      // next time that tab is visible, even if it isn't right now.
      loadOwnFollowCounts();
    }catch(e){
      if(isNetworkError(e)){
        // No server answer to base the new state on — assume the tap does
        // what it looks like it should (flip whatever's currently shown)
        // and queue it; a real mismatch self-corrects on the next full
        // places/feed refresh after reconnecting.
        const willFollow = !(btnEl && btnEl.classList.contains('following'));
        if(btnEl){ btnEl.textContent = willFollow ? 'Following' : 'Follow'; btnEl.classList.toggle('following', willFollow); }
        document.querySelectorAll(`.followpill[onclick*="toggleFollow(${userId},"]`).forEach(el=>{
          if(el === btnEl) return;
          el.textContent = willFollow ? 'Following' : 'Follow';
          el.classList.toggle('following', willFollow);
        });
        queueOfflineAction('POST', '/api/follows', { user_id: userId }, willFollow ? 'Follow' : 'Unfollow');
        showToast("You're offline — this will sync when you're back online.", 2600);
      } else {
        showToast("Couldn't update follow status.", 3000);
      }
    }
  });
}

/* ---------------- Comments ---------------- */
let commentsPostId = null;
function openComments(postId){
  if(postId == null || postId < 0){ showToast("Comments aren't available on demo posts.", 2500); return; }
  commentsPostId = postId;
  document.getElementById('commentsOverlay').classList.add('active');
  pushUIModal('comments');
  renderCommentsShell();
  loadComments();
}
function hideCommentsUI(){ document.getElementById('commentsOverlay').classList.remove('active'); commentsPostId = null; }
function closeComments(){ closeUIModal('comments', hideCommentsUI); }
function renderCommentsShell(){
  document.getElementById('commentsBody').innerHTML = `
    <div class="detail-close" onclick="closeComments()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;">Comments</h2>
      <div id="commentsList"><div class="comment-empty">Loading…</div></div>
    </div>
    <div class="detail-body" style="padding-top:0;">
      <div class="comment-input-row">
        <input type="text" id="commentInput" placeholder="${currentUser ? 'Add a comment…' : 'Sign in to comment…'}" onkeydown="if(event.key==='Enter') postComment()">
        <button onclick="postComment()">Post</button>
      </div>
    </div>`;
}
async function loadComments(){
  try{
    const { comments } = await apiGet(`/api/comments?post_id=${commentsPostId}`);
    const el = document.getElementById('commentsList');
    if(!el) return;
    if(!comments.length){ el.innerHTML = `<div class="comment-empty">No comments yet — be the first to say something about this thikana.</div>`; return; }
    const post = POSTS.find(p=>p.id===commentsPostId);
    const canDeleteAny = currentUser && post && post.user_id === currentUser.id; // your own post: you can moderate any comment on it
    el.innerHTML = comments.map(c => {
      const canDelete = currentUser && (canDeleteAny || c.user_id === currentUser.id);
      return `
      <div class="comment-row" id="comment-${c.id}">
        <div class="c-avatar" ${c.avatar_url?`style="background-image:url('${c.avatar_url}');background-size:cover;"`:''}></div>
        <div style="flex:1;min-width:0;">
          <div class="c-body"><b>${escapeHtml(c.handle)}</b>${escapeHtml(c.body)}</div>
          <div class="c-time">${timeAgo(c.created_at)}</div>
        </div>
        ${canDelete ? `<div class="c-delete" onclick="deleteCommentConfirm(${c.id})" title="Delete comment">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2m3 0l-1 14a2 2 0 01-2 2H7a2 2 0 01-2-2L4 6h16z"/></svg>
        </div>` : ''}
      </div>`;
    }).join('');
  }catch(e){
    const el = document.getElementById('commentsList');
    if(el) el.innerHTML = `<div class="comment-empty">Couldn't load comments right now.</div>`;
  }
}
async function deleteCommentConfirm(commentId){
  if(!confirm("Delete this comment? This can't be undone.")) return;
  try{
    await apiDelete(`/api/comments?id=${commentId}`);
    const row = document.getElementById(`comment-${commentId}`);
    if(row) row.remove();
    const post = POSTS.find(p=>p.id===commentsPostId);
    if(post){ post.comments_count = Math.max(0, (post.comments_count||1) - 1); updateFeedCommentCount(post); }
    const el = document.getElementById('commentsList');
    if(el && !el.children.length){ el.innerHTML = `<div class="comment-empty">No comments yet — be the first to say something about this thikana.</div>`; }
    showToast('Comment deleted.', 2000);
  }catch(e){
    alert((e.data && e.data.message) || 'Could not delete that comment — try again.');
  }
}
function postComment(){
  requireAuth(async ()=>{
    const input = document.getElementById('commentInput');
    const text = input.value.trim();
    if(!text) return;
    input.disabled = true;
    try{
      await apiPost('/api/comments', { post_id: commentsPostId, body: text });
      input.value = '';
      await loadComments();
      const post = POSTS.find(p=>p.id===commentsPostId);
      if(post){ post.comments_count = (post.comments_count||0) + 1; updateFeedCommentCount(post); }
    }catch(e){
      if(isNetworkError(e)){
        // Can't refetch the list to show it inline without a network round
        // trip, so just clear the box, bump the visible count, and queue
        // the actual post — same "trust the tap, reconcile later" approach
        // as likes/saves/follows above.
        queueOfflineAction('POST', '/api/comments', { post_id: commentsPostId, body: text }, 'Comment');
        input.value = '';
        const post = POSTS.find(p=>p.id===commentsPostId);
        if(post){ post.comments_count = (post.comments_count||0) + 1; updateFeedCommentCount(post); }
        showToast("You're offline — your comment will post automatically when you're back online.", 3200);
      } else {
        showToast('Comment failed to post — try again.', 3000);
      }
    }
    finally{ input.disabled = false; input.focus(); }
  });
}
// Patches the "View all N comments" line on the post's own card in the Feed
// (without a full re-render, which would also restart its scroll position
// and video autoplay state) so the count there matches what you just saw
// inside the comments sheet, the same way Instagram's feed card updates the
// instant you add a comment rather than only after the next pull-to-refresh.
function updateFeedCommentCount(post){
  const i = POSTS.indexOf(post);
  if(i < 0) return;
  const capEl = document.getElementById(`postcap-${i}`);
  if(!capEl) return;
  const link = capEl.querySelector('.comment-link');
  if(link){
    link.textContent = `View all ${post.comments_count} comments`;
  } else if(post.comments_count > 0){
    capEl.insertAdjacentHTML('beforeend', `<span class="comment-link" onclick="openComments(${post.id})">View all ${post.comments_count} comments</span>`);
  }
}

/* ---------------- User profile overlay ---------------- */
function openUserProfile(userId){
  if(userId == null || userId < 0){ showToast("This is a demo post — no profile to view yet.", 2500); return; }
  if(currentUser && userId === currentUser.id){ showView('profile'); return; }
  document.getElementById('userProfileOverlay').classList.add('active');
  pushUIModal('userprofile');
  document.getElementById('userProfileBody').innerHTML = `<div class="detail-body" style="padding-top:40px;text-align:center;color:var(--ink-soft);">Loading…</div>`;
  loadUserProfile(userId);
}
function hideUserProfileUI(){ document.getElementById('userProfileOverlay').classList.remove('active'); openProfileUserId = null; }
function closeUserProfile(){ closeUIModal('userprofile', hideUserProfileUI); }
let openProfileUserId = null; // whichever user's profile sheet (openUserProfile) is currently on screen, so a follow/unfollow elsewhere can update its counts live
async function loadUserProfile(userId){
  try{
    const data = await apiGet(`/api/users?id=${userId}`);
    openProfileUserId = userId;
    renderUserProfileBody(data);
  }catch(e){
    document.getElementById('userProfileBody').innerHTML = `<div class="detail-body" style="text-align:center;color:var(--ink-soft);">Couldn't load this profile.</div>`;
  }
}
function renderUserProfileBody(data){
  const u = data.user;
  const grid = data.posts.filter(p=>p.post_kind!=='reel').map(p=>`<div style="background-image:url('${p.media_data}')" onclick="jumpToPlace(${p.place_id})"></div>`).join('');
  document.getElementById('userProfileBody').innerHTML = `
    <div class="detail-close" onclick="closeUserProfile()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body">
      <div class="uprof-head">
        <div class="p-avatar" ${u.avatar_url?`style="background-image:url('${u.avatar_url}');background-size:cover;"`:''}>${!u.avatar_url ? avatarInitial(u) : ''}</div>
        <div>
          <div class="p-name">${u.handle}${u.is_official?VERIFIED_BADGE:''}</div>
          <div class="p-handle">Member since ${memberSinceLabel(u)}</div>
        </div>
      </div>
      <div class="uprof-stats">
        <div><b>${data.posts_count}</b><span>Posts</span></div>
        <div style="cursor:pointer;" onclick="openFollowList(${u.id},'followers')"><b id="uprofFollowersCount">${data.followers_count}</b><span>Followers</span></div>
        <div style="cursor:pointer;" onclick="openFollowList(${u.id},'following')"><b>${data.following_count}</b><span>Following</span></div>
      </div>
      ${!data.is_self ? `<div style="display:flex;gap:8px;margin-bottom:16px;">
        <span class="followpill${data.is_following?' following':''}" id="followpill-prof" onclick="toggleFollow(${u.id}, this)">${data.is_following?'Following':'Follow'}</span>
        <span class="followpill" onclick="closeUserProfile();setTimeout(()=>openThread(${u.id}),300);">Message</span>
      </div>` : ''}
      <div class="p-grid">${grid || `<div class="p-empty">No posts yet.</div>`}</div>
    </div>`;
}

/* ---------------- Messaging (DMs) ---------------- */
let msgView = 'inbox';        // 'inbox' | 'thread'
let msgOtherUser = null;      // the user object of the currently-open thread
let msgCameFromInbox = false; // whether the open thread was reached via the inbox list (so back goes there, not out)
let msgPollTimer = null;
let msgAttachment = null;     // { data, type } pending photo/video attachment in the composer
let msgIsGroup = false;       // whether the currently-open thread is a group instead of a DM
let msgGroup = null;          // { id, name, photo_url, members } for the open group thread
// Keeps #messagesOverlay's CSS in sync with msgView — see .msg-inbox-open
// in styles.css for why (dock visible on the inbox list, hidden inside an
// open thread) — and switches which poll loop (inbox list vs. this one
// thread) is the one currently running.
function setMsgView(v){
  msgView = v;
  const overlay = document.getElementById('messagesOverlay');
  if(overlay) overlay.classList.toggle('msg-inbox-open', v === 'inbox');
  if(v === 'inbox') startInboxPoll(); else stopInboxPoll();
  try {
    if(window.AndroidNativeAuth && typeof window.AndroidNativeAuth.onWebViewChanged === 'function'){
      window.AndroidNativeAuth.onWebViewChanged(v === 'inbox' ? 'messages' : 'chat');
    }
  } catch(e) {}
}
let newMsgSearchTimer = null;
// Incremental-fetch + optimistic-send bookkeeping for whichever thread is open.
let msgLastId = 0;               // highest message id we've seen — driving `since_id` on the next fetch
let msgRenderedIds = new Set();  // every real message id already reflected in the DOM (or superseded by an optimistic bubble)
let msgTheirLastReadId = 0;      // highest id of MY messages the other person has read — drives the read ticks
let msgTempCounter = 0;
let msgOptimisticById = new Map(); // tempId -> in-flight/failed message object, for retry + reconciliation
const MSG_POLL_MS = 3000; // fallback while a thread is open — push is the instant path, this just keeps things live without it
const MSG_INBOX_POLL_MS = 5000; // keeps the conversation list itself live (new chats, previews, unread counts) while it's on screen
const TYPING_PING_MS = 3000;  // how often we tell the server "still typing" while there's text in the box (throttled, not per-keystroke)
let msgLastTypingPingAt = 0;  // Date.now() of the last ping actually sent — the throttle onMsgInputChanged() checks against
const TICK_CLOCK = '<svg viewBox="0 0 16 16" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.6"><circle cx="8" cy="8" r="6.2"/><path d="M8 4.6V8l2.6 1.6"/></svg>';
const TICK_SENT = '<svg viewBox="0 0 18 12" width="13" height="9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M1 6l4 4L15 1"/></svg>';
const TICK_READ = '<svg viewBox="0 0 22 12" width="15" height="9" fill="none" stroke="#34B7F1" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M1 6l4 4L15 1"/><path d="M7 6l4 4L21 1"/></svg>';
const TICK_FAILED = '<svg viewBox="0 0 16 16" width="11" height="11" fill="none" stroke="#B3261E" stroke-width="1.8"><path d="M8 4.4v4.4"/><circle cx="8" cy="8" r="6.4"/></svg>';
let shareSearchTimer = null;
let shareTarget = null;       // { place_id } or { post_id } currently being shared
// renderBubble() (below) can't init a rich-card carousel the instant it
// builds a card's HTML string — the element isn't in the DOM yet. It stows
// {cardId -> slides} here instead; every call site that inserts bubble HTML
// into #msgList calls flushPendingRichCards() right after, once the nodes
// actually exist.
let pendingRichCards = new Map();
function flushPendingRichCards(){
  pendingRichCards.forEach((slides, cardId) => initRichCarousel(cardId, slides));
  pendingRichCards.clear();
}
// Same idea as pendingRichCards above, but for a shared-trip bubble's tap
// target: tapping it loads that trip straight into the planner (see
// openSharedTrip below). Keeping the stop list here instead of inline in
// the onclick attribute avoids the same JSON-in-an-HTML-attribute quoting
// trap navigateToMeetup used to hit (see its old comment, since removed) —
// a stop name with a quote in it would otherwise silently break the
// attribute.
let pendingTripShares = new Map();
let editAvatarPending = null; // compressed data URI staged before saving, or '' to clear the photo

// Messages is a bottom-nav tab like Discover/Feed/Profile, so opening it
// needs to behave the same way those do: replace whatever tab was active
// (not stack on top of it), highlight its own nav icon instead of leaving
// the previous tab's icon lit up, and play the same fade-in as every other
// tab instead of popping in instantly.
function openMessages(){
  requireAuth(()=>{
    while(uiStack.length > 1) uiStack.pop();
    document.querySelectorAll('.view').forEach(v=>v.classList.remove('active','view-in'));
    document.querySelectorAll('.navitem[data-v]').forEach(n=>n.classList.toggle('active', n.dataset.v==='messages'));
    pushUIModal('messages');
    const overlay = document.getElementById('messagesOverlay');
    overlay.classList.remove('view-in');
    overlay.classList.add('active');
    requestAnimationFrame(()=>overlay.classList.add('view-in'));
    setMsgView('inbox');
    stopMsgPoll();
    notifyActiveThread(null);
    renderMessagesInboxShell();
    loadInbox();
  });
}
function hideMessagesUI(){ document.getElementById('messagesOverlay').classList.remove('active','view-in'); stopMsgPoll(); stopInboxPoll(); notifyActiveThread(null); msgOtherUser = null; msgIsGroup = false; msgGroup = null; msgLastTypingPingAt = 0; }
// Leaves the Messages flow entirely — whether the top of uiStack is just
// 'messages' (still on the inbox list, or a thread opened directly from
// outside Messages) or 'messages'+'msgthread' (stepped into a thread from
// an already-open inbox — see openThread's msgCameFromInbox branch, which
// gives that step its own stack entry). The old closeUIModal('messages',
// hideMessagesUI) only ever matched the shallow case: called from inside a
// stepped-into thread (top === 'msgthread'), isModalOpen('messages') was
// false, so it fell straight to hideFn() — tearing down the overlay and
// state WITHOUT touching uiStack. uiStack was then stuck believing
// 'msgthread' was still on top while nothing was actually on screen, and
// a follow-up pushUIModal('trip') (or any other "close messages, then go
// render X elsewhere" call — see the shared card/thread-header taps that
// call this) stacked on top of that already-wrong state. Any later back
// press landed back on a uiStack entry whose kind no longer matched
// anything actually torn down, rendering nothing — the blank screen with
// only the bottom nav showing.
// This collapses however many of the two entries are actually present in
// one synchronous step (direct uiStack/history manipulation, not
// history.back()'s async popstate round-trip — same pattern showView()
// already uses to jump straight to a tab) instead of assuming a fixed
// depth, so every caller works regardless of which way the thread was
// reached. msgThreadBack() (the in-thread "<" arrow) is different — it
// steps back exactly ONE level, not out of Messages entirely — so it
// still branches on msgCameFromInbox itself rather than calling this.
function closeMessages(){
  hideMessagesUI();
  while(uiStack.length && (uiStack[uiStack.length-1].kind === 'messages' || uiStack[uiStack.length-1].kind === 'msgthread')) uiStack.pop();
  history.replaceState({ depth: uiStack.length }, '', location.href);
  const activeName = (typeof topView === 'function' ? topView() : null) || 'discover';
  if(typeof renderActiveView === 'function') renderActiveView(activeName);
  try {
    if(window.AndroidNativeAuth && typeof window.AndroidNativeAuth.onWebViewChanged === 'function'){
      window.AndroidNativeAuth.onWebViewChanged(activeName);
    }
  } catch(e) {}
}

function groupAvatarIcon(){
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`;
}

function renderMessagesInboxShell(){
  document.getElementById('messagesBody').innerHTML = `
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;">Messages</h2>
      <div style="display:flex;gap:8px;">
        <div class="msg-newbtn" style="flex:1;" onclick="openNewMessage()">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
          New message
        </div>
        <div class="msg-newbtn" style="flex:1;" onclick="openNewGroup()">
          ${groupAvatarIcon()}
          New group
        </div>
      </div>
      <div id="msgSearchResults" class="msg-search-results"></div>
      <div id="msgInboxList"><div class="msg-empty">Loading…</div></div>
    </div>`;
}
async function loadInbox(){
  try{
    const { conversations, total_unread } = await apiGet('/api/messages');
    updateMsgBadge(total_unread);
    const el = document.getElementById('msgInboxList');
    if(!el) return;
    if(!conversations.length){ el.innerHTML = `<div class="msg-empty">No messages yet — tap "New message" to say hi, or share a thikana with a friend.</div>`; return; }
    el.innerHTML = conversations.map(c => {
      if(c.is_group){
        let preview = c.last_body ? escapeHtml(c.last_body)
          : c.last_media_type ? (c.last_media_type === 'video' ? '📹 Video' : '📷 Photo')
          : c.last_shared_place_id ? '📍 Shared a thikana'
          : c.last_shared_post_id ? '📍 Shared a post' : '';
        if(preview && c.last_sender_handle) preview = `${c.last_sender_id===currentUser.id?'You':'@'+c.last_sender_handle}: ${preview}`;
        return `
        <div class="msg-inbox-row" onclick="openGroupThread(${c.conversation_id})">
          <div class="m-avatar" style="display:flex;align-items:center;justify-content:center;background:var(--grad-forest);color:#fff;">${groupAvatarIcon()}</div>
          <div class="msg-inbox-info">
            <div class="msg-inbox-name">${escapeHtml(c.group_name || 'Group')}</div>
            <div class="msg-inbox-preview${c.unread_count>0?' unread':''}">${preview || '…'}</div>
          </div>
          <div class="msg-inbox-meta">
            <div class="msg-inbox-time">${timeAgo(c.last_message_at)}</div>
            ${c.unread_count>0?'<div class="msg-inbox-dot"></div>':''}
          </div>
        </div>`;
      }
      const u = c.other;
      let preview = c.last_body ? escapeHtml(c.last_body)
        : c.last_media_type ? (c.last_media_type === 'video' ? '📹 Video' : '📷 Photo')
        : c.last_shared_place_id ? '📍 Shared a thikana'
        : c.last_shared_post_id ? '📍 Shared a post' : '';
      if(c.last_sender_id === currentUser.id) preview = 'You: ' + preview;
      return `
      <div class="msg-inbox-row" onclick="openThread(${u.id})">
        <div class="m-avatar" ${u.avatar_url?`style="background-image:url('${u.avatar_url}');background-size:cover;"`:''}>${!u.avatar_url?avatarInitial(u):''}</div>
        <div class="msg-inbox-info">
          <div class="msg-inbox-name">${escapeHtml(u.display_name || u.handle)}</div>
          <div class="msg-inbox-preview${c.unread_count>0?' unread':''}">${preview || '…'}</div>
        </div>
        <div class="msg-inbox-meta">
          <div class="msg-inbox-time">${timeAgo(c.last_message_at)}</div>
          ${c.unread_count>0?'<div class="msg-inbox-dot"></div>':''}
        </div>
      </div>`;
    }).join('');
  }catch(e){
    const el = document.getElementById('msgInboxList');
    if(el) el.innerHTML = `<div class="msg-empty">Couldn't load messages right now.</div>`;
  }
}

/* ---- New group: pick members, name it, create ---- */
let newGroupPicked = new Map(); // userId -> user object, selection order preserved
function openNewGroup(){
  newGroupPicked = new Map();
  renderNewGroupPicker();
}
function renderNewGroupPicker(){
  const results = document.getElementById('msgSearchResults');
  if(!results) return;
  const chips = Array.from(newGroupPicked.values()).map(u => `
    <span class="followpill" style="background:var(--forest);color:#fff;">${escapeHtml(u.display_name || u.handle)} <span style="cursor:pointer;margin-left:4px;" onclick="toggleNewGroupPick(${u.id}, null)">✕</span></span>
  `).join('');
  results.innerHTML = `
    <div style="display:flex;flex-wrap:wrap;gap:6px;margin-bottom:8px;">${chips}</div>
    <input type="text" id="newGroupSearchInput" placeholder="Add people by name…" oninput="onNewGroupSearchInput()" style="width:100%;border:1px solid var(--line);border-radius:100px;padding:10px 15px;font-family:'Work Sans',sans-serif;font-size:13px;background:var(--paper);margin-bottom:8px;box-sizing:border-box;">
    <div id="newGroupSearchResults"></div>
    ${newGroupPicked.size ? `
      <input type="text" id="newGroupNameInput" placeholder="Group name…" style="width:100%;border:1px solid var(--line);border-radius:100px;padding:10px 15px;font-size:13px;margin:8px 0;box-sizing:border-box;">
      <button class="btn-primary" style="width:100%;" onclick="submitNewGroup()">Create group</button>
    ` : ''}`;
  document.getElementById('newGroupSearchInput').focus();
}
function toggleNewGroupPick(userId, user){
  if(newGroupPicked.has(userId)) newGroupPicked.delete(userId);
  else if(user) newGroupPicked.set(userId, user);
  renderNewGroupPicker();
}
function onNewGroupSearchInput(){
  clearTimeout(newMsgSearchTimer);
  const input = document.getElementById('newGroupSearchInput');
  const q = input ? input.value.trim() : '';
  const list = document.getElementById('newGroupSearchResults');
  if(!list) return;
  if(!q){ list.innerHTML = ''; return; }
  newMsgSearchTimer = setTimeout(async ()=>{
    try{
      const { users } = await apiGet(`/api/users?search=${encodeURIComponent(q)}`);
      const candidates = users.filter(u => u.id !== currentUser.id);
      list.innerHTML = candidates.length ? candidates.map(u=>`
        <div class="msg-inbox-row" style="cursor:pointer;" onclick='toggleNewGroupPick(${u.id}, ${JSON.stringify(u)})'>
          <div class="m-avatar" ${u.avatar_url?`style="background-image:url('${u.avatar_url}');background-size:cover;"`:''}>${!u.avatar_url?avatarInitial(u):''}</div>
          <div class="msg-inbox-info"><div class="msg-inbox-name">${escapeHtml(u.display_name || u.handle)}</div><div class="msg-inbox-preview">@${u.handle}${newGroupPicked.has(u.id)?' · Added':''}</div></div>
        </div>`).join('') : `<div class="msg-empty">No one found.</div>`;
    }catch(e){}
  }, 300);
}
async function submitNewGroup(){
  const nameInput = document.getElementById('newGroupNameInput');
  const name = nameInput ? nameInput.value.trim() : '';
  if(!name){ showToast('Give the group a name.', 2400); return; }
  if(!newGroupPicked.size){ showToast('Add at least one person first.', 2400); return; }
  try{
    const { group } = await apiPost('/api/groups', { name, member_ids: Array.from(newGroupPicked.keys()) });
    newGroupPicked = new Map();
    loadInbox();
    openGroupThread(group.id);
  }catch(e){
    showToast((e.data && e.data.message) || "Couldn't create the group.", 3200);
  }
}
function openNewMessage(){
  const results = document.getElementById('msgSearchResults');
  if(!results) return;
  results.innerHTML = `<input type="text" id="msgSearchInput" placeholder="Search by name…" oninput="onMsgSearchInput()" style="width:100%;border:1px solid var(--line);border-radius:100px;padding:10px 15px;font-family:'Work Sans',sans-serif;font-size:13px;background:var(--paper);margin-bottom:8px;">`;
  document.getElementById('msgSearchInput').focus();
}
function onMsgSearchInput(){
  clearTimeout(newMsgSearchTimer);
  const q = document.getElementById('msgSearchInput').value.trim();
  const list = document.getElementById('msgInboxList');
  if(!q){ loadInbox(); return; }
  newMsgSearchTimer = setTimeout(async ()=>{
    try{
      const { users } = await apiGet(`/api/users?search=${encodeURIComponent(q)}`);
      if(!list) return;
      list.innerHTML = users.length ? users.map(u=>`
        <div class="msg-inbox-row" onclick="openThread(${u.id})">
          <div class="m-avatar" ${u.avatar_url?`style="background-image:url('${u.avatar_url}');background-size:cover;"`:''}>${!u.avatar_url?avatarInitial(u):''}</div>
          <div class="msg-inbox-info"><div class="msg-inbox-name">${escapeHtml(u.display_name || u.handle)}</div><div class="msg-inbox-preview">@${u.handle}</div></div>
        </div>`).join('') : `<div class="msg-empty">No one found.</div>`;
    }catch(e){}
  }, 300);
}

function openThread(userId){
  if(currentUser && userId === currentUser.id) return;
  msgIsGroup = false; msgGroup = null;
  msgCameFromInbox = (msgView === 'inbox') && isModalOpen('messages');
  setMsgView('thread');
  const alreadyOpen = isModalOpen('messages');
  if(!alreadyOpen){
    // Reached from outside the Messages tab (a profile's "Message" button,
    // a notification tap) — activate the tab the same way tapping it in
    // the nav bar would, instead of just stacking the overlay on top.
    while(uiStack.length > 1) uiStack.pop();
    document.querySelectorAll('.view').forEach(v=>v.classList.remove('active','view-in'));
    document.querySelectorAll('.navitem[data-v]').forEach(n=>n.classList.toggle('active', n.dataset.v==='messages'));
    pushUIModal('messages');
  } else if(msgCameFromInbox){
    // Stepping from the inbox list into a thread while Messages is already
    // open — this is its own back-step (the in-thread "<" arrow undoes
    // exactly this), so it needs its own history entry. Without this the
    // list⇄thread transition shared the single 'messages' entry, which a
    // later replaceUIModal() (e.g. handing off into nav) could silently
    // clobber, and a real back-press could skip straight past the inbox
    // to whatever's under Messages.
    pushUIModal('msgthread');
  }
  const overlay = document.getElementById('messagesOverlay');
  overlay.classList.remove('view-in');
  overlay.classList.add('active');
  requestAnimationFrame(()=>overlay.classList.add('view-in'));
  document.getElementById('messagesBody').innerHTML = `<div class="detail-body" style="padding-top:40px;text-align:center;color:var(--ink-soft);">Loading…</div>`;
  loadThread(userId);
  startMsgPoll(userId);
  notifyActiveThread(userId);
}
// Same shape as openThread, for a group conversation instead of a DM.
function openGroupThread(conversationId){
  msgIsGroup = true; msgOtherUser = null;
  msgCameFromInbox = (msgView === 'inbox') && isModalOpen('messages');
  setMsgView('thread');
  const alreadyOpen = isModalOpen('messages');
  if(!alreadyOpen){
    while(uiStack.length > 1) uiStack.pop();
    document.querySelectorAll('.view').forEach(v=>v.classList.remove('active','view-in'));
    document.querySelectorAll('.navitem[data-v]').forEach(n=>n.classList.toggle('active', n.dataset.v==='messages'));
    pushUIModal('messages');
  } else if(msgCameFromInbox){
    // See the matching comment in openThread() — same reasoning for groups.
    pushUIModal('msgthread');
  }
  const overlay = document.getElementById('messagesOverlay');
  overlay.classList.remove('view-in');
  overlay.classList.add('active');
  requestAnimationFrame(()=>overlay.classList.add('view-in'));
  document.getElementById('messagesBody').innerHTML = `<div class="detail-body" style="padding-top:40px;text-align:center;color:var(--ink-soft);">Loading…</div>`;
  loadGroupThread(conversationId);
  startMsgPoll(conversationId);
  // No push-suppression signal for groups yet (notifyActiveThread is DM-shaped) —
  // a minor gap, not a functional bug: an open group thread can still surface a
  // redundant OS notification alongside the in-app update.
}
// Quick member list for now — a full "group info" screen (rename, remove
// members, leave) can come later if this actually gets used a lot.
function showGroupMembers(){
  if(!msgGroup) return;
  const names = msgGroup.members.map(m => `@${m.handle}${m.role==='admin'?' (admin)':''}`).join(', ');
  showToast(names, 5000);
}
function leaveGroup(){
  if(!msgGroup) return;
  if(!confirm(`Leave "${msgGroup.name}"?`)) return;
  apiPatch('/api/groups', { id: msgGroup.id, action: 'leave' }).then(()=>{
    msgThreadBack();
  }).catch(()=> showToast("Couldn't leave the group right now.", 3000));
}
// Back arrow inside an open thread: returns to the inbox list if that's how
// we got here (WhatsApp-style list → conversation → back), otherwise closes
// Messages entirely (e.g. reached via a profile's "Message" button).
function backToMsgInbox(){
  setMsgView('inbox');
  stopMsgPoll();
  notifyActiveThread(null);
  renderMessagesInboxShell();
  loadInbox();
}
function msgThreadBack(){
  if(msgCameFromInbox){
    // Goes through the same history.back() path as every other in-app back
    // button (see closeUIModal) instead of just flipping msgView directly —
    // keeps uiStack in sync with what's on screen so a real back-press and
    // this arrow can never disagree about where "back" goes.
    closeUIModal('msgthread', backToMsgInbox);
  } else {
    closeMessages();
  }
}
async function loadThread(userId){
  try{
    const data = await apiGet(`/api/messages?with=${userId}`);
    msgOtherUser = data.other;
    msgTheirLastReadId = data.their_last_read_id || 0;
    renderThreadShell();
    renderThreadMessages(data.messages);
    loadInbox(); // refreshes the unread badge in the background
  }catch(e){
    document.getElementById('messagesBody').innerHTML = `<div class="detail-body" style="text-align:center;color:var(--ink-soft);">Couldn't load this conversation.</div>`;
  }
}
async function loadGroupThread(conversationId){
  try{
    msgTheirLastReadId = 0;
    const data = await apiGet(`/api/messages?group_id=${conversationId}`);
    msgGroup = { id: data.group.id, name: data.group.group_name, photo_url: data.group.group_photo_url, members: (await apiGet(`/api/groups?id=${conversationId}`)).group.members };
    renderThreadShell();
    renderThreadMessages(data.messages);
    loadInbox();
  }catch(e){
    document.getElementById('messagesBody').innerHTML = `<div class="detail-body" style="text-align:center;color:var(--ink-soft);">Couldn't load this group.</div>`;
  }
}
function renderThreadShell(){
  const u = msgOtherUser;
  const headerHtml = msgIsGroup ? `
        <div class="msg-thread-head" onclick="showGroupMembers()">
          <div class="m-avatar" style="display:flex;align-items:center;justify-content:center;background:var(--grad-forest);color:#fff;">${groupAvatarIcon()}</div>
          <div>
            <div class="p-name" style="font-size:14.5px;">${escapeHtml(msgGroup.name)}</div>
            <div style="font-size:11px;color:var(--ink-soft);">${msgGroup.members.length} members · tap for list</div>
          </div>
        </div>
        <div class="msg-iconbtn" onclick="leaveGroup()" title="Leave group">🚪</div>
  ` : `
        <div class="msg-thread-head" onclick="closeMessages();setTimeout(()=>openUserProfile(${u.id}),300);">
          <div class="m-avatar" ${u.avatar_url?`style="background-image:url('${u.avatar_url}');background-size:cover;"`:''}>${!u.avatar_url?avatarInitial(u):''}</div>
          <div class="p-name" style="font-size:14.5px;">${escapeHtml(u.display_name || u.handle)}</div>
        </div>
  `;
  document.getElementById('messagesBody').innerHTML = `
    <div class="detail-body msg-page">
      <div class="msg-page-header">
        <div class="msg-back-btn" onclick="msgThreadBack()">
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.3" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>
        </div>
        ${headerHtml}
        <div class="msg-iconbtn" onclick="openTripPlannerForChat()" title="Plan a trip">🗺️</div>
      </div>
      <div class="msg-list" id="msgList"></div>
      <div id="msgTypingIndicator" style="display:none;padding:2px 14px 4px;font-size:12px;color:var(--ink-soft);font-style:italic;"></div>
      <div id="msgAttachPreview"></div>
      <div class="emoji-picker" id="emojiPicker"></div>
      <div class="msg-input-row">
        <div class="msg-iconbtn" onclick="toggleEmojiPicker()" title="Emoji">😊</div>
        <div class="msg-iconbtn" onclick="document.getElementById('msgFileInput').click()" title="Attach photo or video">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
        </div>
        <input type="file" id="msgFileInput" accept="image/*,video/*" style="display:none" onchange="onMsgFileSelected(event)">
        <input type="text" id="msgTextInput" placeholder="Message…" onkeydown="if(event.key==='Enter') sendMessage()" oninput="onMsgInputChanged()">
        <div class="msg-send-btn" onclick="sendMessage()">
          <svg viewBox="0 0 24 24" fill="currentColor"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>
        </div>
      </div>
    </div>`;
  buildEmojiPicker();
}
// Throttled "I'm typing" ping — fires at most once per TYPING_PING_MS while
// there's text in the box, not on every keystroke (that'd be one write per
// character for no real benefit, since the indicator's staleness window on
// the read side is seconds-wide anyway).
function onMsgInputChanged(){
  const el = document.getElementById('msgTextInput');
  if(!el || !el.value.trim()) return; // clearing the box is just "stop pinging" — the staleness window handles the rest
  const now = Date.now();
  if(now - msgLastTypingPingAt < TYPING_PING_MS) return;
  msgLastTypingPingAt = now;
  const payload = msgIsGroup ? { group_id: msgGroup.id } : { to_user_id: msgOtherUser.id };
  apiPost('/api/typing', payload).catch(()=>{});
}
// Polled alongside pollThreadOnce (see startMsgPoll) — separate call since
// pollThreadOnce also fires from the instant push-triggered path, where
// re-checking typing status isn't needed.
async function pollTypingOnce(threadId){
  if(msgIsGroup){
    if(!msgGroup || msgGroup.id !== threadId) return;
    try{ renderTypingIndicator((await apiGet(`/api/typing?group_id=${threadId}`)).typing_users); }catch(e){}
    return;
  }
  if(!msgOtherUser || msgOtherUser.id !== threadId) return;
  try{ renderTypingIndicator((await apiGet(`/api/typing?with=${threadId}`)).typing_users); }catch(e){}
}
function renderTypingIndicator(users){
  const el = document.getElementById('msgTypingIndicator');
  if(!el) return;
  if(!users || !users.length){ el.style.display = 'none'; el.textContent = ''; return; }
  const names = users.map(u => u.display_name || u.handle);
  const text = names.length === 1 ? `${names[0]} is typing…`
    : names.length === 2 ? `${names[0]} and ${names[1]} are typing…`
    : `${names.length} people are typing…`;
  el.textContent = text;
  el.style.display = 'block';
}
// Full (re)render — used only for a thread's initial load. Everything after
// that is incremental: appendThreadMessages() adds just the new rows and
// updateReadTicks() patches tick icons in place, so a poll/push tick never
// touches existing DOM nodes (no scroll jump, no interrupted video).
function resetThreadTrackingState(){
  msgLastId = 0;
  msgRenderedIds = new Set();
  msgOptimisticById = new Map();
}
function renderThreadMessages(messages){
  const el = document.getElementById('msgList');
  if(!el) return;
  resetThreadTrackingState();
  if(!messages.length){
    el.innerHTML = `<div class="msg-empty" id="msgEmptyState">Say hi to ${escapeHtml(msgOtherUser.display_name || msgOtherUser.handle)} 👋</div>`;
    return;
  }
  el.innerHTML = messages.map(m => { msgRenderedIds.add(m.id); if(m.id > msgLastId) msgLastId = m.id; return renderBubble(m); }).join('');
  el.scrollTop = el.scrollHeight;
  flushPendingRichCards();
}
// Appends only messages we haven't rendered yet (used by the poll fallback
// and by the instant push-triggered refetch). Our OWN sent messages are
// deliberately skipped here — they're already on screen via the optimistic
// bubble + direct POST-response reconciliation, so re-adding them from a
// background fetch would either duplicate them or race the reconciliation.
function appendThreadMessages(messages){
  const el = document.getElementById('msgList');
  if(!el || !messages || !messages.length) return;
  const toRender = [];
  for(const m of messages){
    if(msgRenderedIds.has(m.id)) continue;
    msgRenderedIds.add(m.id);
    if(m.id > msgLastId) msgLastId = m.id;
    if(m.sender_id !== currentUser.id) toRender.push(m);
  }
  if(!toRender.length) return;
  const emptyState = document.getElementById('msgEmptyState');
  if(emptyState) emptyState.remove();
  const wasNearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  const wrapper = document.createElement('div');
  wrapper.innerHTML = toRender.map(m => renderBubble(m)).join('');
  while(wrapper.firstChild) el.appendChild(wrapper.firstChild);
  if(wasNearBottom) el.scrollTop = el.scrollHeight;
  flushPendingRichCards();
}
// Patches just the tick icon on my own bubbles when the other person's read
// position moves — never touches the rest of the bubble (so a playing video
// or the failed/pending state of an unrelated bubble is left alone).
function updateReadTicks(){
  document.querySelectorAll('#msgList .bubble-row.me[data-id]').forEach(row=>{
    const idAttr = row.getAttribute('data-id');
    if(!idAttr || idAttr.indexOf('tmp') === 0) return; // optimistic/failed — leave clock/failed icon alone
    const id = Number(idAttr);
    const tickEl = row.querySelector('.bubble-status');
    if(tickEl) tickEl.innerHTML = id <= msgTheirLastReadId ? TICK_READ : TICK_SENT;
  });
}
function renderBubble(m){
  const mine = m.sender_id === currentUser.id;
  const mediaOnly = !m.body && m.media_data && !m.shared_place_id && !m.shared_post_id && !m.shared_trip_id;
  const hasCard = !!(m.shared_place_id || m.shared_post_id || m.shared_trip_id); // drives .bubble.has-card in styles.css — see its comment for why cards need this and plain text doesn't
  let inner = '';
  // Group threads show who sent each message (a DM doesn't need to — there's
  // only ever one other person). Skipped for my own bubbles, same as WhatsApp.
  if(msgIsGroup && !mine && m.sender_handle){
    inner += `<div class="msg-sender-label" style="font-size:11px;font-weight:700;color:var(--forest);margin-bottom:2px;">${escapeHtml(m.sender_display_name || m.sender_handle)}</div>`;
  }
  if(m.media_data){
    inner += m.media_type === 'video'
      ? `<video src="${m.media_data}" controls playsinline></video>`
      : `<img src="${m.media_data}" onclick="window.open(this.src,'_blank')">`;
  }
  if(m.shared_place_id){
    const cardId = `share-msg-${m.id}`;
    // Prefer the full local gallery (this device already has PLACES loaded)
    // and fall back to just the cover photo the server sent along with the
    // message, so the card still looks right even before/without that.
    let slides = buildShareSlides({ place_id: m.shared_place_id });
    if(!slides.length && m.shared_place_cover) slides = [{ src: m.shared_place_cover, type:'photo' }];
    pendingRichCards.set(cardId, slides);
    inner += richShareCardHtml({
      cardId, slides, coverFallback: m.shared_place_cover, compact: true,
      name: m.shared_place_name, category: m.shared_place_category,
      lat: m.shared_place_lat, lng: m.shared_place_lng,
      onclick: `closeMessages();setTimeout(()=>jumpToPlace(${m.shared_place_id}),300);`,
    });
  } else if(m.shared_post_id){
    const cardId = `share-msg-${m.id}`;
    let slides = buildShareSlides({ post_id: m.shared_post_id });
    if(!slides.length && m.shared_post_media) slides = [{ src: m.shared_post_media, type: m.shared_post_media_type === 'video' ? 'video' : 'photo' }];
    pendingRichCards.set(cardId, slides);
    inner += richShareCardHtml({
      cardId, slides, coverFallback: m.shared_post_media, compact: true,
      name: m.shared_post_place_name || 'A vibe', category: m.shared_post_place_category,
      lat: m.shared_post_place_lat, lng: m.shared_post_place_lng,
      onclick: `closeMessages();setTimeout(()=>jumpToPlace(${m.shared_post_place_id}),300);`,
    });
  } else if(m.shared_trip_id){
    const stops = m.shared_trip_stops || [];
    const cardId = `share-msg-${m.id}`;
    pendingTripShares.set(cardId, { tripId: m.shared_trip_id, tripName: m.shared_trip_name, stops });
    const card = tripShareCardHtml({
      cardId, stops, tripName: m.shared_trip_name, compact: true,
      onclick: `openSharedTrip('${cardId}')`,
    });
    pendingRichCards.set(cardId, card.slides);
    inner += card.html;
  }
  if(m.body) inner += `<div>${escapeHtml(m.body)}</div>`;

  let statusHtml = '', retryHtml = '', rowExtraClass = '';
  if(mine){
    if(m.__status === 'failed'){
      statusHtml = TICK_FAILED;
      retryHtml = `<span class="msg-retry" onclick="retryMessage('${m.id}')">Retry</span>`;
      rowExtraClass = ' failed';
    } else if(m.__status === 'pending'){
      statusHtml = TICK_CLOCK;
      rowExtraClass = ' pending';
    } else {
      statusHtml = (!msgIsGroup && m.id <= msgTheirLastReadId) ? TICK_READ : TICK_SENT;
    }
  }
  return `<div class="bubble-row ${mine?'me':'them'}${rowExtraClass}" data-id="${m.id}">
    <div class="bubble-col">
      <div class="bubble${mediaOnly?' media-only':''}${hasCard?' has-card':''}">${inner}</div>
      <div class="bubble-time">${timeAgo(m.created_at)}${mine?`<span class="bubble-status">${statusHtml}</span>`:''}${retryHtml}</div>
    </div>
  </div>`;
}
/* ---- Optimistic send: bubble appears immediately, before the POST resolves ---- */
function appendOptimisticBubble(m){
  m.__status = 'pending';
  msgOptimisticById.set(String(m.id), m);
  const el = document.getElementById('msgList');
  if(!el) return;
  const emptyState = document.getElementById('msgEmptyState');
  if(emptyState) emptyState.remove();
  const wrapper = document.createElement('div');
  wrapper.innerHTML = renderBubble(m);
  el.appendChild(wrapper.firstElementChild);
  el.scrollTop = el.scrollHeight;
  flushPendingRichCards();
}
function replaceBubbleRow(id, messageObj){
  const row = document.querySelector(`.bubble-row[data-id="${id}"]`);
  const wrapper = document.createElement('div');
  wrapper.innerHTML = renderBubble(messageObj);
  const newRow = wrapper.firstElementChild;
  if(row) row.replaceWith(newRow);
  else document.getElementById('msgList')?.appendChild(newRow);
  flushPendingRichCards();
}
function reconcileOptimisticBubble(tempId, sent){
  msgRenderedIds.add(sent.id);
  if(sent.id > msgLastId) msgLastId = sent.id;
  msgOptimisticById.delete(tempId);
  replaceBubbleRow(tempId, sent);
}
function markBubbleFailed(tempId, toastMessage){
  const m = msgOptimisticById.get(tempId);
  if(m){ m.__status = 'failed'; replaceBubbleRow(tempId, m); }
  showToast(toastMessage, 3000);
}
async function retryMessage(tempId){
  const m = msgOptimisticById.get(tempId);
  if(!m) return;
  if(!msgIsGroup && !msgOtherUser) return;
  if(msgIsGroup && !msgGroup) return;
  m.__status = 'pending';
  replaceBubbleRow(tempId, m);
  const otherId = msgIsGroup ? msgGroup.id : msgOtherUser.id;
  try{
    const sent = await apiPost('/api/messages', {
      to_user_id: msgIsGroup ? undefined : otherId,
      group_id: msgIsGroup ? otherId : undefined,
      body: m.body || '',
      media_data: m.media_data || undefined,
      media_type: m.media_type || undefined,
    });
    const stillOpen = msgIsGroup ? (msgGroup && msgGroup.id === otherId) : (msgOtherUser && msgOtherUser.id === otherId);
    if(stillOpen) reconcileOptimisticBubble(tempId, sent);
  }catch(e){
    const stillOpen = msgIsGroup ? (msgGroup && msgGroup.id === otherId) : (msgOtherUser && msgOtherUser.id === otherId);
    if(stillOpen) markBubbleFailed(tempId, (e.data && e.data.message) || 'Still failing — tap Retry to try again.');
  }
}
function toggleEmojiPicker(){
  const el = document.getElementById('emojiPicker');
  if(el) el.classList.toggle('active');
}
const EMOJI_SET = ['😀','😂','🥰','😍','😊','😘','😎','🤩','😅','😭','🥺','😡','👍','🙏','🔥','💯','❤️','✨','🎉','😴','🤔','😉','👏','🙌','🌊','⛰️','🌄','🍛','☕','🚗','📍','💚'];
// Must track MAX_VIDEO_BYTES in functions/api/messages.js.
const MSG_VIDEO_MAX_RAW_BYTES = 15 * 1024 * 1024;
function buildEmojiPicker(){
  const el = document.getElementById('emojiPicker');
  if(el) el.innerHTML = EMOJI_SET.map(e=>`<span onclick="insertEmoji('${e}')">${e}</span>`).join('');
}
function insertEmoji(e){
  const input = document.getElementById('msgTextInput');
  if(input){ input.value += e; input.focus(); }
}
async function onMsgFileSelected(evt){
  const file = evt.target.files[0];
  if(!file) return;
  const isVideo = file.type.startsWith('video/');
  const preview = document.getElementById('msgAttachPreview');
  if(preview) preview.innerHTML = `<div class="msg-attach-preview">${isVideo ? 'Preparing…' : 'Compressing…'}</div>`;
  try{
    let data;
    if(isVideo){
      // Same reasoning as reel uploads: send the original file rather than
      // re-encoding it client-side, so audio survives and there's no
      // real-time compression delay. Duration isn't capped for a DM clip,
      // but size still is (checked here so a much-too-big video fails fast
      // with a clear reason instead of a slow doomed upload).
      if(file.size > MSG_VIDEO_MAX_RAW_BYTES){
        throw new Error(`That clip is too large (${Math.round(file.size/1024/1024)}MB) — try a shorter one.`);
      }
      data = await fileToDataURI(file);
    } else {
      data = await fileToCompressedDataURI(file, 1000, 0.72);
    }
    msgAttachment = { data, type: isVideo ? 'video' : 'photo' };
    if(preview) preview.innerHTML = `
      <div class="msg-attach-preview">
        ${isVideo ? `<video src="${data}" muted></video>` : `<img src="${data}">`}
        <span>${isVideo?'Video':'Photo'} ready to send</span>
        <span class="msg-attach-cancel" onclick="cancelMsgAttachment()">Remove</span>
      </div>`;
  }catch(e){
    if(preview) preview.innerHTML = '';
    showToast((e instanceof Error && e.message) || 'Could not process that file.', 3000);
  }
  evt.target.value = '';
}
function cancelMsgAttachment(){ msgAttachment = null; const el = document.getElementById('msgAttachPreview'); if(el) el.innerHTML = ''; }
// Renders the sender's own bubble instantly and never blocks the composer
// on the network — the POST happens in the background and just reconciles
// (success) or flags a Retry (failure) once it settles.
async function sendMessage(){
  const input = document.getElementById('msgTextInput');
  const text = input.value.trim();
  const attachment = msgAttachment;
  if(!text && !attachment) return;
  if(!msgIsGroup && !msgOtherUser) return;
  if(msgIsGroup && !msgGroup) return;
  const otherId = msgIsGroup ? msgGroup.id : msgOtherUser.id;
  const tempId = 'tmp' + (++msgTempCounter) + '_' + Date.now();
  const optimisticMsg = {
    id: tempId, sender_id: currentUser.id, body: text,
    sender_handle: currentUser.handle, sender_display_name: currentUser.display_name, sender_avatar_url: currentUser.avatar_url,
    media_data: attachment ? attachment.data : null,
    media_type: attachment ? attachment.type : null,
    shared_place_id: null, shared_post_id: null,
    created_at: new Date().toISOString(), read_at: null,
  };

  // Clear + refocus the composer right away — don't wait on the network.
  input.value = '';
  cancelMsgAttachment();
  input.focus();
  appendOptimisticBubble(optimisticMsg);

  try{
    const sent = await apiPost('/api/messages', {
      to_user_id: msgIsGroup ? undefined : otherId,
      group_id: msgIsGroup ? otherId : undefined,
      body: text,
      media_data: attachment ? attachment.data : undefined,
      media_type: attachment ? attachment.type : undefined,
    });
    const stillOpen = msgIsGroup ? (msgGroup && msgGroup.id === otherId) : (msgOtherUser && msgOtherUser.id === otherId);
    if(stillOpen) reconcileOptimisticBubble(tempId, sent);
    loadInbox(); // background-refresh the inbox preview/unread count, not the open thread
  }catch(e){
    const stillOpen = msgIsGroup ? (msgGroup && msgGroup.id === otherId) : (msgOtherUser && msgOtherUser.id === otherId);
    if(stillOpen) markBubbleFailed(tempId, (e.data && e.data.message) || 'Message failed to send — tap Retry.');
  }
}
// Incremental catch-up: only ever asks the server for messages newer than
// the last one we've already rendered (since_id), and appends just those —
// no full-thread re-render, so scroll position and any playing video are
// left alone. Called by the low-frequency fallback poll AND by the instant
// push-triggered refetch when a message arrives while the thread is open.
async function pollThreadOnce(threadId){
  if(msgIsGroup){
    if(!msgGroup || msgGroup.id !== threadId) return;
    try{
      const data = await apiGet(`/api/messages?group_id=${threadId}&since_id=${msgLastId}`);
      appendThreadMessages(data.messages);
    }catch(e){}
    return;
  }
  if(!msgOtherUser || msgOtherUser.id !== threadId) return;
  try{
    const data = await apiGet(`/api/messages?with=${threadId}&since_id=${msgLastId}`);
    msgTheirLastReadId = data.their_last_read_id || 0;
    appendThreadMessages(data.messages);
    updateReadTicks();
  }catch(e){}
}
function startMsgPoll(threadId){
  stopMsgPoll();
  msgPollTimer = setInterval(()=>{
    if(msgView !== 'thread') return;
    const activeId = msgIsGroup ? (msgGroup && msgGroup.id) : (msgOtherUser && msgOtherUser.id);
    if(activeId !== threadId) return;
    if(document.hidden) return; // paused while backgrounded — see visibilitychange handler below
    pollThreadOnce(threadId);
    pollTypingOnce(threadId);
  }, MSG_POLL_MS);
}
function stopMsgPoll(){ if(msgPollTimer){ clearInterval(msgPollTimer); msgPollTimer = null; } }


// Same idea as the thread poll above, but for the conversation list itself
// — new incoming chats, updated previews/timestamps, unread counts — so the
// inbox stays live while it's what's on screen, not just the open thread.
let msgInboxPollTimer = null;
function startInboxPoll(){
  stopInboxPoll();
  msgInboxPollTimer = setInterval(()=>{
    if(msgView !== 'inbox' || !isModalOpen('messages')) return;
    if(document.hidden) return;
    loadInbox();
  }, MSG_INBOX_POLL_MS);
}
function stopInboxPoll(){ if(msgInboxPollTimer){ clearInterval(msgInboxPollTimer); msgInboxPollTimer = null; } }
// Best-effort "I'm looking at this thread right now" signal to the service
// worker (see public/sw.js) so an incoming push can skip the OS notification
// and just nudge this tab to refetch instead — purely an optimization, the
// low-frequency poll above and the normal notification are always there as
// a fallback if the service worker doesn't have this (e.g. after a restart).
function notifyActiveThread(otherUserId){
  if('serviceWorker' in navigator && navigator.serviceWorker.controller){
    navigator.serviceWorker.controller.postMessage({ type: 'thikana-active-thread', otherUserId: otherUserId || null });
  }
}
// Pause/resume the fallback poll (and the active-thread signal) with tab
// visibility, and catch up immediately on resume in case a push was missed
// while backgrounded.
// Pause/resume the fallback poll(s) (and the active-thread signal) with tab
// visibility, and catch up immediately on resume in case something was
// missed while backgrounded.
document.addEventListener('visibilitychange', ()=>{
  if(document.hidden){
    stopMsgPoll();
    stopInboxPoll();
    stopFeedPoll();
    stopPlacesPoll();
    if(msgView === 'thread') notifyActiveThread(null);
    return;
  }
  if(msgView === 'thread' && msgOtherUser){
    notifyActiveThread(msgOtherUser.id);
    pollThreadOnce(msgOtherUser.id);
    startMsgPoll(msgOtherUser.id);
  } else if(msgView === 'inbox' && isModalOpen('messages')){
    loadInbox();
    startInboxPoll();
  }
  // Same "catch up immediately, then resume ticking" treatment for whichever
  // main tab is on screen when the app comes back to the foreground.
  const activeName = topView();
  if(activeName === 'feed'){ pollFeedOnce(); startFeedPoll(); }
  if(activeName === 'discover'){ pollPlacesOnce(); startPlacesPoll(); }
});
// ---- Connectivity: the app already works offline (cached places/feed via
// readLocalCache above, plus the service worker's own API cache as a second
// safety net — see public/sw.js). The moment the browser reports we're back
// online, don't leave whatever was on screen sitting on that offline
// snapshot until the next slow poll tick: tell the service worker to drop
// its cached API responses, then immediately re-fetch places + feed (and
// badges, if logged in) so the screen is fully live again right away.
window.addEventListener('online', () => {
  if('serviceWorker' in navigator && navigator.serviceWorker.controller){
    navigator.serviceWorker.controller.postMessage({ type: 'thikana-clear-api-cache' });
  }
  showToast('Back online — refreshing…', 2000);
  loadPlaces();
  loadFeed();
  if(currentUser){ refreshUnreadBadge(); refreshNotifBadge(); }
  flushOfflineQueue().then(({synced, failed})=>{
    if(synced) showToast(`Synced ${synced} queued action${synced>1?'s':''}.`, 2400);
    if(failed) showToast(`${failed} queued action${failed>1?'s':''} couldn't be completed and ${failed>1?'were':'was'} dropped.`, 3400);
  });
});
window.addEventListener('offline', () => {
  showToast("You're offline — showing saved content", 3000);
});
// Instant delivery: the service worker relays a message here the moment a
// push for an open thread arrives, so we refetch right away instead of
// waiting for the next poll tick. See the 'push' handler in public/sw.js.
if('serviceWorker' in navigator){
  navigator.serviceWorker.addEventListener('message', (event)=>{
    const msg = event.data || {};
    if(msg.type !== 'thikana-dm') return;
    if(msgView === 'thread' && msgOtherUser && msgOtherUser.id === msg.from_user_id){
      pollThreadOnce(msg.from_user_id);
    }
    if(document.getElementById('messagesOverlay')?.classList.contains('active')) loadInbox();
    else refreshUnreadBadge();
  });
}
let internalBadgeCounts = { messages: 0, notifs: 0, vibes: 0, dost: 0 };
function syncBadgesToNative(){
  if(window.AndroidNativeAuth && window.AndroidNativeAuth.updateBadgeCounts){
    window.AndroidNativeAuth.updateBadgeCounts(
      internalBadgeCounts.messages,
      internalBadgeCounts.notifs,
      internalBadgeCounts.vibes,
      internalBadgeCounts.dost
    );
  }
}
function updateVibesBadge(count){
  internalBadgeCounts.vibes = Math.max(0, count || 0);
  document.querySelectorAll('.vibes-badge').forEach(el=>{
    if(internalBadgeCounts.vibes > 0){ el.textContent = internalBadgeCounts.vibes > 99 ? '99+' : internalBadgeCounts.vibes; el.style.display = 'flex'; }
    else { el.style.display = 'none'; }
  });
  syncBadgesToNative();
}
function updateDostBadge(count){
  internalBadgeCounts.dost = Math.max(0, count || 0);
  document.querySelectorAll('.dost-badge').forEach(el=>{
    if(internalBadgeCounts.dost > 0){ el.textContent = internalBadgeCounts.dost > 99 ? '99+' : internalBadgeCounts.dost; el.style.display = 'flex'; }
    else { el.style.display = 'none'; }
  });
  syncBadgesToNative();
}
async function refreshDostBadge(){
  if(!currentUser){ updateDostBadge(0); return; }
  try{
    const { users } = await apiGet('/api/users?suggested=1');
    if(Array.isArray(users)){
      const unFollowed = users.filter(u => !u.is_following);
      updateDostBadge(unFollowed.length);
    }
  }catch(e){}
}
function updateMsgBadge(count){
  internalBadgeCounts.messages = Math.max(0, count || 0);
  document.querySelectorAll('.msg-badge').forEach(el=>{
    if(count > 0){ el.textContent = count > 99 ? '99+' : count; el.style.display = 'flex'; }
    else { el.style.display = 'none'; }
  });
  syncBadgesToNative();
}
async function refreshUnreadBadge(){
  if(!currentUser){ updateMsgBadge(0); return; }
  try{
    const { total_unread } = await apiGet('/api/messages');
    updateMsgBadge(total_unread);
    if(window.AndroidNativeAuth && window.AndroidNativeAuth.syncNotificationsNow) {
      window.AndroidNativeAuth.syncNotificationsNow();
    }
  }catch(e){}
}

/* ---------------- Share sheet (share a thikana / post into a DM) ---------------- */
function openShareSheet(target){
  if(target.post_id != null && target.post_id < 0){ showToast("This is a demo post — nothing to share yet.", 2500); return; }
  requireAuth(()=>{
    shareTarget = target;
    document.getElementById('shareOverlay').classList.add('active');
    pushUIModal('share');
    renderShareSheetShell();
    loadShareRecents();
    // Start rendering the WhatsApp/social preview card now rather than on
    // tap: by the time a link is actually sent this has usually finished
    // uploading, and if it hasn't, the share still goes out — the preview
    // just falls back to the plain cover photo.
    const cardPlaceId = target.place_id != null ? target.place_id
      : (target.post_id != null ? (POSTS.find(x=>x.id===target.post_id) || {}).place_id : null);
    if(cardPlaceId != null) ensureShareCard('place', cardPlaceId, async ()=>shareCardSpecForPlace(cardPlaceId));
  });
}
function hideShareSheetUI(){
  document.getElementById('shareOverlay').classList.remove('active');
  stopRichCarousel('shareSheetCard');
}
function closeShareSheet(){ closeUIModal('share', hideShareSheetUI); }
// Resolves whatever's being shared (place or post) down to the display
// info the rich card + external-share text need: a name, category, coords
// (for the map tile), a fallback cover image, and the full media gallery
// when we have it locally.
function resolveShareInfo(target){
  const info = { name:'A thikana', category:'', lat:null, lng:null, cover:null, isPost: target.post_id != null };
  if(target.place_id != null){
    const p = PLACES.find(x=>x.id===target.place_id);
    if(p){
      info.name = p.name; info.lat = p.lat; info.lng = p.lng; info.cover = p.img;
      info.category = (CATS[p.cat] && CATS[p.cat].label) || '';
    }
  } else if(target.post_id != null){
    const post = POSTS.find(x=>x.id===target.post_id);
    if(post){
      info.name = post.place || 'A vibe'; info.cover = post.img; info.caption = post.cap;
      const pl = PLACES.find(x=>x.id===post.place_id);
      if(pl){ info.lat = pl.lat; info.lng = pl.lng; info.category = (CATS[pl.cat] && CATS[pl.cat].label) || ''; }
    }
  }
  return info;
}
function renderShareSheetShell(){
  const info = resolveShareInfo(shareTarget);
  const slides = buildShareSlides(shareTarget);
  const cardHtml = richShareCardHtml({
    cardId: 'shareSheetCard', slides, coverFallback: info.cover,
    name: info.name, category: info.category, lat: info.lat, lng: info.lng,
  });
  document.getElementById('shareBody').innerHTML = `
    <div class="detail-close" onclick="closeShareSheet()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;">Share this ${info.isPost ? 'vibe' : 'thikana'}</h2>
      ${cardHtml}
      <div class="share-external-row">
        <div class="share-ext-btn" onclick="shareToWhatsApp()">
          <svg width="17" height="17" viewBox="0 0 24 24" fill="currentColor"><path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38a9.9 9.9 0 004.74 1.2h.01c5.46 0 9.9-4.45 9.9-9.91C21.96 6.45 17.5 2 12.04 2zm5.8 14.16c-.24.68-1.4 1.32-1.93 1.4-.5.08-1.12.11-1.8-.11-.42-.13-.95-.31-1.64-.6-2.88-1.24-4.76-4.14-4.9-4.33-.14-.19-1.17-1.56-1.17-2.98 0-1.42.74-2.11 1-2.4.26-.29.57-.36.76-.36.19 0 .38 0 .55.01.18.01.41-.07.64.49.24.58.81 2 .88 2.14.07.14.12.31.02.5-.09.19-.14.3-.28.46-.14.16-.29.36-.42.48-.14.13-.28.28-.12.55.16.28.72 1.19 1.55 1.93 1.06.95 1.96 1.24 2.24 1.38.28.14.44.12.6-.07.16-.19.68-.79.87-1.06.18-.28.36-.23.61-.14.24.09 1.55.73 1.82.86.26.14.44.2.5.31.06.12.06.68-.18 1.36z"/></svg>
          <span>WhatsApp</span>
        </div>
        <div class="share-ext-btn" onclick="shareExternally()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>
          <span>Share via…</span>
        </div>
        <div class="share-ext-btn ghost" onclick="copyShareLink()">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1"/></svg>
          <span>Copy link</span>
        </div>
      </div>
      <div class="field-label" style="margin-top:18px;">Send in Mera Thikaana</div>
      <input type="text" id="shareSearchInput" placeholder="Search people…" oninput="onShareSearchInput()" style="width:100%;border:1px solid var(--line);border-radius:100px;padding:10px 15px;font-family:'Work Sans',sans-serif;font-size:13px;background:var(--paper);margin:8px 0 10px;">
      <div class="share-sheet-list" id="shareList"><div class="msg-empty">Loading…</div></div>
    </div>`;
  initRichCarousel('shareSheetCard', slides);
}
async function loadShareRecents(){
  try{
    const { conversations } = await apiGet('/api/messages');
    renderShareList(conversations.map(c=>c.other));
  }catch(e){
    const el = document.getElementById('shareList');
    if(el) el.innerHTML = `<div class="msg-empty">Message someone first to share with them, or search above.</div>`;
  }
}
function renderShareList(users){
  const el = document.getElementById('shareList');
  if(!el) return;
  if(!users.length){ el.innerHTML = `<div class="msg-empty">No one yet — search above to find someone.</div>`; return; }
  el.innerHTML = users.map(u=>`
    <div class="msg-inbox-row" onclick="sendShareTo(${u.id})">
      <div class="m-avatar" ${u.avatar_url?`style="background-image:url('${u.avatar_url}');background-size:cover;"`:''}>${!u.avatar_url?avatarInitial(u):''}</div>
      <div class="msg-inbox-info"><div class="msg-inbox-name">${escapeHtml(u.display_name || u.handle)}</div><div class="msg-inbox-preview">@${u.handle}</div></div>
    </div>`).join('');
}
function onShareSearchInput(){
  clearTimeout(shareSearchTimer);
  const q = document.getElementById('shareSearchInput').value.trim();
  if(!q){ loadShareRecents(); return; }
  shareSearchTimer = setTimeout(async ()=>{
    try{
      const { users } = await apiGet(`/api/users?search=${encodeURIComponent(q)}`);
      renderShareList(users);
    }catch(e){}
  }, 300);
}
async function sendShareTo(userId){
  try{
    await apiPost('/api/messages', {
      to_user_id: userId,
      shared_place_id: shareTarget.place_id || undefined,
      shared_post_id: shareTarget.post_id || undefined,
    });
    showToast('Shared!', 2000);
    closeShareSheet();
  }catch(e){
    showToast((e.data && e.data.message) || "Couldn't share — try again.", 3000);
  }
}

/* ---------------- Sharing outside the app (WhatsApp / native share / copy link) ----------------
   Every shared thikana/post gets a real, stable link — /share/place/:id or
   /share/post/:id (see functions/share/*) — which is server-rendered with
   proper Open Graph tags so pasting it into WhatsApp (or iMessage, Slack,
   Twitter/X…) shows a real title + photo instead of a bare URL, then hands
   a real visitor straight into the app at that thikana/post. */
function shareDeepLink(target){
  const base = window.location.origin;
  if(!target) return base;
  if(target.post_id != null) return `${base}/share/post/${target.post_id}`;
  return `${base}/share/place/${target.place_id}`;
}
function shareExternalText(target, info){
  if(!info) info = resolveShareInfo(target);
  if(info.isPost) return `${info.name} — a vibe on Mera Thikaana`;
  return `${info.name} — check it out on Mera Thikaana`;
}
// The one-tap "always works" path: hands off to wa.me, which opens the
// WhatsApp app on a phone or web.whatsapp.com on desktop, with the message
// pre-filled — no dependency on the browser supporting Web Share.
function shareToWhatsApp(){
  if(!shareTarget) return;
  const url = shareDeepLink(shareTarget);
  const text = shareExternalText(shareTarget);
  if (window.AndroidNativeAuth && window.AndroidNativeAuth.shareToWhatsApp) {
    window.AndroidNativeAuth.shareToWhatsApp(text, url);
    return;
  }
  window.open(`https://wa.me/?text=${encodeURIComponent(text + ' ' + url)}`, '_blank', 'noopener');
}
// The general "share sheet" path — on a phone this surfaces every share
// target the OS knows about (WhatsApp included) via one native picker;
// on a desktop browser without Web Share support, falls back to copying
// the link instead of silently doing nothing.
async function shareExternally(){
  if(!shareTarget) return;
  const url = shareDeepLink(shareTarget);
  const info = resolveShareInfo(shareTarget);
  const text = shareExternalText(shareTarget, info);
  if(navigator.share){
    try{
      await navigator.share({ title: info.name, text, url });
      return;
    }catch(e){
      if(e && e.name === 'AbortError') return; // user cancelled the native sheet — not a failure
    }
  }
  copyShareLink();
}
function copyShareLink(){
  if(!shareTarget) return;
  copyTextToClipboard(shareDeepLink(shareTarget));
}

/* ---------------- Edit profile (avatar + display name) ---------------- */
function openEditProfile(){
  requireAuth(()=>{
    document.getElementById('editProfileOverlay').classList.add('active');
    pushUIModal('editprofile');
    editAvatarPending = null;
    renderEditProfileBody();
  });
}
function hideEditProfileUI(){ document.getElementById('editProfileOverlay').classList.remove('active'); }
function closeEditProfile(){ closeUIModal('editprofile', hideEditProfileUI); }
function removeEditAvatarPhoto(){ editAvatarPending = ''; renderEditProfileBody(); }
function renderEditProfileBody(){
  const u = currentUser;
  const avatar = editAvatarPending !== null ? editAvatarPending : u.avatar_url;
  document.getElementById('editProfileBody').innerHTML = `
    <div class="detail-close" onclick="closeEditProfile()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body" style="padding-top:22px;">
      <h2 style="font-size:17px;text-align:center;">Edit profile</h2>
      <div class="editprof-avatar-wrap">
        <div class="p-avatar" ${avatar?`style="background-image:url('${avatar}');background-size:cover;"`:''}>${!avatar?avatarInitial(u):''}</div>
        <div class="editprof-cam" onclick="document.getElementById('avatarFileInput').click()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/></svg>
        </div>
        <input type="file" id="avatarFileInput" accept="image/*" style="display:none" onchange="onAvatarFileSelected(event)">
      </div>
      ${avatar ? `<div class="editprof-remove" onclick="removeEditAvatarPhoto();">Remove photo</div>` : ''}
      <div class="field-label">Display name</div>
      <input type="text" id="editDisplayName" maxlength="60">
      <button class="btn-primary" style="margin-top:14px;" onclick="saveProfileEdits()">Save changes</button>
      <div id="editProfMsg" style="margin-top:10px;font-size:12px;color:var(--ink-soft);text-align:center;"></div>
    </div>`;
  document.getElementById('editDisplayName').value = u.display_name || u.handle;
}
async function onAvatarFileSelected(evt){
  const file = evt.target.files[0];
  if(!file) return;
  try{
    editAvatarPending = await fileToCompressedDataURI(file, 500, 0.78);
    renderEditProfileBody();
  }catch(e){
    showToast('Could not process that photo.', 2500);
  }
  evt.target.value = '';
}
async function saveProfileEdits(){
  const name = document.getElementById('editDisplayName').value.trim();
  if(!name){ document.getElementById('editProfMsg').textContent = 'Display name cannot be empty.'; return; }
  const payload = { display_name: name };
  if(editAvatarPending !== null) payload.avatar_url = editAvatarPending;
  try{
    const { user } = await apiPatch('/api/users', payload);
    setCurrentUser(user);
    editAvatarPending = null;
    closeEditProfile();
    renderProfileView();
    renderWhoBar();
  }catch(e){
    document.getElementById('editProfMsg').textContent = (e.data && e.data.message) || 'Could not save — try again.';
  }
}

/* ---------------- Followers / Following lists ---------------- */
let followListCache = { userId: null, kind: null };
function openOwnFollowList(kind){ if(currentUser) openFollowList(currentUser.id, kind); }
function openFollowList(userId, kind){
  followListCache = { userId, kind };
  document.getElementById('followListOverlay').classList.add('active');
  pushUIModal('followlist');
  renderFollowListShell();
  loadFollowList();
}
function hideFollowListUI(){ document.getElementById('followListOverlay').classList.remove('active'); }
function closeFollowList(){ closeUIModal('followlist', hideFollowListUI); }
function renderFollowListShell(){
  document.getElementById('followListBody').innerHTML = `
    <div class="detail-close" onclick="closeFollowList()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;text-transform:capitalize;">${followListCache.kind}</h2>
      <div id="followListItems"><div class="msg-empty">Loading…</div></div>
    </div>`;
}
async function loadFollowList(){
  const { userId, kind } = followListCache;
  try{
    const { users } = await apiGet(`/api/follows?user_id=${userId}&list=${kind}`);
    const el = document.getElementById('followListItems');
    if(!el) return;
    if(!users.length){
      el.innerHTML = `<div class="msg-empty">${kind === 'followers' ? 'No followers yet.' : 'Not following anyone yet.'}</div>`;
      return;
    }
    el.innerHTML = users.map(u => `
      <div class="msg-inbox-row">
        <div class="m-avatar" style="cursor:pointer;${u.avatar_url?`background-image:url('${u.avatar_url}');background-size:cover;`:''}" onclick="closeFollowList();setTimeout(()=>openUserProfile(${u.id}),300);">${!u.avatar_url?avatarInitial(u):''}</div>
        <div class="msg-inbox-info" style="cursor:pointer;" onclick="closeFollowList();setTimeout(()=>openUserProfile(${u.id}),300);">
          <div class="msg-inbox-name">${escapeHtml(u.display_name || u.handle)}</div>
          <div class="msg-inbox-preview">@${u.handle}</div>
        </div>
        ${u.is_self ? '' : `<span class="followpill${u.is_following?' following':''}" onclick="toggleFollow(${u.id}, this)">${u.is_following?'Following':'Follow'}</span>`}
      </div>`).join('');
  }catch(e){
    const el = document.getElementById('followListItems');
    if(el) el.innerHTML = `<div class="msg-empty">Couldn't load this list right now.</div>`;
  }
}
async function loadOwnFollowCounts(){
  if(!currentUser) return;
  try{
    const { followers_count, following_count } = await apiGet(`/api/follows?user_id=${currentUser.id}`);
    const fEl = document.getElementById('pStatFollowers');
    const gEl = document.getElementById('pStatFollowing');
    if(fEl) fEl.textContent = followers_count;
    if(gEl) gEl.textContent = following_count;
  }catch(e){}
}

/* ---------------- Suggested follows (Task 2: post-signup onboarding) ----------------
   Shown once right after a fresh signup so nobody
   lands on an empty Following feed. Entirely skippable — the header "Skip"
   and the footer "Done" both just close it, and it never re-opens itself;
   it's only ever triggered right after signup, not on every login. */
function openSuggestedFollows(){
  document.getElementById('suggestedFollowsOverlay').classList.add('active');
  pushUIModal('suggestedfollows');
  document.getElementById('suggestedFollowsBody').innerHTML = `
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;">Follow a few people to start</h2>
      <p style="color:var(--ink-soft);font-size:12.5px;margin:2px 0 14px;">Your Vibes feed shows posts from people you follow — here are a few active accounts to get started. You can skip this.</p>
      <div id="suggestedFollowsItems"><div class="msg-empty">Loading…</div></div>
    </div>
    <div style="padding:12px 18px 18px;border-top:1px solid var(--paper-dim);">
      <button class="btn-primary" style="width:100%;" onclick="closeSuggestedFollows()">Done</button>
    </div>`;
  loadSuggestedFollows();
}
function hideSuggestedFollowsUI(){ document.getElementById('suggestedFollowsOverlay').classList.remove('active'); }
function closeSuggestedFollows(){ closeUIModal('suggestedfollows', hideSuggestedFollowsUI); }
async function loadSuggestedFollows(){
  const el = document.getElementById('suggestedFollowsItems');
  if(!el) return;
  try{
    const { users } = await apiGet('/api/users?suggested=1');
    if(!el.isConnected) return; // sheet may have been closed/skipped while this was in flight
    if(!users || !users.length){
      el.innerHTML = `<div class="msg-empty">No suggestions yet — check back once more people have joined.</div>`;
      return;
    }
    el.innerHTML = users.map(u => `
      <div class="msg-inbox-row">
        <div class="m-avatar" style="cursor:pointer;${u.avatar_url?`background-image:url('${u.avatar_url}');background-size:cover;`:''}" onclick="closeSuggestedFollows();setTimeout(()=>openUserProfile(${u.id}),300);">${!u.avatar_url?avatarInitial(u):''}</div>
        <div class="msg-inbox-info" style="cursor:pointer;" onclick="closeSuggestedFollows();setTimeout(()=>openUserProfile(${u.id}),300);">
          <div class="msg-inbox-name">${escapeHtml(u.display_name || u.handle)}</div>
          <div class="msg-inbox-preview">@${u.handle}</div>
        </div>
        <span class="followpill" onclick="toggleFollow(${u.id}, this)">Follow</span>
      </div>`).join('');
  }catch(e){
    el.innerHTML = `<div class="msg-empty">Couldn't load suggestions right now.</div>`;
  }
}

/* ---------------- Find Dost (find people to follow) ---------------- */
let findDostSearchTimer = null;
function openFindDost(){
  requireAuth(()=>{
    document.getElementById('findDostOverlay').classList.add('active');
    pushUIModal('finddost');
    document.getElementById('findDostBody').innerHTML = `
      <div class="detail-close" onclick="closeFindDost()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
      </div>
      <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
        <h2 style="font-size:17px;">Find Dost</h2>
        <input id="findDostInput" type="text" placeholder="Search by name or @handle…" autocomplete="off" oninput="onFindDostInput()" style="width:100%;margin-bottom:12px;">
        <div id="findDostItems"><div class="msg-empty">Loading people…</div></div>
      </div>`;
    document.getElementById('findDostInput').focus();
    // Show a starter list of accounts right away instead of making people
    // type something before they see anyone to follow.
    loadFindDostUsers('');
  });
}
function hideFindDostUI(){ document.getElementById('findDostOverlay').classList.remove('active'); }
function closeFindDost(){ closeUIModal('finddost', hideFindDostUI); }
function onFindDostInput(){
  clearTimeout(findDostSearchTimer);
  const q = document.getElementById('findDostInput').value.trim();
  findDostSearchTimer = setTimeout(()=> loadFindDostUsers(q), 300);
}
async function loadFindDostUsers(q){
  const el = document.getElementById('findDostItems');
  if(!el) return;
  try{
    const { users } = await apiGet(`/api/users?search=${encodeURIComponent(q)}`);
    if(!el.isConnected) return; // overlay may have been closed while this was in flight
    if(!users.length){ el.innerHTML = `<div class="msg-empty">${q ? 'No one found.' : 'No accounts to show yet.'}</div>`; return; }
    el.innerHTML = users.map(u => `
      <div class="msg-inbox-row">
        <div class="m-avatar" style="cursor:pointer;${u.avatar_url?`background-image:url('${u.avatar_url}');background-size:cover;`:''}" onclick="closeFindDost();setTimeout(()=>openUserProfile(${u.id}),300);">${!u.avatar_url?avatarInitial(u):''}</div>
        <div class="msg-inbox-info" style="cursor:pointer;" onclick="closeFindDost();setTimeout(()=>openUserProfile(${u.id}),300);">
          <div class="msg-inbox-name">${escapeHtml(u.display_name || u.handle)}</div>
          <div class="msg-inbox-preview">@${u.handle}</div>
        </div>
        ${u.is_self ? '' : `<span class="followpill${u.is_following?' following':''}" onclick="toggleFollow(${u.id}, this)">${u.is_following?'Following':'Follow'}</span>`}
      </div>`).join('');
  }catch(e){
    if(el.isConnected) el.innerHTML = `<div class="msg-empty">Couldn't load people right now.</div>`;
  }
}

/* ---------------- Discover carousel: minimize / maximize the nearby-cards
   sheet ----------------
   Tapping the small button below "my location" shrinks the nearby-thikanas
   card row down to a slim strip that still shows each thumbnail, so you
   can glance at what's nearby without it covering half the map. Tapping it
   again restores the full card row. Only ever touches the card sheet —
   nothing else on the page changes. */
let carouselMinimized = false;
function setCarouselMinimized(min){
  carouselMinimized = min;
  const wrap = document.getElementById('discoverCarouselWrap');
  if(wrap) wrap.classList.toggle('minimized', min);
  const btn = document.getElementById('cardsToggleBtn');
  if(btn) btn.classList.toggle('is-min', min);
}
function toggleCarouselMinimized(){ setCarouselMinimized(!carouselMinimized); }

/* ---------------- Notifications (likes, follows, trending, unread messages) ---------------- */
function openNotifications(){
  requireAuth(()=>{
    document.getElementById('notifOverlay').classList.add('active');
    pushUIModal('notif');
    renderNotifShell();
    loadNotifications();
  });
}
function hideNotificationsUI(){ document.getElementById('notifOverlay').classList.remove('active'); }
function closeNotifications(){ closeUIModal('notif', hideNotificationsUI); }
function renderNotifShell(){
  document.getElementById('notifBody').innerHTML = `
    <div class="detail-close" onclick="closeNotifications()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;">Notifications</h2>
      <div id="notifList"><div class="msg-empty">Loading…</div></div>
    </div>`;
}
const NOTIF_ICONS = { like:'❤️', follow:'👋', trending:'🔥', message:'💬', comment:'💬' };
function notifText(n){
  const who = escapeHtml(n.actor ? (n.actor.display_name || n.actor.handle) : 'Someone');
  const placeName = escapeHtml(n.place_name || '');
  if(n.type === 'like') return `<b>${who}</b> liked your post${placeName ? ` at ${placeName}` : ''}`;
  if(n.type === 'follow') return `<b>${who}</b> started following you`;
  if(n.type === 'trending') return `Your thikana <b>${placeName}</b> is trending right now 🔥`;
  if(n.type === 'message') return `<b>${who}</b>: ${escapeHtml(n.preview || 'sent you a message')}`;
  if(n.type === 'comment') return `<b>${who}</b> commented on your post${placeName ? ` at ${placeName}` : ''}`;
  return '';
}
function notifTap(n){
  closeNotifications();
  if(n.type === 'like' || n.type === 'trending' || n.type === 'comment'){
    if(n.place_id) setTimeout(()=>jumpToPlace(n.place_id), 300);
  } else if(n.type === 'follow'){
    if(n.actor) setTimeout(()=>openUserProfile(n.actor.id), 300);
  } else if(n.type === 'message'){
    if(n.actor) setTimeout(()=>openThread(n.actor.id), 300);
  }
}
async function loadNotifications(){
  try{
    const { notifications, total_unread } = await apiGet('/api/notifications');
    updateNotifBadge(total_unread);
    const el = document.getElementById('notifList');
    if(!el) return;
    if(!notifications.length){
      el.innerHTML = `<div class="msg-empty">Nothing yet — likes, new followers, trending thikanas, and message alerts will show up here.</div>`;
      return;
    }
    window.__notifCache = {};
    notifications.forEach(n => window.__notifCache[n.id] = n);
    el.innerHTML = notifications.map(n => {
      const u = n.actor;
      const useAvatar = (n.type === 'follow' || n.type === 'message') && u;
      return `
      <div class="notif-row${n.read?'':' unread'}" onclick="notifTap(window.__notifCache['${n.id}'])">
        ${useAvatar
          ? `<div class="m-avatar" ${u.avatar_url?`style="background-image:url('${u.avatar_url}');background-size:cover;"`:''}>${!u.avatar_url?avatarInitial(u):''}</div>`
          : `<div class="notif-icon ${n.type}">${NOTIF_ICONS[n.type]||'🔔'}</div>`}
        <div class="msg-inbox-info">
          <div class="msg-inbox-name" style="font-weight:500;">${notifText(n)}</div>
          <div class="msg-inbox-time" style="margin-top:3px;">${timeAgo(n.created_at)}</div>
        </div>
      </div>`;
    }).join('');
  }catch(e){
    const el = document.getElementById('notifList');
    if(el) el.innerHTML = `<div class="msg-empty">Couldn't load notifications right now.</div>`;
  }
  try{ await apiPatch('/api/notifications', {}); }catch(e){} // mark like/follow/trending as read now that they've been seen
}
function updateNotifBadge(count){
  internalBadgeCounts.notifs = Math.max(0, count || 0);
  document.querySelectorAll('.notif-badge').forEach(el=>{
    if(count > 0){ el.textContent = count > 99 ? '99+' : count; el.style.display = 'flex'; }
    else { el.style.display = 'none'; }
  });
  syncBadgesToNative();
}
async function refreshNotifBadge(){
  if(!currentUser){ updateNotifBadge(0); return; }
  try{
    const { total_unread } = await apiGet('/api/notifications');
    updateNotifBadge(total_unread);
    if(window.AndroidNativeAuth && window.AndroidNativeAuth.syncNotificationsNow) {
      window.AndroidNativeAuth.syncNotificationsNow();
    }
  }catch(e){}
}

/* Swipe-to-change-tabs was intentionally removed — tabs switch strictly on
   tap now, per spec. (This does not affect the Reels feed's own vertical
   swipe between videos, which is a separate, unrelated touch handler.) */

/* ---------------- Tag a spot / Add a hidden gem: shared setup ---------------- */
function buildUploadSelect(){
  const sel = document.getElementById('uPlace');
  if(!sel) return;
  if(!PLACES.length){
    sel.innerHTML = `<option value="">No thikanas yet — add one in the admin panel first</option>`;
    sel.disabled = true;
    const geoText = document.getElementById('geoText');
    const box = document.getElementById('geoBox');
    const btn = document.getElementById('postBtn');
    if(geoText) geoText.textContent = 'Nothing to tag a post to yet — add a real spot in admin, then come back here.';
    if(box) box.className = 'geo-box geo-bad';
    if(btn) btn.disabled = true;
    return;
  }
  sel.disabled = false;
  // Every real spot added in admin shows up here automatically — this list
  // is just PLACES, the same data driving the map and grid.
  sel.innerHTML = PLACES.map(p=>`<option value="${p.id}">${escapeHtml(p.name)}</option>`).join('');
  sel.onchange = ()=>{ geoConfirmed=false; checkGeo(); };
  checkGeo();
}
const REEL_MAX_DURATION_SEC = 30;
const MAX_GALLERY_IMAGES = 6;
// Videos upload as the original file now (see prepareVideoUpload below) —
// no client-side re-encoding, so this is a raw-file cap, not a compressed
// one. Must track MAX_REEL_MEDIA_BYTES in functions/api/posts.js and
// MAX_VIDEO_BYTES in functions/api/places.js (both sized for the ~33%
// base64 overhead on top of this).
const REEL_MAX_RAW_BYTES = 45 * 1024 * 1024;

/* ---------------- Upload chooser: tap "+" asks which one before opening the page ---------------- */
function openUploadChooser(){
  document.getElementById('uploadChooserOverlay').classList.add('active');
  pushUIModal('uploadchooser');
}
function hideUploadChooserUI(){ document.getElementById('uploadChooserOverlay').classList.remove('active'); }
function closeUploadChooser(){ closeUIModal('uploadchooser', hideUploadChooserUI); }
// Each choice now opens its OWN page (view-tagspot / view-addgem) rather
// than a shared page with tabs — separate flows, separate back buttons,
// nothing left over from one bleeding into the other.
function chooseUploadMode(mode){
  closeUploadChooser();
  setTimeout(()=>{ showView(mode === 'gem' ? 'addgem' : 'tagspot'); }, 300);
}

/* ---------------- Shared media strip: used by both Tag a spot and Add a hidden gem ----------------
   One "+" tile picks photos and/or a video in a single go (accept accepts
   both, and the picker allows multi-select) — there's no separate
   photo-mode/video-mode switch to tap through first. Every added item gets
   a cross to remove it and can be tapped to view full-size. */
function mediaThumbMarkup(m, i, listName, coverIndex){
  const removeFn = listName === 'gem' ? 'removeGemMedia' : 'removeTagMedia';
  const inner = m.type === 'video'
    ? `<video src="${URL.createObjectURL(m.file)}" muted autoplay loop playsinline></video>`
    : '';
  const style = m.type === 'photo' ? ` style="background-image:url('${URL.createObjectURL(m.file)}')"` : '';
  return `<div class="u-thumb u-media-fade${m.type==='video'?' u-thumb-video':''}"${style} onclick="openMediaPreview('${listName}',${i})">
    ${inner}
    ${i===coverIndex ? '<span class="u-thumb-cover">Cover</span>' : ''}
    ${m.type==='video' ? '<span class="u-thumb-play">▶</span>' : ''}
    <div class="u-thumb-x" onclick="event.stopPropagation();${removeFn}(${i})" title="Remove">
      <svg width="9" height="9" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
  </div>`;
}
function openMediaPreview(listName, i){
  const item = (listName === 'gem' ? gemMediaItems : tagMediaItems)[i];
  if(!item) return;
  const body = document.getElementById('mediaPreviewBody');
  body.innerHTML = `<div class="detail-close" onclick="closeMediaPreview()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>` + (item.type === 'video'
    ? `<video src="${URL.createObjectURL(item.file)}" controls autoplay playsinline></video>`
    : `<img src="${URL.createObjectURL(item.file)}" alt="">`);
  document.getElementById('mediaPreviewOverlay').classList.add('active');
  pushUIModal('mediapreview');
}
function hideMediaPreviewUI(){
  document.getElementById('mediaPreviewOverlay').classList.remove('active');
  const body = document.getElementById('mediaPreviewBody');
  if(body) body.innerHTML = '';
}
function closeMediaPreview(){ closeUIModal('mediapreview', hideMediaPreviewUI); }

/* ---------------- Tag a spot: post a photo/flick to a thikana already on the map ---------------- */
// A single unified pick list — but a tagged post can still only ever be a
// photo gallery OR one flick, never both at once (that's a post_kind
// constraint on the backend, not a UI choice) — so picking a video always
// replaces any photos already added, and picking photos always replaces an
// already-added video. You just never have to declare which one up front.
let tagMediaItems = []; // [{type:'photo'|'video', file}]
function tagHasVideo(){ return tagMediaItems.some(m=>m.type==='video'); }
function pickTagMedia(){
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*,video/*';
  input.multiple = true;
  input.onchange = () => {
    const files = Array.from(input.files || []);
    if(!files.length) return;
    const imgs = files.filter(f=>f.type.startsWith('image/'));
    const vids = files.filter(f=>f.type.startsWith('video/'));
    if(vids.length){
      if(tagMediaItems.length) showToast("A post is either photos or one flick — this video replaces what you'd added.", 3200);
      tagMediaItems = [{ type:'video', file: vids[0] }];
      if(vids.length > 1) showToast('Only one flick per post — used the first clip.', 2600);
    } else if(imgs.length){
      if(tagHasVideo()){ tagMediaItems = []; showToast("A post is either photos or one flick — these photos replace your flick.", 3200); }
      const room = MAX_GALLERY_IMAGES - tagMediaItems.length;
      if(room <= 0){ showToast(`You can attach up to ${MAX_GALLERY_IMAGES} photos.`, 2500); return; }
      tagMediaItems.push(...imgs.slice(0, room).map(file=>({ type:'photo', file })));
      if(imgs.length > room) showToast(`Only added ${room} more — ${MAX_GALLERY_IMAGES} photos max per post.`, 3000);
    }
    renderTagMedia();
  };
  input.click();
}
function renderTagMedia(){
  const box = document.getElementById('uPhoto');
  const strip = document.getElementById('uThumbStrip');
  if(!box) return;
  box.classList.toggle('has-media', tagMediaItems.length > 0);
  box.classList.toggle('u-photo-reel', tagHasVideo());
  if(!tagMediaItems.length){
    box.style.backgroundImage = '';
    box.innerHTML = `<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="M21 16l-5.5-5.5L5 21"/></svg><span id="uPhotoLabel">Tap to add photos or a video</span>`;
  } else if(tagHasVideo()){
    box.style.backgroundImage = '';
    box.innerHTML = '';
    const vid = document.createElement('video');
    vid.src = URL.createObjectURL(tagMediaItems[0].file);
    vid.muted = true; vid.autoplay = true; vid.loop = true; vid.playsInline = true;
    vid.className = 'u-media-fade';
    box.appendChild(vid);
  } else {
    box.innerHTML = '';
    box.style.backgroundImage = `url('${URL.createObjectURL(tagMediaItems[0].file)}')`;
  }
  if(strip){
    if(!tagMediaItems.length){ strip.style.display = 'none'; strip.innerHTML = ''; }
    else {
      strip.style.display = 'flex';
      const coverIdx = tagMediaItems.findIndex(m=>m.type==='photo');
      strip.innerHTML = tagMediaItems.map((m,i)=>mediaThumbMarkup(m,i,'tag',coverIdx)).join('') +
        (!tagHasVideo() && tagMediaItems.length < MAX_GALLERY_IMAGES ? `<div class="u-thumb u-thumb-add" onclick="pickTagMedia()">+</div>` : '');
    }
  }
}
function removeTagMedia(i){
  tagMediaItems.splice(i,1);
  renderTagMedia();
}
/* ---- Stylish progress bar shown during compress + upload. `pct` is a
   0-100 number for determinate progress (e.g. "photo 2 of 5"), or null for
   the indeterminate sliding animation used while waiting on the network
   POST itself (no real byte-level progress available there). ---- */
function setUploadProgress(prefix, pct){
  const wrap = document.getElementById(prefix + 'UploadProgress');
  const bar = document.getElementById(prefix + 'UploadProgressBar');
  if(!wrap || !bar) return;
  wrap.style.display = 'block';
  if(pct == null){
    wrap.classList.add('indeterminate');
  } else {
    wrap.classList.remove('indeterminate');
    bar.style.width = Math.max(0, Math.min(100, pct)) + '%';
  }
}
function hideUploadProgress(prefix){
  const wrap = document.getElementById(prefix + 'UploadProgress');
  const bar = document.getElementById(prefix + 'UploadProgressBar');
  if(!wrap || !bar) return;
  wrap.classList.remove('indeterminate');
  wrap.style.display = 'none';
  bar.style.width = '0%';
}
// Clears the Tag-a-spot page back to its empty state — after a successful
// post, and also whenever this page is freshly opened (see
// renderActiveView below) or a draft is discarded, so nothing picked in an
// abandoned attempt ever lingers into the next visit.
function resetTagSpotForm(){
  tagMediaItems = [];
  renderTagMedia();
  const statusEl = document.getElementById('uCompressStatus');
  if(statusEl) statusEl.textContent = '';
  geoConfirmed = false;
  const ta = document.querySelector('#view-tagspot textarea');
  if(ta) ta.value = '';
  const remoteEl = document.getElementById('remoteTag');
  if(remoteEl) remoteEl.checked = false;
}
// Reels used to be re-encoded client-side in real time via canvas +
// MediaRecorder — that took as long as the clip itself to run, routinely
// dropped audio (canvas.captureStream() only carries the video track unless
// the original audio track is explicitly re-attached, which this never
// did), and could fail/time out on a flaky connection even after the file
// had already finished uploading. None of that is worth it: we now just
// upload the original file as-is. Audio is untouched because nothing
// touches the file at all — the only client-side work left is a quick
// metadata check that the clip isn't longer than the limit, which is
// instant (reads the video's duration, doesn't decode/re-render any frames).
function checkVideoDuration(file){
  return new Promise((resolve, reject) => {
    const video = document.createElement('video');
    video.preload = 'metadata';
    video.src = URL.createObjectURL(file);
    video.onloadedmetadata = () => {
      URL.revokeObjectURL(video.src);
      if(video.duration > REEL_MAX_DURATION_SEC){
        reject(new Error(`Keep flicks under ${REEL_MAX_DURATION_SEC}s — this clip is ${Math.round(video.duration)}s.`));
        return;
      }
      resolve();
    };
    video.onerror = () => { URL.revokeObjectURL(video.src); reject(new Error('Could not read that video file.')); };
  });
}
// Validates duration, then reads the raw file straight to a data URI — no
// re-encoding step. `onProgress` is invoked with real byte-level progress
// (0-100) read via FileReader, since a large raw file is exactly the case
// where a stalled-looking bar is most likely to make someone think the app
// is frozen.
async function prepareVideoUpload(file, { onProgress } = {}){
  if(file.size > REEL_MAX_RAW_BYTES){
    throw Object.assign(new Error(`That clip is too large (${Math.round(file.size/1024/1024)}MB) — try a shorter one.`), { status: 413 });
  }
  await checkVideoDuration(file);
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onprogress = e => { if(onProgress && e.lengthComputable) onProgress((e.loaded / e.total) * 100); };
    reader.onload = () => { if(onProgress) onProgress(100); resolve(reader.result); };
    reader.onerror = () => reject(new Error('Could not read that video file.'));
    reader.readAsDataURL(file);
  });
}
function checkGeo(){
  const geoText = document.getElementById('geoText');
  const box = document.getElementById('geoBox');
  const btn = document.getElementById('postBtn');
  if(!geoText || !box || !btn) return;
  if(!PLACES.length || !document.getElementById('uPlace').value) return; // buildUploadSelect() already shows the empty-state message
  if(currentUser && currentUser.is_official){
    const place = PLACES.find(p=>p.id==document.getElementById('uPlace').value);
    geoConfirmed = true;
    lastLat = null; lastLng = null; // backend defaults to the spot's own coordinates
    geoText.textContent = `Posting as the official Mera Thikaana account — no location check needed for ${place ? place.name : 'this thikana'}.`;
    box.className = 'geo-box geo-ok';
    btn.disabled = false;
    return;
  }
  const remoteEl = document.getElementById('remoteTag');
  if(remoteEl && remoteEl.checked){
    const place = PLACES.find(p=>p.id==document.getElementById('uPlace').value);
    geoConfirmed = true;
    lastLat = null; lastLng = null; // tagged remotely — no device GPS reading sent
    geoText.textContent = `Tagging ${place ? place.name : 'this thikana'} without a location check.`;
    box.className = 'geo-box geo-ok';
    btn.disabled = false;
    return;
  }
  geoText.textContent = 'Checking your location against this thikana…';
  box.className = 'geo-box';
  btn.disabled = true;
  if(!navigator.geolocation){
    geoText.textContent = "Location unavailable in this browser — check \"tag it remotely\" below to post anyway.";
    return;
  }
  navigator.geolocation.getCurrentPosition(pos=>{
    const place = PLACES.find(p=>p.id==document.getElementById('uPlace').value);
    const d = haversine(pos.coords.latitude,pos.coords.longitude, place.lat, place.lng);
    lastLat = pos.coords.latitude; lastLng = pos.coords.longitude;
    if(d < 0.3){
      geoConfirmed = true;
      geoText.textContent = `You're right here — ${Math.round(d*1000)}m from ${place.name}. Post unlocked.`;
      box.classList.add('geo-ok');
      btn.disabled = false;
    } else {
      geoText.textContent = `You're ~${d.toFixed(1)}km from ${place.name}. Get within 300m to post here, or check "tag it remotely" below.`;
      box.classList.add('geo-bad');
    }
  }, ()=>{
    geoText.textContent = "Location permission denied — check \"tag it remotely\" below to post anyway.";
  });
}
// The "I'm not at this spot right now" checkbox: re-runs checkGeo() so
// checking it immediately unlocks posting, and unchecking it goes back to
// requiring a real GPS match (re-triggers the geolocation prompt/result).
function onRemoteToggle(){
  checkGeo();
}
let lastLat = null, lastLng = null;
const FALLBACK_POST_IMAGE = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=';
function doPost(){
  if(!geoConfirmed) return;
  requireAuth(async ()=>{
    const btn = document.getElementById('postBtn');
    const statusEl = document.getElementById('uCompressStatus');
    const placeId = document.getElementById('uPlace').value;
    const caption = document.querySelector('#view-tagspot textarea').value;
    const isReel = tagHasVideo();
    const photoFiles = tagMediaItems.filter(m=>m.type==='photo').map(m=>m.file);
    btn.disabled = true; btn.classList.add('btn-loading'); btn.textContent = isReel ? 'Preparing…' : (photoFiles.length > 1 ? `Preparing 1 of ${photoFiles.length}…` : 'Posting…');
    setUploadProgress('u', 0);
    let posted = false;
    try{
      let mediaData, mediaType, galleryItems = null;
      if(isReel){
        if(statusEl) statusEl.textContent = 'Preparing video…';
        mediaData = await prepareVideoUpload(tagMediaItems[0].file, { onProgress: pct => setUploadProgress('u', pct) });
        mediaType = 'video';
        btn.textContent = 'Posting…';
      } else if(photoFiles.length > 1){
        // Compress every image in order, updating the button/status/bar so a
        // multi-photo post doesn't look stuck while it works.
        galleryItems = [];
        for(let i=0;i<photoFiles.length;i++){
          btn.textContent = `Preparing ${i+1} of ${photoFiles.length}…`;
          if(statusEl) statusEl.textContent = `Compressing photo ${i+1} of ${photoFiles.length}…`;
          setUploadProgress('u', Math.round((i / photoFiles.length) * 100));
          galleryItems.push(await fileToCompressedDataURI(photoFiles[i]));
        }
        setUploadProgress('u', 100);
        mediaData = galleryItems[0];
        mediaType = 'photo';
        btn.textContent = 'Posting…';
      } else if(photoFiles.length === 1){
        mediaData = await fileToCompressedDataURI(photoFiles[0]);
        mediaType = 'photo';
      } else {
        // No photo picked — fine for a demo post, but real posts should always attach one.
        mediaData = FALLBACK_POST_IMAGE;
        mediaType = 'photo';
      }
      if(statusEl) statusEl.textContent = 'Uploading…';
      // From here it's all network time with no real byte-progress to
      // report — switch the bar to its indeterminate sliding animation.
      setUploadProgress('u', null);
      const remoteEl = document.getElementById('remoteTag');
      const payload = {
        place_id: placeId, media_data: mediaData, media_type: mediaType, post_kind: isReel ? 'reel' : 'post',
        caption, capture_lat: lastLat, capture_lng: lastLng,
        remote: !!(remoteEl && remoteEl.checked)
      };
      if(galleryItems && galleryItems.length > 1) payload.media_items = galleryItems;
      const postResult = await apiPost('/api/posts', payload);
      // The post is safely saved server-side as of here — anything that
      // goes wrong past this point (a feed refresh hiccup, etc.) must never
      // be reported back to the person as "posting failed", since it didn't.
      posted = true;
      showToast(isReel ? "Flick posted! It's live in Flicks and on this thikana." : "Posted! It's live on the map and feed for this thikana.", 3500);
      resetTagSpotForm();
      await showView('feed');
      handleXpResult(postResult && postResult.xp);
    }catch(e){
      if(posted) return; // already succeeded — see comment above
      if(e && e.status === 401){ openAuthModal(); }
      else if(e && e.status === 422){ alert((e.data && e.data.message) || "You're too far from this spot to post here."); }
      else if(e && e.status === 413){ alert((e.data && e.data.message) || e.message || 'That file is too large — try a shorter/smaller one.'); }
      else if(e instanceof Error && e.message){ alert(e.message); }
      else { alert("Something went wrong sending that — check your connection and try again."); }
    }finally{
      btn.disabled = false; btn.classList.remove('btn-loading'); btn.textContent = "Confirm I was here & post";
      if(statusEl) statusEl.textContent = '';
      hideUploadProgress('u');
    }
    if(posted){
      // The feed is freshness-cached client-side (POSTS), so it needs an
      // explicit reload here — otherwise the new post only appears after a
      // manual refresh. Places is reloaded too so the map/sheet-list
      // trending badges (computed server-side from recent post activity)
      // and the place's own detail-page carousel pick up the fresh post
      // right away, the same way Feed does. Runs outside the try/catch
      // above on purpose: a hiccup here is a stale view, not a failed post,
      // so it's logged quietly instead of alerting the user.
      try{
        await Promise.all([loadFeed(), loadPlaces()]);
        renderProfileGrid();
      }catch(refreshErr){ console.warn('Post feed/places refresh failed:', refreshErr); }
    }
  });
}
function fileToDataURI(file){
  return new Promise((resolve,reject)=>{
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

/* ---------------- Add a hidden gem: submit a brand-new spot not yet on the map ---------------- */
// Unlike Tag-a-spot, a hidden-gem submission genuinely can include a photo
// gallery AND a flick at once — the backend stores them as separate fields
// (cover_photo_data/gallery_data/video_data), so there's no exclusivity
// here: the single "+" tile just keeps adding to one list of up to 6
// photos plus one video.
let gemMap = null, gemMarker = null, gemLocated = false;
let gemMediaItems = []; // [{type:'photo'|'video', file}]
function gemPhotoCount(){ return gemMediaItems.filter(m=>m.type==='photo').length; }
function gemHasVideo(){ return gemMediaItems.some(m=>m.type==='video'); }
function pickGemMedia(){
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*,video/*';
  input.multiple = true;
  input.onchange = () => {
    const files = Array.from(input.files || []);
    if(!files.length) return;
    const imgs = files.filter(f=>f.type.startsWith('image/'));
    const vids = files.filter(f=>f.type.startsWith('video/'));
    if(imgs.length){
      const room = MAX_GALLERY_IMAGES - gemPhotoCount();
      if(room <= 0){ showToast(`You can attach up to ${MAX_GALLERY_IMAGES} photos.`, 2500); }
      else {
        gemMediaItems.push(...imgs.slice(0, room).map(file=>({ type:'photo', file })));
        if(imgs.length > room) showToast(`Only added ${room} more — ${MAX_GALLERY_IMAGES} photos max.`, 3000);
      }
    }
    if(vids.length){
      if(gemHasVideo()){
        gemMediaItems = gemMediaItems.filter(m=>m.type!=='video');
        showToast('Replaced your flick with the new video.', 2400);
      }
      gemMediaItems.push({ type:'video', file: vids[0] });
      if(vids.length > 1) showToast('Only one flick per spot — used the first clip.', 2600);
    }
    renderGemMedia();
  };
  input.click();
}
function renderGemMedia(){
  const box = document.getElementById('gPhoto');
  const strip = document.getElementById('gThumbStrip');
  if(!box) return;
  const cover = gemMediaItems.find(m=>m.type==='photo') || gemMediaItems.find(m=>m.type==='video');
  box.classList.toggle('has-media', !!cover);
  box.classList.toggle('u-photo-reel', !!cover && cover.type==='video' && gemPhotoCount()===0);
  if(!cover){
    box.style.backgroundImage = '';
    box.innerHTML = `<svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="1.6"/><path d="M21 16l-5.5-5.5L5 21"/></svg><span id="gPhotoLabel">Tap to add photos and/or a video</span>`;
  } else if(cover.type === 'photo'){
    box.innerHTML = '';
    box.style.backgroundImage = `url('${URL.createObjectURL(cover.file)}')`;
  } else {
    box.style.backgroundImage = '';
    box.innerHTML = '';
    const vid = document.createElement('video');
    vid.src = URL.createObjectURL(cover.file);
    vid.muted = true; vid.autoplay = true; vid.loop = true; vid.playsInline = true;
    vid.className = 'u-media-fade';
    box.appendChild(vid);
  }
  if(strip){
    if(!gemMediaItems.length){ strip.style.display = 'none'; strip.innerHTML = ''; }
    else {
      strip.style.display = 'flex';
      const coverIdx = gemMediaItems.findIndex(m=>m.type==='photo');
      const canAddMore = gemPhotoCount() < MAX_GALLERY_IMAGES || !gemHasVideo();
      strip.innerHTML = gemMediaItems.map((m,i)=>mediaThumbMarkup(m,i,'gem',coverIdx)).join('') +
        (canAddMore ? `<div class="u-thumb u-thumb-add" onclick="pickGemMedia()">+</div>` : '');
    }
  }
}
function removeGemMedia(i){
  gemMediaItems.splice(i,1);
  renderGemMedia();
}
function initGemPinMap(){
  if(gemMap) return; // built once; re-opening the page just re-locates
  gemMap = new mappls.Map('gemPinMap', { center:{lat:23.35,lng:85.33}, zoom:12, zoomControl:false, clickableIcons:false, clickableIcons_callback: () => {} });
  if(typeof gemMap.on === 'function'){
    gemMap.on('click', e => {
      // Different SDK builds surface the clicked point differently.
      const ll = (e && (e.lngLat || e.latlng || e.lnglat)) || {};
      const lat = ll.lat != null ? ll.lat : (Array.isArray(e && e.lngLat) ? e.lngLat[1] : null);
      const lng = ll.lng != null ? ll.lng : (Array.isArray(e && e.lngLat) ? e.lngLat[0] : null);
      if(lat != null && lng != null) placeGemMarker(lat, lng);
    });
  }
  setTimeout(() => { if(typeof gemMap.resize === 'function') gemMap.resize(); }, 60);
}
function locateForGem(){
  const text = document.getElementById('gGeoText');
  const box = document.getElementById('gGeoBox');
  const btn = document.getElementById('gemLocateBtn');
  text.textContent = 'Getting your GPS location…';
  box.className = 'geo-box';
  if(!navigator.geolocation){
    text.textContent = 'Location unavailable in this browser — tap the map to drop the pin yourself.';
    return;
  }
  if(btn) btn.classList.add('is-locating');
  navigator.geolocation.getCurrentPosition(pos => {
    const { latitude, longitude, accuracy } = pos.coords;
    gemLocated = true;
    if(btn) btn.classList.remove('is-locating');
    if(typeof gemMap.setCenter === 'function') gemMap.setCenter({lat:latitude,lng:longitude});
    if(typeof gemMap.setZoom === 'function') gemMap.setZoom(17);
    placeGemMarker(latitude, longitude);
    text.textContent = `Pinned from your GPS (±${Math.round(accuracy || 0)}m). Drag the pin only to fine-tune it.`;
    box.classList.add('geo-ok');
  }, () => {
    if(btn) btn.classList.remove('is-locating');
    text.textContent = 'Location permission denied — tap the map to drop the pin yourself.';
    box.classList.add('geo-bad');
  }, { enableHighAccuracy: true });
}
function placeGemMarker(lat, lng){
  if(gemMarker){
    if(typeof gemMarker.setPosition === 'function') gemMarker.setPosition({lat,lng});
    else if(typeof gemMarker.setLngLat === 'function') gemMarker.setLngLat([lng,lat]);
  } else {
    gemMarker = new mappls.Marker({ map:gemMap, position:{lat,lng}, draggable:true, fitbounds:false });
    const onDrag = () => {
      let pos = null;
      if(typeof gemMarker.getPosition === 'function') pos = gemMarker.getPosition();
      else if(typeof gemMarker.getLngLat === 'function'){ const ll = gemMarker.getLngLat(); pos = {lat:ll.lat, lng:ll.lng}; }
      if(pos) setGemLatLngOut(pos);
    };
    if(typeof gemMarker.on === 'function') gemMarker.on('drag', onDrag);
    else if(typeof gemMarker.addListener === 'function') gemMarker.addListener('drag', onDrag);
  }
  setGemLatLngOut({ lat, lng });
}
function setGemLatLngOut(ll){
  document.getElementById('gLatOut').textContent = ll.lat.toFixed(6);
  document.getElementById('gLngOut').textContent = ll.lng.toFixed(6);
}
// Clears the Add-a-hidden-gem page back to its empty state — after a
// successful submission, whenever this page is freshly opened, or a draft
// is discarded. gemLocated resets too, so the next visit re-reads GPS
// instead of reusing wherever the pin was left.
function resetGemForm(){
  gemMediaItems = [];
  renderGemMedia();
  const statusEl = document.getElementById('gCompressStatus');
  if(statusEl) statusEl.textContent = '';
  const gName = document.getElementById('gName'); if(gName) gName.value = '';
  const gCat = document.getElementById('gCat'); if(gCat) gCat.value = 'waterfall';
  const gDesc = document.getElementById('gDesc'); if(gDesc) gDesc.value = '';
  const gVisitTime = document.getElementById('gVisitTime'); if(gVisitTime) gVisitTime.value = '';
  const gParking = document.getElementById('gParking'); if(gParking) gParking.value = '';
  const gRouteInfo = document.getElementById('gRouteInfo'); if(gRouteInfo) gRouteInfo.value = '';
  const gCustomRoute = document.getElementById('gCustomRoute'); if(gCustomRoute) gCustomRoute.value = '';
  const gExplorerType = document.getElementById('gExplorerType'); if(gExplorerType) gExplorerType.value = '';
  const gDifficulty = document.getElementById('gDifficulty'); if(gDifficulty) gDifficulty.value = '';
  gemLocated = false;
}
function submitHiddenGem(){
  requireAuth(async () => {
    const name = document.getElementById('gName').value.trim();
    const category = document.getElementById('gCat').value;
    const description = document.getElementById('gDesc').value.trim();
    const explorer_type = document.getElementById('gExplorerType').value || null;
    const best_visiting_time = document.getElementById('gVisitTime').value.trim() || null;
    const difficulty_level = document.getElementById('gDifficulty').value || null;
    const parking_info = document.getElementById('gParking').value.trim() || null;
    const route_info = document.getElementById('gRouteInfo').value.trim() || null;
    const custom_route_notes = document.getElementById('gCustomRoute').value.trim() || null;
    const btn = document.getElementById('gemPostBtn');
    const statusEl = document.getElementById('gCompressStatus');
    if(!name){ alert('Give the spot a name first.'); return; }
    if(!gemMarker){ alert("We don't have a location yet — allow GPS access or tap the map to drop a pin."); return; }
    let ll;
    if(typeof gemMarker.getPosition === 'function') ll = gemMarker.getPosition();
    else if(typeof gemMarker.getLngLat === 'function'){ const p = gemMarker.getLngLat(); ll = {lat:p.lat, lng:p.lng}; }
    const { lat, lng } = ll;
    const photoFiles = gemMediaItems.filter(m=>m.type==='photo').map(m=>m.file);
    const videoItem = gemMediaItems.find(m=>m.type==='video');
    btn.disabled = true; btn.textContent = 'Submitting…';
    setUploadProgress('g', 0);
    let submitted = false;
    try{
      let cover_photo_data = null, gallery_data = null, video_data = null;
      if(photoFiles.length){
        const items = [];
        for(let i=0;i<photoFiles.length;i++){
          btn.textContent = `Preparing ${i+1} of ${photoFiles.length}…`;
          if(statusEl) statusEl.textContent = `Compressing photo ${i+1} of ${photoFiles.length}…`;
          setUploadProgress('g', Math.round((i / photoFiles.length) * (videoItem ? 50 : 100)));
          items.push(await fileToCompressedDataURI(photoFiles[i]));
        }
        cover_photo_data = items[0];
        if(items.length > 1) gallery_data = items.slice(1);
      }
      if(videoItem){
        if(statusEl) statusEl.textContent = 'Preparing video…';
        btn.textContent = 'Preparing video…';
        video_data = await prepareVideoUpload(videoItem.file, { onProgress: pct => setUploadProgress('g', photoFiles.length ? 50 + pct/2 : pct) });
      }
      btn.textContent = 'Submitting…';
      if(statusEl) statusEl.textContent = 'Uploading…';
      setUploadProgress('g', null); // indeterminate — network POST phase
      const gemResult = await apiPost('/api/places', {
        name, category, description, lat, lng, cover_photo_data, gallery_data, video_data,
        explorer_type, best_visiting_time, difficulty_level, parking_info, route_info, custom_route_notes,
      });
      // Saved server-side as of here — a hiccup in the refresh steps below
      // must never be reported back as "submission failed".
      submitted = true;
      showToast("Submitted! It'll show up on the map right away, tagged Community Added, until an admin verifies it.", 4500);
      resetGemForm();
      await showView('discover');
      handleXpResult(gemResult && gemResult.xp);
    }catch(e){
      if(submitted) return; // already succeeded — see comment above
      if(e.status === 401){ openAuthModal(); }
      else if(e.status === 413 || e.status === 400){ alert((e.data && e.data.message) || e.message || 'Something about the media/fields was rejected.'); }
      else if(e instanceof Error && e.message){ alert(e.message); }
      else { alert("Something went wrong sending that — check your connection and try again."); }
    }finally{
      btn.disabled = false; btn.textContent = 'Submit for review';
      if(statusEl) statusEl.textContent = '';
      hideUploadProgress('g');
    }
    if(submitted){
      // Without this, a freshly-submitted thikana only shows up (on the map,
      // and in the profile tab's "Thikanas" list) after a manual refresh —
      // PLACES was never reloaded after a successful submission. Its cover
      // photo/gallery/flick also became a companion feed post server-side
      // (see places.js), so the Feed needs reloading too, or that post only
      // appears after a manual refresh. Outside the try/catch on purpose,
      // same reasoning as doPost() above.
      try{
        await Promise.all([loadPlaces(), loadFeed()]);
        renderProfileGrid();
      }catch(refreshErr){ console.warn('Places/feed refresh failed:', refreshErr); }
    }
  });
}

/* ---------------- Discard-progress guard ----------------
   Leaving Tag-a-spot or Add-a-hidden-gem with anything picked/typed — via
   the page's own back arrow, a bottom-nav tap, or a hardware/browser back
   gesture — asks for confirmation first instead of silently dropping it.
   Confirming resets that page's form immediately, so a subsequent refresh
   (or just reopening the page) never shows leftover media or text. */
function tagHasUnsavedProgress(){
  const ta = document.querySelector('#view-tagspot textarea');
  return tagMediaItems.length > 0 || (!!ta && ta.value.trim().length > 0);
}
function gemHasUnsavedProgress(){
  const ids = ['gName','gDesc','gVisitTime','gParking','gRouteInfo','gCustomRoute'];
  return gemMediaItems.length > 0 || ids.some(id=>{ const el = document.getElementById(id); return el && el.value.trim().length > 0; });
}
function currentUploadPageDirty(){
  const v = topView();
  if(v === 'tagspot') return tagHasUnsavedProgress();
  if(v === 'addgem') return gemHasUnsavedProgress();
  return false;
}
const DISCARD_PROGRESS_WARNING = "Discard this post? Your added photos/video and details will be lost.";
// Promise-based replacement for a native confirm() — themed to match the
// rest of the app instead of the browser's blocking dialog. Resolves true
// on "Discard", false on "Keep editing" or tapping the backdrop.
let _discardConfirmResolve = null;
function showDiscardConfirm(){
  return new Promise(resolve => {
    _discardConfirmResolve = resolve;
    document.getElementById('discardConfirmOverlay').classList.add('active');
  });
}
function discardConfirmResolve(result){
  document.getElementById('discardConfirmOverlay').classList.remove('active');
  const resolve = _discardConfirmResolve;
  _discardConfirmResolve = null;
  if(resolve) resolve(result);
}
function discardConfirmDismiss(){ discardConfirmResolve(false); }
async function confirmDiscardIfNeeded(){
  if(!currentUploadPageDirty()) return true;
  const ok = await showDiscardConfirm();
  if(ok){
    const v = topView();
    if(v === 'tagspot') resetTagSpotForm();
    else if(v === 'addgem') resetGemForm();
  }
  return ok;
}

/* ---------------- Business claim + Razorpay checkout ---------------- */
function buildBizSelect(){
  const sel = document.getElementById('bizPlace');
  if(!sel) return;
  sel.innerHTML = PLACES.map(p=>`<option value="${p.id}">${escapeHtml(p.name)}</option>`).join('');
}
function claimTier(tier){
  requireAuth(async ()=>{
    const placeId = document.getElementById('bizPlace').value;
    try{
      if(tier === 'verified'){
        await apiPost('/api/businesses', { place_id: placeId, tier });
        alert("You've claimed this thikana as verified. It'll show a verified badge once an admin confirms ownership.");
        return;
      }
      const order = await apiPost('/api/payments/create-order', { place_id: placeId, tier });
      if(typeof Razorpay === 'undefined'){
        alert('Razorpay checkout script did not load — check your network/ad-blocker.');
        return;
      }
      const rzp = new Razorpay({
        key: order.key_id,
        amount: order.amount,
        currency: order.currency,
        order_id: order.order_id,
        name: 'Mera Thikaana',
        description: order.label,
        handler: function(){
          alert('Payment received — this tier activates automatically once our webhook confirms it (usually within seconds).');
        },
        theme: { color: '#5B21B6' }
      });
      rzp.open();
    }catch(e){
      if(e.status === 401){ openAuthModal(); }
      else { alert("This preview isn't connected to a live backend yet, so claiming/paying only works once deployed to Cloudflare Pages with Razorpay configured."); }
    }
  });
}

/* ---------------- Profile grid ---------------- */
// Shows the signed-in user's own posts (and honest counts derived from
// them) instead of placeholder stock photos and made-up numbers — nothing
// here is shown unless it's real data pulled from POSTS.
let profileTab = 'posts'; // 'posts' | 'places' — which grid the profile shows
function setProfileTab(tab){
  profileTab = tab;
  ['posts','places'].forEach(t=>{
    const el = document.getElementById('pTab' + t[0].toUpperCase() + t.slice(1));
    if(el) el.classList.toggle('active', t === tab);
  });
  renderProfileGrid();
}
// ---------------- Gamification: profile card + leaderboard screen ----------------
async function loadGamificationCard(){
  const el = document.getElementById('pGamification');
  if(!el) return;
  try{
    const stats = await apiGet('/api/stats');
    const pct = stats.xp_for_next_level > 0
      ? Math.min(100, Math.round(100 * stats.xp_this_level / stats.xp_for_next_level))
      : 100;
    const xpToGo = Math.max(0, stats.xp_for_next_level - stats.xp_this_level);
    el.innerHTML = `
      <div class="gami-card" onclick="showLeaderboard()">
        <div class="gami-top">
          <div class="gami-level">Lv ${stats.level}</div>
          <div class="gami-title">${escapeHtml(stats.level_title)}</div>
          <div class="gami-xp">${stats.xp} XP</div>
        </div>
        <div class="gami-bar"><div class="gami-bar-fill" style="width:${pct}%;"></div></div>
        <div class="gami-next">${xpToGo} XP to next level</div>
        <div class="gami-badges">
          ${stats.badges.map(b=>`<div class="gami-badge ${b.earned?'earned':'locked'}" title="${escapeHtml(b.name)} — ${escapeHtml(b.description)}">${b.icon}</div>`).join('')}
        </div>
        <div class="gami-leaderboard-link">🏆 View leaderboard →</div>
      </div>`;
  }catch(e){
    // Not signed in, or the fetch failed — just leave the slot empty rather
    // than show a broken card; this is bonus content, not core profile info.
    el.innerHTML = '';
  }
}

let lbScope = 'week';
function showLeaderboard(){
  showView('leaderboard');
  loadLeaderboard(lbScope);
}
function switchLeaderboardScope(scope){
  if(scope === lbScope) return;
  lbScope = scope;
  document.getElementById('lbTabWeek').classList.toggle('active', scope === 'week');
  document.getElementById('lbTabAll').classList.toggle('active', scope === 'all');
  loadLeaderboard(scope);
}
async function loadLeaderboard(scope){
  const listEl = document.getElementById('lbList');
  const meEl = document.getElementById('lbMeRow');
  if(!listEl) return;
  listEl.innerHTML = `<div class="lb-loading">Loading leaderboard…</div>`;
  meEl.innerHTML = '';
  try{
    const { leaderboard, me } = await apiGet(`/api/leaderboard?scope=${scope}`);
    listEl.innerHTML = leaderboard.length ? leaderboard.map((r, i)=>lbRowHtml(r, i+1, currentUser && r.id === currentUser.id)).join('')
      : `<div class="lb-empty">No one's earned XP ${scope === 'week' ? 'this week' : 'yet'} — be the first!</div>`;
    if(me && !(currentUser && leaderboard.some(r=>r.id===currentUser.id))){
      meEl.innerHTML = `<div class="lb-me-divider">Your rank</div>` + lbRowHtml(me, me.rank, true);
    }
  }catch(e){
    listEl.innerHTML = `<div class="lb-empty">Couldn't load the leaderboard — check your connection.</div>`;
  }
}
function lbRowHtml(r, rank, isMe){
  const medal = rank === 1 ? '🥇' : rank === 2 ? '🥈' : rank === 3 ? '🥉' : `#${rank}`;
  const avatar = r.avatar_url
    ? `<img src="${r.avatar_url}" class="lb-avatar" alt="">`
    : `<div class="lb-avatar lb-avatar-fallback">${avatarInitial(r.display_name || r.handle || '?')}</div>`;
  return `
    <div class="lb-row${isMe ? ' lb-row-me' : ''}">
      <div class="lb-rank">${medal}</div>
      ${avatar}
      <div class="lb-name-col">
        <div class="lb-name">${escapeHtml(r.display_name || r.handle || 'Explorer')}${isMe ? ' (you)' : ''}</div>
        <div class="lb-level">Lv ${r.level || 1}</div>
      </div>
      <div class="lb-xp">${r.xp} XP</div>
    </div>`;
}

function renderProfileGrid(){
  const el = document.getElementById('pGrid');
  if(!el || !currentUser) return;
  // Photos and flicks (videos) are just "posts" here now — one combined
  // grid/tab/count, no separate Flicks section. Each item still carries its
  // own post_kind so the grid can render a video tile vs a photo tile and
  // show the right corner mark, it's just no longer split into its own tab.
  const myPosts = liveMode ? POSTS.filter(p => p.user_id === currentUser.id) : [];
  const myPlaces = liveMode ? PLACES.filter(p => p.submittedBy === currentUser.id) : [];
  const postsCountEl = document.getElementById('pStatPosts');
  const thikanasCountEl = document.getElementById('pStatThikanas');
  if(postsCountEl) postsCountEl.textContent = myPosts.length;
  if(thikanasCountEl) thikanasCountEl.textContent = myPlaces.length;

  // "🧭 N thikanas discovered" badge (Task 1) — approved hidden gems this
  // user submitted. PLACES already excludes rejected spots (see
  // /api/places's comment), so verified+gem is exactly "approved hidden
  // gem" — no extra request needed, this is a client-side filter over data
  // already on screen. Quiet social proof, not a popup, so it's blank
  // (not "0 discovered") until there's something to show.
  const discoveredCount = myPlaces.filter(p => p.gem && p.verified).length;
  const gemsBadgeEl = document.getElementById('pGemsBadge');
  if(gemsBadgeEl){
    gemsBadgeEl.innerHTML = discoveredCount > 0
      ? `<div class="gem-badge" style="color:var(--forest);margin:4px 0 0;display:inline-block;">🧭 ${discoveredCount} thikana${discoveredCount===1?'':'s'} discovered</div>`
      : '';
  }
  renderProfileCompletionNudge(myPosts);

  if(profileTab === 'places'){
    el.className = 'p-places-list';
    if(!myPlaces.length){
      el.innerHTML = `<div class="p-empty">No thikanas added yet — use "Add a hidden gem" from the <b>+</b> tab to put a new spot on the map.</div>`;
      return;
    }
    el.innerHTML = myPlaces.map(p=>`
      <div class="p-place-row" onclick="jumpToPlace(${p.id})">
        <div class="p-place-thumb" ${p.img?`style="background-image:url('${p.img}')"`:''}></div>
        <div style="min-width:0;flex:1;">
          <div class="p-place-name">${escapeHtml(p.name)}</div>
          <div class="p-place-meta">${CATS[p.cat] ? CATS[p.cat].label : p.cat}</div>
        </div>
        <span class="p-place-status ${p.status}">${p.status === 'approved' ? 'Live' : p.status}</span>
      </div>`).join('');
    return;
  }

  el.className = 'p-grid';
  if(!myPosts.length){
    el.innerHTML = `<div class="p-empty">No posts yet — tap <b>+</b> below to share your first hidden gem.</div>`;
    return;
  }
  el.innerHTML = '';
  myPosts.forEach(p=>{
    const isReel = p.post_kind === 'reel';
    const d = document.createElement('div');
    d.className = 'p-grid-item';
    d.onclick = (e)=>{ if(e.target.closest('.p-grid-menu')) return; openPostViewer(p.id); };
    const cover = isReel
      ? `<video class="p-grid-cover" src="${p.img}" muted playsinline preload="metadata"></video>`
      : `<div class="p-grid-cover" ${lazyBgAttrs(p.img)}></div>`;
    const mark = isReel
      ? `<svg class="p-reel-mark" width="15" height="15" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>`
      : (p.gallery ? `<svg class="p-gallery-mark" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="7" width="14" height="14" rx="2"/><path d="M7 7V5a2 2 0 012-2h10a2 2 0 012 2v10a2 2 0 01-2 2h-2"/></svg>` : '');
    d.innerHTML = `${cover}${mark}<div class="p-grid-menu" onclick="event.stopPropagation();openPostMenu(event, ${p.id})"><svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/></svg></div>`;
    el.appendChild(d);
  });
  observeLazyBg(el);
}

// Profile-completion nudge (Task 2) — a light "finish setting up" prompt on
// the user's own Profile tab, dismissible and never blocking. Condition is
// just currentUser.avatar_url / whether they have any posts, both already
// on screen — no new DB state. Dismissal is remembered per-user in
// localStorage (same pattern as readLocalCache/writeLocalCache above) so it
// doesn't reappear every time the tab is opened, without needing a server
// round-trip just to hide a tip.
function nudgeDismissKey(){ return currentUser ? `thikana_nudge_dismissed_${currentUser.id}` : null; }
function dismissProfileNudge(){
  const key = nudgeDismissKey();
  if(key) writeLocalCache(key, true);
  const el = document.getElementById('pCompletionNudge');
  if(el) el.innerHTML = '';
}
function renderProfileCompletionNudge(myPosts){
  const el = document.getElementById('pCompletionNudge');
  if(!el || !currentUser) return;
  const key = nudgeDismissKey();
  if(key && readLocalCache(key)){ el.innerHTML = ''; return; }
  // Photo first, then posting — whichever's still missing, one at a time
  // rather than stacking both, so this stays a light nudge, not a checklist.
  let text, action;
  if(!currentUser.avatar_url){
    text = 'Add a profile photo';
    action = 'openEditProfile()';
  } else if(!myPosts.length){
    text = "You haven't posted yet — share your first thikana";
    action = 'openUploadChooser()';
  } else {
    el.innerHTML = '';
    return;
  }
  el.innerHTML = `
    <div class="profile-nudge" style="display:flex;align-items:center;gap:10px;background:var(--paper-dim);border-radius:12px;padding:10px 12px;margin:2px 0 14px;font-size:12px;color:var(--ink-soft);">
      <span style="flex:1;cursor:pointer;" onclick="${action}">${escapeHtml(text)} →</span>
      <span style="cursor:pointer;color:var(--ink-soft);flex-shrink:0;padding:2px 4px;" onclick="dismissProfileNudge()" title="Dismiss">✕</span>
    </div>`;
}

/* ---------------- Nav & back-button handling ----------------
   This is a single HTML page with no real routes, so on its own the
   phone/browser back button has nothing of the app's own to step back
   through: pressing it — from any tab, any modal, any place-detail card —
   immediately leaves the site. This block gives the app its own tiny
   history: every tab switch and every modal/overlay open pushes one
   history entry, and back always undoes exactly one of those, only
   leaving the site once you're back at Discover with nothing open.

   Closing a modal (its own "X"/close button, or finishing an action like
   logging in) goes through the same history.back() as a real back-button
   press, so there is exactly one code path that hides the UI — a button
   click and a back-gesture can never disagree about what's open. */
let uiStack = [{ kind:'view', name:'discover' }];
// Views reachable directly from a bottom-nav tap — see renderActiveView().
const BOTTOM_NAV_VIEWS = new Set(['discover','feed','profile']);
history.replaceState({ depth:1 }, '', location.href);

function topView(){
  for(let i = uiStack.length - 1; i >= 0; i--){ if(uiStack[i].kind === 'view') return uiStack[i].name; }
  return 'discover';
}
function isModalOpen(kind){
  return uiStack.length > 0 && uiStack[uiStack.length - 1].kind === kind;
}
// Opens a modal/overlay on top of the current view.
function pushUIModal(kind){
  if(isModalOpen(kind)) return; // already open — don't double-stack
  uiStack.push({ kind });
  history.pushState({ depth: uiStack.length }, '', location.href);
}
// Swaps the modal/overlay currently on top for a different one, without
// adding a new back-step (used when one overlay hands off straight into
// another, e.g. a place's detail sheet handing off into turn-by-turn nav).
function replaceUIModal(kind){
  if(uiStack.length && uiStack[uiStack.length - 1].kind !== 'view'){
    uiStack[uiStack.length - 1] = { kind };
  } else {
    uiStack.push({ kind });
  }
  history.replaceState({ depth: uiStack.length }, '', location.href);
}
// Closes whichever modal/overlay is on top, the same way a back-button
// press would. Safe to call even if that modal isn't actually the one
// on top (falls back to just hiding it).
function closeUIModal(kind, hideFn){
  if(isModalOpen(kind)){ history.back(); }
  else { hideFn(); }
}
window.addEventListener('popstate', async (e) => {
  const targetDepth = Math.max(1, (e.state && e.state.depth) || 1);
  // A hardware/browser back gesture bypasses navBack()'s own guard, so it
  // needs its own: if it's about to pop out of Tag-a-spot/Add-a-hidden-gem
  // with something picked/typed, cancel the pop (push the state right back
  // on) and ask before letting it go through.
  if(targetDepth < uiStack.length && currentUploadPageDirty() && !window.__allowUploadLeave){
    history.pushState({ depth: uiStack.length }, '', location.href);
    const v = topView();
    const ok = await showDiscardConfirm();
    if(ok){
      if(v === 'tagspot') resetTagSpotForm();
      else if(v === 'addgem') resetGemForm();
      window.__allowUploadLeave = true;
      history.back();
    }
    return;
  }
  window.__allowUploadLeave = false;
  while(uiStack.length > targetDepth){
    const entry = uiStack.pop();
    if(entry.kind === 'auth') hideAuthModalUI();
    else if(entry.kind === 'detail') hideDetailUI();
    else if(entry.kind === 'nav') cleanupNavigation();
    else if(entry.kind === 'install') hideInstallSheetUI();
    else if(entry.kind === 'push') hidePushSheetUI();
    else if(entry.kind === 'comments') hideCommentsUI();
    else if(entry.kind === 'userprofile') hideUserProfileUI();
    else if(entry.kind === 'messages') hideMessagesUI();
    else if(entry.kind === 'msgthread') backToMsgInbox();
    else if(entry.kind === 'share') hideShareSheetUI();
    else if(entry.kind === 'editprofile') hideEditProfileUI();
    else if(entry.kind === 'followlist') hideFollowListUI();
    else if(entry.kind === 'suggestedfollows') hideSuggestedFollowsUI();
    else if(entry.kind === 'trip') hideTripPlannerUI();
    else if(entry.kind === 'notif') hideNotificationsUI();
    else if(entry.kind === 'finddost') hideFindDostUI();
    else if(entry.kind === 'uploadchooser') hideUploadChooserUI();
    else if(entry.kind === 'mediapreview') hideMediaPreviewUI();
    else if(entry.kind === 'postviewer') hidePostViewerUI();
    else if(entry.kind === 'music') hideMusicPanelUI();
  }
  // Messages is an overlay, not a `.view` entry, so topView() looks straight
  // through it to whatever view sits underneath (e.g. 'discover') — calling
  // renderActiveView() here would activate that view *underneath* an overlay
  // that's still open (e.g. backing out of a chat thread into the inbox),
  // and since the inbox overlay switches to position:static, the two would
  // render stacked on top of each other instead of one replacing the other.
  // Skip it while Messages (inbox or thread) is still the thing on screen —
  // backToMsgInbox()/hideMessagesUI() already handle its own contents.
  if(!isModalOpen('messages') && !isModalOpen('msgthread')) {
    const actName = (typeof topView === 'function' ? topView() : null) || 'discover';
    renderActiveView(actName);
    try {
      if(window.AndroidNativeAuth && typeof window.AndroidNativeAuth.onWebViewChanged === 'function'){
        window.AndroidNativeAuth.onWebViewChanged(actName);
      }
    } catch(e) {}
  }
});
/* ---------------- Smooth entrance transitions (tabs + overlays) ----------------
   Self-contained, CSS-independent animation built on the Web Animations API,
   so it works even where no keyframe animation is defined in the stylesheet.
   Two call sites use it:
     1) renderActiveView() below, for the bottom-nav tabs (Discover/Feed/Profile)
        that previously switched instantly with no motion at all.
     2) Every "...Overlay" modal (post viewer, comments, messages, share sheet,
        upload chooser, etc.) via a MutationObserver keyed off the id naming
        convention those all already share, rather than touching each of the
        dozen-plus open*UI() functions individually.
   Respects prefers-reduced-motion. Exit stays instant (unchanged) for now -
   only the "switching to" / "opening" side was asked for. */
const _reduceMotion = !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);

function _animateEntrance(el, variant){
  if(_reduceMotion || !el || typeof el.animate !== 'function') return;
  const keyframes = variant === 'overlay'
    ? [
        { opacity: 0, transform: 'translateY(18px) scale(0.98)' },
        { opacity: 1, transform: 'translateY(0) scale(1)' }
      ]
    : [
        { opacity: 0, transform: 'translateY(8px)' },
        { opacity: 1, transform: 'translateY(0)' }
      ];
  try{
    el.animate(keyframes, {
      duration: variant === 'overlay' ? 260 : 200,
      easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
      fill: 'backwards'
    });
  }catch(e){}
}

// Generic coverage for "opening anything" (modals/sheets): every overlay in
// this app is a top-level element whose id ends in "Overlay" and that gets a
// plain `.active` class toggle to show/hide (see openXyzUI()/hideXyzUI()
// pairs throughout this file). Watching for that one shared convention means
// new overlays get the same smooth entrance automatically, with nothing to
// remember to wire up at each call site.
(function initOverlayEntranceObserver(){
  if(typeof MutationObserver === 'undefined') return;
  const activeOverlays = new WeakSet();
  const maybeAnimate = (el) => {
    if(!(el instanceof Element) || !el.id || !el.id.endsWith('Overlay')) return;
    const isActive = el.classList.contains('active');
    if(isActive && !activeOverlays.has(el)){
      activeOverlays.add(el);
      _animateEntrance(el, 'overlay');
    } else if(!isActive){
      activeOverlays.delete(el);
    }
  };
  const observer = new MutationObserver(mutations => {
    for(const m of mutations){
      if(m.type === 'attributes'){
        maybeAnimate(m.target);
      } else if(m.type === 'childList'){
        m.addedNodes.forEach(node => {
          if(node.nodeType !== 1) return;
          maybeAnimate(node);
          if(node.querySelectorAll) node.querySelectorAll('[id$="Overlay"]').forEach(maybeAnimate);
        });
      }
    }
  });
  const start = () => observer.observe(document.documentElement, {
    attributes: true, attributeFilter: ['class'], subtree: true, childList: true
  });
  if(document.documentElement) start();
  else document.addEventListener('DOMContentLoaded', start);
})();

// A tab tap (bottom nav, business page, etc.) is a plain forward step, not
// a "back". Tabs sit as peers of Discover rather than stacking endlessly,
// so switching tabs repeatedly never requires more than one extra back
// press to get home. Modals can't actually be open at the same time as a
// tab tap in this UI (they cover the whole screen including the nav bar),
// so it's safe to just hide any of them defensively here too.
async function showView(name){
  if(name !== topView() && !(await confirmDiscardIfNeeded())) return;
  hideAuthModalUI(); hideDetailUI(); hideInstallSheetUI(); hideCommentsUI(); hideUserProfileUI(); hideSuggestedFollowsUI(); hideTripPlannerUI();
  if(name === 'messages'){ openMessages(); updateMsgBadge(0); return; }
  if(name === 'feed'){ updateVibesBadge(0); }
  if(name === 'discover'){ updateDostBadge(0); }
  hideMessagesUI();
  while(uiStack.length > 1) uiStack.pop();
  if(name !== 'discover') uiStack.push({ kind:'view', name });
  history.pushState({ depth: uiStack.length }, '', location.href);
  renderActiveView(name);
}
// For in-app "←" back-arrow buttons (e.g. the Business page): actually
// consumes the tab's own history entry instead of pushing a new forward
// one, so a subsequent hardware/browser back doesn't need an extra press.
async function navBack(){
  if(!(await confirmDiscardIfNeeded())) return;
  if(uiStack.length > 1){ history.back(); } else { showView('discover'); }
}
function renderActiveView(name){
  const feedEl = document.getElementById('view-feed');
  const wasFeed = feedEl && feedEl.classList.contains('active');
  if(wasFeed && name !== 'feed'){ document.querySelectorAll('#feedList video').forEach(v=>v.pause()); }
  const tagspotEl = document.getElementById('view-tagspot');
  const wasTagspot = tagspotEl && tagspotEl.classList.contains('active');
  const addgemEl = document.getElementById('view-addgem');
  const wasAddgem = addgemEl && addgemEl.classList.contains('active');
  document.querySelectorAll('.view').forEach(v=>v.classList.remove('active','view-in'));
  let target = document.getElementById('view-'+name);
  if(!target || name === 'chat' || name === 'messages' || name === 'msgthread'){
    target = document.getElementById('view-discover') || document.getElementById('view-feed');
  }
  if(!target) return;
  target.classList.add('active');
  // Bottom-nav destinations are peers of each other (Discover/Feed/Profile),
  // same as Instagram's tab bar. They get a quick, subtle fade+rise via the
  // Web Animations API (_animateEntrance) rather than the heavier .32s
  // viewIn animation, which stays reserved for views entered as a forward
  // "push" from inside a tab (tagspot, addgem, biz) — those benefit from a
  // little more motion to help orient the user on where they landed.
  if(BOTTOM_NAV_VIEWS.has(name)){
    _animateEntrance(target, 'tab');
  } else {
    requestAnimationFrame(()=>target.classList.add('view-in'));
  }
  document.querySelectorAll('.navitem[data-v]').forEach(n=>n.classList.toggle('active', n.dataset.v===name));
  if(name==='discover' && map){ setTimeout(()=>{ if(typeof map.resize === 'function') map.resize(); }, 50); }
  // Freshly opening either upload page always starts from a clean slate —
  // nothing picked/typed in a previous, abandoned attempt ever lingers.
  if(name==='tagspot' && !wasTagspot && typeof resetTagSpotForm === 'function'){
    resetTagSpotForm();
    buildUploadSelect();
  }
  if(name==='addgem' && !wasAddgem && typeof resetGemForm === 'function'){
    resetGemForm();
    initGemPinMap();
    if(!gemLocated) locateForGem();
  }
  // Keep the live-refresh polls running only where they're actually useful:
  // the feed poll while Feed is on screen, the places poll while Discover is
  // (the only screen a detail sheet opens from — see jumpToPlace) — and
  // catch up immediately on switching in, not just on the next tick.
  if(name === 'feed'){
    if(liveMode) pollFeedOnce();
    startFeedPoll();
  } else {
    stopFeedPoll();
  }
  if(name === 'discover'){
    if(liveMode) pollPlacesOnce();
    startPlacesPoll();
  } else {
    stopPlacesPoll();
  }
}

/* ---------------- Music ----------------
   A single shared playlist anyone signed in can add to. The <audio> element
   lives in index.html *outside* every .view container on purpose: views are
   toggled with display:none, and an <audio> inside one would keep playing
   but is easy to tear down accidentally on re-render. Keeping it in the app
   chrome means playback survives switching tabs, opening navigation, and
   every overlay — it only stops when you stop it.

   Audio streams from /api/media/:filename, which forwards Range headers, so
   tracks start playing before they've fully downloaded and the scrubber in
   the OS media controls works. */
let MUSIC_TRACKS = [];
let musicOrder = [];        // shuffled indices into MUSIC_TRACKS
let musicOrderPos = -1;     // where we are within musicOrder
let musicLoaded = false;    // playlist fetched at least once
let musicLoading = false;
let musicPanelOpen = false;
let musicLongPressTimer = null;
let musicLongPressFired = false;

function musicEl(){ return document.getElementById('musicAudio'); }
function currentTrack(){
  if(musicOrderPos < 0 || musicOrderPos >= musicOrder.length) return null;
  return MUSIC_TRACKS[musicOrder[musicOrderPos]] || null;
}
function musicIsPlaying(){
  const a = musicEl();
  return !!(a && !a.paused && !a.ended && a.currentTime >= 0 && a.src);
}
async function loadMusicTracks(force){
  if(musicLoading) return;
  if(musicLoaded && !force) return;
  musicLoading = true;
  try{
    const { tracks } = await apiGet('/api/music');
    MUSIC_TRACKS = tracks || [];
    musicLoaded = true;
    // Proactively cache all playlist music tracks to local disk
    if(Array.isArray(MUSIC_TRACKS)){
      MUSIC_TRACKS.forEach(t => {
        if(t && t.url){
          if(window.AndroidNativeAuth && window.AndroidNativeAuth.preloadMusicTrack){
            window.AndroidNativeAuth.preloadMusicTrack(t.url);
          }
          if('caches' in window){
            caches.open('thikana-music-cache-v1').then(c => c.add(t.url).catch(()=>{})).catch(()=>{});
          }
        }
      });
    }
  }catch(e){
    MUSIC_TRACKS = [];
  }finally{
    musicLoading = false;
    if(musicPanelOpen) renderMusicPanel();
  }
}
// Fisher-Yates over the track indices. Rebuilt whenever shuffle (re)starts
// or the playlist changes underneath us, so a newly uploaded track can show
// up in the rotation without restarting playback.
function reshuffleMusic(keepCurrent){
  const cur = keepCurrent ? currentTrack() : null;
  musicOrder = MUSIC_TRACKS.map((_, i)=>i);
  for(let i = musicOrder.length - 1; i > 0; i--){
    const j = Math.floor(Math.random() * (i + 1));
    [musicOrder[i], musicOrder[j]] = [musicOrder[j], musicOrder[i]];
  }
  if(cur){
    const idx = MUSIC_TRACKS.findIndex(t=>t.id === cur.id);
    const at = musicOrder.indexOf(idx);
    if(at > 0){ musicOrder.splice(at, 1); musicOrder.unshift(idx); }
    musicOrderPos = 0;
  } else {
    musicOrderPos = -1;
  }
}
function playMusicAt(pos){
  const a = musicEl();
  if(!a || !musicOrder.length) return;
  musicOrderPos = ((pos % musicOrder.length) + musicOrder.length) % musicOrder.length;
  const track = currentTrack();
  if(!track) return;
  a.src = track.url;
  a.play().catch(()=>{ /* autoplay refused or the file 404'd — UI just shows paused */ });
  updateMusicUI();
  updateMediaSession();
  // Pre-cache next song in playlist to ensure gapless transitions
  if(musicOrder.length > 1){
    const nextIdx = musicOrder[(musicOrderPos + 1) % musicOrder.length];
    const nextTrack = MUSIC_TRACKS[nextIdx];
    if(nextTrack && nextTrack.url){
      if(window.AndroidNativeAuth && window.AndroidNativeAuth.preloadMusicTrack){
        window.AndroidNativeAuth.preloadMusicTrack(nextTrack.url);
      }
      if('caches' in window){
        caches.open('thikana-music-cache-v1').then(c => c.add(nextTrack.url).catch(()=>{})).catch(()=>{});
      }
    }
  }
}
function musicNext(){
  if(!musicOrder.length) return;
  // Reshuffle at the end of a pass so a long session doesn't repeat the
  // same running order forever.
  if(musicOrderPos >= musicOrder.length - 1){
    reshuffleMusic(false);
    playMusicAt(0);
  } else {
    playMusicAt(musicOrderPos + 1);
  }
}
function musicPrev(){
  const a = musicEl();
  // Standard player behaviour: >3s in, "previous" restarts the track.
  if(a && a.currentTime > 3){ a.currentTime = 0; return; }
  if(musicOrder.length) playMusicAt(musicOrderPos - 1);
}
// The topbar button's primary action: start shuffled playback, or pause /
// resume whatever's already going.
async function toggleMusicPlay(){
  const a = musicEl();
  if(!a) return;
  if(musicIsPlaying()){ a.pause(); updateMusicUI(); return; }
  if(currentTrack() && a.src){
    a.play().catch(()=>{});
    updateMusicUI();
    return;
  }
  await loadMusicTracks();
  if(!MUSIC_TRACKS.length){
    showToast('No music yet — add a track from the playlist.', 2800);
    openMusicPanel();
    return;
  }
  reshuffleMusic(false);
  playMusicAt(0);
}
function stopMusic(){
  const a = musicEl();
  if(!a) return;
  a.pause();
  a.removeAttribute('src');
  a.load();
  musicOrderPos = -1;
  updateMusicUI();
}
// Keeps both topbar buttons and the now-playing bar in sync. Called on every
// audio state change rather than tracked manually — cheaper to be correct.
function updateMusicUI(){
  const playing = musicIsPlaying();
  const track = currentTrack();
  document.querySelectorAll('.music-btn').forEach(btn=>{
    btn.classList.toggle('playing', playing);
    btn.setAttribute('title', playing ? 'Pause music' : 'Play music (shuffle)');
  });
  const bar = document.getElementById('musicBar');
  if(bar){
    bar.classList.toggle('active', !!track);
    const nameEl = document.getElementById('musicBarTitle');
    const subEl = document.getElementById('musicBarSub');
    const icon = document.getElementById('musicBarToggleIcon');
    if(nameEl) nameEl.textContent = track ? track.title : '';
    if(subEl) subEl.textContent = track ? (track.artist || 'Shuffle') : '';
    if(icon) icon.innerHTML = playing
      ? '<path d="M8 5h3v14H8zM13 5h3v14h-3z"/>'
      : '<path d="M7 4l12 8-12 8z"/>';
  }
  if(musicPanelOpen) renderMusicPanel();
  if(window.AndroidNativeAuth && window.AndroidNativeAuth.onMusicPlaybackStateChanged){
    try{
      const a = musicEl();
      const dur = Math.round((a && a.duration) || 0);
      const pos = Math.round((a && a.currentTime) || 0);
      window.AndroidNativeAuth.onMusicPlaybackStateChanged(
        playing,
        track ? track.title : 'Mera Thikaana',
        track ? (track.artist || 'Ranchi Beats') : 'Ranchi Beats',
        dur,
        pos
      );
    }catch(err){}
  }
}
// Lock-screen / notification-shade controls. Cheap to set up and makes
// background playback feel native rather than like a stray browser tab.
function updateMediaSession(){
  const track = currentTrack();
  if(window.AndroidNativeAuth && window.AndroidNativeAuth.onMusicPlaybackStateChanged){
    try{
      const a = musicEl();
      const isPlaying = musicIsPlaying();
      const dur = Math.round((a && a.duration) || 0);
      const pos = Math.round((a && a.currentTime) || 0);
      window.AndroidNativeAuth.onMusicPlaybackStateChanged(
        isPlaying,
        track ? track.title : 'Mera Thikaana',
        track ? (track.artist || 'Ranchi Beats') : 'Ranchi Beats',
        dur,
        pos
      );
    }catch(err){}
  }
  if(!('mediaSession' in navigator)) return;
  if(!track) return;
  try{
    navigator.mediaSession.metadata = new MediaMetadata({
      title: track.title,
      artist: track.artist || 'Mera Thikaana',
      album: 'Mera Thikaana',
      artwork: [{ src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png' }],
    });
    navigator.mediaSession.setActionHandler('play', ()=>{ const a=musicEl(); if(a){ a.play().catch(()=>{}); updateMusicUI(); } });
    navigator.mediaSession.setActionHandler('pause', ()=>{ const a=musicEl(); if(a){ a.pause(); updateMusicUI(); } });
    navigator.mediaSession.setActionHandler('nexttrack', musicNext);
    navigator.mediaSession.setActionHandler('previoustrack', musicPrev);
    navigator.mediaSession.setActionHandler('seekto', (details)=>{
      const a=musicEl();
      if(a && details.seekTime !== undefined){
        a.currentTime = details.seekTime;
        updateMusicUI();
      }
    });
  }catch(e){ /* older browser — no lock-screen controls, playback still fine */ }
}

/* ---- Playlist panel ---- */
function openMusicFilePicker(){
  try{
    if(typeof hideUploadChooserUI === 'function') hideUploadChooserUI();
    const chooser = document.getElementById('uploadChooserOverlay');
    if(chooser) chooser.classList.remove('active');
    if(window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged){
      window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
    }
  }catch(e){}
  const input = document.getElementById('musicFileInput');
  if(input) input.click();
}
function openMusicPanel(){
  try{
    if(typeof hideUploadChooserUI === 'function') hideUploadChooserUI();
    const chooser = document.getElementById('uploadChooserOverlay');
    if(chooser) chooser.classList.remove('active');
    if(window.AndroidNativeAuth && window.AndroidNativeAuth.onUploadChooserVisibilityChanged){
      window.AndroidNativeAuth.onUploadChooserVisibilityChanged(false);
    }
  }catch(e){}
  musicPanelOpen = true;
  document.getElementById('musicOverlay').classList.add('active');
  pushUIModal('music');
  renderMusicPanel();
  loadMusicTracks(true);
}
function hideMusicPanelUI(){
  musicPanelOpen = false;
  document.getElementById('musicOverlay').classList.remove('active');
}
function closeMusicPanel(){ closeUIModal('music', hideMusicPanelUI); }
function fmtTrackDuration(sec){
  if(!sec || !isFinite(sec)) return '';
  const m = Math.floor(sec / 60), s = Math.floor(sec % 60);
  return `${m}:${String(s).padStart(2,'0')}`;
}
function renderMusicPanel(){
  const body = document.getElementById('musicBody');
  if(!body) return;
  const playing = musicIsPlaying();
  const cur = currentTrack();
  const list = MUSIC_TRACKS.length
    ? MUSIC_TRACKS.map((t, i)=>{
        const isCur = cur && cur.id === t.id;
        const mine = currentUser && t.uploaded_by === currentUser.id;
        return `<div class="music-row${isCur ? ' current' : ''}" onclick="playTrackById(${t.id})">
          <div class="music-row-icon">${isCur && playing ? '<span class="music-eq"><i></i><i></i><i></i></span>' : '▶'}</div>
          <div class="music-row-text">
            <div class="music-row-title">${escapeHtml(t.title)}</div>
            <div class="music-row-sub">${escapeHtml(t.artist || (t.uploader_handle ? '@'+t.uploader_handle : ''))}${t.duration ? ' · ' + fmtTrackDuration(t.duration) : ''}</div>
          </div>
          ${mine ? `<div class="music-row-del" onclick="event.stopPropagation();deleteTrack(${t.id})" title="Remove">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </div>` : ''}
        </div>`;
      }).join('')
    : `<div class="msg-empty">${musicLoading ? 'Loading…' : 'No tracks yet — add the first one.'}</div>`;

  body.innerHTML = `
    <div class="detail-close" onclick="closeMusicPanel()">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M18 6L6 18M6 6l12 12"/></svg>
    </div>
    <div class="detail-body" style="padding-top:22px;flex:1;overflow-y:auto;">
      <h2 style="font-size:17px;">Music</h2>
      <div style="font-size:12.5px;color:var(--ink-soft);margin-bottom:14px;">Shared playlist — anything added here plays for everyone.</div>

      <div class="music-controls">
        <div class="music-ctl" onclick="musicPrev()" title="Previous">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 5h2v14H6zM20 5v14l-11-7z"/></svg>
        </div>
        <div class="music-ctl big" onclick="toggleMusicPlay()" title="${playing ? 'Pause' : 'Shuffle play'}">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">${playing ? '<path d="M8 5h3v14H8zM13 5h3v14h-3z"/>' : '<path d="M7 4l12 8-12 8z"/>'}</svg>
        </div>
        <div class="music-ctl" onclick="musicNext()" title="Next">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M16 5h2v14h-2zM4 5l11 7-11 7z"/></svg>
        </div>
        <div class="music-ctl" onclick="shuffleNow()" title="Reshuffle">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5"/></svg>
        </div>
      </div>

      <div class="music-list">${list}</div>

      <input type="file" id="musicFileInput" accept="audio/*" style="display:none" onchange="onMusicFileSelected(event)">
      <button class="btn-primary" style="width:100%;margin-top:14px;" onclick="openMusicFilePicker()">Add a track</button>
      <div style="font-size:11px;color:var(--ink-soft);margin-top:8px;text-align:center;">Up to 10MB per track · only add music you have the right to share</div>
    </div>`;
}
function playTrackById(id){
  const idx = MUSIC_TRACKS.findIndex(t=>t.id===id);
  if(idx < 0) return;
  // Tapping a specific track shouldn't abandon shuffle — just jump the
  // shuffled order to that track and carry on from there.
  if(!musicOrder.length) reshuffleMusic(false);
  const at = musicOrder.indexOf(idx);
  if(at >= 0) playMusicAt(at);
  else { musicOrder.push(idx); playMusicAt(musicOrder.length - 1); }
}
function shuffleNow(){
  if(!MUSIC_TRACKS.length) return;
  reshuffleMusic(false);
  playMusicAt(0);
}
async function deleteTrack(id){
  try{
    await apiDelete(`/api/music?id=${id}`);
    const cur = currentTrack();
    MUSIC_TRACKS = MUSIC_TRACKS.filter(t=>t.id!==id);
    if(cur && cur.id === id){ stopMusic(); reshuffleMusic(false); }
    else reshuffleMusic(true);
    renderMusicPanel();
    showToast('Track removed.', 1800);
  }catch(e){
    showToast((e.data && e.data.message) || "Couldn't remove that track.", 2600);
  }
}
// Reads duration locally before upload so the playlist can show it without
// the server having to parse audio metadata.
function probeAudioDuration(file){
  return new Promise(resolve=>{
    try{
      const url = URL.createObjectURL(file);
      const probe = new Audio();
      const done = d => { URL.revokeObjectURL(url); resolve(d); };
      probe.onloadedmetadata = ()=>done(isFinite(probe.duration) ? probe.duration : null);
      probe.onerror = ()=>done(null);
      setTimeout(()=>done(null), 4000);
      probe.src = url;
    }catch(e){ resolve(null); }
  });
}
function onMusicFileSelected(e){
  const file = e.target.files && e.target.files[0];
  e.target.value = '';
  if(!file) return;
  requireAuth(async ()=>{
    if(file.size > 10 * 1024 * 1024){
      showToast('That track is over 10MB — try a smaller file.', 3200);
      return;
    }
    showToast('Uploading track…', 2000);
    try{
      const duration = await probeAudioDuration(file);
      const dataUri = await new Promise((res, rej)=>{
        const r = new FileReader();
        r.onload = ()=>res(r.result);
        r.onerror = ()=>rej(new Error('read_failed'));
        r.readAsDataURL(file);
      });
      const title = file.name.replace(/\.[^.]+$/, '').slice(0, 120) || 'Untitled';
      const { track } = await apiPost('/api/music', { title, audio: dataUri, duration });
      MUSIC_TRACKS.unshift(track);
      reshuffleMusic(true);
      renderMusicPanel();
      showToast('Track added!', 2000);
    }catch(err){
      showToast((err.data && err.data.message) || "Couldn't add that track — try again.", 3200);
    }
  });
}

/* ---- Topbar button gestures: tap = play/pause, long-press = playlist ---- */
function musicBtnDown(){
  musicLongPressFired = false;
  clearTimeout(musicLongPressTimer);
  musicLongPressTimer = setTimeout(()=>{
    musicLongPressFired = true;
    openMusicPanel();
  }, 480);
}
function musicBtnUp(){
  clearTimeout(musicLongPressTimer);
  if(musicLongPressFired){ musicLongPressFired = false; return; }
  toggleMusicPlay();
}
function musicBtnCancel(){
  clearTimeout(musicLongPressTimer);
  musicLongPressFired = false;
}
// Wires the audio element's own events once, at startup, so the UI reflects
// reality even when playback changes from the lock screen or a headset.
function initMusic(){
  const a = musicEl();
  if(!a) return;
  a.addEventListener('play', updateMusicUI);
  a.addEventListener('pause', updateMusicUI);
  a.addEventListener('ended', musicNext);
  a.addEventListener('error', ()=>{
    // A single dead file shouldn't kill the session — skip past it.
    if(currentTrack()) musicNext();
  });
  updateMusicUI();
}


/* ---------------- Init ---------------- */
buildGroupBar();
buildChips();
buildStories();
initPullToRefresh();
initMusic();
// Don't call initMap() straight away — the Mappls SDK may still be doing
// async setup (see onMapplsSDKReady above). Register initMap so it fires
// the moment Mappls is actually ready, or immediately if it already is.
window.__mapplsInit = initMap;
if(window.__mapplsReady) initMap();
// Safety net: don't rely solely on the &callback= param firing (it turned
// out not to on map_sdk_plugins, and may be flaky elsewhere too). Poll for
// mappls.Map actually existing, and init the moment it shows up — up to
// ~10s, then give up and let initMap()'s own try/catch show the friendly
// "couldn't load" message.
(function pollForMapplsSDK(triesLeft){
  if(window.__mapplsReady) return; // already handled via the real callback
  if(typeof mappls !== 'undefined' && typeof mappls.Map === 'function'){
    window.__mapplsReady = true;
    initMap();
    return;
  }
  if(triesLeft <= 0){ initMap(); return; } // give up, show fallback UI
  setTimeout(()=>pollForMapplsSDK(triesLeft-1), 300);
})(33); // ~10s at 300ms intervals
renderSheetList();
renderFeed();
buildUploadSelect();
renderProfileView();
checkAuth().then(() => setTimeout(maybeShowPushPrompt, 1200)); // small delay so this never races the initial paint or the install banner
loadPublicConfig();
Promise.all([loadPlaces().then(buildBizSelect), loadFeed()]).then(handleDeepLinkParams);
// Leftover queued actions from a session that ended while offline (tab
// closed, app killed) get one attempt right away; if there's still no
// connection this just quietly re-queues them, same as any other flush.
if(navigator.onLine !== false) flushOfflineQueue();
requestUserLocation();
// Feed ("Vibes") is the default landing tab. Switching via showView()
// (rather than just editing the static HTML's active classes) reuses its
// existing nav-highlight logic, and — importantly — leaves Discover's
// markup exactly as it always was at page-load, so the Mappls map's
// container is in the identical state it's always been in at the moment the
// async SDK script resolves (see pollForMapplsSDK above). This runs
// synchronously, long before that network-dependent callback could possibly
// fire, so it changes nothing about map init timing.
showView('feed');
setInterval(refreshUnreadBadge, 30000); // keep the Messages badge fresh while the app is open
setInterval(refreshNotifBadge, 30000);  // same for the notification bell
setInterval(refreshDostBadge, 30000);   // same for Find Dost count

/* ---------------- Service worker (PWA shell + install eligibility) ---------------- */
if('serviceWorker' in navigator){
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => { /* non-fatal — app still works without it */ });
  });
}

/* ---------------- Expose handlers referenced by inline HTML attributes ----------------
   This script is now an ES module (see the import statements at the top), and module
   scope is NOT global scope — so functions called from onclick="..." etc. in the HTML
   above need to be attached to window explicitly, or the browser can't find them. */

Object.assign(window, {
  cancelMsgAttachment, chooseUploadMode, claimTier, clearMapSearch, closeAuthModal, closeComments, closeDetail,
  closeEditProfile, closeFindDost, closeFollowList, closeMediaPreview, closeMessages, closeNotifications, closePostMenu, closeShareSheet, closeSuggestedFollows,
  closeUploadChooser, closeUserProfile, collapseMapSearch, deleteCommentConfirm, deletePostConfirm, discardConfirmDismiss, discardConfirmResolve, dismissInstallPrompt, dismissProfileNudge, dismissPushPrompt, doLogin, doPost, doSignup,
  editPostCaption, enablePushFromPrompt, enablePushNotifications, endNavigation, expandMapSearch, insertEmoji, jumpToPlace, likePost, locateForGem, locateMe,
  logout, navBack, navigateToDestination, notifTap, onAvatarFileSelected, onFindDostInput, onMapSearchInput, onMapSearchKeydown,
  msgThreadBack, onMsgFileSelected, onMsgSearchInput, onRemoteToggle, onShareSearchInput, openComments, openEditProfile,
  openFindDost, openFollowList, openMediaPreview, openMessages, openNewMessage, openNewGroup, openNotifications, openOwnFollowList, openPostMenu, openShareSheet, openSuggestedFollows, openThread, openGroupThread,
  toggleNewGroupPick, onNewGroupSearchInput, submitNewGroup, showGroupMembers, leaveGroup,
  openUploadChooser, openUserProfile, pickGemMedia, pickLocalPlaceResult, pickMapSearchResult, pickTagMedia, postComment,
  profDoLogin, profDoSignup, recenterNav, removeEditAvatarPhoto, removeGemMedia, removeTagMedia,
  renderEditProfileBody, retryMessage,
  savePost, saveProfileEdits,
  sendMessage, sendShareTo, setAuthTab, setFeedMode, setProfAuthTab, setProfileTab,
  shareExternally, shareToWhatsApp, copyShareLink,
  showView, startNavigation, submitHiddenGem, submitRouteSuggestion, suggestGemFromSearch,
  toggleEmojiPicker, toggleFollow, toggleMapSearch, toggleNavMute, toggleNavSteps, toggleNavOrientation, toggleNavTilt,
  toggleCarouselMinimized, togglePostVideoMute,
  triggerInstall,
  openTripPlanner, openTripPlannerForChat, closeTripPlanner, addToTripPlan, removeTripStop, clearTripPlan, onTripPickerInput,
  addTripStopFromPicker, computeTripRoute, navigateTripLeg, sendTripToGroup, startTripNavigation, openSharedTrip,
  openTripSharePicker, closeTripSharePicker, onTripShareSearchInput, pickTripShareTarget,
  checkInAtPlace, leavePlaceCheckin,
  addExternalTripStop, addExternalStopFromSearch,
  dismissLevelUpPopup, dismissBadgePopup, showLeaderboard, switchLeaderboardScope,
  shareTripToWhatsApp, shareTripExternally, copyTripLink, openTripFromLink,
  toggleMusicPlay, musicNext, musicPrev, shuffleNow, openMusicPanel, closeMusicPanel, openMusicFilePicker,
  playTrackById, deleteTrack, onMusicFileSelected, musicBtnDown, musicBtnUp, musicBtnCancel,
  updateVibesBadge, updateDostBadge, refreshDostBadge
});
