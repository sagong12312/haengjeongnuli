// 모든 화면 공통 동작 (Content-Security-Policy 때문에 HTML 안에 스크립트를 쓰지 않는다)
//  data-confirm="질문"      : 폼 제출·버튼 누름 전에 확인
//  data-require="입력칸 id"  : 그 칸이 비어 있으면 data-require-msg 를 보여 주고 멈춤
//  data-require-checked="선택자" : 고른 것이 없으면 data-require-msg 를 보여 주고 멈춤
//  data-autosubmit          : 값이 바뀌면 폼 제출
//  data-print               : 인쇄
(function () {
  function check(el) {
    var req = el.getAttribute('data-require');
    if (req) {
      var f = document.getElementById(req);
      if (f && !f.value.trim()) {
        alert(el.getAttribute('data-require-msg') || '내용을 입력하세요.');
        f.focus();
        return false;
      }
    }
    var sel = el.getAttribute('data-require-checked');
    if (sel && !document.querySelector(sel)) {
      alert(el.getAttribute('data-require-msg') || '하나 이상 고르세요.');
      return false;
    }
    var q = el.getAttribute('data-confirm');
    return !q || confirm(q);
  }
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (form.hasAttribute && form.hasAttribute('data-confirm') && !check(form)) {
      e.preventDefault();
    }
  }, true);
  document.addEventListener('click', function (e) {
    var el = e.target.closest ? e.target.closest('button[data-confirm], a[data-confirm], input[data-confirm], button[data-require], button[data-require-checked]') : null;
    if (el && !check(el)) {
      e.preventDefault();
      e.stopPropagation();
      return;
    }
    if (e.target.closest && e.target.closest('[data-print]')) {
      window.print();
    }
  }, true);
  document.addEventListener('change', function (e) {
    if (e.target.hasAttribute && e.target.hasAttribute('data-autosubmit') && e.target.form) {
      e.target.form.submit();
    }
  });
})();

// 자동 로그아웃: 남은 시간 표시, 1분 전 경고·연장, 시간이 다 되면 로그인 화면으로
(function () {
  var box = document.querySelector('.session-timer');
  if (!box) { return; }
  var total = parseInt(box.getAttribute('data-timeout'), 10) * 1000;
  var left = box.querySelector('.session-left');
  var KEY = 'hjn-last-activity';
  function now() { return Date.now(); }
  function mark() { try { localStorage.setItem(KEY, String(now())); } catch (e) { /* 저장 못 해도 이 창 기준으로 센다 */ } }
  var last = now();
  mark();
  function lastActivity() {
    var v = 0;
    try { v = parseInt(localStorage.getItem(KEY) || '0', 10); } catch (e) { v = 0; }
    return Math.max(last, v || 0); // 다른 창에서 일한 시간도 반영
  }
  var warned = false, banner = null;
  function warn(on) {
    if (on && !banner) {
      banner = document.createElement('div');
      banner.className = 'session-warning';
      banner.setAttribute('role', 'alert');
      banner.innerHTML = '1분 뒤 자동으로 로그아웃됩니다. 계속 쓰려면 <button type="button" class="btn btn-primary btn-sm">로그인 연장</button>';
      banner.querySelector('button').addEventListener('click', extend);
      document.body.appendChild(banner);
    } else if (!on && banner) {
      banner.remove();
      banner = null;
    }
  }
  function extend() {
    fetch(box.getAttribute('data-ping'), { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) {
        if (r.ok) { last = now(); mark(); warned = false; warn(false); }
        else { location.href = box.getAttribute('data-expired'); }
      });
  }
  box.addEventListener('click', extend);
  var shown = '';
  function tick() {
    var remain = total - (now() - lastActivity());
    if (remain <= 0) { location.href = box.getAttribute('data-expired'); return; }
    var s = Math.ceil(remain / 1000);
    // 5분이 넘게 남았으면 분 단위로만 고쳐 그린다 (저사양 휴대폰에서 매초 다시 그리지 않게)
    var text = s > 300 ? Math.ceil(s / 60) + '분' : Math.floor(s / 60) + ':' + ('0' + (s % 60)).slice(-2);
    if (text !== shown) { left.textContent = text; shown = text; }
    var soon = s <= 60;
    if (soon !== box.classList.contains('soon')) box.classList.toggle('soon', soon);
    if (soon && !warned) { warned = true; warn(true); }
    if (!soon && warned) { warned = false; warn(false); }
  }
  // 글을 쓰는 동안(화면 요청 없이 입력만 할 때)에도 로그인을 이어 준다: 5분에 한 번만 서버에 알린다
  var lastPing = now();
  function busy() {
    if (now() - lastPing < 5 * 60 * 1000 || warned) return;
    lastPing = now();
    fetch(box.getAttribute('data-ping'), { credentials: 'same-origin', cache: 'no-store' })
      .then(function (r) { if (r.ok) { last = now(); mark(); } })
      .catch(function () { /* 연결이 잠깐 끊겨도 다음 입력 때 다시 */ });
  }
  document.addEventListener('input', busy, { passive: true });
  document.addEventListener('keydown', busy, { passive: true });
  var timer = null;
  function run() {
    if (timer) { clearInterval(timer); timer = null; }
    if (document.hidden) return; // 다른 앱으로 가 있는 동안은 멈춘다 (배터리·CPU 절약)
    tick();
    timer = setInterval(tick, 1000);
  }
  document.addEventListener('visibilitychange', run);
  run();
})();

// 민원 처리: 추천 담당자 누르면 담당자 칸을 고르고, 답변 문구를 고르면 결과 칸 끝에 넣는다
(function () {
  document.querySelectorAll('[data-pick-select]').forEach(function (b) {
    b.addEventListener('click', function () {
      var sel = document.getElementById(b.getAttribute('data-pick-select'));
      if (sel) { sel.value = b.getAttribute('data-value'); sel.focus(); }
    });
  });
  document.querySelectorAll('select[data-fill-target]').forEach(function (sel) {
    sel.addEventListener('change', function () {
      var t = document.getElementById(sel.getAttribute('data-fill-target'));
      if (!t || !sel.value) return;
      t.value = t.value ? t.value.replace(/\s+$/, '') + '\n\n' + sel.value : sel.value;
      sel.value = '';
      t.focus();
    });
  });
})();

// 보안 프로그램: 설치 확인 주소가 있으면 이 PC 에서 프로그램이 실제로 응답하는지 본 뒤에만 '설치했습니다'를 보낸다
(function () {
  document.querySelectorAll('form[data-check-url]').forEach(function (form) {
    var url = form.getAttribute('data-check-url');
    if (!url) return;
    var checked = false;
    form.addEventListener('submit', function (e) {
      if (checked) return;
      e.preventDefault();
      var btn = form.querySelector('button');
      if (btn) { btn.disabled = true; btn.textContent = '설치 확인 중…'; }
      var done = false;
      var timer = setTimeout(function () { fail(); }, 4000);
      function fail() {
        if (done) return; done = true;
        if (btn) { btn.disabled = false; btn.textContent = '설치했습니다'; }
        alert((form.getAttribute('data-check-name') || '보안 프로그램') + '이(가) 이 PC에서 실행 중이 아닙니다. 설치한 뒤 실행하고 다시 누르세요.');
      }
      fetch(url, { mode: 'no-cors', cache: 'no-store', credentials: 'omit' })
        .then(function () { if (done) return; done = true; clearTimeout(timer); checked = true; form.submit(); })
        .catch(function () { clearTimeout(timer); fail(); });
    });
  });
})();

// 휴대폰에서 표를 카드처럼 보이게: 머리글 이름을 칸마다 data-label 로 붙이고, 옆으로 넘치는 표 영역은 키보드로도 움직일 수 있게
(function () {
  document.querySelectorAll('table.list').forEach(function (t) {
    var heads = [];
    t.querySelectorAll('thead th').forEach(function (th) { heads.push((th.textContent || '').trim()); });
    if (heads.length) {
      t.querySelectorAll('tbody tr').forEach(function (tr) {
        var i = 0;
        tr.querySelectorAll('td').forEach(function (td) {
          if (!td.hasAttribute('data-label') && heads[i]) td.setAttribute('data-label', heads[i]);
          i += td.colSpan || 1;
        });
      });
      t.classList.add('stacked');
    }
    var wrap = t.closest('.table-wrap');
    if (wrap) {
      if (heads.length) wrap.classList.add('stacked');
      if (!wrap.hasAttribute('tabindex')) { wrap.setAttribute('tabindex', '0'); wrap.setAttribute('role', 'region'); wrap.setAttribute('aria-label', '표'); }
    }
  });
})();
