# 一个 jar 就能跑，所以镜像也分两层：一层用 Maven 打包，一层只有 JRE 和那个 jar。
# 最终镜像不带 Maven、不带源码、不带 ~/.m2。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 先只拷 pom：依赖不变时这一层能复用，改了源码不用重新下依赖。
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -q package -DskipTests

FROM eclipse-temurin:17-jre

# 不用 root 跑：要扫的代码是挂进来的，容器里没有任何要写的东西。
RUN useradd --system --create-home --shell /usr/sbin/nologin scanner
WORKDIR /src

COPY --from=build /build/target/llm-lint.jar /app/llm-lint.jar
USER scanner

ENTRYPOINT ["java", "-jar", "/app/llm-lint.jar"]
CMD ["--help"]