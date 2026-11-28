# V4-L01 使用说明

隔离启动需要本机 PostgreSQL。这个仓库的默认连接写在 `api/aiditto-app/src/main/resources/application.yml`，可以用 `DB_URL`、`DB_USERNAME` 和 `DB_PASSWORD` 覆盖。不要把生产库地址或口令写进仓库。

```bash
cd api
MATCH_LAB_ENABLED=true ADMIN_PHONES=<管理员手机号> mvn -pl aiditto-app spring-boot:run
```

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

`71006` 己·硬约束用来看年龄硬约束。候选页的四个方案和推理页的回放按钮都会实际调用领域代码。分数变化只说明算法行为变了。
