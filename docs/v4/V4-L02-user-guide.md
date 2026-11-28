# V4-L02 使用说明

启动方式与 L01 相同。环境名必须是 `local`、`ci` 或 `isolated`，并且不能是生产 profile。

```bash
cd api
MATCH_LAB_ENABLED=true MATCH_LAB_ENVIRONMENT=local ADMIN_PHONES=<管理员手机号> mvn -pl aiditto-app -am spring-boot:run
```

另一个终端：

```bash
cd web
npm run dev
```

用管理员手机号在 `/login` 登录，然后打开 `http://127.0.0.1:5173/match-lab/global`。勾选虚构用户，执行沙箱全局匹配。可继续的配对进入反馈。双方操作后进入画像学习，确认提案，再执行重新匹配。

案例 A 到 F 的按钮会改当前工作区。数学对照不会打开反馈。服务重启后工作区丢失。不要把页面上的分数变化理解成真实关系变好了。
