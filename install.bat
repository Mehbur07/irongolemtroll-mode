@echo off
rem Builds Iron Golem Troll (with all tests) and installs it into the MehburMC test instance.
rem Also downloads Fabric API there if it is missing, verified by SHA-512.
setlocal EnableExtensions
chcp 65001 >nul

set "PROJECT=%~dp0"
set "MODS=%APPDATA%\MehburMC\game\mc\instances\iron-golem-troll-test-8f1ac0\mods"
set "FAPI_NAME=fabric-api-0.161.0+26.2.jar"
set "FAPI_URL=https://cdn.modrinth.com/data/P7dR8mSH/versions/ewUK83HI/fabric-api-0.161.0%%2B26.2.jar"
set "FAPI_SHA512=2502fa5ade78e9a120b3747bc1a9a2167d6671743bea45bf115d3174c94121aeda87c549446d1a604a9c11fb828de17a551e3514ce42f3e93069dd14bdaee55b"

title Iron Golem Troll - Build ^& Install
echo ============================================
echo   Iron Golem Troll - Derle ve Kur
echo ============================================
echo.

if not exist "%MODS%\" (
	echo [HATA] MehburMC mods klasoru bulunamadi:
	echo        %MODS%
	echo        Instance silinmis ya da adi degismis olabilir.
	goto :fail
)

rem Fall back to an installed Temurin JDK 25 when JAVA_HOME is missing (e.g. stale environment).
if not defined JAVA_HOME (
	for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do set "JAVA_HOME=%%~fJ"
)
if not defined JAVA_HOME (
	echo [HATA] Java 25 bulunamadi. Eclipse Temurin JDK 25 kurulu olmali.
	goto :fail
)

echo [1/3] Mod derleniyor ve testler calisiyor (yaklasik 20-30 sn)...
set "BUILD_LOG=%PROJECT%build\install-build.log"
if not exist "%PROJECT%build\" mkdir "%PROJECT%build"
pushd "%PROJECT%"
call "%PROJECT%gradlew.bat" build --console=plain > "%BUILD_LOG%" 2>&1
set "BUILD_RESULT=%ERRORLEVEL%"
popd
if not "%BUILD_RESULT%"=="0" (
	echo [HATA] Derleme ya da testler basarisiz oldu, kurulum yapilmadi. Son satirlar:
	echo.
	powershell -NoProfile -Command "Get-Content -LiteralPath $env:BUILD_LOG -Tail 25"
	echo.
	echo Tam kayit: %BUILD_LOG%
	goto :fail
)
for /f "delims=" %%L in ('findstr /c:"required tests passed" "%BUILD_LOG%"') do echo       %%L

set "MOD_JAR="
for %%F in ("%PROJECT%build\libs\iron-golem-troll-*.jar") do (
	echo %%~nxF | findstr /i /c:"-sources" >nul || set "MOD_JAR=%%~fF"
)
if not defined MOD_JAR (
	echo [HATA] Derlenen jar bulunamadi.
	goto :fail
)

echo [2/3] Mod kuruluyor...
del /q "%MODS%\iron-golem-troll-*.jar" 2>nul
copy /y "%MOD_JAR%" "%MODS%\" >nul || (
	echo [HATA] Jar kopyalanamadi. Oyun dosyayi kilitliyor olabilir, oyunu kapatip tekrar dene.
	goto :fail
)
for %%F in ("%MOD_JAR%") do echo       %%~nxF kuruldu.

echo [3/3] Fabric API kontrol ediliyor...
if exist "%MODS%\fabric-api-*.jar" (
	echo       Fabric API zaten var.
	goto :done
)
echo       Fabric API indiriliyor...
curl -fsSL -o "%MODS%\%FAPI_NAME%.part" "%FAPI_URL%" || (
	echo [HATA] Fabric API indirilemedi. Internet baglantini kontrol et.
	del /q "%MODS%\%FAPI_NAME%.part" 2>nul
	goto :fail
)
set "GOT_SHA="
for /f "skip=1 delims=" %%H in ('certutil -hashfile "%MODS%\%FAPI_NAME%.part" SHA512') do if not defined GOT_SHA set "GOT_SHA=%%H"
set "GOT_SHA=%GOT_SHA: =%"
if /i not "%GOT_SHA%"=="%FAPI_SHA512%" (
	echo [HATA] Fabric API dosyasinin dogrulamasi tutmadi, silindi.
	del /q "%MODS%\%FAPI_NAME%.part" 2>nul
	goto :fail
)
move /y "%MODS%\%FAPI_NAME%.part" "%MODS%\%FAPI_NAME%" >nul
echo       %FAPI_NAME% indirildi ve dogrulandi.

:done
echo.
echo Tamamlandi! MehburMC'de "Iron Golem Troll Test" instance'ini baslatabilirsin.
echo.
pause
exit /b 0

:fail
echo.
pause
exit /b 1
