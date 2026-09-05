@echo off
rem PractiseAura 一键构建脚本（自动使用项目自带的 JDK 25 + Maven）
setlocal
cd /d "%~dp0"
set "JAVA_HOME=%~dp0tools\jdk-25.0.4.1"
tools\apache-maven-3.9.9\bin\mvn.cmd -B -ntp package
if %errorlevel%==0 (
  echo.
  echo 构建成功: target\PractiseAura-1.0.0.jar
  copy /y target\PractiseAura-1.0.0.jar run\plugins\ >nul
  echo 已同步到 run\plugins\
)
endlocal
