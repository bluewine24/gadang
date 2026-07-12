@echo off
set "JAVA_HOME=C:\Program Files\sts-5.0.1.RELEASE\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_25.0.1.v20251108-1451\jre"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "C:\SSAFY\pjt13_buk03_15_04\Backend"
echo [STARTUP] java=%JAVA_HOME% >> "C:\SSAFY\pjt13_buk03_15_04\backend.log"
echo [STARTUP] cwd=%CD% >> "C:\SSAFY\pjt13_buk03_15_04\backend.log"
call "%CD%\mvnw.cmd" spring-boot:run -Dmaven.test.skip=true >> "C:\SSAFY\pjt13_buk03_15_04\backend.log" 2>&1