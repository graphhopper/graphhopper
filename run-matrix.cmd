@echo off
setlocal
cd /d "%~dp0"
if not exist web\target\graphhopper-web-11.0-SNAPSHOT.jar (
  echo Build first: mvn -B -pl web -am verify
  exit /b 1
)
if /I "%~1"=="regional" (
  java -Xmx4g -jar web\target\graphhopper-web-11.0-SNAPSHOT.jar server docs\matrix\config-manchester.yml
) else (
  java -Xmx2g -jar web\target\graphhopper-web-11.0-SNAPSHOT.jar server docs\matrix\config-matrix.yml
)
