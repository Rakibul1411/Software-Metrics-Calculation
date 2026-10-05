const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  onStatus: (callback) => ipcRenderer.on('status-update', (_event, value) => callback(value)),
  onError: (callback) => ipcRenderer.on('error-occurred', (_event, value) => callback(value)),
  retry: () => ipcRenderer.send('retry-start')
});
