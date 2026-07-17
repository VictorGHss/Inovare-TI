$content = [System.IO.File]::ReadAllText('c:\Projeto\Inovare-TI\logs_hoje_redirecionamento.txt', [System.Text.Encoding]::GetEncoding('iso-8859-1'))
$lines = $content -split "`n"
$lineNum = 0
$results = [System.Collections.Generic.List[string]]::new()
foreach ($line in $lines) {
    $lineNum++
    if ($line -match 'QUEUE-RESOLVER|fallback|Unauthorized|401|ERROR|WEBHOOK-EXEC|confirm_') {
        $results.Add("${lineNum}: $line")
    }
}
$results | Out-File 'C:\Projeto\Inovare-TI\log_analysis_matches.txt' -Encoding UTF8
Write-Output "Total matches: $($results.Count)"
