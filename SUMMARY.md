# Spring Boot 4 + MyBatis-Plus + GraalVM Native Image 实战总结

> 目标：在 Windows 开发，将 Spring Boot 应用打包成可在 Linux Docker 中运行的 GraalVM Native Image。
>
> 最终成果：毫秒级启动、低内存占用、支持完整 CRUD + XML Mapper 分组统计的 native 镜像。

---

## 一、环境与技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| OS（开发） | Windows 11 | |
| OS（运行） | Linux（容器内） | |
| GraalVM | 25（JDK 25 LTS） | 提供 `native-image` 工具及 agent |
| Spring Boot | 4.0.6 | AOT 一等公民支持 |
| MyBatis-Plus | 3.5.16 | 官方 `mybatis-plus-spring-boot4-starter` |
| Database | MySQL | |
| 简化代码 | Lombok 1.18.46 | |
| 镜像构建 | Cloud Native Buildpacks | 通过 `mvn spring-boot:build-image` |

---

## 二、关键里程碑

1. **空项目跑通最小 HTTP 接口**（Spring Boot 4 + 一个 `/hello` 接口）
2. **打成 Linux Docker 镜像**（`mvn -Pnative spring-boot:build-image`）
3. **引入 MyBatis-Plus + MySQL** 完整 CRUD
4. **引入 Lombok** 简化实体
5. **引入 Service 层 + XML Mapper** 实现分组统计 SQL
6. **每一步都验证 native 模式下可正常运行**

---

## 三、踩坑实录（按出现顺序）

### 1. Docker 镜像名大小写

**报错**：`'NativeImageTest' must be a parsable name ... containing only [a-z0-9][.][_][-]`

**原因**：`artifactId` 含大写。Docker 镜像名必须全小写。

**解决**：在 `spring-boot-maven-plugin` 配置中显式指定镜像名：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <image>
            <name>native-image-test:${project.version}</name>
        </image>
    </configuration>
</plugin>
```

---

### 2. MyBatis LogFactory NPE

**报错**：`java.lang.NullPointerException at LogFactory.getLog`，发生在 `ClassPathMapperScanner.<clinit>`

**原因**：MyBatis 的 `LogFactory` 静态初始化通过反射依次尝试 SLF4J / Commons-Logging / Log4j2 等。Native Image 下这些 Log 类的构造方法未注册反射，全部探测失败导致 `logConstructor = null`。

**解决**：通过 `RuntimeHintsRegistrar` 显式注册 SLF4J Log 实现的构造方法。

---

### 3. @MapperScan AOT 注入失败

**报错**：`No qualifying bean of type 'java.lang.Class<?>' available`

**原因**：`MapperFactoryBean` 的构造方法签名是 `MapperFactoryBean(Class<T>)`。JVM 模式下 Spring 把 `Class` 当作 `BeanDefinition` 的构造参数值直接塞进去；AOT 模式下退化为"按类型自动注入"，找不到 `Class<?>` 类型的 Bean。

**解决**：去掉 `@MapperScan`，新建 `@Configuration` 显式声明：

```java
@Configuration
public class MyBatisConfig {
    @Bean
    public MapperFactoryBean<UserMapper> userMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<UserMapper> bean = new MapperFactoryBean<>(UserMapper.class);
        bean.setSqlSessionFactory(sqlSessionFactory);
        return bean;
    }
}
```

---

### 4. Javassist ProxyFactory 找不到

**报错**：`Cannot enable lazy loading because Javassist is not available`，`ClassNotFoundException: org.apache.ibatis.javassist.util.proxy.ProxyFactory`

**原因**：MyBatis 的 `Configuration` 构造方法**无条件**实例化 `JavassistProxyFactory`，内部用字符串反射加载 javassist 类。Native Image 静态分析时看不到直接引用，把这个 shaded 进 mybatis-core 的类裁掉了。

**解决**：注册 javassist 类反射 hints。

---

### 5. XMLLanguageDriver 无构造方法

**报错**：`NoSuchMethodException: org.apache.ibatis.scripting.xmltags.XMLLanguageDriver.<init>()`

**原因**：同 4，又一个内部通过字符串反射加载的类被裁。MyBatis 内部还有数十个这种"字符串配置 → 反射实例化"的模式。

**结论**：手写 hints 是打地鼠，**应该用 Tracing Agent 一次性收集**。

---

### 6. Lombok 注解处理器未运行

**报错**：`找不到符号: 方法 setId(java.lang.Long)`

**原因**：Spring Boot 4 starter parent 不再自动检测 `<optional>` 依赖中的注解处理器，需要在 `maven-compiler-plugin` 显式声明。

**解决**：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

---

### 7. XML Mapper 在 native 镜像下找不到

**报错**：`Invalid bound statement (not found): com.maxqiu.test.mapper.UserMapper.countByAge`

**原因**：MyBatis-Plus 通过 `classpath*:mapper/**/*.xml` 通配符扫描 XML，**这种 PathMatchingResourcePatternResolver 模式 agent 跟踪不到**（agent 只能记录明确文件名的资源加载），所以 native 镜像构建时 XML 被裁掉。

**解决**：显式注册资源 pattern：

```java
@Override
public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints.resources().registerPattern("mapper/*.xml");
    hints.resources().registerPattern("mapper/**/*.xml");
}
```

---

## 四、解决 Native Image hints 的两种策略

| 策略 | 适用场景 | 优劣 |
|------|---------|------|
| **手写 RuntimeHintsRegistrar** | 简单、可数的反射点；通配符资源 | 精准、可控；写多了像打地鼠 |
| **GraalVM Tracing Agent** | 框架级反射（MyBatis、Jackson、AOP 等） | 自动覆盖运行时实际路径；但通配符扫描捕获不到 |

**最佳实践**：**Agent + 手写 hints 互补使用**。Agent 负责框架反射、JDK 代理、序列化等"自动可追踪"的部分；手写 hints 负责通配符资源和明确知道的边界场景。

### Tracing Agent 操作

```powershell
# 1. 打 jar
mvn clean package -DskipTests

# 2. 启动 + 挂 agent（周期写入，无需优雅停止）
java "-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/<group>/<artifact>,config-write-period-secs=5" -jar target/xxx.jar

# 3. 调用全部接口（agent 只能记录跑到的代码路径）

# 4. 停止 JVM

# 5. 重打 native
mvn -Pnative spring-boot:build-image
```

`config-write-period-secs=5` 是关键 —— 周期性写入，避免因强制 kill 丢失 hints。

---

## 五、项目结构

```
NativeImageTest/
├── pom.xml                                          # 依赖与构建配置
├── test-api.ps1                                     # PowerShell 接口测试脚本
├── SUMMARY.md                                       # 本文件
└── src/main/
    ├── java/com/maxqiu/test/
    │   ├── NativeImageTestApplication.java          # 主类（@ImportRuntimeHints）
    │   ├── config/
    │   │   ├── MyBatisConfig.java                   # 显式 MapperFactoryBean
    │   │   └── MyBatisNativeHints.java              # XML 资源 hints
    │   ├── controller/
    │   │   └── UserController.java                  # REST 接口（6 个）
    │   ├── service/
    │   │   ├── UserService.java                     # IService<User>
    │   │   └── impl/UserServiceImpl.java            # ServiceImpl
    │   ├── mapper/
    │   │   └── UserMapper.java                      # BaseMapper<User>
    │   ├── entity/
    │   │   └── User.java                            # @Data @TableName
    │   └── dto/
    │       └── AgeCount.java                        # 分组统计 DTO
    └── resources/
        ├── application.yaml
        ├── mapper/
        │   └── UserMapper.xml                       # GROUP BY SQL
        └── META-INF/native-image/com.maxqiu/native-image-test/
            └── reachability-metadata.json           # agent 生成（~180KB）
```

---

## 六、HTTP 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/users` | 列表 |
| GET | `/users/{id}` | 单个查询 |
| POST | `/users` | 新增 |
| PUT | `/users/{id}` | 更新 |
| DELETE | `/users/{id}` | 删除 |
| GET | `/users/stats/age` | **XML Mapper 分组统计**（按年龄） |

---

## 七、构建与运行命令速查

### 本机 JVM 模式（最快验证业务）

```powershell
mvn spring-boot:run
```

### 收集 / 更新 hints（每次新增功能后跑一次）

```powershell
mvn clean package -DskipTests
java "-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image/com.maxqiu/native-image-test,config-write-period-secs=5" -jar target/NativeImageTest-0.0.1-SNAPSHOT.jar
# 调用所有接口（test-api.ps1）
# 然后停止 JVM
```

### 打 Linux Docker 镜像

```powershell
mvn -Pnative spring-boot:build-image
```

### 运行容器

```powershell
docker run --rm -p 8080:8080 `
  -e SPRING_DATASOURCE_URL="jdbc:mysql://192.168.220.201:3306/native_demo?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" `
  native-image-test:0.0.1-SNAPSHOT
```

### 执行接口测试

```powershell
.\test-api.ps1
```

---

## 八、心得与建议

1. **先 JVM 跑通，再 native 编译** —— Native 编译慢（10 分钟级别），不要把它当作调试手段，先保证 JVM 模式无误
2. **每加一个新功能就要重跑 agent** —— 没跑过的代码路径不会被记录
3. **Agent 解决不了通配符资源** —— 手写 `hints.resources().registerPattern(...)`
4. **Spring Boot AOT 处理框架的"特殊构造逻辑"未必完整** —— 像 `MapperFactoryBean(Class<?>)` 这种，需要手动显式声明 Bean 来绕过
5. **`config-write-period-secs` 是 agent 的救命参数** —— 避免强制 kill 时丢 hints
6. **保留 `@RegisterReflectionForBinding` 在实体 / DTO 上** —— Spring 自带的 AOT 注解，和 agent 互补
7. **native 镜像的优势在生产** —— 启动毫秒级、内存几十 MB，但代价是构建慢、debug 难

---

## 九、可继续探索的方向

- 接入 Spring Security / Redis / Cache，每次记得跑 agent
- 镜像瘦身：基础镜像换 `distroless` 或 `alpine`（注意 musl 静态链接）
- 可观测性：Micrometer + Prometheus 在 native 下的差异
- 多模块项目：每个 jar 单独维护自己的 `META-INF/native-image/<group>/<artifact>/`
- CI 集成：GitHub Actions / GitLab CI 中用 GraalVM 容器跑 agent + 编译
