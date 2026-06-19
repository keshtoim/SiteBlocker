@rem Gradle wrapper for Windows
@echo off
set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%

set GRADLE_OPTS=%GRADLE_OPTS% "-Xmx64m" "-Xms64m"

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: JAVA_HOME is not set and java.exe was not found in PATH.
    exit /b 1
)

%JAVA_EXE% -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" ^
  org.gradle.wrapper.GradleWrapperMain %*
