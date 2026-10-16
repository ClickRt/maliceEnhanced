# Mixin 安装说明

## 问题
使徒 (Apostle) 类来自 Goety 模组，在开发环境中不可用，因此无法直接编译 Mixin 类。

## 解决方案

### 方法 1：在安装 Goety 模组的环境中编译

1. 确保你的开发环境中安装了 Goety 模组（deobf 版本）
2. 将 `mixin-classes/ApostleMixin.java` 复制到 `src/main/java/com/enchantmentSkill/mixin/` 目录
3. 运行 `gradlew build` 编译
4. 生成的 JAR 文件将包含 Mixin 支持

### 方法 2：手动添加编译后的类到 JAR

1. 在一个安装了 Goety 模组的项目中编译 `ApostleMixin.java`
2. 获取编译后的 `ApostleMixin.class` 文件
3. 使用以下命令添加到 JAR：
   ```
   jar uf shatter-1.0.0.jar com/enchantmentSkill/mixin/ApostleMixin.class
   ```

### 方法 3：使用运行时反射（当前默认）

当前版本使用反射来尝试绕过使徒的伤害上限，但这可能不够可靠。
如果反射方法不起作用，请使用方法 1 或方法 2 添加 Mixin 支持。

## 测试

安装后，使用锋利 255 的剑攻击使徒，应该能看到：
- 控制台输出："Shatterer enchantment bypassed damage cap!"
- 造成理论伤害（而不是被限制到 19 点）
