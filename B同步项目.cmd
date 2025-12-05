@echo off
chcp 65001 >nul
cd /d %~dp0

echo 正在从 GitHub 同步项目...

git fetch GlazedTile_QQBot
git pull GlazedTile_QQBot main

echo 同步完成！
pause
