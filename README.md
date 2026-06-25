# SpoofDate - Xposed 日期伪装模块

为 `com.zyyad.game` 伪装手机日期时间信息。

## 使用方式

### 1. Fork 或创建仓库

1. 手机浏览器打开 GitHub，新建仓库（如 `spoof-date-module`）
2. 把本项目所有文件上传到仓库

### 2. 自动编译

推送到 `main` 分支后，GitHub Actions 会自动编译。
进入仓库 → **Actions** 标签 → 点击最新的 build → 下载 **SpoofDate-debug** artifact。

### 3. 安装使用

1. 安装 APK
2. LSPosed 管理器中启用模块，勾选作用域 `com.zyyad.game`
3. 打开 SpoofDate App，设置目标日期，保存
4. 重启目标应用
