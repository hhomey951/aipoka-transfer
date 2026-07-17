const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('aipoka', {
  chooseFolder: () => ipcRenderer.invoke('choose-folder'),
  getStatus: () => ipcRenderer.invoke('get-status'),
  getUploads: () => ipcRenderer.invoke('get-uploads'),
  getRegistrations: () => ipcRenderer.invoke('get-registrations')
});
