  (function () {
    var open = document.getElementById('dopen');
    var restrict = document.getElementById('restrict');
    open.addEventListener('change', function () { restrict.hidden = open.value === '공개'; });

    var kind = document.getElementById('dkind');
    var spend = document.getElementById('spend');
    kind.addEventListener('change', function () { spend.hidden = kind.value !== '지출결의'; });

    // 지출 금액: 세 자리마다 쉼표, 한글 금액
    var D = ['', '일', '이', '삼', '사', '오', '육', '칠', '팔', '구'], S = ['', '십', '백', '천'], B = ['', '만', '억', '조'];
    function hangul(n) {
      var s = String(n), out = '';
      for (var i = 0; i < s.length; i++) {
        var d = +s[i], pos = s.length - 1 - i;
        if (d) { out += D[d] + S[pos % 4]; }
        if (pos % 4 === 0 && pos > 0 && /[1-9]/.test(s.substring(Math.max(0, i - 3), i + 1))) { out += B[pos / 4]; }
      }
      return out || '영';
    }
    var amt = document.getElementById('samount'), amtText = document.getElementById('samount-text');
    function showAmount() {
      var raw = amt.value.replace(/[^0-9]/g, '');
      if (!raw) { amtText.textContent = ''; return; }
      amt.value = Number(raw).toLocaleString('ko-KR');
      amtText.textContent = '금' + amt.value + '원(금' + hangul(raw.replace(/^0+/, '') || '0') + '원)';
    }
    amt.addEventListener('input', showAmount);
    showAmount();

    var recvType = document.getElementById('drecvtype');
    recvType.addEventListener('change', function () {
      var ext = recvType.value === 'external';
      document.querySelectorAll('.external').forEach(function (el) { el.hidden = !ext; });
    });

    // 비어 있는 검토·협조 칸은 숨겨 두고 '추가' 버튼으로 하나씩 연다
    function slots(cls, btnId) {
      var items = Array.prototype.slice.call(document.querySelectorAll(cls));
      var btn = document.getElementById(btnId);
      var shown = 0;
      items.forEach(function (li, i) { if (li.querySelector('select').value !== '') { shown = i + 1; } });
      function refresh() {
        items.forEach(function (li, i) { li.hidden = i >= shown; });
        btn.disabled = shown >= items.length;
      }
      btn.addEventListener('click', function () {
        if (shown < items.length) { shown++; refresh(); items[shown - 1].querySelector('select').focus(); }
      });
      refresh();
    }
    slots('.reviewer', 'add-approver');
    slots('.coop', 'add-coop');
  })();

  // 본문 도구: 현재 줄 앞에 항목 기호를 넣거나 표 틀을 넣는다
  (function () {
    var ta = document.getElementById('dbody');
    if (!ta) { return; }
    var depth = {'1.': 0, '가.': 1, '1)': 2, '가)': 3, '(1)': 4, '(가)': 5, '①': 6, '㉮': 7};
    function insert(text, atLineStart) {
      var s = ta.selectionStart, v = ta.value;
      var pos = atLineStart ? v.lastIndexOf('\n', s - 1) + 1 : s;
      ta.value = v.slice(0, pos) + text + v.slice(pos);
      ta.focus();
      ta.selectionStart = ta.selectionEnd = pos + text.length + (atLineStart ? s - pos : 0);
    }
    document.querySelectorAll('.body-tools [data-mark]').forEach(function (b) {
      b.addEventListener('click', function () {
        var m = b.getAttribute('data-mark');
        insert(new Array(depth[m] + 1).join('  ') + m + ' ', true);
      });
    });
    var t = document.querySelector('.body-tools [data-table]');
    if (t) {
      t.addEventListener('click', function () {
        insert('\n| 구분 | 내용 | 비고 |\n|---|---|---|\n|  |  |  |\n|  |  |  |\n', false);
      });
    }
  })();
