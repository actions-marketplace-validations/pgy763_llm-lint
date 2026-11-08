---
title: Search
description: Find a rule or a guide by id, error text or API name.
permalink: /en/search.html
---

<input id="q" type="search" placeholder="Try LLM007, maxTokens, SseEmitter, connection pool…" autofocus
       style="width:100%;padding:10px 12px;font-size:16px;border:1px solid #d0d7de;border-radius:6px">

<p id="hint">Start typing; rule ids, titles and one-line summaries all match.</p>

<ul id="hits"></ul>

<script>
(function () {
  var docs = [
    ['Getting started', 'start.html'],
    ['Rules', 'rules.html'],
    ['Configuration', 'config.html'],
    ['Errors', 'errors.html'],
    ['CI and integrations', 'integrations.html'],
    ['Baseline', 'baseline.html'],
    ['FAQ', 'faq.html']
  ];
  var all = docs.map(function (d) {
    return {title: d[0], url: d[1], summary: '', level: ''};
  });
  var box = document.getElementById('q');
  var list = document.getElementById('hits');
  var hint = document.getElementById('hint');

  function render(items) {
    list.innerHTML = '';
    items.forEach(function (it) {
      var li = document.createElement('li');
      var a = document.createElement('a');
      a.href = it.url;
      a.textContent = it.title + (it.level ? ' · ' + it.level : '');
      li.appendChild(a);
      if (it.summary) {
        li.appendChild(document.createTextNode(' — ' + it.summary));
      }
      list.appendChild(li);
    });
    if (!items.length) {
      list.innerHTML = '<li>Nothing matched. Try LLM001, SseEmitter or maxTokens.</li>';
    }
  }

  fetch('search.json').then(function (r) {
    return r.json();
  }).then(function (rules) {
    rules.forEach(function (r) {
      all.push({title: r.id + ' ' + r.title, url: r.url, summary: r.summary, level: r.level});
    });
    hint.textContent = all.length + ' entries: twenty-two rules plus a few guides.';
    render(all);
  }).catch(function () {
    hint.textContent = 'Could not load search.json — try reloading.';
  });

  box.addEventListener('input', function () {
    var q = box.value.trim().toLowerCase();
    if (!q) {
      render(all);
      return;
    }
    render(all.filter(function (it) {
      return (it.title + ' ' + it.summary).toLowerCase().indexOf(q) !== -1;
    }));
  });
})();
</script>