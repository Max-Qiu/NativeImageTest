# NativeImageTest

Spring Boot 4 + MyBatis-Plus + Redis + Spring Security + Jakarta Validation + GraalVM Native Image 完整兼容性验证示例。

---

## 环境要求

| 工具 | 版本 | 备注 |
|---|---|---|
| JDK | 25 | 容器构建会自动下载 Linux GraalVM，本机无需安装 tracing agent |
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
│   ├── CacheController.java     # Spring Cache 注解 + StringRedisTemplate
│   ├── ValidationController.java # 请求体 / 分组 / MVC 参数 / Service 方法校验
│   └── ValidationExceptionHandler.java # 校验异常统一返回 400
├── dto/
│   ├── ValidationRequest.java   # 字段、嵌套对象、集合元素与 Update 分组
│   └── ValidationErrorResponse.java # 统一校验错误响应
├── validation/
│   ├── DemoCode.java            # 自定义约束
│   └── DemoCodeValidator.java   # 自定义校验器
├── entity/
│   ├── SysUser.java             # 认证用户（t_sys_user）
│   └── User.java                # 业务用户（t_user）
├── mapper/
│   ├── SysUserMapper.java
│   └── UserMapper.java          # 含 countByAge（XML SQL）
├── service/
│   ├── SysUserService / Impl
│   ├── UserService / Impl
│   ├── CacheService / Impl      # @Cacheable / @CachePut / @CacheEvict
│   └── ValidationService.java   # @Validated 方法代理校验
└── config/
    ├── SecurityConfig.java      # form login，/cache/** 需 ADMIN 角色
    ├── MyBatisConfig.java       # MapperFactoryBean 显式注册（native 兼容）
    ├── CacheConfig.java         # Redis cache TTL + key prefix
    ├── BusinessNativeHints.java # 构建时自动扫描 Entity / DTO
    ├── ValidationNativeHints.java # 从 DTO 约束元数据注册校验器构造器
    ├── MyBatisNativeHints.java  # Mapper 代理、XML 资源及框架反射 hint
    ├── NativeLambdaFeature.java # 自动注册可序列化的方法引用
    └── SecuritySessionNativeHints.java  # Security / Session 序列化 hint

src/main/resources/
├── application.yaml
├── mapper/UserMapper.xml        # countByAge GROUP BY SQL
└── db/init-schema.sql           # 建表 + 初始数据
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
| Native 镜像打包 | `build-native-image.bat` |
| Native 容器测试 | `run-native-test.bat`（使用已有的本地镜像） |
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

### 参数校验（`/validation`）

所有接口需要登录；演示接口直接返回校验后的数据，不写入数据库。
引入 `spring-boot-starter-validation`，使用 Jakarta Validation 注解与 Hibernate Validator。

| 方法 | 路径 | 验证内容 |
|---|---|---|
| POST | `/validation/body` | `@Valid` 请求体：`@NotBlank`、`@Size`、`@Email`、`@NotNull`、`@Min`、`@Max` |
| PUT | `/validation/body` | `@Validated(Update.class)`：额外要求 `id` 非空，并继承 Default 分组 |
| GET | `/validation/parameters?count=1` | MVC 方法参数校验，`count` 范围为 1～10 |
| GET | `/validation/service?value=native` | Service 的 `@Validated` 代理校验，`value` 非空白且长度为 3～20 |

POST 请求体示例（PUT 时增加 `"id": 1`）：

```json
{
  "name": "Native Demo",
  "email": "demo@example.com",
  "age": 28,
  "address": { "city": "Shanghai", "zipCode": "200000" },
  "tags": ["native"],
  "code": "DEMO-ABC"
}
```

`name` 最多 40 字符，`age` 范围为 18～120。`address` 必填并通过 `@Valid` 级联校验，
其中 `city` 非空白，`zipCode` 为六位数字；`tags` 至少包含一个元素且每个元素非空白。
`code` 使用自定义 `@DemoCode`，格式为 `DEMO-` 加三个大写字母。

校验失败返回 HTTP 400，包含字段路径和错误信息，例如：

```json
{
  "error": "VALIDATION_FAILED",
  "violations": [
    { "field": "address.city", "message": "must not be blank" }
  ]
}
```

错误信息随请求语言变化，测试断言状态码、字段路径和非空消息。
`ValidationExceptionHandler` 仅作用于演示控制器，覆盖请求体、MVC 方法参数和 Service 约束异常。
MVC 控制器不加类级 `@Validated`，使用框架内建方法校验；Service 使用类级 `@Validated` 触发代理校验。
参见 [Spring MVC 校验文档](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
与 [Spring Boot 校验文档](https://docs.spring.io/spring-boot/reference/io/validation.html)。

---

## 接口测试

应用启动后，运行：

```powershell
.\test-api.ps1
```

`test-api.ps1` 分 5 段，覆盖原有接口：

| Part | 内容 | 关键验证点 |
|---|---|---|
| 0. 初始化 | 注册管理员 → form login → `/auth/me` | Session 写入 Redis，角色正确 |
| 1. MySQL CRUD | list / create / get / update / delete / stats | MyBatis-Plus + XML Mapper |
| 2. Spring Cache | `@Cacheable` / `@CachePut` / `@CacheEvict` | hitCount 计数器验证命中行为 |
| 3. RedisTemplate | set / get / keys / delete（含 TTL） | StringRedisTemplate 直接操作 |
| 4. 登出 | 销毁 Session → 再访问返回 401 | Session 清理 |

Validation 的 26 项行为断言单独运行（JVM 和 Native 均可）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-validation.ps1
# 指定其他端口
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-validation.ps1 -BaseUrl http://localhost:8081
# JVM 自动化测试，包含 20 个 validation 测试用例
mvn test
```

脚本自动注册或复用 `test-admin`、登录、验证正反例并登出，覆盖字段约束、嵌套对象、集合元素、
Update 分组、自定义校验器、MVC 参数边界、Service 代理及未登录访问。
任一断言失败返回非零退出码；通过后保存报告到 `target/validation-assertions.json`。

---

## Native Hints 自动生成

项目通过 Spring AOT、Java Hints 注册器和依赖元数据仓库构建 Native Image，日常不需要运行 Agent，也不维护采集得到的 JSON。
源码中的旧 `reachability-metadata.json` 和收集脚本已移除。构建目录中仍会生成元数据，这是 Native Image 正常的构建产物。

| 内容 | 处理方式 |
|---|---|
| Spring Bean 和自动配置 | Spring AOT |
| `com.maxqiu.test.entity`、`com.maxqiu.test.dto` | `BusinessNativeHints` 在构建时扫描，并注册字段、构造器、属性和嵌套属性类型 |
| Entity / DTO 上的 Validation 约束 | `ValidationNativeHints` 在构建时扫描约束元数据，注册内置及自定义校验器构造器，包含集合元素和组合约束；Bean 方法约束由 Spring AOT 处理 |
| `com.maxqiu.test.mapper` | `MyBatisNativeHints` 在构建时扫描并注册 Mapper 代理和方法 |
| Mapper XML、MyBatis DTD、框架动态访问 | `MyBatisNativeHints` 显式注册 |
| MyBatis-Plus Lambda 查询的方法引用 | `NativeLambdaFeature` 在 Native 构建阶段扫描 `com.maxqiu.test` 下的可序列化 Lambda 捕获类 |
| Security / Redis Session 的 JDK 序列化 | `SecuritySessionNativeHints` 显式注册 |
| 已有配置的第三方依赖 | Native Maven 插件自动引入 GraalVM Reachability Metadata Repository |

新增 Entity / DTO 放入上述包即可，不需要添加 `@RegisterReflectionForBinding`。
新增 Mapper 仍需按 `MyBatisConfig` 的方式注册为 `MapperFactoryBean`；原生代理提示由扫描生成。
扫描只在 AOT 构建阶段执行。其他包中的业务类型需要扩展扫描范围；新增第三方组件、自定义 TypeHandler、动态类名或 Session 属性类型，需要评估并补充对应的 Java Hints。

可运行以下测试验证新类型无需注解也能生成提示：

```powershell
mvn -Dtest=NativeHintsTests test
```

---

## 构建 Native 镜像并测试

### 打包脚本

```bat
build-native-image.bat
```

执行 `mvn -Pnative clean spring-boot:build-image -DskipTests`，清理旧构建产物并通过 Paketo buildpack 在 Linux 容器内编译。
生成本地镜像 `native-image-test:0.0.1-SNAPSHOT`，打包完成后不启动容器或运行接口测试。
额外的 Maven 参数可以直接传给脚本，例如 `build-native-image.bat -Dnative-image.parallelism=4`。

### 测试脚本

```bat
run-native-test.bat
```

使用已有的本地镜像进行测试，不执行 Maven 构建。镜像不存在时，脚本返回非零退出码并提示先运行打包脚本。
首次使用或代码有改动时，先运行 `build-native-image.bat` 更新镜像，再运行测试脚本。

测试流程：

1. 检查 Docker 和本地镜像
2. 启动测试容器，将端口 `8080` 映射到本机
3. 轮询 `docker logs`，等待 `Started NativeImageTestApplication`
4. 自动运行 `test-api.ps1` 的 32 个接口场景、`test-native-behavior.ps1` 的 18 项行为断言和 `test-validation.ps1` 的 26 项校验断言；任一失败时脚本返回非零退出码
5. 保存容器日志到 `target/native-container.log`，行为断言报告到 `target/native-assertions.json` 和 `target/validation-assertions.json`，然后清理测试容器；本地镜像会保留

> 构建耗时：首次约 10~20 分钟（拉取 buildpack 镜像 + GraalVM 编译），后续约 5 分钟。

容器内 Native 编译默认使用 8 个线程，避免占满本机所有逻辑处理器。可通过 `-Dnative-image.parallelism=4` 等参数调整。
本机 BellSoft NIK 25.0.4.1 的构建 JVM 曾多次发生 JVMCI / C2 崩溃，因此默认通过 `-J-XX:TieredStopAtLevel=1` 让构建器使用 HotSpot C1，并限制其处理器数量。
该参数只影响构建 JVM，不会将最终程序改为 JVM 应用，也不改变 Native Image 的优化等级。可通过 `native-image.builder-jvm-arguments` 属性覆盖。

### 手动步骤

```powershell
# 构建
mvn -Pnative clean spring-boot:build-image -DskipTests

# 运行
docker run -d --name native-test -p 8080:8080 native-image-test:0.0.1-SNAPSHOT
docker logs -f native-test

# 测试（另开窗口）
.\test-api.ps1
.\test-native-behavior.ps1
.\test-validation.ps1

# 清理
docker stop native-test
docker rm native-test
```
