# Cursor IDE에서 JVM 옵션 추가 방법

## 방법 1: launch.json 설정 (권장)

1. `.vscode` 폴더에 `launch.json` 파일 생성 또는 수정
2. 다음 내용 추가:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot App",
      "request": "launch",
      "mainClass": "com.carizon.AppApplication",
      "projectName": "backend",
      "args": "",
      "vmArgs": "-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Seoul"
    }
  ]
}
```

## 방법 2: application.yaml에 설정

`src/main/resources/application.yaml`에 추가:

```yaml
spring:
  application:
    name: carizon-backend
  # JVM 옵션은 여기서 직접 설정 불가
```

## 방법 3: Maven 실행 설정

`pom.xml`에 플러그인 설정 추가:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
      <configuration>
        <jvmArguments>
          -Dfile.encoding=UTF-8
          -Duser.timezone=Asia/Seoul
        </jvmArguments>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## 방법 4: 환경 변수 설정

Windows PowerShell:
```powershell
$env:JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"
```

또는 시스템 환경 변수에 추가:
- 변수명: `JAVA_TOOL_OPTIONS`
- 값: `-Dfile.encoding=UTF-8`

## 방법 5: Cursor 설정에서 직접 추가

1. Cursor 설정 열기 (Ctrl + ,)
2. "java.configuration.vmargs" 검색
3. 다음 추가:
```
-Dfile.encoding=UTF-8
-Duser.timezone=Asia/Seoul
```

## 확인 방법

애플리케이션 시작 시 로그에서 확인:
```
file.encoding=UTF-8
user.timezone=Asia/Seoul
```

또는 Java 코드로 확인:
```java
System.out.println("file.encoding: " + System.getProperty("file.encoding"));
System.out.println("user.timezone: " + System.getProperty("user.timezone"));
```
