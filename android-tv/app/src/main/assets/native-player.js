(function () {
  'use strict';
  if (location.origin !== 'https://ramazanke.github.io' || !/^\/Tvalp\/(?:index\.html)?$/.test(location.pathname)) return;
  if (window.__alpicaNativeInstalling) return;
  window.__alpicaNativeInstalling = true;
  let attempts = 0, number = '', numberTimer;
  const send = value => window.AlpicaNative.postMessage(JSON.stringify(value));
  function install() {
    if (typeof channels !== 'function' || typeof play !== 'function' || typeof wake !== 'function' || !document.getElementById('video') || !window.AlpicaNative) {
      if (++attempts < 150) setTimeout(install, 100);
      else window.__alpicaNativeInstalling = false;
      return;
    }
    const style = document.createElement('style');
    style.textContent = 'html,body{background:transparent!important}#video{visibility:hidden!important;background:transparent!important}header,footer,.manage-link,#fullscreen,#reloadList,#listState{display:none!important}body #welcome{display:none!important}body.cinema .shade{display:none!important}';
    document.head.append(style);
    const htmlVideo = document.getElementById('video');
    if (typeof hls !== 'undefined' && hls) { hls.destroy(); hls = null; }
    htmlVideo.pause(); htmlVideo.removeAttribute('src'); htmlVideo.load();
    document.body.classList.add('cinema');
    fullscreen = async function () { document.body.classList.add('cinema'); return true; };
    if (typeof isFullscreen === 'function') isFullscreen = () => true;
    if (typeof openingFullscreenPending !== 'undefined') openingFullscreenPending = false;
    play = function () {
      const channel = channels()[channelIndex];
      if (!channel) return;
      ++playToken;
      playbackFailed = false; sourceReady = false; usingNative = false;
      if (hls) { hls.destroy(); hls = null; }
      document.body.classList.remove('playback-error');
      document.getElementById('welcome').hidden = true;
      document.getElementById('channelName').textContent = channel.name;
      document.getElementById('categoryLabel').textContent = groups[groupIndex].name + ' · ' + (channelIndex + 1) + ' / ' + channels().length;
      document.getElementById('status').textContent = 'Yayına bağlanılıyor…';
      wake();
      // Preserve the supplied URL. Do not guess a different path or append an extension.
      send({ action: 'play', url: channel.url, token: playToken });
    };
    htmlVideo.pause = function () { send({ action: 'stop' }); };
    tryPlay = async function (token) { if (token !== undefined && token !== playToken) return; send({ action: 'resume', token: playToken }); };
    function commitNumber() {
      if (!number) return;
      const index = Number(number) - 1;
      number = '';
      if (index >= 0 && index < channels().length) { channelIndex = index; closeMenu(); play(); }
      else { document.getElementById('status').textContent = 'Bu kategoride böyle bir kanal numarası yok.'; wake(); }
    }
    window.AlpicaAndroid = {
      key(key) {
        if (key === 'Enter' && number) { clearTimeout(numberTimer); commitNumber(); return; }
        if (number) { clearTimeout(numberTimer); number = ''; }
        document.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
      },
      digit(value) {
        if (!channels().length) return;
        clearTimeout(numberTimer);
        number = (number + value).slice(-3);
        document.getElementById('channelName').textContent = 'Kanal ' + number;
        wake();
        numberTimer = setTimeout(commitNumber, 1300);
      },
      back() {
        if (number) { clearTimeout(numberTimer); number = ''; wake(); return true; }
        if (menu) { closeMenu(); return true; }
        return false;
      },
      state(token, state, message) {
        if (token !== playToken) return;
        document.getElementById('status').textContent = message;
        if (state === 'error') {
          failPlayback(message);
        } else if (state === 'playing' || state === 'ready') {
          playbackFailed = false;
          document.body.classList.remove('playback-error');
          document.getElementById('welcome').hidden = true;
        } else if (state === 'paused') {
          wake();
        }
      }
    };
    send({ action: 'ready' });
    if (channels().length) play();
  }
  install();
})();
