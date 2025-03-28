# Highly Dependable Systems Project - Stage 1

- André Pereira -  ist1103082
- André Gamito  -  ist1104167
- Miguel Bibi   -  ist1102737

## TODO

#### Main
- ~~Criar Block.java, Transaction.java, WorldState.java e AccountState.java~~

- ~~Implementar o genesisBlock.json~~


#### Client
- Criar "See Balance": 
    - No client.java & clientLibrary.java
- Criar "Send Money": 
    - No client.java & clientLibrary.java
    - Temos de enviar um objecto "trasaction" ou um json e o leader cria o object "transaction" de accordo com o json

#### Leader
- Interpertar resposta do client e formar um bloco (fazer transaction class --> bloco class)
- Fazer o consenso sobre o bloco
- Atualizar o world state

### Passos de Implementação

- ~~**Step 1**: Garantir que todos os requisitos da primeira etapa sejam atendidos.~~
- **Step 2**: Implementar os contratos inteligentes em Solidity e testar sua execução usando o código base EVM do Hyperledger Besu fornecido no segundo trabalho de laboratório (ou seja, lab2).
- **Step 3**: Implementar o conceito de bloco gênesis, transações, execução de transações (por exemplo, execução de contratos inteligentes), bem como a adição e persistência de blocos.
- **Step 4**: Conectar o algoritmo de consenso desenvolvido na primeira etapa aos componentes desenvolvidos na primeira etapa. O consenso deve ser responsável por decidir a ordem das transações a serem executadas e adicionadas ao blockchain.
- **Step 5**: Implementar um conjunto completo de testes que demonstrem que a implementação é robusta contra comportamentos bizantinos.



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
