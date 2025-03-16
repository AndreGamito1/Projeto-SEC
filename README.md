# Highly Dependable Systems Project - Stage 1

- André Pereira -  ist1103082
- André Gamito  -  ist1104167
- Miguel Bibi   -  ist1102737

## Demo Quick Start

Due to a late discovery of architectural flaws, we were forced to make significant changes to our architecture, which left us without enough time to complete the full implementation.

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

## Manual Startup

To start each component manually:

### 1. Prepare Key Directories

```bash
# Create/clean directories for keys
mkdir -p shared/priv_keys shared/pub_keys
rm -rf shared/priv_keys/* shared/pub_keys/*
```

## 2. Start Leader

Open a new terminal and run:

```bash
mvn exec:java -Dexec.mainClass=com.example.Leader
```

Wait a few seconds for the Leader to initialize.

## 3. Start Members

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

Wait a few seconds for all Members to initialize.

### 4. Start ClientLibrary

Open a new terminal and run:

```bash
# Start ClientLibrary with REST API on port 8080
mvn exec:java -Dexec.mainClass=com.example.ClientLibrary -Dexec.args=8080
```

### 5. Start Clients

Open a new terminal for each client and run:

```bash
# Start Client 1
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Client1

# For additional clients, use a unique client ID
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Client2
```

### System Requirements

- Java 8 or higher
- Maven
