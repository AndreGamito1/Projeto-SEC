# Highly Dependable Systems Project

- André Pereira -  ist1103082
- André Gamito  -  ist1104167
- Miguel Bibi   -  ist1102737

## System Components

- **Leader**: Coordinates the blockchain network
- **Members**: Participate in consensus
- **ClientLibrary**: Provides a REST API for interacting with the blockchain
- **Clients**: Applications that interact with the blockchain

## Quick Start

You can start the demo using either the provided PowerShell script (Windows) or Bash script (Linux/macOS).

### Windows (PowerShell)

```powershell
# Start with default settings (2 clients)
.\Start-BlockchainSystem.ps1

# Start with custom settings
.\Start-BlockchainSystem.ps1 -HttpPort 8080 -NumClients 3

# Start only the REST API (no clients)
.\Start-BlockchainSystem.ps1 -RestApiOnly
```

### Linux/macOS (Bash)

```bash
# Make the script executable (first time only)
chmod +x run.sh

# Start with default settings (2 clients)
./run.sh

# Start with custom settings
./run.sh --http-port 8080 --num-clients 3
# or using short options
./run.sh -p 8080 -n 3

# Start only the REST API (no clients)
./run.sh --rest-api-only
# or using short options
./run.sh -r

# Use a specific terminal program
./run.sh -t konsole
```

## Manual Startup

If you prefer to start each component manually, follow these steps:

### 1. Prepare Key Directories

```bash
# Create/clean directories for keys
mkdir -p shared/priv_keys shared/pub_keys
rm -rf shared/priv_keys/* shared/pub_keys/*
```

### 2. Start Leader

Open a new terminal and run:

```bash
mvn exec:java -Dexec.mainClass=com.example.Leader
```

Wait a few seconds for the Leader to initialize.

### 3. Start Members

Open a new terminal for each member (1-4) and run:

```bash
# For Member 1
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member1

# For Member 2
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member2

# Repeat for Members N members
```

Wait a few seconds for all Members to initialize.

### 4. Start ClientLibrary with REST API

Open a new terminal and run:

```bash
# Start ClientLibrary with REST API on port 8080
mvn exec:java -Dexec.mainClass=com.example.ClientLibrary -Dexec.args=8080
```

The REST API will be available at `http://localhost:8081/blockchain/` (port + 1).

### 5. Start Clients

Open a new terminal for each client and run:

```bash
# Start Client 1
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Client1

# Start Client 2
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Client2

# For additional clients, use a unique client ID
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Alice
```

## Adding More Clients Later

### Using the Helper Function (When Started with Scripts)

#### PowerShell
```powershell
Start-BlockchainClient -ClientId "Alice"
```

#### Bash
```bash
start_blockchain_client Alice
```

### Manually
```bash
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=CustomClientName
```


## System Requirements

- Java 8 or higher
- Maven
- PowerShell (for Windows) or Bash (for Linux/macOS)
- A terminal emulator (gnome-terminal, konsole, xterm, etc.) for Linux/macOS