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
  function tick() {
    var remain = total - (now() - lastActivity());
    if (remain <= 0) { location.href = box.getAttribute('data-expired'); return; }
    var s = Math.ceil(remain / 1000);
    left.textContent = Math.floor(s / 60) + ':' + ('0' + (s % 60)).slice(-2);
    box.classList.toggle('soon', s <= 60);
    if (s <= 60 && !warned) { warned = true; warn(true); }
    if (s > 60 && warned) { warned = false; warn(false); }
  }
  tick();
  setInterval(tick, 1000);
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
