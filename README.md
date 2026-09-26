# 余记 · 净资产

离线优先的多币种个人净资产追踪器，专为持有人民币及海外资产的用户设计。

不是记账本。不记流水。只看现在值多少钱。

## 功能

- **净资产总览** — 资产、负债、净值，按组分类展示
- **多币种支持** — CNY / USD / EUR / GBP / JPY / HKD / AUD / CAD / SGD / USDT / BTC / ETH，统一折算为人民币
- **实时汇率** — 法币来自 open.er-api.com，BTC / ETH 来自 CoinGecko，支持手动覆盖
- **历史快照** — 余额变动时自动记录，每天只保留最后一条，趋势图查看净资产变化
- **账户转账** — 记录账户间划转，不影响净值
- **分组管理** — 自定义分组，支持拖拽排序
- **数据备份** — 导出 / 导入 .yuji 备份文件，完整恢复

## 技术栈

| 层 | 库 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose |
| 数据库 | Room 2.6 |
| 持久化设置 | DataStore |
| 后台任务 | WorkManager |
| 网络 | OkHttp 4 |
| 序列化 | kotlinx.serialization |
| 图片 | Coil 3 |

## 构建要求

- Android Studio Ladybug 或更新版本
- JDK 17+
- minSdk 26（Android 8.0）

```bash
git clone https://github.com/ZaiLinn/yuji.git
cd yuji
./gradlew assembleDebug
```

## 签名发布

签名配置读取 `keystore.properties`（不纳入版本控制）。自行构建时创建该文件：

```properties
storeFile=/path/to/your.keystore
storePassword=yourPassword
keyAlias=yourAlias
keyPassword=yourPassword
```

## 数据存储

全部数据存储在本机 Room 数据库，无网络上传，无账号系统。汇率刷新与搜寻图标是唯二的网络请求。

## 许可

MIT

数字与英文字体使用 [Inter](https://github.com/rsms/inter)，浅色模式的大数字使用 [Calistoga](https://github.com/SorkinType/Calistoga)，均为 SIL OFL 1.1。应用内只打包拉丁字符子集，许可证见 `app/src/main/res/raw/`。
