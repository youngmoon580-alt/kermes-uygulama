const { app, BrowserWindow, shell, Menu } = require('electron');
const path = require('path');

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
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true }
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

app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
