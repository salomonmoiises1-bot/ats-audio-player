@rem Gradle startup script for Windows
@if "%DEBUG%" == "" @echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_HOME=%DIRNAME%
set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
if exist "%CLASSPATH%" (
    "%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) else (
    gradle %*
)
