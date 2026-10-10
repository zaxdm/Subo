'use strict';
const $ = id => document.getElementById(id);
const form = $('route-form');
const state = { base: '', token: '', routes: [], editing: null, stops: [], path: [], map: null, layer: null, busy: false, uploading: false };
const statusLabels = { APPROVED: 'Publicada', PENDING: 'Pendiente', REJECTED: 'Oculta' };
let noticeTimer;
try { $('server-url').value = localStorage.getItem('ruta-norte-server') || ''; } catch {}
function notify(message, error = false) {
  clearTimeout(noticeTimer); $('notice').textContent = message; $('notice').className = error ? 'error' : ''; $('notice').hidden = false;
  if (!error) noticeTimer = setTimeout(() => { $('notice').hidden = true; }, 7000);
}
function normalizeServer(raw) {
  const url = new URL(raw.trim());
  if (url.username || url.password || url.search || url.hash) throw new Error('Usa la dirección del servidor sin credenciales ni parámetros.');
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost','127.0.0.1'].includes(url.hostname))) throw new Error('El servidor debe usar HTTPS (HTTP solo en localhost).');
  return url.href.replace(/\/+$/, '');
}
async function api(path, { method = 'GET', body, image } = {}) {
  const headers = { Accept: 'application/json' };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (body) headers['Content-Type'] = 'application/json';
  if (image) headers['Content-Type'] = image.type;
  const response = await fetch(state.base + path, { method, headers, body: image || (body ? JSON.stringify(body) : undefined), signal: AbortSignal.timeout(30000) });
  const text = await response.text();
  let data = {}; try { data = text ? JSON.parse(text) : {}; } catch { throw new Error('El servidor devolvió una respuesta inesperada. Revisa su dirección.'); }
  if (!response.ok) {
    if (response.status === 401 && path !== '/api/auth/login') signOut();
    throw new Error(data.message || `No se pudo completar la operación (${response.status}).`);
  }
  return data;
}
function busy(value) {
  state.busy = value;
  for (const id of ['save','delete-route','back','refresh','new-route','logout']) $(id).disabled = value;
  for (const element of form.elements) element.disabled = value || element.dataset.intrinsicDisabled === 'true';
}
$('login-form').addEventListener('submit', async event => {
  event.preventDefault(); const button = event.submitter; button.disabled = true;
  try {
    state.base = normalizeServer($('server-url').value);
    state.token = '';
    const login = await api('/api/auth/login', { method: 'POST', body: { email: $('email').value.trim(), password: $('password').value } });
    state.token = login.token;
    const admin = await api('/api/admin/me');
    $('account-name').textContent = admin.displayName;
    try { localStorage.setItem('ruta-norte-server', state.base); } catch {}
    await loadRoutes();
    $('password').value = ''; $('login-section').hidden = true; $('dashboard').hidden = false; $('logout').hidden = false;
    notify('Sesión iniciada. El catálogo está actualizado.');
  } catch (error) { state.token = ''; notify(error.message, true); }
  finally { button.disabled = false; }
});
function signOut() {
  state.token = ''; state.routes = []; state.editing = null; state.stops = []; state.path = [];
  $('dashboard').hidden = true; $('editor').hidden = true; $('login-section').hidden = false; $('logout').hidden = true; $('account-name').textContent = 'Acceso de administrador';
  $('route-grid').replaceChildren(); $('password').value = ''; form.reset();
}
$('logout').onclick = signOut;
async function loadRoutes() {
  const data = await api('/api/admin/routes');
  state.routes = data.routes || []; renderRoutes();
}
function element(tag, className, text) { const node = document.createElement(tag); if (className) node.className = className; if (text !== undefined) node.textContent = text; return node; }
function safeImage(url) { try { return new URL(url).protocol === 'https:' ? url : null; } catch { return null; } }
function renderRoutes() {
  $('total-count').textContent = state.routes.length;
  $('published-count').textContent = state.routes.filter(r => r.status === 'APPROVED').length;
  $('photo-count').textContent = state.routes.filter(r => r.imageUrl).length;
  const query = $('route-search').value.trim().toLocaleLowerCase(); const filter = $('status-filter').value;
  const routes = state.routes.filter(r => (!filter || r.status === filter) && [r.name,r.vehicleName,r.origin,r.destination,r.letter].join(' ').toLocaleLowerCase().includes(query));
  $('route-grid').replaceChildren(); $('empty-catalog').hidden = routes.length > 0;
  for (const route of routes) {
    const card = element('article','route-card');
    const placeholder = element('div','route-placeholder',route.letter || 'RN');
    if (safeImage(route.imageUrl)) { const img = element('img','route-photo'); img.src = route.imageUrl; img.alt = route.vehicleName || route.name; img.loading = 'lazy'; img.referrerPolicy = 'no-referrer'; img.onerror = () => img.replaceWith(placeholder); card.append(img); } else card.append(placeholder);
    const body = element('div','route-card-body');
    body.append(element('span',`status ${route.status}`,statusLabels[route.status] || route.status),element('h2','',route.name),element('p','',`${route.origin} → ${route.destination}`));
    const info = element('div','route-info'); info.append(element('strong','',`S/ ${Number(route.fare).toFixed(2)}`),element('span','',`${route.durationMinutes} min`),element('span','',`${route.stops.length} paraderos`)); body.append(info);
    const edit = element('button','secondary','Editar ruta ↗'); edit.onclick = () => openEditor(route); body.append(edit); card.append(body); $('route-grid').append(card);
  }
}
$('route-search').oninput = renderRoutes; $('status-filter').onchange = renderRoutes;
$('refresh').onclick = async () => { busy(true); try { await loadRoutes(); notify('Catálogo actualizado.'); } catch (e) { notify(e.message,true); } finally { busy(false); } };
$('new-route').onclick = () => openEditor(null);
function openEditor(route) {
  state.editing = route?.id || null; form.reset(); $('image-file').value = '';
  state.stops = structuredClone(route?.stops || []); state.path = structuredClone(route?.path || []);
  for (const key of ['name','letter','vehicleName','origin','destination','fare','durationMinutes','schedule','description','imageUrl','status']) if (route) form.elements.namedItem(key).value = route[key] ?? '';
  form.elements.namedItem('bidirectional').checked = route?.bidirectional !== false;
  $('editor-title').textContent = route ? 'Editar ruta' : 'Nueva ruta'; $('delete-route').hidden = !route;
  $('dashboard').hidden = true; $('editor').hidden = false; updateImagePreview();
  requestAnimationFrame(() => { initializeMap(); state.map?.invalidateSize(); renderStops(); if (state.stops.length) state.map?.fitBounds(state.stops.map(s => [s.latitude,s.longitude]), { padding: [35,35], maxZoom: 15 }); });
  window.scrollTo(0,0);
}
function backToCatalog() { if (state.busy || state.uploading) return; if (!confirm('¿Volver al catálogo? Los cambios que no guardaste se perderán.')) return; $('editor').hidden = true; $('dashboard').hidden = false; }
$('back').onclick = backToCatalog;
function initializeMap() {
  if (state.map) return;
  if (!window.L) { notify('No se pudo cargar el mapa. Revisa tu conexión y vuelve a abrir el panel.',true); return; }
  state.map = L.map('map').setView([-8.112,-79.0288],13);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' }).addTo(state.map);
  state.layer = L.layerGroup().addTo(state.map);
  state.map.on('click', event => {
    if (state.busy) return;
    const point = { latitude: event.latlng.lat, longitude: event.latlng.lng };
    if ($('map-mode').value === 'path') { if (state.path.length >= 20000) return; state.path.push(point); }
    else { if (state.stops.length >= 300) return; state.stops.push({name:`Paradero ${state.stops.length + 1}`, ...point}); }
    renderStops();
  });
}
function renderMap() {
  if (!state.layer) return;
  state.layer.clearLayers();
  const line = state.path.length > 1 ? state.path : state.stops;
  if (line.length > 1) L.polyline(line.map(s => [s.latitude,s.longitude]),{color:'#241f10',weight:5}).addTo(state.layer);
  state.stops.forEach((stop,index) => {
    const marker = L.marker([stop.latitude,stop.longitude],{ draggable: !state.busy, icon: L.divIcon({className:'stop-marker',html:String(index+1),iconSize:[28,28]}) }).addTo(state.layer);
    marker.bindTooltip(element('span','',stop.name));
    marker.on('dragend', () => { if (state.busy) return; const p = marker.getLatLng(); stop.latitude=p.lat; stop.longitude=p.lng; renderStops(); });
  });
}
function renderStops() {
  $('stop-list').replaceChildren(); $('stop-count').textContent = `${state.stops.length} paraderos`; $('no-stops').hidden = state.stops.length >= 2;
  state.stops.forEach((stop,index) => {
    const row = element('div','stop-row'); row.append(element('span','stop-number',String(index+1)));
    const name = element('input'); name.value = stop.name; name.required = true; name.maxLength = 120; name.setAttribute('aria-label',`Nombre del paradero ${index+1}`); name.oninput = () => { stop.name = name.value; }; name.onchange = renderMap; row.append(name);
    for (const [text,label,action,disabled] of [ ['↑','Subir paradero',()=>swapStop(index,index-1),index===0], ['↓','Bajar paradero',()=>swapStop(index,index+1),index===state.stops.length-1], ['×','Quitar paradero',()=>{ state.stops.splice(index,1);renderStops(); },false] ]) { const button=element('button','',text); button.type='button'; button.title=label; button.setAttribute('aria-label',label); button.dataset.intrinsicDisabled=String(disabled); button.disabled=disabled; button.onclick=action; row.append(button); }
    $('stop-list').append(row,element('div','stop-coords',`${stop.latitude.toFixed(5)}, ${stop.longitude.toFixed(5)}`));
  }); renderMap();
}
function swapStop(a,b) { [state.stops[a],state.stops[b]] = [state.stops[b],state.stops[a]]; renderStops(); }
$('undo-point').onclick = () => { ($('map-mode').value === 'path' ? state.path : state.stops).pop(); renderStops(); };
$('clear-path').onclick = () => { if (!confirm('¿Borrar el trazado dibujado? Los paraderos se conservan.')) return; state.path=[];renderMap(); };
function updateImagePreview() {
  const url = form.elements.namedItem('imageUrl').value;
  $('image-preview').replaceChildren();
  if (safeImage(url)) { const image=element('img');image.src=url;image.alt='Foto del micro';image.referrerPolicy='no-referrer';image.onerror=()=>{$('image-preview').replaceChildren(element('span','','No se pudo cargar la imagen. Revisa la URL.'));};$('image-preview').append(image); }
  else $('image-preview').append(element('span','','Una foto ayuda a reconocer el micro'));
}
form.elements.namedItem('imageUrl').onchange=updateImagePreview;
$('clear-image').onclick=()=>{form.elements.namedItem('imageUrl').value='';$('image-file').value='';updateImagePreview();};
$('image-file').onchange = async () => {
  const file=$('image-file').files[0]; if (!file) return;
  if (!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size > 5242880) { notify('Usa JPG, PNG o WebP de hasta 5 MB.',true);$('image-file').value='';return; }
  state.uploading=true; busy(true);
  try { const data=await api('/api/admin/images',{method:'POST',image:file});form.elements.namedItem('imageUrl').value=data.url;updateImagePreview();notify('Foto subida. Guarda la ruta para publicarla.'); }
  catch(e){notify(e.message,true);} finally{state.uploading=false;busy(false);}
};
form.onsubmit = async event => {
  event.preventDefault(); if (state.busy || state.uploading) return;
  if (state.stops.length < 2) { notify('Añade al menos dos paraderos en el mapa.',true);return; }
  if (state.stops.some(s=>!s.name.trim())) {notify('Cada paradero necesita un nombre.',true);return;}
  const body={ stops:state.stops, path:state.path.length>=2?state.path:null, pathSnapped:false, bidirectional:form.elements.namedItem('bidirectional').checked };
  for(const key of ['name','letter','vehicleName','origin','destination','schedule','description','status']) body[key]=form.elements.namedItem(key).value.trim();
  body.imageUrl=form.elements.namedItem('imageUrl').value.trim()||null;
  body.fare=Number(form.elements.namedItem('fare').value);body.durationMinutes=Number(form.elements.namedItem('durationMinutes').value);
  busy(true);
  try { const route=await api('/api/admin/routes'+(state.editing?'/'+encodeURIComponent(state.editing):''),{method:state.editing?'PUT':'POST',body});
    state.routes=state.routes.filter(r=>r.id!==route.id);state.routes.unshift(route);renderRoutes();
    $('editor').hidden=true;$('dashboard').hidden=false;notify('Ruta guardada. Las rutas publicadas estarán disponibles cuando la app actualice su catálogo.');
  } catch(e){notify(e.message,true);}finally{busy(false);}
};
$('delete-route').onclick=async()=>{
  if(!state.editing||!confirm('¿Eliminar esta ruta del catálogo? Esta acción no se puede deshacer.'))return;
  busy(true);try{await api('/api/admin/routes/'+encodeURIComponent(state.editing),{method:'DELETE'});state.routes=state.routes.filter(r=>r.id!==state.editing);renderRoutes();$('editor').hidden=true;$('dashboard').hidden=false;notify('Ruta eliminada.');}catch(e){notify(e.message,true);}finally{busy(false);}
};
window.addEventListener('beforeunload',event=>{if(!$('editor').hidden){event.preventDefault();event.returnValue='';}});
