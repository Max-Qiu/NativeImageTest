# NativeImageTest

Spring Boot 4 + MyBatis-Plus + Redis + Spring Security + GraalVM Native Image 完整兼容性验证示例。

---

## 环境要求

| 工具 | 版本 | 备注 |
|---|---|---|
| GraalVM JDK | 25 | 含 `native-image` 与 tracing agent |
| Maven | 3.9+ | |
| Docker | 20+ | native 镜像构建用 buildpacks，Windows 无需装 Visual Studio |
| MySQL | 8+ | |
| Redis | 6+ | session / cache 存储 |
| PowerShell | 5.1+ | 测试脚本 |

---

## 项目结构

```
src/main/java/com/maxqiu/test/
├── controller/
│   ├── AuthController.java      # 注册 / 登录 / 登出 / 当前用户
│   ├── UserController.java      # 业务用户 CRUD
│   └── CacheController.java     # Spring Cache 注解 + StringRedisTemplate
├── entity/
│   ├── SysUser.java             # 认证用户（t_sys_user）
│   └── User.java                # 业务用户（t_user）
├── mapper/
│   ├── SysUserMapper.java
│   └── UserMapper.java          # 含 countByAge（XML SQL）
├── service/
│   ├── SysUserService / Impl
│   ├── UserService / Impl
│   └── CacheService / Impl      # @Cacheable / @CachePut / @CacheEvict
└── config/
    ├── SecurityConfig.java      # form login，/cache/** 需 ADMIN 角色
    ├── MyBatisConfig.java       # MapperFactoryBean 显式注册（native 兼容）
    ├── CacheConfig.java         # Redis cache TTL + key prefix
    ├── MyBatisNativeHints.java  # mapper XML 资源 hint
    └── SecuritySessionNativeHints.java  # Security / Session 序列化 hint

src/main/resources/
├── application.yaml
├── mapper/UserMapper.xml        # countByAge GROUP BY SQL
├── db/init-schema.sql           # 建表 + 初始数据
└── META-INF/native-image/
    └── .../reachability-metadata.json   # GraalVM 反射 / 资源 hint
```

---

## 快速开始

### 1. 配置外部服务

编辑 `src/main/resources/application.yaml`，默认值：

- MySQL：`192.168.220.201:3306/native_demo`（`root` / `123`）
- Redis：`192.168.220.201:6379`（密码 `123`）

也可用环境变量覆盖：`SPRING_DATASOURCE_URL`、`SPRING_DATA_REDIS_HOST` 等。

### 2. 初始化数据库

```sql
CREATE DATABASE native_demo CHARACTER SET utf8mb4;
```

然后执行建表脚本：

```sql
-- src/main/resources/db/init-schema.sql
```

脚本会创建两张表并写入初始数据：

| 表 | 用途 |
|---|---|
| `t_sys_user` | Spring Security 认证，含 `username / password / role` |
| `t_user` | 业务 CRUD 测试，含 `name / age` |

> `t_sys_user.username` 有 UNIQUE 约束，`/auth/register` 重复注册时返回 `409 Conflict`。

### 3. 选择运行模式

| 模式 | 操作 |
|---|---|
| JVM 开发 | `mvn spring-boot:run` |
| JVM JAR | `mvn package -DskipTests` → `java -jar target/*.jar` |
| Native 容器（一键） | `run-native-test.bat` |
| Native 容器（手动） | 见下方"构建 Native 镜像"节 |

---

## 接口说明

### 认证（`/auth`）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/register` | 注册账号（body: `username/password/role`） | 公开 |
| POST | `/auth/login` | 表单登录（`x-www-form-urlencoded`） | 公开 |
| GET | `/auth/me` | 查看当前会话 | 需登录 |
| GET | `/auth/roles` | 查看当前角色 | 需登录 |
| POST | `/auth/logout` | 登出，销毁 Redis Session | 需登录 |

### 业务用户 CRUD（`/users`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/users` | 查询全部（MyBatis-Plus `list()`） |
| GET | `/users/{id}` | 按 ID 查询 |
| POST | `/users` | 新增（body: `name/age`） |
| PUT | `/users/{id}` | 更新 |
| DELETE | `/users/{id}` | 删除 |
| GET | `/users/stats/age` | 按年龄分组统计（XML SQL） |

> 所有 `/users/**` 需要登录，无需 ADMIN 角色。

### 缓存（`/cache`）

> 所有 `/cache/**` 需要 **ADMIN** 角色。

**Spring Cache 注解：**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cache/anno/load?key=` | `@Cacheable` 加载 |
| POST | `/cache/anno/put?key=&value=` | `@CachePut` 写入 |
| DELETE | `/cache/anno/evict?key=` | `@CacheEvict` 清除单个 |
| DELETE | `/cache/anno/evictAll` | `@CacheEvict allEntries` 清空 |
| POST | `/cache/anno/resetHits` | 重置命中计数器 |

**StringRedisTemplate 直接操作：**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/cache/redis/keys` | 列出 `native-demo:*` 所有键 |
| GET | `/cache/redis/get?key=` | 读取键值 |
| POST | `/cache/redis/set?key=&value=&ttlSeconds=` | 写入（TTL 可选） |
| DELETE | `/cache/redis/delete?key=` | 删除键 |

---

## 接口测试

应用启动后，运行：

```powershell
.\test-api.ps1
```

脚本分 5 段，共覆盖所有接口：

| Part | 内容 | 关键验证点 |
|---|---|---|
| 0. 初始化 | 注册管理员 → form login → `/auth/me` | Session 写入 Redis，角色正确 |
| 1. MySQL CRUD | list / create / get / update / delete / stats | MyBatis-Plus + XML Mapper |
| 2. Spring Cache | `@Cacheable` / `@CachePut` / `@CacheEvict` | hitCount 计数器验证命中行为 |
| 3. RedisTemplate | set / get / keys / delete（含 TTL） | StringRedisTemplate 直接操作 |
| 4. 登出 | 销毁 Session → 再访问返回 401 | Session 清理 |

---

## 收集 Native Hints

每当新增功能或改动反射/资源加载相关代码，需重跑 agent 更新 `reachability-metadata.json`。

### 一键脚本

```bat
collect-hints.bat
```

流程：

1. `mvn clean package -DskipTests`
2. 后台启动 JAR + native-image-agent（每 5 秒 flush 一次）
3. 轮询日志，等待 `Started NativeImageTestApplication`
4. 自动运行 `test-api.ps1` 覆盖所有反射路径
5. 等待 7 秒最终 flush，杀 JVM，清理 `.lock` 文件

### 手动步骤（调试时）

```powershell
# 1. 打包
mvn clean package -DskipTests

# 2. 启动 + agent
java "-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/com.maxqiu/native-image-test,config-write-period-secs=5" `
     -jar target/NativeImageTest-0.0.1-SNAPSHOT.jar

# 3. 另开窗口跑测试
.\test-api.ps1

# 4. 测完后 Ctrl+C 停 JVM（让 agent 正常 flush，勿强杀）
```

---

## 构建 Native 镜像并测试

### 一键脚本

```bat
run-native-test.bat
```

流程：

1. `mvn spring-boot:build-image -Pnative -DskipTests`（Paketo buildpack，Linux 容器内编译）
2. `docker run -d -p 8080:8080 native-image-test:0.0.1-SNAPSHOT`
3. 轮询 `docker logs`，等待 `Started NativeImageTestApplication`
4. 自动运行 `test-api.ps1`
5. `docker stop` + `docker rm` 清理容器

> 构建耗时：首次约 10~20 分钟（拉取 buildpack 镜像 + GraalVM 编译），后续约 5 分钟。

### 手动步骤

```powershell
# 构建
mvn spring-boot:build-image -Pnative -DskipTests

# 运行
docker run -d --name native-test -p 8080:8080 native-image-test:0.0.1-SNAPSHOT
docker logs -f native-test

# 测试（另开窗口）
.\test-api.ps1

# 清理
docker stop native-test
docker rm native-test
```
