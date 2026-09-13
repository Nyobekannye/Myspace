/**
 * Whempy v1.9 — background side of the mobile compatibility layer.
 * Loaded via importScripts() from background.js.
 *
 * On desktop Chrome (chrome.sidePanel available) we clear the action popup so a
 * click on the toolbar icon opens the side panel. On browsers without the side
 * panel API (Kiwi etc.) the manifest's default_popup stays active and the UI
 * opens as a popup/tab. As an extra fallback, chrome.action.onClicked opens the
 * UI in a new tab when neither mechanism fires.
 */
(() => {
    'use strict';
    const UI_URL = 'popup.html?mode=popup';

    function configureEntryPoint() {
        try {
            if (chrome.sidePanel?.setPanelBehavior) {
                chrome.action?.setPopup?.({popup: ''}, () => void chrome.runtime.lastError);
                chrome.sidePanel.setPanelBehavior({openPanelOnActionClick: true}).catch(() => {});
            } else {
                chrome.action?.setPopup?.({popup: UI_URL}, () => void chrome.runtime.lastError);
            }
        } catch (error) {
            console.warn('[Whempy] Entry point setup failed:', error);
        }
    }

    chrome.runtime.onInstalled.addListener(configureEntryPoint);
    chrome.runtime.onStartup?.addListener(configureEntryPoint);
    configureEntryPoint();

    chrome.action?.onClicked?.addListener(async tab => {
        // Only reached when no popup is set AND the side panel did not handle it.
        if (chrome.sidePanel?.open) {
            try {
                await chrome.sidePanel.open(tab?.windowId ? {windowId: tab.windowId} : {tabId: tab.id});
                return;
            } catch {}
        }
        const url = chrome.runtime.getURL(UI_URL);
        chrome.tabs.query({url: chrome.runtime.getURL('popup.html') + '*'}, tabs => {
            const existing = (tabs || [])[0];
            if (existing?.id) chrome.tabs.update(existing.id, {active: true});
            else chrome.tabs.create({url});
        });
    });
})();
