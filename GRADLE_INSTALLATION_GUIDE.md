# Gradle 9.0.0 安装指南

## 1. 安装步骤

### 1.1 解压Gradle
1. 将下载的 `gradle-9.0.0-bin.zip` 文件解压到一个合适的目录，例如：
   - `C:\Gradle\gradle-9.0.0`（Windows）
   - `/opt/gradle/gradle-9.0.0`（Linux/macOS）

### 1.2 设置环境变量（Windows）
1. 右键点击"此电脑"或"计算机"，选择"属性"
2. 点击"高级系统设置"
3. 点击"环境变量"
4. 在"系统变量"部分，点击"新建"
5. 变量名：`GRADLE_HOME`
   变量值：`C:\Gradle\gradle-9.0.0`（根据你实际的解压路径）
6. 在"系统变量"部分，找到`Path`变量，点击"编辑"
7. 点击"新建"，添加 `%GRADLE_HOME%\bin`
8. 点击"确定"保存所有设置

### 1.3 设置环境变量（Linux/macOS）
1. 打开终端
2. 编辑 `~/.bashrc` 或 `~/.zshrc` 文件
3. 添加以下内容：
   ```bash
   export GRADLE_HOME=/opt/gradle/gradle-9.0.0
   export PATH=$PATH:$GRADLE_HOME/bin
   ```
4. 保存文件并执行 `source ~/.bashrc` 或 `source ~/.zshrc` 使设置生效

### 1.4 验证安装
1. 打开新的终端或命令提示符
2. 运行以下命令：
   ```bash
   gradle --version
   ```
3. 你应该看到类似以下输出：
   ```
   Gradle 9.0.0

   Build time:   2024-02-25 13:10:20 UTC
   Revision:     a12280099a49b81b080f99b6f34b06e4e9f77eae

   Kotlin:       1.9.22
   Groovy:       4.0.16
   Ant:          Apache Ant(TM) version 1.10.13 compiled on January 4 2023
   JVM:          21.0.9 (Eclipse Adoptium 21.0.9+11)
   OS:           Windows 10 10.0 amd64
   ```

## 2. 让项目使用新的Gradle版本

### 2.1 方法1：使用gradle wrapper命令（推荐）
1. 打开终端，导航到项目目录
2. 运行以下命令生成新的wrapper：
   ```bash
   gradle wrapper --gradle-version 9.0.0
   ```
3. 这会更新项目中的 `gradle/wrapper/gradle-wrapper.properties` 文件，指向新的Gradle版本

### 2.2 方法2：手动更新gradle-wrapper.properties
1. 打开项目中的 `gradle/wrapper/gradle-wrapper.properties` 文件
2. 修改 `distributionUrl` 行：
   ```properties
   distributionUrl=https\://services.gradle.org/distributions/gradle-9.0.0-bin.zip
   ```

## 3. 验证项目使用新的Gradle版本
1. 在项目目录中运行：
   ```bash
   ./gradlew --version
   ```
2. 你应该看到Gradle 9.0.0的版本信息

## 4. 常见问题解决

### 4.1 环境变量不生效
- 确保你打开了新的终端窗口
- 检查环境变量的设置是否正确
- 重启电脑以确保所有设置生效

### 4.2 下载速度慢
- 可以考虑使用国内镜像，修改 `gradle-wrapper.properties` 文件：
  ```properties
  distributionUrl=https\://mirrors.cloud.tencent.com/gradle/distributions/gradle-9.0.0-bin.zip
  ```

### 4.3 与项目不兼容
- 如果项目使用的插件版本过旧，可能需要更新插件版本以兼容Gradle 9.0.0
- 查看Gradle 9.0.0的发布说明，了解重要的变更

## 5. 参考链接
- [Gradle官方网站](https://gradle.org/)
- [Gradle安装指南](https://gradle.org/install/)
- [Gradle 9.0.0发布说明](https://docs.gradle.org/9.0.0/release-notes.html)
