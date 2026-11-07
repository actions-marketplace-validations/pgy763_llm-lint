---
title: 搜索
description: 在 llm-lint 的二十二条规则和几篇文档里找一条：规则号、报错原文、API 名都行。
permalink: /search.html
---

<input id="q" type="search" placeholder="搜 LLM007、maxTokens、SseEmitter、连接池…" autofocus
       style="width:100%;padding:10px 12px;font-size:16px;border:1px solid #d0d7de;border-radius:6px">

<p id="hint">输入几个字就开始过滤：规则号、标题、一句话摘要都参与匹配。</p>

<ul id="hits"></ul>

<script>
(function () {
  var docs = [
    ['开始使用', 'start.html'],
    ['规则手册', 'rules.html'],
    ['配置', 'config.html'],
    ['报错对照', 'errors.html'],
    ['接进 CI', 'integrations.html'],
    ['基线', 'baseline.html'],
    ['常见问题', 'faq.html']
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
        li.appendChild(document.createTextNode(' —— ' + it.summary));
      }
      list.appendChild(li);
    });
    if (!items.length) {
      list.innerHTML = '<li>没有匹配的，换个词试试：LLM001、SseEmitter、maxTokens。</li>';
    }
  }

  fetch('search.json').then(function (r) {
    return r.json();
  }).then(function (rules) {
    rules.forEach(function (r) {
      all.push({title: r.id + ' ' + r.title, url: r.url, summary: r.summary, level: r.level});
    });
    hint.textContent = '共 ' + all.length + ' 条：二十二条规则加几篇文档。';
    render(all);
  }).catch(function () {
    hint.textContent = '搜索索引没加载出来（search.json），刷新一下试试。';
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