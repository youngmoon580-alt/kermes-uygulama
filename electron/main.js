const { app, BrowserWindow, shell, Menu, ipcMain } = require('electron');
const path = require('path');
const fs = require('fs');

const HOME = 'https://app.kermesdefterim.com/';
const HOST = 'app.kermesdefterim.com';

if (!app.requestSingleInstanceLock()) {
  app.quit();
}

let win;

function offlinePage() {
  const html = `<html><body style="font-family:Segoe UI,sans-serif;background:#f6f1e7;color:#184d43;text-align:center;padding-top:20vh">
  <h2>Bağlantı kurulamadı</h2><p>İnternet bağlantını kontrol et.</p>
  <button onclick="location.href='${HOME}'" style="padding:12px 28px;border:0;border-radius:12px;background:#184d43;color:#fff;font-size:16px;cursor:pointer">Tekrar dene</button>
  </body></html>`;
  return 'data:text/html;charset=utf-8,' + encodeURIComponent(html);
}

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 420,
    minHeight: 600,
    backgroundColor: '#184d43',
    title: 'Kermes Defterim',
    icon: path.join(__dirname, 'build', 'icon.ico'),
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true, preload: path.join(__dirname, 'preload.js') }
  });
  Menu.setApplicationMenu(null);
  win.loadURL(HOME);

  win.webContents.setWindowOpenHandler(({ url }) => {
    try {
      if (new URL(url).hostname !== HOST) { shell.openExternal(url); return { action: 'deny' }; }
    } catch (e) {}
    return { action: 'allow' };
  });
  win.webContents.on('will-navigate', (e, url) => {
    try {
      if (new URL(url).hostname !== HOST && !url.startsWith('data:')) { e.preventDefault(); shell.openExternal(url); }
    } catch (e2) {}
  });
  win.webContents.on('did-fail-load', (e, code, desc, url, isMain) => {
    if (isMain && code !== -3) win.loadURL(offlinePage());
  });
  win.webContents.on('before-input-event', (e, input) => {
    if (input.type === 'keyDown' && (input.key === 'F5' || (input.control && input.key.toLowerCase() === 'r'))) {
      win.loadURL(HOME); e.preventDefault();
    }
    if (input.type === 'keyDown' && input.key === 'F11') win.setFullScreen(!win.isFullScreen());
  });
}

// Sayfayı PDF'e çevirip bilgisayarın PDF görüntüleyicisinde açar (önizleme ve yazdır düğmesi orada).
ipcMain.handle('pdf-yazdir', async (event, ad) => {
  let host = '';
  try { host = new URL(event.senderFrame.url).hostname; } catch (e) {}
  if (host !== HOST) return { ok: false };
  const data = await event.sender.printToPDF({ printBackground: true, preferCSSPageSize: true });
  const temiz = String(ad || 'Kermes Defterim').replace(/[^\p{L}\p{N} _-]/gu, '').trim().slice(0, 60) || 'Kermes Defterim';
  const dosya = path.join(app.getPath('temp'), `${temiz} ${Date.now()}.pdf`);
  await fs.promises.writeFile(dosya, data);
  const hata = await shell.openPath(dosya);
  return { ok: !hata, hata };
});

app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
