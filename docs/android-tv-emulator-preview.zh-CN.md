# 电脑验收电视端

使用 Android TV 模拟器运行真正的电视端 APK。推荐 1920 × 1080、API 29 或更高。这个工作站已有 `codex_stash_tv_api30`，当前 ADB 序列号为 `emulator-5560`。

从项目根目录运行以下命令。把序列号替换成 `adb devices` 返回的模拟器序列号。

```powershell
.\gradlew.bat :app:assembleTvPreview
python tools/tv_preview.py --device emulator-5560 --apk app/build/outputs/apk/tvPreview/app-tvPreview.apk
```

脚本安装电视测试包、启动模拟器里的应用，并连接电脑上的示例片库。保持脚本窗口打开。首次启动会下载并缓存观看用的示例图片和短片。示例模式只在 Android 模拟器上启用，服务器设置仅在本次运行时生效。

在 Android Studio 的模拟器窗口使用方向键和 Enter，或用 [scrcpy](https://github.com/Genymobile/scrcpy/blob/master/doc/windows.md) 打开无窗口模拟器的画面。scrcpy 支持右键返回，默认也可按 Alt+B。

```powershell
scrcpy -s emulator-5560 --no-audio --max-size=1440
```

按以下顺序验收：

1. **影片**：前两行的封面、标题和厂商名横向对齐。方向键换影片时，顶部展示跟随焦点并更新片名。按 Enter 直接开始播放，返回后应恢复到原卡片。
2. **演员与厂商**：进入任一资料页，首屏显示关联影片与片长。按 Enter 直接播放该影片。厂商页切换子厂商开关后检查关联列表。
3. **搜索与续播**：搜「山间」，检查结果。退回列表后选择「继续观看」，检查样例续播条。

示例片库用于验收页面结构与遥控路径，所有示例影片共用 [MDN 示例短片](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/video)。真实片库可以在模拟器里按普通方式打开应用，再使用服务器设置接入，适合继续验证授权、不同编码、续播进度和服务器数据。
