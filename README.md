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

## 使用

1. 装好后首次启动会弹「3 步上手」
2. 选消息目录：root 模式填绝对路径（如 `/data/local/tmp/msgbus`）；或选一个文件夹走 SAF
3. 添加会话（名字要和发送方 `from:` 一致），或让另一个会话直接往 `spool/` 写消息
4. `⋮` 里有：成员管理、通知档位、消息目录、写示例消息

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
