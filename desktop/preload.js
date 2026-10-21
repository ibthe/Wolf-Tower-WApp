const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('wt', {
  getState: () => ipcRenderer.invoke('state:get'),
  getLogs: () => ipcRenderer.invoke('log:get'),
  toggleProfile: (id, on) => ipcRenderer.invoke('profile:toggle', id, on),
  setStrategy: (id, s) => ipcRenderer.invoke('profile:strategy', id, s),
  toggleProtection: () => ipcRenderer.invoke('protection:toggle'),
  activate: (key) => ipcRenderer.invoke('license:activate', key),
  startProbe: () => ipcRenderer.invoke('probe:start'),
  cancelProbe: () => ipcRenderer.invoke('probe:cancel'),
  clearLog: () => ipcRenderer.invoke('log:clear'),
  copyDiag: () => ipcRenderer.invoke('diag:copy'),
  copyText: (t) => ipcRenderer.invoke('copy', t),
  paste: () => ipcRenderer.invoke('paste'),
  telegramAdd: () => ipcRenderer.invoke('telegram:add'),
  componentLicenses: () => ipcRenderer.invoke('licenses:get'),
  onState: (cb) => ipcRenderer.on('state', (_e, s) => cb(s)),
  onLog: (cb) => ipcRenderer.on('log', (_e, line) => cb(line)),
});
