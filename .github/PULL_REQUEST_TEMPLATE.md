## 这个 PR 做了什么

<!-- 一两句。加规则的话写清规则号；修 bug 的话写清现象。 -->

## 为什么

<!--
改行为、加规则的 PR 必须写。比如「工具方法跑在请求线程上，里面的 JDBC 会把响应拖住」。
只改文档/CI 的可以写「无」。
-->

## 自测

- [ ] `mvn -B -ntp verify` 通过（Windows 沙箱加 `-DforkCount=0`）
- [ ] 加规则的话：`samples/bad` 新文件**正好**报一条，`samples/good` 新文件一条都不报
- [ ] 加规则的话：`docs/rules.md` 的表格和正文小节都补了，`#llm0nn` 锚点在
- [ ] 加规则的话：README / README.en / action.yml / llms.txt / 测试里的条数都跟着改了
- [ ] 没有新增依赖（除非在 PR 里说明为什么必须加）

## 相关 issue

<!-- Closes #123 -->