  // 금액 입력 중에 세 자리마다 쉼표를 넣고 한글 금액을 보여 준다
  (function () {
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
    var input = document.getElementById('bamount'), text = document.getElementById('bamount-text');
    function show() {
      var raw = input.value.replace(/[^0-9]/g, '');
      if (!raw) { text.textContent = ''; return; }
      input.value = Number(raw).toLocaleString('ko-KR');
      text.textContent = '금' + input.value + '원(금' + hangul(raw.replace(/^0+/, '') || '0') + '원)';
    }
    input.addEventListener('input', show);
    show();
  })();
