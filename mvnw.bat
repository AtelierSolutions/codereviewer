@ECHO OFF
SETLOCAL ENABLEEXTENSIONS

REM Minimal Maven Wrapper for Windows.
REM - Downloads maven-wrapper.jar if missing
REM - Executes org.apache.maven.wrapper.MavenWrapperMain

SET "BASEDIR=%~dp0"
REM Strip trailing backslash to avoid \" argument parsing issues
IF "%BASEDIR:~-1%"=="\" SET "BASEDIR=%BASEDIR:~0,-1%"
SET "WRAPPER_DIR=%BASEDIR%\.mvn\wrapper"
SET "PROPS_FILE=%WRAPPER_DIR%\maven-wrapper.properties"
SET "WRAPPER_JAR=%WRAPPER_DIR%\maven-wrapper.jar"

IF NOT EXIST "%PROPS_FILE%" (
  ECHO [ERROR] Missing "%PROPS_FILE%".
  EXIT /B 1
)

FOR /F "usebackq tokens=1* delims==" %%A IN ("%PROPS_FILE%") DO (
  IF /I "%%A"=="wrapperUrl" SET "WRAPPER_URL=%%B"
)

IF "%WRAPPER_URL%"=="" (
  ECHO [ERROR] wrapperUrl not set in "%PROPS_FILE%".
  EXIT /B 1
)

REM Find Java
SET "JAVA_EXE=java"
IF NOT "%JAVA_HOME%"=="" (
  IF EXIST "%JAVA_HOME%\bin\java.exe" SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

REM Validate Java
IF /I "%JAVA_EXE%"=="java" (
  WHERE java >NUL 2>&1
  IF ERRORLEVEL 1 (
    ECHO [ERROR] Java not found. Install JDK 17+ and ensure JAVA_HOME or PATH is set.
    EXIT /B 1
  )
) ELSE (
  IF NOT EXIST "%JAVA_EXE%" (
    ECHO [ERROR] Java not found at "%JAVA_EXE%". Check JAVA_HOME is correct.
    EXIT /B 1
  )
)

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Downloading Maven Wrapper JAR...
  IF NOT EXIST "%WRAPPER_DIR%" MKDIR "%WRAPPER_DIR%" >NUL 2>&1

  REM Use PowerShell (present on Windows 10+) to download.
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "$u='%WRAPPER_URL%';" ^
    "$o='%WRAPPER_JAR%';" ^
    "Invoke-WebRequest -Uri $u -OutFile $o -UseBasicParsing"

  IF ERRORLEVEL 1 (
    ECHO [ERROR] Failed to download Maven Wrapper JAR from "%WRAPPER_URL%".
    EXIT /B 1
  )
)

REM Execute wrapper (single line to avoid caret continuation issues)
"%JAVA_EXE%" -Dmaven.multiModuleProjectDirectory="%BASEDIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*

EXIT /B %ERRORLEVEL%

