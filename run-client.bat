@echo off
REM =====================================================================
REM run-client.bat — OmniChest (Stonecutter) クライアント起動ヘルパ
REM ---------------------------------------------------------------------
REM   omnichest は自己完結した Stonecutter included build。 版ノードタスク
REM   :<MC>:runClient を mods\omnichest の中で実行する。
REM   26.1.x は loom-back-compat の都合で Gradle デーモン自身が JDK 25 で
REM   起動している必要があるため JAVA_HOME を JDK 25 に向ける。
REM   (JDK のパスは環境に合わせて調整すること)
REM
REM   使い方:
REM     run-client.bat              -> 推奨版 26.1.2 を起動
REM     run-client.bat 1.21.11      -> 指定した MC 版を起動
REM =====================================================================
setlocal
set JAVA_HOME=C:\Users\ppapk\.jdks\jdk-25.0.3+9

set MC=%1
if "%MC%"=="" set MC=26.1.2

pushd "%~dp0mods\omnichest"
call gradlew.bat :%MC%:runClient
popd
endlocal
