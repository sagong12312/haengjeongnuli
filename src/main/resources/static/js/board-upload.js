  document.getElementById('uf').addEventListener('change', function () {
    var names = Array.prototype.map.call(this.files, function (f) { return f.name; });
    document.getElementById('uf-names').textContent = names.length ? '선택한 파일: ' + names.join(', ') : '';
  });
