// Pure, stateless utility/formatting functions extracted from the former inline
// <script> in index.html. None of these read or write shared app state, so they
// were safe to split out mechanically — same code, just addressable on its own.

function escapeHtml(s){ const d = document.createElement('div'); d.textContent = s == null ? '' : s; return d.innerHTML; }

function timeAgo(iso){
  const then = new Date(iso.includes('Z')||iso.includes('+') ? iso : iso.replace(' ','T')+'Z');
  const sec = Math.max(1, Math.floor((Date.now() - then.getTime())/1000));
  if(sec < 60) return `${sec}s`;
  if(sec < 3600) return `${Math.floor(sec/60)}m`;
  if(sec < 86400) return `${Math.floor(sec/3600)}h`;
  return `${Math.floor(sec/86400)}d`;
}

function fmtDist(km){ return km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(1)} km`; }

function fmtClockTime(d){ return d.toLocaleTimeString([], { hour:'numeric', minute:'2-digit' }); }

function bearing(lat1, lon1, lat2, lon2){
  const toRad = d => d * Math.PI / 180, toDeg = r => r * 180 / Math.PI;
  const y = Math.sin(toRad(lon2 - lon1)) * Math.cos(toRad(lat2));
  const x = Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) - Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(toRad(lon2 - lon1));
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

// Light Hinglish flavour (chalo/yaar/seedha/dhyaan se/gol chakkar) —
// deliberately only ever added as a prefix or a spelled-in-Latin-script
// loanword around the actual instruction, never replacing or reordering
// the turn direction (modifier) or road name (name), since those two are
// the safety-critical part someone driving actually needs. Reads fine
// through any voice, but sounds most natural through an Indian-English
// (en-IN) voice — see pickNavVoice() in app.js's speak().
function stepText(step){
  const type = step.maneuver.type, modifier = step.maneuver.modifier, name = step.name || 'the road';
  if(type === 'depart') return `Chalo, head ${modifier ? modifier + ' ' : ''}on ${name}`;
  if(type === 'arrive') return `Yaar, you've arrived at your destination`;
  if(type === 'turn') return `Ab turn ${modifier || ''} onto ${name}`;
  if(type === 'new name') return `Seedha continue onto ${name}`;
  if(type === 'continue') return `Bas continue ${modifier || ''} on ${name}`;
  if(type === 'merge') return `Merge ${modifier || ''} onto ${name}, dhyaan se`;
  if(type === 'fork') return `Fork aa raha hai — keep ${modifier || ''} onto ${name}`;
  if(type === 'end of road') return `Road khatam ho raha hai — turn ${modifier || ''} onto ${name}`;
  if(type === 'roundabout' || type === 'rotary') return `Gol chakkar mein, take the exit onto ${name}`;
  return `Seedha continue onto ${name}`;
}

function maneuverRotation(modifier){
  const map = { straight:0, 'slight right':30, right:75, 'sharp right':120, 'sharp left':-120, left:-75, 'slight left':-30, uturn:180 };
  return map[modifier] || 0;
}

function avatarInitial(user){
  const letter = ((user && (user.handle || user.email)) || '?').trim().charAt(0).toUpperCase();
  return `<span>${letter}</span>`;
}

function memberSinceLabel(user){
  const raw = user && user.created_at;
  const d = raw ? new Date(raw) : null;
  if(!d || isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-US', { month:'short', year:'numeric' });
}

function isIOS(){ return /iP(hone|od|ad)/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1); }

function isAndroid(){ return /Android/i.test(navigator.userAgent); }

function haversine(lat1,lon1,lat2,lon2){
  const R=6371, toRad=d=>d*Math.PI/180;
  const dLat=toRad(lat2-lat1), dLon=toRad(lon2-lon1);
  const a=Math.sin(dLat/2)**2+Math.cos(toRad(lat1))*Math.cos(toRad(lat2))*Math.sin(dLon/2)**2;
  return R*2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));
}

function hypeBadgeHtml(p){
  if(!p.isHyped) return '';
  const label = p.hypeRank ? `Trending now · #${p.hypeRank}` : 'Trending now';
  return `<div class="hype-badge"><span class="flame">🔥</span> ${label}</div>`;
}

// "Live Thikana" badge — a plain count of who's checked in right now (see
// functions/api/place-presence.js). Deliberately separate from the hype
// badge: hype is "busy lately", this is "someone's there this minute" —
// a place can be one, both, or neither.
function liveBadgeHtml(p){
  if(!p.liveCount) return '';
  const label = p.liveCount === 1 ? '1 person here now' : `${p.liveCount} people here now`;
  return `<div class="live-badge"><span class="live-dot"></span>${label}</div>`;
}

function navArrowIcon(heading){
  return { html:`<div style="width:34px;height:34px;display:flex;align-items:center;justify-content:center;">
      <svg width="30" height="30" viewBox="0 0 24 24" style="transform:rotate(${heading}deg);filter:drop-shadow(0 2px 5px rgba(0,0,0,0.4));">
        <circle cx="12" cy="12" r="11" fill="#5B21B6" fill-opacity="0.16"/>
        <path d="M12 2 L18 20 L12 16 L6 20 Z" fill="#FF3B5C" stroke="#FFFFFF" stroke-width="1"/>
      </svg>
    </div>`,
    width:34, height:34 };
}

function userLocIcon(){
  return { html:`<div class="user-loc-dot"><div class="user-loc-pulse"></div></div>`, width:16, height:16 };
}

function searchPinIcon(){
  return { html:`<div style="width:30px;height:40px;">
    <svg width="30" height="40" viewBox="0 0 30 40" style="overflow:visible">
      <defs><linearGradient id="msg" x1="0" y1="0" x2="30" y2="40">
        <stop offset="0" stop-color="#FA7E1E"/><stop offset="0.5" stop-color="#D62976"/><stop offset="1" stop-color="#962FBF"/>
      </linearGradient></defs>
      <path d="M15 0C6.7 0 0 6.7 0 15c0 11.2 15 25 15 25s15-13.8 15-25C30 6.7 23.3 0 15 0z" fill="url(#msg)"/>
      <circle cx="15" cy="15" r="6" fill="#FFFFFF"/>
    </svg></div>`,
    width:30, height:40 };
}

// Photon (photon.komoot.io) returns GeoJSON Features: geometry.coordinates
// is [lon, lat] (GeoJSON order, note the flip vs lat/lon), and the place's
// name/address bits live under properties. This coerces that into the same
// { lat, lon, display_name } shape the search-results UI and
// pickMapSearchResult() already expect.
function coercePhotonResult(f){
  const coords = f && f.geometry && f.geometry.coordinates;
  if(!coords || coords.length < 2) return null;
  const lon = coords[0], lat = coords[1];
  if(lat == null || lon == null) return null;
  const p = f.properties || {};
  const name = p.name || '';
  const addrParts = [p.street, p.city, p.state, p.country].filter(Boolean);
  const addr = addrParts.join(', ');
  // name/addr kept separate (not just merged into display_name) so callers
  // that need to reason about them independently — e.g. deciding whether a
  // named result is actually close enough to a tapped point to use its name
  // at all — don't have to re-parse a combined string.
  return { lat: parseFloat(lat), lon: parseFloat(lon), name, addr, display_name: [name, addr].filter(Boolean).join(', ') };
}

export {
  escapeHtml,
  timeAgo,
  fmtDist,
  fmtClockTime,
  bearing,
  stepText,
  maneuverRotation,
  avatarInitial,
  memberSinceLabel,
  isIOS,
  isAndroid,
  haversine,
  hypeBadgeHtml,
  liveBadgeHtml,
  navArrowIcon,
  userLocIcon,
  searchPinIcon,
  coercePhotonResult
};
