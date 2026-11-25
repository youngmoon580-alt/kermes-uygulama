// Uygulama sayfasına küçük bir köprü açar: QR kartlarını PDF olarak hazırlayıp açmak için.
// (Windows'un yazdırma penceresi bu uygulamada önizleme gösteremiyor; PDF'te önizleme ve yazdırma düğmesi var.)
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('KermesPC', {
  pdfYazdir: (ad) => ipcRenderer.invoke('pdf-yazdir', String(ad || '')),
});
