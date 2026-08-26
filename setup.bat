@echo off
setlocal
java -jar "%~dp0MinecraftLootPredictor.jar" setup %*
if errorlevel 1 pause
exit /b %errorlevel%
