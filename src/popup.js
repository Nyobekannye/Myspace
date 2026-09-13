const hideToggle = document.querySelector("#hide-face");
const bypassToggle = document.querySelector("#bypass-face");

chrome.storage.sync.get({ hideFaceControls: true, bypassFaceFilter: true }, settings => {
  hideToggle.checked = settings.hideFaceControls;
  bypassToggle.checked = settings.bypassFaceFilter;
});

hideToggle.addEventListener("change", () => {
  chrome.storage.sync.set({ hideFaceControls: hideToggle.checked });
});

bypassToggle.addEventListener("change", () => {
  chrome.storage.sync.set({ bypassFaceFilter: bypassToggle.checked });
});
