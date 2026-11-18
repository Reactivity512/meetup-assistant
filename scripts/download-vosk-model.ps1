param(
    [ValidateSet('ru', 'en')]
    [string]$Language = 'ru',
    [string]$ModelsDir = (Join-Path $PSScriptRoot '..\models')
)

$ErrorActionPreference = 'Stop'

$modelName = switch ($Language) {
    'ru' { 'vosk-model-small-ru-0.22' }
    'en' { 'vosk-model-small-en-us-0.15' }
}

# The official host is sometimes throttled to a few hundred bytes per second.
# The Russian model is also published in alphacep's own GitHub repository, which
# serves the exact same archive much faster. The official URL stays as a fallback.
$urls = switch ($Language) {
    'ru' {
        @(
            "https://raw.githubusercontent.com/alphacep/vosk-unity-asr/master/Assets/StreamingAssets/$modelName.zip",
            "https://alphacephei.com/vosk/models/$modelName.zip"
        )
    }
    'en' {
        @("https://alphacephei.com/vosk/models/$modelName.zip")
    }
}

$target = Join-Path $ModelsDir $modelName
if (Test-Path (Join-Path $target 'conf')) {
    Write-Host "Model already exists: $target"
    exit 0
}

New-Item -ItemType Directory -Force -Path $ModelsDir | Out-Null
$zipPath = Join-Path ([System.IO.Path]::GetTempPath()) "$modelName.zip"

$downloaded = $false
foreach ($url in $urls) {
    try {
        Write-Host "Downloading $url ..."
        Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $zipPath -TimeoutSec 300
        $downloaded = $true
        break
    } catch {
        Write-Warning "Download failed from $url : $($_.Exception.Message)"
    }
}

if (-not $downloaded) {
    throw "Could not download $modelName. Download it manually from https://alphacephei.com/vosk/models and unpack into $ModelsDir."
}

Write-Host "Extracting to $ModelsDir ..."
Expand-Archive -Path $zipPath -DestinationPath $ModelsDir -Force
Remove-Item $zipPath -Force

if (-not (Test-Path (Join-Path $target 'conf'))) {
    throw "The archive was extracted, but $target does not look like a Vosk model (no conf directory)."
}

Write-Host ""
Write-Host "Done. Model path: $target"
Write-Host "Default application.yml expects: ./models/$modelName"
