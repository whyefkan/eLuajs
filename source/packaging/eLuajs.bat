@echo off
setlocal
set "BASE=%~dp0"
set "JAVA=%BASE%runtime\bin\javaw.exe"
if not exist "%JAVA%" set "JAVA=%BASE%runtime\bin\java.exe"
if not exist "%JAVA%" set "JAVA=javaw"
start "" "%JAVA%" -Xms64m -Xmx1024m -Dfile.encoding=UTF-8 -jar "%BASE%app\eLuajs.jar"
