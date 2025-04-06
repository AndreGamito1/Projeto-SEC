# Highly Dependable Systems Project - Stage 2

- André Pereira -  ist1103082
- André Gamito  -  ist1104167
- Miguel Bibi   -  ist1102737

## Demo Quick Start

You can start the demo using either the provided PowerShell script (Windows) or Bash script (Linux/macOS).

### Windows (PowerShell)

```powershell
.\Start-BlockchainSystem.ps1
```

### Linux/macOS (Bash)

```bash
chmod +x run.sh

./run.sh
```

## Test Scripts

```bash
# TODO Talk about tests scripts 
```

## Manual Startup

To start each component manually:

### 1. Prepare Key Directories

```bash
# Create/clean directories for keys
mkdir -p shared/priv_keys shared/pub_keys
rm -rf shared/priv_keys/* shared/pub_keys/*
```

## 2. Start Members

Open a new terminal for each member (1-4) and run:

```bash
# For Member 1
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member1

# For Member 2
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member2

# For Member 3
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member3

# For Member 4
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member4

```

### 4. Start ClientLibrary

Open a new terminal and run:

```bash
# Start ClientLibrary with REST API on port 8080
mvn exec:java -Dexec.mainClass=com.example.ClientLibrary -Dexec.args=8080
```

### 5. Start Clients

Open a new terminal for each client and run:


```bash
# Make sure the client exists in accounts.json & genesisBlock.json
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Client1
```

### System Requirements

- Java 8 or higher
- Maven
