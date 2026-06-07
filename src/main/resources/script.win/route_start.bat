@echo off
chcp 65001 >nul
powershell Import-Module AudioDeviceCmdlets -ErrorAction SilentlyContinue
powershell "$dev=Get-AudioDevice -Playback|Where-Object{$_.Name -eq 'CABLE Input (VB-Audio Virtual Cable)'};if($dev){Set-AudioDevice -Id $dev.Id}"
exit