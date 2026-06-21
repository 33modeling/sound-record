@rem Gradle startup script for Windows
@echo off
set APP_HOME=%~dp0
set JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%JAR%" (
  echo Missing Gradle wrapper jar: %JAR% 1>&2
  exit /b 1
)

java -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
