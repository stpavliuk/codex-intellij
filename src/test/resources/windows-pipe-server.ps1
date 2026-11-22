$ErrorActionPreference = 'Stop'
$pipe = [System.IO.Pipes.NamedPipeServerStream]::new(
    'PIPE_NAME', [System.IO.Pipes.PipeDirection]::InOut, 1,
    [System.IO.Pipes.PipeTransmissionMode]::Byte,
    [System.IO.Pipes.PipeOptions]::Asynchronous
)

function Read-Fully([byte[]] $buffer) {
    $offset = 0
    while ($offset -lt $buffer.Length) {
        $count = $pipe.Read($buffer, $offset, $buffer.Length - $offset)
        if ($count -eq 0) { throw 'Unexpected EOF' }
        $offset += $count
    }
}

try {
    [Console]::WriteLine('READY')
    $pipe.WaitForConnection()
    $header = [byte[]]::new(4)
    Read-Fully $header
    $payload = [byte[]]::new([BitConverter]::ToInt32($header, 0))
    Read-Fully $payload
    $pipe.Write($header, 0, 1)
    $pipe.Write($header, 1, 3)
    $pipe.Write($payload, 0, $payload.Length)
    $pipe.Flush()
    [Console]::WriteLine('IDLE')
    [Console]::ReadLine() | Out-Null
} finally {
    $pipe.Dispose()
}
