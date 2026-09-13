/**
 * Sesi MAX MODE v2.11 by Whempy & Dhon — iOS 17 style popup.
 * 30s · Max HD · no watermark are locked. Auto-download is a user setting (default OFF,
 * toggle in Settings). The UI shows status, a Scan button and a per-video download list for the most recent Dola tab.
 * Works as a side panel (desktop) and as a standalone tab (Kiwi & other mobile browsers).
 */
(() => {
    'use strict';
    const DOLA_URL = /^https?:\/\/([a-z0-9-]+\.)*(dola\.com|seaart\.ai)\//i;
    const $ = id => document.getElementById(id);
    const isMobileUa = /Android|iPhone|iPad|Mobile/i.test(navigator.userAgent || '');
    const hasSidePanel = Boolean(globalThis.chrome?.sidePanel);
    const standalone = !hasSidePanel || isMobileUa || new URLSearchParams(location.search).get('mode') === 'popup';
    document.documentElement.classList.toggle('is-mobile', isMobileUa);
    document.documentElement.classList.toggle('is-standalone', standalone);
    document.documentElement.classList.toggle('no-side-panel', !hasSidePanel);

    const query = info => new Promise(resolve => chrome.tabs.query(info, tabs => resolve(tabs || [])));
    const send = (tabId, msg) => new Promise(resolve => {
        try { chrome.tabs.sendMessage(tabId, msg, res => { void chrome.runtime.lastError; resolve(res || null); }); }
        catch { resolve(null); }
    });

    async function findDolaTab() {
        const all = await query({});
        const dola = all.filter(t => DOLA_URL.test(t.url || t.pendingUrl || ''));
        if (!dola.length) return null;
        dola.sort((a, b) => (a.active !== b.active) ? (a.active ? -1 : 1) : (Number(b.lastAccessed) || 0) - (Number(a.lastAccessed) || 0));
        return dola[0];
    }

    function setHint(text, tone) {
        const el = $('hint'); el.textContent = text || ''; if (tone) el.dataset.tone = tone; else delete el.dataset.tone;
    }

    let currentTab = null;
    let lastVideos = [];
    let lastStatus = null;

    function fmtBytes(b) { b = Number(b) || 0; if (!b) return ''; if (b < 1048576) return `${Math.round(b / 1024)} KB`; return `${(b / 1048576).toFixed(1)} MB`; }
    function fmtDur(d) { d = Math.round(Number(d) || 0); if (!d) return ''; if (d > 1000) d = Math.round(d / 1000); return `${Math.floor(d / 60)}:${String(d % 60).padStart(2, '0')}`; }
    function esc(t) { return String(t || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]); }

    function renderVideos(videos, collecting) {
        const list = $('vid-list');
        const sig = JSON.stringify(videos.map(v => [v.key, v.saved, v.saving]));
        if (list.dataset.sig === sig && list.childElementCount) return;
        list.dataset.sig = sig;
        list.innerHTML = '';
        $('vid-title').textContent = videos.length ? `Video siap download (${videos.length})` : 'Video siap download';
        if (!videos.length) {
            const e = document.createElement('div'); e.className = 'vid-empty';
            e.textContent = collecting ? 'Memindai chat… video akan muncul di sini.' : 'Belum ada video. Ketuk Scan chat di halaman Dola yang berisi video.';
            list.appendChild(e); return;
        }
        videos.forEach((v, i) => {
            const row = document.createElement('div'); row.className = 'vid-row'; row.dataset.key = v.key;
            const meta = [v.width && v.height ? `${v.width}×${v.height}` : '', fmtBytes(v.bytes), v.saved ? 'Tersimpan · Download/Whempy_Videos' : ''].filter(Boolean).join(' · ');
            row.innerHTML = `
                <div class="vid-thumb" ${v.poster ? `style="background-image:url('${esc(v.poster)}')"` : ''}>${v.poster ? '' : '🎬'}${v.duration ? `<span class="dur">${fmtDur(v.duration)}</span>` : ''}</div>
                <div class="vid-copy"><span class="vid-name">${esc(v.title || `Video ${videos.length - i}`)}</span><span class="vid-meta">${esc(meta)}</span></div>
                <button class="btn ${v.saved ? 'saved' : 'primary'} ${v.saving ? 'saving' : ''}" type="button" ${v.saving ? 'disabled' : ''}>${v.saved ? '✓ Tersimpan' : v.saving ? 'Menyimpan…' : '⬇ Download'}</button>`;
            row.querySelector('button').addEventListener('click', async ev => {
                const btn = ev.currentTarget;
                if (!currentTab) return;
                btn.disabled = true; btn.textContent = v.saved ? 'Mengunduh ulang…' : 'Menyimpan…'; btn.classList.add('saving');
                const res = await send(currentTab.id, { type: 'WHEMPY_DOWNLOAD_ONE', key: v.key });
                if (!res?.ok) { setHint(res?.reason || res?.error || 'Gagal memulai unduhan. Coba scan ulang.', 'err'); btn.disabled = false; btn.classList.remove('saving'); btn.textContent = '⬇ Download'; }
                else setHint(res.downloaded === false && res.reason ? res.reason : 'Mengunduh HD MP4 tanpa watermark → Download/Whempy_Videos', 'ok');
                setTimeout(refresh, 900);
            });
            list.appendChild(row);
        });
    }

    async function refresh() {
        currentTab = await findDolaTab();
        const pill = $('status-pill');
        const scanBtn = $('btn-scan');
        if (!currentTab) {
            $('status-text').textContent = 'Dola not open'; pill.dataset.state = 'off';
            $('n-found').textContent = '0'; $('n-saved').textContent = '0';
            scanBtn.disabled = true; scanBtn.classList.remove('scanning'); scanBtn.textContent = 'Scan chat';
            renderVideos([], false);
            setHint(standalone ? 'Buka Dola di tab lain, lalu kembali ke sini.' : 'Buka dola.com di sebuah tab untuk mulai.');
            return;
        }
        const status = await send(currentTab.id, { type: 'WHEMPY_GET_STATUS' });
        if (!status) {
            $('status-text').textContent = 'Reload Dola tab'; pill.dataset.state = 'off';
            scanBtn.disabled = true;
            renderVideos([], false);
            setHint('MAX MODE belum aktif di tab ini — muat ulang halaman Dola sekali.', 'err');
            return;
        }
        lastStatus = status;
        lastVideos = Array.isArray(status.videos) ? status.videos : [];
        $('status-text').textContent = status.collecting ? 'Scanning…' : 'Active'; pill.dataset.state = 'on';
        $('n-found').textContent = String(status.found ?? 0);
        $('n-saved').textContent = String(status.saved ?? 0);
        scanBtn.disabled = false;
        scanBtn.classList.toggle('scanning', Boolean(status.collecting));
        scanBtn.textContent = status.collecting ? 'Stop scan' : (status.found > 0 ? 'Scan ulang chat' : 'Scan chat');
        renderVideos(lastVideos, Boolean(status.collecting));
        if (status.collecting) setHint('Memindai riwayat chat untuk video…');
        else if (autoDl) setHint(status.found > 0 ? 'Video baru otomatis tersimpan sebagai HD MP4 tanpa watermark.' : 'Scroll chat Dola (atau ketuk Scan chat) untuk mengumpulkan video lama.');
        else setHint(status.found > 0 ? `${status.found} video ditemukan — ketuk Download pada video yang mau disimpan.` : 'Ketuk Scan chat untuk mencari video di percakapan ini.');
    }

    $('btn-scan').addEventListener('click', async () => {
        if (!currentTab) return;
        $('btn-scan').disabled = true;
        const res = await send(currentTab.id, { type: 'WHEMPY_SCAN' });
        if (!res?.ok) setHint(res?.reason || 'Tidak bisa memulai scan. Muat ulang tab Dola lalu coba lagi.', 'err');
        else setHint(res.stopped ? 'Scan dihentikan.' : 'Memindai chat… video akan muncul di daftar.', 'ok');
        setTimeout(refresh, 600);
    });

    $('btn-open-dola').addEventListener('click', async () => {
        const tab = await findDolaTab();
        if (tab) chrome.tabs.update(tab.id, { active: true }, () => void chrome.runtime.lastError);
        else chrome.tabs.create({ url: 'https://www.dola.com/' });
        if (standalone) setTimeout(() => window.close(), 150);
    });

    // ---- Settings: Auto-download (default OFF) ----
    let autoDl = false;
    const sw = $('sw-auto-dl');
    chrome.storage.local.get(['autoDownload'], res => {
        void chrome.runtime.lastError;
        autoDl = res?.autoDownload === true;
        sw.checked = autoDl;
    });
    sw.addEventListener('change', () => {
        autoDl = sw.checked;
        chrome.storage.local.set({ autoDownload: autoDl }, () => void chrome.runtime.lastError);
        setHint(autoDl ? 'Auto-download aktif — video baru otomatis tersimpan.' : 'Auto-download mati — pilih video dari daftar untuk menyimpan.', 'ok');
    });
    chrome.storage.onChanged.addListener((changes, area) => {
        if (area === 'local' && changes.autoDownload) { autoDl = changes.autoDownload.newValue === true; sw.checked = autoDl; }
    });

    // ---- Settings: Force 1 video x 30s (default ON) + Aggressive mode (default ON) ----
    const swSingle = $('sw-single-clip');
    const swAggr = $('sw-aggressive');
    const swSplit = $('sw-accept-split');
    const swForce25 = $('sw-force-25');
    const swAnime = $('sw-anime-ref');
    const leds = () => { $('led-single').classList.toggle('off', !swSingle.checked); $('led-anime').classList.toggle('off', !swAnime.checked); };
    chrome.storage.local.get(['singleClip', 'aggressiveMode', 'autoAcceptSplit', 'forceModel25', 'animeRef'], res => {
        void chrome.runtime.lastError;
        swSingle.checked = res?.singleClip !== false;
        swAggr.checked = res?.aggressiveMode !== false;
        swSplit.checked = res?.autoAcceptSplit === true;
        swForce25.checked = res?.forceModel25 !== false;
        swAnime.checked = res?.animeRef !== false;
        leds();
    });
    swAnime.addEventListener('change', () => {
        leds();
        chrome.storage.local.set({ animeRef: swAnime.checked }, () => void chrome.runtime.lastError);
        setHint(swAnime.checked ? 'Referensi animasi AKTIF — setiap prompt video dengan gambar referensi diberi catatan "ini karakter animasi, bukan orang asli", dan penolakan "wajah asli" dijawab otomatis.' : 'Referensi animasi nonaktif — Dola menilai gambar referensi apa adanya.', 'ok');
    });
    swForce25.addEventListener('change', () => {
        chrome.storage.local.set({ forceModel25: swForce25.checked }, () => void chrome.runtime.lastError);
        setHint(swForce25.checked ? 'Kunci 2.5 AKTIF — model 1.0/2.0 di payload ditulis ulang ke Seedance 2.5 (ID dipelajari otomatis). Ingat: 2.5 = 5× kredit.' : 'Kunci 2.5 nonaktif — model mengikuti pilihan Dola.', 'ok');
    });
    swSplit.addEventListener('change', () => {
        chrome.storage.local.set({ autoAcceptSplit: swSplit.checked }, () => void chrome.runtime.lastError);
        setHint(swSplit.checked ? 'Auto-terima 2×15s AKTIF — kalau Dola menolak 30s, dijawab "Ya" otomatis.' : 'Auto-terima nonaktif — kamu putuskan manual saat Dola menawarkan 2 video.', 'ok');
    });
    swSingle.addEventListener('change', () => {
        leds();
        chrome.storage.local.set({ singleClip: swSingle.checked }, () => void chrome.runtime.lastError);
        setHint(swSingle.checked ? 'Paksa 1 video × 30s AKTIF — payload, prompt & jawaban chat dipaksa jadi satu klip.' : 'Paksa 1 video dimatikan — Dola boleh membagi jadi beberapa klip.', 'ok');
    });
    swAggr.addEventListener('change', () => {
        chrome.storage.local.set({ aggressiveMode: swAggr.checked }, () => void chrome.runtime.lastError);
        setHint(swAggr.checked ? 'Mode Ganas AKTIF — prompt disuntik directive 1 klip, pertanyaan "2 video?" dijawab otomatis.' : 'Mode Ganas nonaktif — hanya rewrite payload.', 'ok');
    });
    chrome.storage.onChanged.addListener((changes, area) => {
        if (area !== 'local') return;
        if (changes.singleClip) swSingle.checked = changes.singleClip.newValue !== false;
        if (changes.aggressiveMode) swAggr.checked = changes.aggressiveMode.newValue !== false;
        if (changes.autoAcceptSplit) swSplit.checked = changes.autoAcceptSplit.newValue === true;
        if (changes.forceModel25) swForce25.checked = changes.forceModel25.newValue !== false;
        if (changes.animeRef) swAnime.checked = changes.animeRef.newValue !== false;
        leds();
    });

    refresh();
    setInterval(refresh, 1500);
})();
