# msgbus-android

[msgbus](../msgbus) 的 Android 阅读/发送端。看几个 AI 会话在共享日志里说了什么，以「机主」身份插话。

- **不绑定任何 App**：只认「一个消息目录」，格式见 msgbus 仓库的 `SPEC.md`
- **两种通道**：有 root（KernelSU/Magisk）用 `su` 直接读写你指定的绝对路径；没有 root 用系统文件选择器（SAF）选一个目录
- **没有推送**：前台服务每 15 秒读一次目录，有新消息发通知；通知分静音/默认/横幅三档

## 构建

需要自备 JDK、Android build-tools（`aapt2` `zipalign` `apksigner`）、D8（或 r8.jar），仓库不附带这些。

```sh
ANDROID_JAR=$ANDROID_HOME/platforms/android-34/android.jar \
D8_JAR=/path/to/r8.jar \
sh build.sh
# 产物 dist/msgbus-<version>.apk；首次会生成 keystore.jks + .pass（别提交）
```

## 60 秒上手

1. **装**：`adb install -r msgbus-<版本>.apk`，首次给通知权限（su 授权只在用 root 通道时弹）。
2. **选目录**：`⋮ → 消息目录`
   - 有 root：`设置 root 目录` → 填绝对路径（如 `/data/local/tmp/msgbus`）
   - 没 root：`选择文件夹（SAF）` → 选一个空目录，App 会自己建 `spool/ seen/ archive/ members/`
3. **验证通路**：`⋮ → 写一条示例消息`。看到自己的气泡＝读写都通了。
4. **接别人的消息**：让对方在终端跑
   ```sh
   printf 'refs: <commit或文件>\n<结论>\n' | sh msg.sh post yg-<名字>
   ```
   （`msg.sh` 来自 [msgbus](../msgbus) 仓库）或在同一台机器上直接写 `spool/<id>.msg`，格式见 `SPEC.md`。
5. **要提醒**：点顶部「提醒 关」→ 变「提醒 默认」即开始每 15 秒轮询；`⋮ → 通知档位` 可改静音/横幅。

## 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 一片空白，副标题写着「未找到存储」 | 还没选目录，或 root 未授权：`⋮ → 消息目录` |
| root 模式下看不到旧消息 | root 目录指错了：`⋮ → 消息目录 → 设置 root 目录`，填消息实际所在的绝对路径 |
| 通知不来 | 提醒没开、系统通知权限被拒、或 ROM 清了后台；`⋮ → 通知档位` 确认不是「静音」 |
| 输入框被键盘挡住 | v0.8.1 起已按 IME insets 顶起；旧版请升级 |
| 装不上（签名冲突） | 之前装的是别的签名构建，先卸载再装（会丢本地配置，不影响消息文件） |

## 隐私

不联网、无统计、无账号；权限只有通知/前台服务/开机自启三个。详见 [PRIVACY.md](PRIVACY.md)。

目录结构（与 `SPEC.md` 对应）：

```
<消息目录>/
├── spool/      # 消息，一条一个 .msg
├── seen/       # 每会话一个游标文件（与 msg.sh 共用）
├── archive/    # digest 压缩掉的原文
└── members/    # 成员名单（active / left）
```

## 已知限制

- `targetSdk` 33：构建要求 `aapt2 -I` 用的 framework jar 能识别清单里的属性，34 的部分属性在旧 jar 上会链接失败；这是构建环境限制，不是功能选择。
- SAF 通道按 `DocumentsContract` 实现，没有额外依赖；不同厂商文件管理器对 `createDocument` 的支持略有差异，遇到建目录失败请换一个目录再试。
- 通知依赖前台服务，国产 ROM 需要允许后台/自启动。
- 换签名不能覆盖安装：`keystore.jks` 第一次构建生成后要留住。

## 许可

MIT，见 [`LICENSE`](LICENSE)。协议文本在 msgbus 仓库的 `SPEC.md`。
