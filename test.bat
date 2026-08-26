@echo off
setlocal
java -jar "%~dp0MinecraftLootPredictor.jar" self-test %*
if errorlevel 1 pause
exit /b %errorlevel%
