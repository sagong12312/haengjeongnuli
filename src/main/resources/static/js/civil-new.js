  (function () {
    var api = document.getElementById('civil-new-js').getAttribute('data-api');
    var kind = document.getElementById('ckind');
    var qtype = document.getElementById('cqtype');
    var affair = document.getElementById('caffair');

    function refresh() {
      var k = kind.value;
      document.getElementById('qtype-field').hidden = k !== 'QUERY';
      document.getElementById('affair-field').hidden = k !== 'LEGAL';
      affair.required = k === 'LEGAL';
      var params = new URLSearchParams({ kind: k, law: qtype.value });
      if (k === 'LEGAL' && affair.value) { params.set('affair', affair.value); }
      fetch(api + '?' + params.toString())
        .then(function (r) { return r.json(); })
        .then(function (d) {
          document.getElementById('period').textContent = d.period;
          document.getElementById('deadline').textContent = d.deadline;
        });
    }
    [kind, qtype, affair].forEach(function (el) { el.addEventListener('change', refresh); });
    refresh();

    // 연락처: 휴대전화 없음 → 집전화, 집전화도 없음 → 안내 / 문자 통지 막기
    var noTel = document.getElementById('no-tel');
    var noHome = document.getElementById('no-home');
    var tel = document.getElementById('ctel');
    var home = document.getElementById('chome');
    var sms = document.getElementById('notify-sms');

    function contacts() {
      var mobileNone = noTel.checked;
      if (!mobileNone) { noHome.checked = false; }
      var homeNone = mobileNone && noHome.checked;
      tel.disabled = mobileNone;
      tel.required = !mobileNone;
      if (mobileNone) { tel.value = ''; }
      tel.placeholder = mobileNone ? '휴대전화 없음' : '010-0000-0000';
      document.getElementById('tel-req').hidden = mobileNone;
      document.getElementById('home-wrap').hidden = !mobileNone;
      home.disabled = homeNone;
      home.required = mobileNone && !homeNone;
      if (homeNone) { home.value = ''; }
      home.placeholder = homeNone ? '집전화 없음' : '지역번호 포함 (예: 02-000-0000)';
      document.getElementById('home-req').hidden = homeNone;
      document.getElementById('no-phone-note').hidden = !homeNone;
      sms.disabled = mobileNone;
      sms.parentElement.style.textDecoration = mobileNone ? 'line-through' : '';
      sms.parentElement.style.color = mobileNone ? '#8A96A8' : '';
      document.getElementById('sms-note').hidden = !mobileNone;
      if (mobileNone && sms.checked) { document.getElementById('notify-post').checked = true; }
    }
    noTel.addEventListener('change', contacts);
    noHome.addEventListener('change', contacts);
    contacts();
  })();
