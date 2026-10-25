$ErrorActionPreference = 'Stop'
$gradleVersion = '8.10.2'
$gradleDir = "$env:TEMP\gradle-install"

$jdkZip = "$env:TEMP\jdk-21.zip"
$jdkDir = "$env:TEMP\jdk-21-install"

if (-Not (Test-Path "$jdkDir")) {
    Write-Host "Downloading JDK 21..."
    Invoke-WebRequest -Uri "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" -OutFile $jdkZip
    Write-Host "Extracting JDK..."
    Expand-Archive -Path $jdkZip -DestinationPath $jdkDir -Force
}

$jdkPath = Get-ChildItem -Path $jdkDir -Directory | Select-Object -First 1
$env:JAVA_HOME = $jdkPath.FullName

Write-Host "Running build with JAVA_HOME=$env:JAVA_HOME..."
& "$gradleDir\gradle-$gradleVersion\bin\gradle.bat" clean build
