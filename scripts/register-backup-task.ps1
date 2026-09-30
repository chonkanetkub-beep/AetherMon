## How to register the daily backup scheduled task
## Run this ONE TIME in PowerShell as Administrator

## Step 1: Open PowerShell as Admin
## (Right-click the Start button -> "Windows PowerShell (Admin)" or "Terminal (Admin)")

## Step 2: Paste and run this entire block:

$action = New-ScheduledTaskAction -Execute "C:\AetherMon\scripts\backup.bat"
$trigger = New-ScheduledTaskTrigger -Daily -At "04:00AM"
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -RunOnlyIfNetworkAvailable:$false
Register-ScheduledTask -TaskName "AethermonDailyBackup" `
    -Action $action -Trigger $trigger -Settings $settings `
    -RunLevel Highest `
    -Description "Daily backup of Aethermon Minecraft server (keeps 7 days)" `
    -Force

Write-Host "Done! Backup will run every day at 4:00 AM."

## Step 3: Confirm it registered by running:
## Get-ScheduledTask -TaskName "AethermonDailyBackup"

## To run it manually anytime:
## Start-ScheduledTask -TaskName "AethermonDailyBackup"

## To remove it:
## Unregister-ScheduledTask -TaskName "AethermonDailyBackup"
