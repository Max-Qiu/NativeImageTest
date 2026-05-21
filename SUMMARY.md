# Spring Boot 4 + MyBatis-Plus + Redis Cache + GraalVM Native Image 示例

> 目标：在 Windows 开发，把 Spring Boot 应用打包成可在 Linux Docker 中运行的 GraalVM Native Image。
>
> 覆盖能力：完整 User CRUD（MySQL / XML Mapper 分组统计）+ Spring Cache 注解 + `StringRedisTemplate` 直接操作。

---

## 一、技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| OS（开发） | Windows 11 | |
| OS（运行） | Linux（容器内） | |
| GraalVM | 25（JDK 25 LTS） | 提供 `native-image` 与 tracing agent |
| Spring Boot | 4.0.6 | AOT 一等公民 |
| MyBatis-Plus | 3.5.16 | `mybatis-plus-spring-boot4-starter` |
| Database | MySQL | 通过 HikariCP |
| Cache | Redis | `spring-boot-starter-data-redis` + `spring-boot-starter-cache` |
| Lombok | 1.18.x | |
| 镜像构建 | Cloud Native Buildpacks | `mvn -Pnative spring-boot:build-image` |

---

## 二、项目结构

```
NativeImageTest/
├── pom.xml
├── collect-hints.bat                                # 一键收集 native-image hints
├── test-api.ps1                                     # PowerShell 接口测试脚本
├── SUMMARY.md
└── src/main/
    ├── java/com/maxqiu/test/
    │   ├── NativeImageTestApplication.java          # @EnableCaching + @ImportRuntimeHints
    │   ├── config/
    │   │   ├── MyBatisConfig.java                   # 显式 MapperFactoryBean
    │   │   ├── MyBatisNativeHints.java              # XML 资源 hints
    │   │   ├── CacheConfig.java                     # RedisCacheConfiguration（key/value 序列化、TTL、前缀）
    │   │   └── Jackson3JsonRedisSerializer.java     # 自定义 JSON 序列化器
    │   ├── controller/
    │   │   ├── UserController.java                  # User CRUD（纯 MySQL）
    │   │   └── CacheController.java                 # 缓存注解 + RedisTemplate
    │   ├── service/
    │   │   ├── UserService.java
    │   │   ├── CacheService.java                    # @Cacheable / @CachePut / @CacheEvict 演示
    │   │   └── impl/{UserServiceImpl,CacheServiceImpl}.java
    │   ├── mapper/UserMapper.java                   # BaseMapper + XML countByAge
    │   ├── entity/User.java                         # @RegisterReflectionForBinding
    │   └── dto/AgeCount.java                        # @RegisterReflectionForBinding
    └── resources/
        ├── application.yaml                         # MySQL / Redis / cache:type=redis
        ├── mapper/UserMapper.xml                    # GROUP BY SQL
        └── META-INF/native-image/com.maxqiu/native-image-test/
            └── reachability-metadata.json           # agent 生成（~230KB）
```

---

## 三、HTTP 接口清单

### User CRUD（纯 MySQL）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/users` | 列表 |
| GET | `/users/{id}` | 单个查询 |
| POST | `/users` | 新增 |
| PUT | `/users/{id}` | 更新 |
| DELETE | `/users/{id}` | 删除 |
| GET | `/users/stats/age` | XML Mapper 分组统计（按年龄） |

### Spring Cache 注解（`@Cacheable / @CachePut / @CacheEvict`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/cache/anno/load?key=` | `@Cacheable`：未命中时执行方法并累加 hits |
| POST | `/cache/anno/put?key=&value=` | `@CachePut`：覆盖缓存值 |
| DELETE | `/cache/anno/evict?key=` | `@CacheEvict`：按 key 失效 |
| DELETE | `/cache/anno/evictAll` | `@CacheEvict(allEntries=true)` |
| POST | `/cache/anno/resetHits` | 重置内部命中计数 |

### `StringRedisTemplate` 直接操作

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/cache/redis/keys` | 列出所有 `native-demo:*` |
| GET | `/cache/redis/get?key=` | 取值 |
| POST | `/cache/redis/set?key=&value=&ttlSeconds=` | 写入（TTL 可选） |
| DELETE | `/cache/redis/delete?key=` | 删除 key |

---

## 四、使用方法

### 0. 准备外部依赖

`application.yaml` 默认连：

- MySQL：`192.168.220.201:3306/native_demo`（用户 `root` / 密码 `123`）
- Redis：`192.168.220.201:6379`（密码 `123`）

请按本机环境改 `src/main/resources/application.yaml`，或通过环境变量覆盖：
`SPRING_DATASOURCE_URL` / `SPRING_DATA_REDIS_HOST` 等。

数据库需要事先建好 `t_user` 表（字段：`id BIGINT AUTO_INCREMENT PK, name VARCHAR, age INT`）。

### 1. 本机 JVM 运行（最快验证业务）

```powershell
mvn spring-boot:run
```

访问 `http://localhost:8080`。这是开发调试的首选模式：编译快、可热重载、报错好读。

### 2. 执行接口测试

应用运行后，在另一个 PowerShell 窗口：

```powershell
.\test-api.ps1
```

脚本会按顺序调用 22 个端点，覆盖：
- PART 1：User CRUD
- PART 2：缓存注解的命中 / 覆盖 / 失效行为（带 hits 计数器验证）
- PART 3：`StringRedisTemplate` 的 `set / get / keys / delete`（含 TTL）

### 3. 收集 / 更新 native-image hints

每当新增功能、改动反射 / 资源加载相关代码，都需要重跑 agent 让 `reachability-metadata.json` 同步。

**一键脚本**（推荐）：

```bat
collect-hints.bat
```

脚本流程：

1. `mvn clean package -DskipTests`
2. 后台启动 jar，带 `-agentlib:native-image-agent=config-merge-dir=...,config-write-period-secs=5`，日志写到 `target/agent-run.log`
3. 轮询日志，等 `Started NativeImageTestApplication`（超时 120s，捕获启动失败提前退出）
4. 自动执行 `test-api.ps1` 跑遍所有接口
5. 等 7s 让 agent 周期落盘 → `taskkill` → 清掉残留 `.lock`

执行完后 `git diff src/main/resources/META-INF/native-image/` 可看到 hints 增量。

**手动等价命令**（需要排错时可拆开跑）：

```powershell
mvn clean package -DskipTests
java "-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/com.maxqiu/native-image-test,config-write-period-secs=5" -jar target/NativeImageTest-0.0.1-SNAPSHOT.jar
# 另开窗口：.\test-api.ps1
# 测完后停掉 JVM
```

> `config-write-period-secs=5` 是关键参数：周期性落盘，避免 Windows 强制 kill 时丢 hints。

### 4. 打 Linux Docker 镜像（native）

```powershell
mvn -Pnative spring-boot:build-image
```

构建慢（10 分钟级），构建期间不需要服务运行。产物镜像名：`native-image-test:0.0.1-SNAPSHOT`。

### 5. 运行 native 容器

```powershell
docker run --rm -p 8080:8080 native-image-test:0.0.1-SNAPSHOT
```

启动毫秒级，可再次用 `test-api.ps1` 验证。

---

## 五、Hints 策略要点

| 策略 | 适用场景 |
|------|---------|
| **GraalVM Tracing Agent** | 框架级反射（MyBatis、Jackson、Spring Cache 代理等），自动覆盖运行时路径 |
| **手写 `RuntimeHintsRegistrar`** | 通配符资源（`classpath*:mapper/**/*.xml`）、agent 看不到的边界场景 |
| **`@RegisterReflectionForBinding`** | 实体 / DTO 的序列化反射，注解形式更内聚 |

两者互补：agent 解决"跑到才记录"的反射调用，手写 hints 兜底通配符与未触达路径。

新增功能（新 Controller / 新 Entity / 新缓存 key 等）后请重新跑 `collect-hints.bat`，并把 `reachability-metadata.json` 的 diff 一起提交。
