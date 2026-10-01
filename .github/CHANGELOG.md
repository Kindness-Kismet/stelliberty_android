- Updated the application icon
- Reduced the installation package size by compressing bundled geographic data and optimizing the native library
- Improved geographic data initialization to keep extraction off the main thread and avoid proxy startup delays caused by missing data
- Limited bundled translations to English, Simplified Chinese, and Traditional Chinese to avoid mixed-language interfaces

---

- 更新应用图标
- 压缩内置地理数据并优化原生库，减小安装包体积
- 优化地理数据初始化，在后台解压，避免数据缺失导致代理启动等待下载
- 仅保留英文、简体中文和繁体中文翻译，避免界面混用语言
