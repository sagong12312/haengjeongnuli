// 화면 색 (자동 · 밝게 · 어둡게). 화면이 그려지기 전에 정해야 깜빡이지 않으므로 defer 없이 <head> 에서 읽는다.
(function () {
  var t = null;
  try { t = window.localStorage.getItem('hjn-theme'); } catch (e) { t = null; }
  if (t === 'dark' || t === 'light') document.documentElement.setAttribute('data-theme', t);
})();
