// 모든 화면 공통 동작 (Content-Security-Policy 때문에 HTML 안에 스크립트를 쓰지 않는다)
//  data-confirm="질문"      : 폼 제출·버튼 누름 전에 확인
//  data-require="입력칸 id"  : 그 칸이 비어 있으면 data-require-msg 를 보여 주고 멈춤
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
    var el = e.target.closest ? e.target.closest('button[data-confirm], a[data-confirm], input[data-confirm], button[data-require]') : null;
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
