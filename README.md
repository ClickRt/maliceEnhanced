# Malice Enhanced / 恶意·扩展附魔

Minecraft **1.20.1 / Forge 47.x** 战斗向模组。mod id：`malice_enhanced`，作者：LJY，许可证：All Rights Reserved。

模组由三部分组成：**扩展附魔**（7 个自定义附魔）、**装备**（无垠长魂之剑、浩瀚星魂之弓、星冕宝晶）、**Boss**（「无从认知」凋灵 + 十三种头衔）。仓库里同时保留了 Forge MDK 的 `README.txt`（那是 MDK 模板自带的通用搭建说明，与本模组内容无关）。

## 功能

### 附魔

代码在 `com.enchantmentSkill` 下按 `Positive/`（正向）与 `Curse/`（诅咒）分包，每组附魔独立一套 `Enchantment` / `Enchantments` / `EventHandler` / `Registries` 五件套，主类逐个调用 `XxxRegistries.init(bus)` 注册。

| 中文名     | id                         | 最大等级             | 效果                                                       |
| ---------- | -------------------------- | -------------------- | ---------------------------------------------------------- |
| 破限杀手   | `shatterer`              | 1（treasure）        | 无视大部分目标的伤害上限（「亚波伦」除外）                 |
| 幸运一击   | `lucky_hit`              | 5                    | 满蓄力攻击有概率造成双倍伤害                               |
| 触手的延伸 | `extension_of_tentacles` | 5（treasure）        | 提升实体触及范围与方块触及范围属性                         |
| 血债血偿   | `blood_debt`             | 1                    | 生命值越低伤害越高；损失的生命值转化为伤害吸收             |
| 反冲力     | `recoil`                 | 5                    | 攻击目标时自身受到反向击退                                 |
| 纯粹恶意   | `pure_malice`            | 5                    | 命中附加灵魂伤害（`damage_soul` 伤害类型，绕过常规护甲） |
| 嘲讽诅咒   | `mocking_curse`          | 3（诅咒 / treasure） | 蹲下时强制注视你的玩家转向并自动攻击你                     |

### 装备

- **无垠长魂之剑**（`weapon/SoulSwordItem`）—— 通过 Goety 灵魂仪式锻出；带黑洞、格挡、法术免疫、飞行等客户端/服务端联动，自定义宇宙材质渲染见 `weapon/cosmic/`（`SwordCosmicModelLoader` + `SwordShaders` + `assets/malice_enhanced/shaders/core`）。
- **浩瀚星魂之弓**（`weapon/SoulBowItem`）—— 累积杀敌数，星魂之箭追踪（`SoulBowHoming`），拉弓时显示准星覆盖层（`client/SoulBowAimOverlay`）。
- **星冕宝晶**（`weapon/starcrystal/`）—— Curios 饰品（槽位 `star_crystal`），内嵌头衔宝库存与专用 Screen/Menu，可把多个「头衔之星」的效果压缩进一件饰品；附带悦灵仆从。

### Boss：「无从认知」凋灵

`wither_title/` 包。召唤物 `SummonUndefinedWither`，仆从 `UndefinedWitherServant`，技能实体含引力奇点、闪电球、星冕光束。客户端在 `wither_title/client/`（自绘 BossBar、天穹染色、六角护盾穹顶、充能残影、专属 BGM）。

十三种头衔（下界之星变体，效果数值见 `lang/zh_cn.json` 的 `wither_title.malice_enhanced.*.desc`）：粒子护盾、空间跃迁、极寒风暴、生机收割、电击淋浴、维生续航、稳定聚焦、极限充能、坍缩力场、集束火箭、复生护盾、球状闪电、末日炎爆。

进阶线：`不详征兆 → 无从认知 → 撕裂噩梦`，另加 `灵魂支配`（仪式锻剑）。注册见 `com/enchantmentSkill/advancements/ModAdvancements`，数据在 `data/malice_enhanced/advancements/wither`。

### 配置

`config/` 下的 COMMON 配置（`wither_title/WitherTitleConfig`）：

| 键                         | 默认  | 说明                     |
| -------------------------- | ----- | ------------------------ |
| `HEALTH`                 | 1453  | 无从认知凋灵最大生命值   |
| `HURT_LIMIT`             | 20.0  | 凋灵每 tick 承伤上限     |
| `SOUL_HURT_LIMIT`        | 200.0 | 凋灵单次灵魂伤害上限     |
| `HURT_DEFEND_TICKS`      | 30    | 命中后的无敌帧 tick 数   |
| `SERVANT_LIFETIME_TICKS` | 1200  | 凋灵仆从寿命（0 = 永久） |

## 兼容矩阵

`META-INF/mods.toml` 中除 Forge/Minecraft 外全部为 `mandatory=false`，`ordering="AFTER"`。因此**单独安装本模组可运行**，但附魔与装备的大多数联动只在对方存在时生效：

| Mod                                  | 用途                                                              | 加载方式                                                  |
| ------------------------------------ | ----------------------------------------------------------------- | --------------------------------------------------------- |
| Goety                                | 灵魂仪式（锻剑/锻弓）、使徒相关 Mixin                             | `compileOnly` + 运行时 `libs/goety.jar`               |
| Goety Revelation                     | 亚波伦/使徒伤害上限绕过                                           | 反射 + 字符串 target，不引入编译期依赖                    |
| Iron's Spellbooks                    | 施法打断联动、黑洞/时间停止 Mixin                                 | `compileOnly`                                           |
| Curios                               | `star_crystal` 饰品槽                                           | `libs/curios-forge-5.14.1+1.20.1.jar`                   |
| Jade                                 | 仆从实体信息插件                                                  | `compileOnly files('libs/jade-api-11.13.2-mapped.jar')` |
| Patchouli                            | Goety 黑书仪式条目                                                | 资源在 `assets/goety/patchouli_books/`                  |
| GeckoLib / Caelus / Player Animation | 源码未直接引用，随 `libs/` 提供以满足 Goety 等 mod 的运行期前置 | `libs/`                                                 |

触及范围加成用的是 Forge 自带的 `ForgeMod.ENTITY_REACH` / `BLOCK_REACH`，不依赖额外属性 mod。

第三方 mod 类一律走 `compileOnly` 或反射，避免把这些 mod 打进产物、也避免缺少依赖时启动崩溃。Mixin 是否生效由 `mixin/SoftDependsMixinPlugin` 按 mod 存在与否动态裁剪。

## 构建

环境要求 JDK 17，Gradle Wrapper 已随仓库提供。

```bash
./gradlew build          # 产物在 build/libs，已 reobf
./gradlew runClient      # 开发环境客户端
./gradlew runServer
```

首次搭建或依赖异常时：`./gradlew --refresh-dependencies`，必要时 `./gradlew clean` 后重来。IDE 导入按 MDK 常规流程（IntelliJ 直接 import `build.gradle` 后 `./gradlew genIntellijRuns`）。

Mixin 配置在 `src/main/resources/malice_enhanced.mixins.json`，refmap 由 `build.gradle` 的 `mixin { add sourceSets.main, 'malice_enhanced.refmap.json' }` 生成；`processResources` 会展开 `mods.toml` / `pack.mcmeta` / mixins json，并强制 `filteringCharset = 'UTF-8'`（语言文件是中文，别改这行）。

## 仓库导航

```
src/main/java/com/enchantmentSkill/   附魔（Positive/ + Curse/）、仪式、属性桥接、全部 Mixin
src/main/java/weapon/                 武器物品、实体、网络包、客户端渲染、cosmic/ 自定义模型、starcrystal/
src/main/java/wither_title/           Boss、技能实体、客户端渲染与 BossBar、Jade 兼容
src/main/java/textture/               贴图相关（bossHealth / effect / ritual / soulSword）
src/main/resources/assets/            模型、贴图、shader、音效、中英语言文件
src/main/resources/data/              配方（灵魂仪式）、curios 槽位、damage_type、advancements、标签
libs/                                 本地第三方 mod jar（Goety、Curios、Caelus、Iron's Spellbooks 等）
tools/                                贴图/模型生成脚本、CFR 反编译器、批量重命名脚本
Reference*/ mixin-classes/            Mixin 的历史版本与手工编译副本，非构建输入
```

包名有两处历史遗留值得注意：目录是 `Positive/`（大写 P）而 `EnchantmentSkillMod.java` 里 import 的是 `com.enchantmentSkill.positive.*`（小写）；`textture` 是 `texture` 的拼写错误。在大小写不敏感的 Windows 文件系统上能编过，迁移到 Linux CI 前要留意。

## Mixin 相关说明

`MIXIN_README.txt` 记录的是早期方案：当时使徒（Apostle）类在开发环境不可用，所以提供了「装有 Goety 的环境编译 / 手工 `jar uf` 塞 class / 运行时反射」三条路径。**当前仓库默认走反射**，`src/main/java/com/enchantmentSkill/mixin/ApostleMixin.java` 已直接纳入源码树并列入 mixins json，该文档只作背景资料。

验证方式：用高等级「破限杀手」的剑攻击使徒，控制台输出 `Shatterer enchantment bypassed damage cap!` 且造成理论伤害（而非被限制在 19 点）即为生效。
