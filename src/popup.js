const toggle = document.querySelector("#hide-face");

chrome.storage.sync.get({ hideFaceControls: true }, settings => {
  toggle.checked = settings.hideFaceControls;
});

toggle.addEventListener("change", () => {
  chrome.storage.sync.set({ hideFaceControls: toggle.checked });
});
