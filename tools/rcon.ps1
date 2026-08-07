param(
    [string]$Server = "26.119.147.48",
    [int]$Port = 25575,
    [string]$Password = "td-test-2026",
    [Parameter(Mandatory = $true)][string]$Command
)

$ErrorActionPreference = "Stop"
$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($Server, $Port)
    $stream = $client.GetStream()

    function Send-RconPacket($client, $stream, [int]$id, [int]$type, [string]$payload) {
        $payloadBytes = [System.Text.Encoding]::ASCII.GetBytes($payload)
        $length = 4 + 4 + $payloadBytes.Length + 2
        $packet = New-Object byte[] (4 + $length)
        [BitConverter]::GetBytes([int]$length) | ForEach-Object { $packet[$script:i++] = $_ }
        [BitConverter]::GetBytes([int]$id) | ForEach-Object { $packet[$script:i++] = $_ }
        [BitConverter]::GetBytes([int]$type) | ForEach-Object { $packet[$script:i++] = $_ }
        $payloadBytes | ForEach-Object { $packet[$script:i++] = $_ }
        $packet[$script:i++] = 0; $packet[$script:i++] = 0
        $stream.Write($packet, 0, $packet.Length)
        $stream.Flush()
    }

    function Read-RconPacket($stream) {
        $lenBuf = New-Object byte[] 4
        $read = 0
        while ($read -lt 4) { $n = $stream.Read($lenBuf, $read, 4 - $read); if ($n -le 0) { throw "connection closed" }; $read += $n }
        $length = [BitConverter]::ToInt32($lenBuf, 0)
        $body = New-Object byte[] $length
        $read = 0
        while ($read -lt $length) { $n = $stream.Read($body, $read, $length - $read); if ($n -le 0) { throw "connection closed" }; $read += $n }
        $id = [BitConverter]::ToInt32($body, 0)
        $type = [BitConverter]::ToInt32($body, 4)
        $payload = [System.Text.Encoding]::ASCII.GetString($body, 8, $length - 10)
        return @{ Id = $id; Type = $type; Payload = $payload }
    }

    $script:i = 0
    Send-RconPacket $client $stream 1 3 $Password
    $auth = Read-RconPacket $stream
    if ($auth.Id -ne 1) { throw "RCON auth failed (id=$($auth.Id))" }

    $script:i = 0
    Send-RconPacket $client $stream 2 2 $Command
    do {
        $resp = Read-RconPacket $stream
    } while ($resp.Type -ne 0 -and $resp.Type -ne 2)

    Write-Output $resp.Payload
}
finally {
    $client.Close()
}
