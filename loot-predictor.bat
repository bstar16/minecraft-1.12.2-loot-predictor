@echo off
setlocal
java -jar "%~dp0MinecraftLootPredictor.jar" %*
exit /b %errorlevel%
