# Open a terminal for the Leader
Start-Process powershell -ArgumentList "-NoExit", "-Command", "& {mvn exec:java '-Dexec.mainClass=com.example.Leader'}"

# Open 4 terminals for members
for ($i = 1; $i -le 4; $i++) {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "& {mvn exec:java '-Dexec.mainClass=com.example.Member' '-Dexec.args=member$i'}"
}

# Open a terminal for the client
Start-Process powershell -ArgumentList "-NoExit", "-Command", "& {mvn exec:java '-Dexec.mainClass=com.example.ClientLibrary'}"