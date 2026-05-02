@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET "MVN_CMD=mvn") ELSE (SET "MVN_CMD=%__MVNW_ARG0_NAME__%")
@SET MAVEN_PROJECTBASEDIR=%~dp0

@IF EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" GOTO validateJavaHome

:validateJavaHome
@IF NOT "%JAVA_HOME%"=="" GOTO checkJavaHome
@ECHO Error: JAVA_HOME not found in your environment.
@EXIT /B 1

:checkJavaHome
@SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
@IF EXIST "%JAVA_EXE%" GOTO executeCmd

:executeCmd
@SET MAVEN_OPTS=%MAVEN_OPTS%
@"%JAVA_EXE%" -jar "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar" %* && EXIT /B 0 || (
  @REM fallback to system mvn
  mvn %*
)
