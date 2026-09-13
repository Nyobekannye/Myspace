(() => {
    'use strict';
    const B = window.__WhempyBridge;
    if (!B) return;
    const $ = id => document.getElementById(id);
    const esc = s => String(s || '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

    function render() {
        let list = [];
        try { list = JSON.parse(B.credsList() || '[]'); } catch (e) { list = []; }
        const box = $('acc-list');
        if (!list.length) { box.innerHTML = '<div class="acc-empty">Belum ada akun tersimpan.</div>'; return; }
        box.innerHTML = list.map(a => `
            <div class="row acc-row" data-id="${esc(a.id)}">
                <div class="row-icon acc" aria-hidden="true">👤</div>
                <div class="row-copy">
                    <span class="row-title">${esc(a.label)}</span>
                    <span class="row-sub">${esc(a.email)}</span>
                </div>
                <div class="row-actions">
                    <button class="btn primary acc-fill" type="button">Isi ke Google</button>
                    <button class="btn danger acc-del" type="button" title="Hapus">✕</button>
                </div>
            </div>`).join('');
    }

    document.addEventListener('click', ev => {
        const fillBtn = ev.target.closest('.acc-fill');
        if (fillBtn) {
            const id = fillBtn.closest('.acc-row').dataset.id;
            const r = B.fillLogin(id);
            const hint = $('hint');
            if (r === 'OK') {
                if (hint) { hint.textContent = 'Mengisi form Google…'; hint.dataset.tone = 'ok'; }
                setTimeout(() => { if (window.__WhempyBridge) B.closePopup ? B.closePopup() : window.close(); }, 300);
            } else if (hint) { hint.textContent = (r || '').replace(/^ERR:/, '') || 'Gagal mengisi form.'; hint.dataset.tone = 'err'; }
            return;
        }
        const delBtn = ev.target.closest('.acc-del');
        if (delBtn) {
            const row = delBtn.closest('.acc-row');
            if (confirm('Hapus akun ' + (row.querySelector('.row-sub')?.textContent || '') + '?')) { B.credsDelete(row.dataset.id); render(); }
            return;
        }
        if (ev.target.closest('#acc-toggle-pw')) {
            const pw = $('acc-password');
            pw.type = pw.type === 'password' ? 'text' : 'password';
            ev.target.textContent = pw.type === 'password' ? '👁' : '🙈';
        }
    });

    const shareBtn = $('btn-share-log');
    if (shareBtn) shareBtn.addEventListener('click', () => { try { B.shareDebugLog(); } catch (e) {} });

    $('acc-save').addEventListener('click', () => {
        const email = $('acc-email').value.trim();
        const password = $('acc-password').value;
        const label = $('acc-label').value.trim();
        const hint = $('hint');
        if (!email || !password) { if (hint) { hint.textContent = 'Isi email dan password dulu.'; hint.dataset.tone = 'err'; } return; }
        const r = B.credsSave(JSON.stringify({ email, password, label }));
        if (String(r).indexOf('ERR:') === 0) { if (hint) { hint.textContent = r.slice(4); hint.dataset.tone = 'err'; } return; }
        $('acc-email').value = ''; $('acc-password').value = ''; $('acc-label').value = '';
        if (hint) { hint.textContent = 'Akun disimpan di HP ini.'; hint.dataset.tone = 'ok'; }
        render();
    });

    render();
})();
