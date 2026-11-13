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
   - 有 root：`浏览/设置 root 目录` → 从 `/` 一路点进去选中（也可以手打绝对路径），能到 `/data/local/tmp`
   - 没 root：`选择文件夹（SAF）` → 只能选**共享存储**里的目录（内部存储 / Download / SD 卡），
     选完 App 自己建 `spool/ seen/ archive/ members/`
   - **SAF 为什么到不了系统根目录**：系统文件选择器只暴露 StorageVolume（共享卷），
     `/data`、`/system` 不在其列；第三方便捷 App 读 `/data/local/tmp` 本来也需要 root
3. **验证通路**：`⋮ → 写一条示例消息`。看到自己的气泡＝读写都通了。
4. **接别人的消息**：`⋮ → 生成命令（复制给别人）`，填三格（会话名 / refs / 一句话结论），
   App 会生成一条**已填好、可直接粘贴**的命令，例如：

   ```sh
   printf 'refs: 6e996a5\nime 样本归档完成\n' \
     | MSGBUS_DIR=/data/local/tmp/msgbus sh /path/to/msg.sh post yg-文件管理
   MSGBUS_DIR=/data/local/tmp/msgbus sh /path/to/msg.sh new yg-文件管理
   ```

   （`msg.sh` 来自 [msgbus](../msgbus) 仓库；找不到脚本时 App 会给出「直接写 spool/*.msg」的格式说明）
5. **要提醒**：点顶部「提醒 关」→ 变「提醒 默认」即开始每 15 秒轮询；`⋮ → 通知档位` 可改静音/横幅。

## 聊天记录与清理

- `⋮ → 聊天记录（归档）`：看被「压缩成摘要」替掉、移到 `archive/group/` 的原文；对话框里可直接「清空归档」
- 长按任意消息 → 「删除这条消息」：只删那一个 `.msg` 文件
- `⋮ → 清理数据`：清空消息（spool）/ 清空归档 / 两者都清 / 重置本地设置（游标、通知、目录配置）。全部二次确认，**不删任何非本工具的文件**
- `⋮ → 开启/关闭后台提醒`：通知服务的总开关（之前版本这个入口缺失，v0.9.0 补回）

## 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 一片空白，副标题写着「未找到存储」 | 还没选目录，或 root 未授权：`⋮ → 消息目录` |
| root 模式下看不到旧消息 | root 目录指错了：`⋮ → 消息目录 → 设置 root 目录`，填消息实际所在的绝对路径 |
| 通知不来 | `⋮ → 开启后台提醒` 没开、系统通知权限被拒、或 ROM 清了后台；`⋮ → 通知档位` 确认不是「静音」 |
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
