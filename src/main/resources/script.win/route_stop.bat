@echo off
chcp 65001 >nul
powershell Import-Module AudioDeviceCmdlets -ErrorAction SilentlyContinue
powershell "$dev=Get-AudioDevice -Playback|Where-Object{$_.Name -match '扬声器'};if($dev){Set-AudioDevice -Id $dev.Id}"
exit