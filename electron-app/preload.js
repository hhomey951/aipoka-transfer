const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('aipoka', {
  chooseFolder: () => ipcRenderer.invoke('choose-folder'),
  getStatus: () => ipcRenderer.invoke('get-status'),
  getUploads: () => ipcRenderer.invoke('get-uploads'),
  clearUploads: () => ipcRenderer.invoke('clear-uploads'),
  getRegistrations: () => ipcRenderer.invoke('get-registrations'),
  clearRegistrations: () => ipcRenderer.invoke('clear-registrations'),
  removeDevice: (token) => ipcRenderer.invoke('remove-device', token)
});
