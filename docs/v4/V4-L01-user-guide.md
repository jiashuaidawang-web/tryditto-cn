# V4-L01 使用说明

隔离启动需要本机 PostgreSQL。这个仓库的默认连接写在 `api/aiditto-app/src/main/resources/application.yml`，可以用 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD` 覆盖。不要把生产库地址或口令写进仓库。

```bash
cd api
MATCH_LAB_ENABLED=true MATCH_LAB_ENVIRONMENT=local ADMIN_PHONES=<管理员手机号> mvn -pl aiditto-app -am spring-boot:run
```

`MATCH_LAB_ENVIRONMENT` 只能是 `local`、`ci` 或 `isolated`。漏掉它，或者 Spring profile 是 `prod` / `production`，接口保持关闭。演示验证码只用于这种隔离环境。

另一个终端：

```bash
cd web
npm run dev
```

浏览器打开 `http://127.0.0.1:5173/match-lab`。先在 `/login` 用 `ADMIN_PHONES` 里的手机号登录。验证码使用本机已有的 `DEMO_LOGIN_CODE`。生产口令不要提交。

三组可以直接执行的虚构评分：

- `71001` 甲·双向 与 `71002` 乙·双向。双方偏好对齐，推荐资格通过。
- `71003` 丙·单向 与 `71004` 丁·反向偏好。单向高分被拒绝推荐。
- `71001` 与 `71005` 戊·证据不足。覆盖不足，拒绝推荐。

`71006` 己·硬约束用来看年龄硬约束。`71007` 庚·行为带一条已确认的行为特征。记忆层没有单独写入，页面会显示为空。

候选页先选开发集或保留集寻求者，再执行四个冻结方案。旁边的沙箱按钮用当前工作区画像跑完整合法池，不显示召回路由。推理页的回放按钮调用确定性夹具。分数变化只说明算法行为变了。总览上的人数和样本量各自属于一条实验，不能加成一次业务统计。

总览会显示当前工作区编号。服务重启后这份内存数据消失。画像保存带上一次读到的版本；别人已经改过时，这次保存会被拒绝。
